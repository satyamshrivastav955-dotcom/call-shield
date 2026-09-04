"""REST API surface (auth, contacts, chat, calls, verdicts, reports, flags, trust circle)."""
from __future__ import annotations

import asyncio
import datetime as dt
import logging

import numpy as np
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from pydantic import BaseModel

from ..config import get_config
from ..storage import get_db
from ..storage.models import User
from .auth import current_user, request_otp, verify_otp

log = logging.getLogger(__name__)
router = APIRouter()


# ---------------- request schemas ----------------
class OtpRequest(BaseModel):
    phone: str


class OtpVerify(BaseModel):
    phone: str
    otp: str
    display_name: str | None = None


class ContactCreate(BaseModel):
    peer_phone: str
    label: str
    relationship_tag: str | None = None
    is_trusted: bool = False


class ProfileUpdate(BaseModel):
    display_name: str | None = None
    avatar: str | None = None


class ChatSend(BaseModel):
    recipient_phone: str
    body: str


class FlagSubmit(BaseModel):
    phone: str
    kind: str = "scam"          # scam | ai-generated
    note: str | None = None


class VerifyResponse(BaseModel):
    session_key: str
    answer: bool


class FreezeDecision(BaseModel):
    freeze_id: int
    decision: str               # confirm | override


class DeviceRegister(BaseModel):
    platform: str = "android"   # android | ios | web
    token: str


class ExternalNotify(BaseModel):
    """An external notification (WhatsApp/SMS/Telegram etc.) captured by the
    app's NotificationListenerService -> ingested by the agentic graph."""
    source: str = "whatsapp"    # whatsapp | sms | telegram | other
    sender: str = ""
    text: str = ""


# ---------------- auth ----------------
@router.post("/auth/otp")
def otp(req: OtpRequest):
    return request_otp(req.phone)


@router.post("/auth/verify")
def verify(req: OtpVerify):
    return verify_otp(req.phone, req.otp, req.display_name)


@router.get("/auth/me")
def me(user: User = Depends(current_user)):
    return {"user_id": user.id, "display_name": user.display_name, "created_at": user.created_at.isoformat()}


# ---------------- contacts ----------------
@router.get("/contacts")
def list_contacts(user: User = Depends(current_user)):
    db = get_db()
    out = []
    for c in db.list_contacts(user.id):
        peer = db.get_user(c.peer_id)
        out.append({
            "contact_id": c.id, "peer_id": c.peer_id,
            "phone": db.phone_of(peer) if peer else None,
            "label": c.label, "relationship_tag": c.relationship_tag,
            "is_trusted": c.is_trusted, "linked": c.linked,
        })
    return {"contacts": out}


@router.post("/contacts")
def add_contact(req: ContactCreate, user: User = Depends(current_user)):
    db = get_db()
    peer = db.get_user_by_phone(req.peer_phone)
    if peer is None:
        # auto-provision: any number can be saved as a contact (they join
        # later); removes the 404 wall when two phones link each other
        peer = db.create_user(req.peer_phone, req.label or "Contact")
    c = db.add_contact(user.id, peer.id, req.label, req.relationship_tag, req.is_trusted)
    return {"contact_id": c.id}


# ---------------- profile ----------------
@router.patch("/profile")
def update_profile(req: ProfileUpdate, user: User = Depends(current_user)):
    db = get_db()
    with db.session() as s:
        u = s.get(User, user.id)
        if req.display_name:
            u.display_name = req.display_name
        if req.avatar:
            u.avatar = req.avatar
        s.commit()
        s.refresh(u)
    return {"display_name": u.display_name}


# ---------------- chat ----------------
@router.get("/chat/history")
def chat_history(peer_phone: str, limit: int = 100, user: User = Depends(current_user)):
    db = get_db()
    peer = db.get_user_by_phone(peer_phone)
    if peer is None:
        raise HTTPException(404, "peer not found")
    msgs = db.list_messages(user.id, peer.id, limit)
    return {"messages": [
        {"id": m.id, "from_me": m.sender_id == user.id, "body": db.message_body(m),
         "risk_score": m.risk_score, "intercepted": m.intercepted,
         "created_at": m.created_at.isoformat()} for m in msgs]}


@router.post("/chat/send")
async def chat_send(req: ChatSend, user: User = Depends(current_user)):
    db = get_db()
    peer = db.get_user_by_phone(req.recipient_phone)
    if peer is None:
        # auto-provision the recipient so messages to un-registered numbers
        # still flow (they receive once they install and register)
        peer = db.create_user(req.recipient_phone, "Contact")

    # INSTANT DELIVERY: persist + push to the recipient BEFORE running the
    # detection pipeline. The pipeline (10+ models incl. a local LLM) can take
    # seconds; chat must not wait for it. Verdict/freeze are pushed over the
    # realtime hub afterwards, only when actionable.
    msg = db.save_message(user.id, peer.id, req.body)
    await _deliver_chat(peer.id, user.id, msg.id, req.body, None, None)
    _spawn(_detect_chat(msg.id, user.id, peer.id, req.body))

    return {"message_id": msg.id, "risk_score": 0.0,
            "intercepted": False, "verdict": None, "analysis_pending": True}


# keep strong refs so background detection tasks are never garbage-collected
_bg_tasks: set = set()


def _spawn(coro) -> None:
    t = asyncio.create_task(coro)
    _bg_tasks.add(t)
    t.add_done_callback(_bg_tasks.discard)


async def _detect_chat(msg_id: int, sender_id: int, recipient_id: int, body: str):
    from ..orchestration.dispatcher import dispatch_message
    try:
        result = await dispatch_message(sender_id=sender_id,
                                        recipient_id=recipient_id, body=body)
    except Exception:
        import logging
        logging.getLogger(__name__).exception("chat detection failed (message was already delivered)")
        return
    db = get_db()
    try:
        db.update_message_result(msg_id, result.get("risk_score", 0.0),
                                 result.get("intercepted", False))
    except Exception:
        pass
    verdict = result.get("verdict")
    freeze = result.get("freeze")
    if not (verdict or freeze):
        return
    sender_phone = db.phone_of(db.get_user(sender_id))
    recipient_phone = db.phone_of(db.get_user(recipient_id))
    from ..realtime import get_hub
    hub = get_hub()
    if verdict:
        base = dict(verdict)
        base.update(kind="message", message_id=msg_id,
                    session_key=f"msg:{sender_id}:{recipient_id}")
        to_recipient = dict(base, peer_phone=sender_phone)
        await hub.send(recipient_id, "verdict.update", to_recipient)
        # the sender's own copy (peer is the recipient from their perspective)
        to_sender = dict(base, peer_phone=recipient_phone)
        await hub.send(sender_id, "verdict.update", to_sender)
    if freeze:
        await hub.send(recipient_id, "freeze.request", freeze)


async def _deliver_chat(recipient_id: int, sender_id: int, msg_id: int, body: str,
                        verdict: dict | None, freeze: dict | None):
    from ..realtime import get_hub
    hub = get_hub()
    db = get_db()
    sender_phone = db.phone_of(db.get_user(sender_id))
    await hub.send(recipient_id, "chat.recv", {
        "message_id": msg_id, "sender_id": sender_id,
        "sender_phone": sender_phone, "body": body})
    if verdict:
        payload = dict(verdict)
        payload["kind"] = "message"
        payload["peer_phone"] = sender_phone
        await hub.send(recipient_id, "verdict.update", payload)
    if freeze:
        await hub.send(recipient_id, "freeze.request", freeze)


# ---------------- calls / verdicts / reports ----------------
@router.get("/calls")
def calls(user: User = Depends(current_user)):
    db = get_db()
    with db.session() as s:
        from sqlalchemy import or_
        from ..storage.models import Call
        rows = list(s.query(Call).filter(or_(Call.caller_id == user.id, Call.callee_id == user.id))
                    .order_by(Call.started_at.desc()).limit(50))
    return {"calls": [
        {"id": r.id, "kind": r.kind, "status": r.status, "risk_peak": r.risk_peak,
         "started_at": r.started_at.isoformat(), "ended_at": r.ended_at.isoformat() if r.ended_at else None}
        for r in rows]}


@router.get("/verdicts")
def verdicts(user: User = Depends(current_user)):
    db = get_db()
    return {"verdicts": [
        {"id": v.id, "kind": v.kind, "risk_score": v.risk_score, "band": v.band,
         "verdict": v.verdict_text, "why": v.why, "action": v.action,
         "scam_type": v.scam_type, "signals": v.signals,
         "created_at": v.created_at.isoformat()} for v in db.list_verdicts(user.id)]}


@router.get("/reports")
def reports(user: User = Depends(current_user)):
    db = get_db()
    return {"reports": [
        {"id": r.id, "kind": r.kind, "title": r.title, "body": r.body,
         "scam_type": r.scam_type, "signals_fired": r.signals_fired,
         "created_at": r.created_at.isoformat()} for r in db.list_reports(user.id)]}


# ---------------- voiceprint enrollment ----------------
@router.post("/voiceprints/enroll")
async def enroll_voiceprint(file: UploadFile = File(...),
                            user: User = Depends(current_user)):
    data = await file.read()
    from ..inference.voice.speaker_verify import (ENROLL_HINTS, enroll_audio,
                                                  enrollment_status)
    ok, emb_len, reason = enroll_audio(user.id, data)
    # A rejected recording is a normal outcome, not a transport error: the app
    # needs to show "too quiet, try again" in the recorder UI, and a 422 would
    # send it down the generic network-failure path instead. The reason + hint
    # come from the server so the wording stays in one place.
    return {"enrolled": ok, "embedding_dim": emb_len, "reason": reason,
            "hint": ENROLL_HINTS.get(reason, "Enrolment failed."),
            "bytes_received": len(data),
            **enrollment_status(user.id)}


@router.get("/voiceprints/me")
def my_voiceprints(user: User = Depends(current_user)):
    """Enrolment state, so the screen can say what is actually stored."""
    from ..inference.voice.speaker_verify import enrollment_status
    return enrollment_status(user.id)


@router.delete("/voiceprints/me")
def delete_my_voiceprints(user: User = Depends(current_user)):
    from ..inference.voice.speaker_verify import enrollment_status
    removed = get_db().delete_voiceprints(user.id, owner_id=user.id)
    return {"deleted": removed, **enrollment_status(user.id)}


# ---------------- trust circle ----------------
@router.post("/trust-circle/link")
def link(req: ContactCreate, user: User = Depends(current_user)):
    db = get_db()
    peer = db.get_user_by_phone(req.peer_phone)
    if peer is None:
        peer = db.create_user(req.peer_phone, req.label or "Contact")
    db.add_contact(user.id, peer.id, req.label, req.relationship_tag, True)
    db.link_trusted(user.id, peer.id)
    return {"linked": True}


# ---------------- verify + freeze responses ----------------
@router.post("/verify/respond")
async def verify_respond(req: VerifyResponse, user: User = Depends(current_user)):
    from ..trust_circle.verify import resolve_verify
    return await resolve_verify(req.session_key, user.id, req.answer)


class VerifyRequest(BaseModel):
    session_key: str
    claim_peer_phone: str


@router.post("/verify/request")
async def verify_request(req: VerifyRequest, user: User = Depends(current_user)):
    """User taps 'Verify Caller' -> push a verify prompt to the claimed contact."""
    from ..trust_circle.verify import trigger_verify
    db = get_db()
    peer = db.get_user_by_phone(req.claim_peer_phone)
    if peer is None:
        raise HTTPException(404, "claimed contact not on platform")
    from ..trust_circle.roster import is_linked
    if not is_linked(user.id, peer.id):
        raise HTTPException(403, "claimed contact is not in your trusted circle")
    r = await trigger_verify(req.session_key, user.id, peer.id)
    # `delivered` distinguishes "their phone is listening" from "queued blindly";
    # the button must not claim success when nobody could receive the challenge.
    return {"ok": bool(r.get("prompt_sent")),
            "prompt_sent": bool(r.get("prompt_sent")),
            "delivered": bool(r.get("delivered")),
            "reason": r.get("reason") or "",
            "timeout_s": 45.0}


class VoiceCrossVerify(BaseModel):
    """Cross-check the live call audio against a contact's enrolled voiceprint."""
    session_key: str
    claim_peer_phone: str


@router.post("/verify/voiceprint")
async def verify_voiceprint(req: VoiceCrossVerify,
                            user: User = Depends(current_user)):
    """Second opinion on an AI-voice alert, using the ECAPA voiceprint model.

    The synthetic-voice detectors answer "was this voice generated?". This answers
    the independent question "is this actually the person they claim to be?" by
    comparing the buffered call audio against the voiceprint the real contact
    enrolled. Two independent models agreeing is the strongest evidence the system
    can offer, which is exactly what the user should get when a clone is flagged.
    """
    db = get_db()
    peer = db.get_user_by_phone(req.claim_peer_phone)
    if peer is None:
        raise HTTPException(404, "claimed contact not on platform")

    from ..orchestration.dispatcher import CROSS_VERIFY_HINTS, get_session_runner
    runner = get_session_runner(req.session_key)
    if runner is None or not getattr(runner, "_begun", False):
        raise HTTPException(404, "no active analysis session for that call")

    result = await runner.cross_verify_voice(peer.id)
    if not result.get("ok"):
        reason = result.get("reason") or "unavailable"
        # 200 with ok=false: the client shows "couldn't check" rather than an error
        return {"ok": False, "reason": reason,
                "hint": result.get("hint") or CROSS_VERIFY_HINTS.get(reason, ""),
                "similarity": None, "matches": None}
    return result


@router.post("/freeze/decide")
def freeze_decide(req: FreezeDecision):
    from ..intercept.controller import decide_freeze
    return decide_freeze(req.freeze_id, req.decision)


# ---------------- collective flags ----------------
@router.post("/flags")
def submit_flag(req: FlagSubmit, user: User = Depends(current_user)):
    from ..collective_db.flags import submit_flag as sf
    return sf(user.id, req.phone, req.kind, req.note)


# ---------------- push device registration ----------------
@router.post("/devices")
def register_device(req: DeviceRegister, user: User = Depends(current_user)):
    db = get_db()
    db.register_device_token(user.id, req.token, req.platform)
    return {"registered": True}


# ---------------- external notification ingestion ----------------
@router.post("/notify/external")
async def notify_external(req: ExternalNotify, user: User = Depends(current_user)):
    """A notification from another app (WhatsApp/SMS/Telegram...) arrives;
    route it through the agentic graph and push the verdict back.

    A cheap keyword gate runs first. Notifications with no scam-related language
    — music players, group banter, "hi" — are acknowledged and dropped without
    touching a model, so they cannot produce a spurious alert and cost no LLM
    call. The Android client applies the same gate before calling us; this is the
    server-side backstop (and covers older clients).
    """
    if not req.text.strip():
        return {"ingested": False, "reason": "empty"}

    from ..inference.text.triage import should_analyse
    gate = should_analyse(req.text)
    if not gate["analyse"]:
        log.debug("notify/external skipped (%s): %r", gate["reason"], req.text[:80])
        return {"ingested": False, "reason": gate["reason"],
                "risk_score": 0.0, "verdict": None}

    from ..orchestration.dispatcher import dispatch_message
    result = await dispatch_message(sender_id=user.id, recipient_id=user.id,
                                    body=req.text)
    await _deliver_chat(user.id, user.id, -1, req.text,
                        result.get("verdict"), result.get("freeze"))
    return {"ingested": True, "matched": gate["matched"][:8],
            "risk_score": result.get("risk_score", 0.0),
            "verdict": result.get("verdict")}


# ---------------- debug: model readiness (P3-C) ----------------
@router.get("/debug/models")
def debug_models():
    """Return the ready() state of every inference model in the hub.

    If a model shows ready=false, real detection signals will be 0/None and the
    pipeline output will always look like risk=0 / band='passive' — i.e. hardcoded.
    Check server logs for the model's load error.

    Example: GET /api/debug/models
    """
    from ..inference.hub import get_hub
    hub = get_hub()
    _MODEL_KEYS = [
        "asr", "voice_deepfake", "speaker_verify",
        "face", "video_deepfake", "lipsync",
        "scam_pattern", "behavioral", "urgency", "intent",
    ]
    models = {}
    for k in _MODEL_KEYS:
        eng = hub.get(k)
        if eng is None:
            models[k] = {"ready": False, "reason": "not registered in hub"}
        else:
            try:
                ready = bool(eng.ready())
            except Exception as e:
                ready = False
                models[k] = {"ready": False, "reason": str(e)}
                continue
            info = {"ready": ready}
            # surface any version / path info if the engine exposes it
            for attr in ("model_path", "version", "device", "name", "backend"):
                if hasattr(eng, attr):
                    info[attr] = getattr(eng, attr)
            if not ready:
                reason = getattr(eng, "_unavailable_reason", None)
                if reason:
                    info["reason"] = reason
            models[k] = info

    from ..orchestration.graph import get_graph
    graph_ok = get_graph() is not None

    # Provider/backend health. Without this, a hosted API that answers nothing
    # looks identical to a healthy one: the engine says ready=true, the signal is
    # None, and the risk sits at 0 with no way to tell why.
    from ..config import get_config
    from ..inference.providers import (last_llm_error, last_velma_error,
                                       velma_eos_in_use)
    prov = get_config().providers
    vd = hub.get("voice_deepfake")
    providers = {
        "asr": {"configured": prov.asr,
                "key_present": bool(prov.deepgram_api_key)
                if prov.asr == "deepgram" else None},
        "voice_deepfake": {
            "configured": prov.voice_deepfake,
            "active_backend": getattr(vd, "backend", None),
            "velma_key_present": bool(prov.velma_api_key),
            "velma_endpoint_set": bool(prov.velma_endpoint),
            "velma_in_use": bool(getattr(vd, "_use_velma", False)),
            "local_ensemble_ready": bool(getattr(vd, "_local_ready", False)),
            "velma_consecutive_failures": getattr(vd, "_velma_failures", None),
            # the two fields that turn "it isn't working" into a diagnosis
            "velma_last_error": (getattr(vd, "_velma_last_error", None)
                                 or last_velma_error()),
            "velma_eos_negotiated": velma_eos_in_use(),
            "can_detect_cloned_voice": bool(
                getattr(vd, "_use_velma", False)
                or getattr(vd, "_local_ready", False)),
        },
        "llm": {"configured": prov.llm,
                "key_present": bool(prov.groq_api_key),
                "model": prov.groq_model,
                "last_error": last_llm_error()},
    }

    # Which code this process is running. A stale process has repeatedly made a
    # landed fix look broken, so this is reported FIRST, above everything else.
    from ..build_info import runtime_info
    runtime = runtime_info()

    return {
        "runtime": runtime,
        "restart_required": runtime["restart_required"],
        "graph_compiled": graph_ok,
        "fallback_sequential": not graph_ok,
        "providers": providers,
        "models": models,
        "all_ready": all(v.get("ready") for v in models.values()),
    }


# ---------------- scenarios (P1.2) ----------------
@router.get("/scenarios")
def list_scenarios():
    """Return available scenario names, labels, and threshold values.

    The dashboard scenario selector and the stream API 'start' frame use these
    names. Thresholds are read from ``config.yaml``; no fabricated values here.
    """
    cfg = get_config()
    return {
        name: {
            "label": s.label,
            "description": s.description,
            "risk_verify_at": s.risk_verify_at,
            "risk_critical_at": s.risk_critical_at,
        }
        for name, s in cfg.scenarios.items()
    }


# ---------------- admin: privacy-retention enforcement (P1.6) ----------------
@router.post("/admin/cleanup")
def admin_cleanup(days: int = 30, _: User = Depends(current_user)):
    """Delete verdict and report rows older than ``days`` days.

    Requires authentication. Returns counts of deleted rows.
    This is the privacy-safe retention enforcement endpoint.
    """
    db = get_db()
    v = db.cleanup_old_verdicts(days)
    r = db.cleanup_old_reports(days)
    return {"deleted_verdicts": v, "deleted_reports": r, "older_than_days": days}

