"""Orchestration nodes (pure-ish functions over AnalysisState).

Each node calls an inference engine via the hub; engines degrade gracefully.
Router fans out by event kind so a text-only scam never runs the video node.
"""
from __future__ import annotations

import asyncio
import functools
import logging
import time

from ..inference.hub import get_hub
from ..storage import get_db
from .state import AnalysisState

log = logging.getLogger(__name__)


# --------------------------------------------------------------- resilience
# One model throwing on live call media (a bad frame, a shape mismatch, an
# OOM) must NEVER take down the whole evaluation. Before these guards, an
# exception in any detector bubbled up, aborted the compiled graph, forced the
# sequential fallback to re-run every node, hit the same error, and lost the
# ENTIRE eval — so every engine's signal read 0 even though the others were
# healthy. That is a prime cause of "all models sit at 0 all the time".
def _safe_node(fn):
    """Decorator: a node that fails logs and returns the (partially populated)
    state instead of raising, so sibling nodes and the fusion/verdict still
    run. State is mutated in place, so any signal set before the failure
    survives."""
    @functools.wraps(fn)
    async def wrapper(state: AnalysisState) -> AnalysisState:
        try:
            return await fn(state)
        except Exception:
            log.exception("node %s failed — continuing with partial signals",
                          getattr(fn, "__name__", "?"))
            return state
    return wrapper


async def _safe(label: str, coro):
    """Await a single model call, returning None (and logging) on failure so
    the OTHER model calls in the same node still run and set their signals."""
    try:
        return await coro
    except Exception:
        log.exception("model call failed: %s — other signals continue", label)
        return None


# All model inference below is offloaded via asyncio.to_thread. These graph
# nodes run on the SAME event loop as the SFU media relay, so a synchronous
# model call here would freeze audio/video for every participant. Keeping the
# calls off the loop is what stopped the relayed audio from breaking up.
def _detect_mouth_openness(face, frames: list) -> list:
    """Run face detection over a batch of frames (blocking) in one worker hop."""
    out: list = []
    for f in frames:
        try:
            d = face.detect(f)
        except Exception:
            continue
        if d.get("faces"):
            out.append(d["faces"][0]["openness"])
    return out


# ------------------------------------------------------------------ router
async def router_node(state: AnalysisState) -> AnalysisState:
    state["events"] = state.get("events", [])
    return state


# ------------------------------------------------------------ voice nodes
@_safe_node
async def voice_detector_node(state: AnalysisState) -> AnalysisState:
    # Score the rolling window when the dispatcher supplied one, else the raw
    # segment. A single 0.4-2.0s VAD segment is far shorter than the utterances
    # these SSL models were trained on, so on segments alone a real cloned voice
    # comes back "uncertain" (~0.5) and never reaches the alert line — the
    # detector runs, reports a number, and still misses the clone. ECAPA speaker
    # verification wants a few seconds too. `pending_audio` deliberately stays the
    # raw segment for lip-sync, which must stay aligned to the video frames.
    audio = state.get("detector_audio") or state.get("pending_audio")
    hub = get_hub()
    if audio and audio.get("audio") is not None:
        sr = audio.get("sample_rate", 16000)
        # Long unpadded context for the AST head (padded short windows read as
        # spoof to it); falls back to the window inside analyze() when absent.
        long_audio = state.get("detector_audio_long")
        context = (long_audio.get("audio")
                   if long_audio and long_audio.get("audio") is not None else None)
        dfa = hub.get("voice_deepfake")
        if dfa and dfa.ready():
            r = await _safe("voice_deepfake.analyze",
                            asyncio.to_thread(dfa.analyze, audio["audio"], sr,
                                              context))
            if r is not None:
                state["voice_deepfake"] = r.get("spoof_prob")
                state["voice_per_model"] = r.get("per_model") or {}
                # provenance for the warning popup and /api/debug/models: how
                # many of the two detectors scored this segment, whether they
                # agreed, and which backend is actually live. Without this a
                # silent hosted API is indistinguishable from a healthy one.
                state["voice_sources"] = r.get("sources")
                state["voice_agreement"] = r.get("agreement")
                state["voice_backend"] = r.get("backend")
                state["voice_label"] = r.get("label")
        sv = hub.get("speaker_verify")
        claim_id = state.get("claimed_identity_id")
        if sv and sv.ready() and claim_id:
            r = await _safe("speaker_verify.verify_against",
                            asyncio.to_thread(sv.verify_against, audio["audio"], claim_id, sr))
            if r is not None:
                state["speaker_similarity"] = r.get("similarity")
                if r.get("similarity") is not None:
                    state["identity_mismatch"] = not r.get("matches")
    return state


# ------------------------------------------------------------ video nodes
@_safe_node
async def video_detector_node(state: AnalysisState) -> AnalysisState:
    frames = state.get("pending_frames") or []
    hub = get_hub()
    if not frames:
        return state
    face = hub.get("face")
    dfv = hub.get("video_deepfake")
    lipsync = hub.get("lipsync")

    if dfv and dfv.ready():
        r = await _safe("video_deepfake.score_frames",
                        asyncio.to_thread(dfv.score_frames, frames))
        if r is not None:
            state["video_deepfake"] = r.get("fake_prob")
            state["video_votes"] = r.get("votes")
            state["video_agreement"] = r.get("agreement")
            # ensemble agreement (>= N signals) is a strong signal on its own
            if r.get("flagged"):
                state["video_deepfake"] = max(r.get("fake_prob") or 0.0, 0.9)

    mouth, env = [], []
    if face and face.ready():
        mouth = await _safe("face.detect_batch",
                            asyncio.to_thread(_detect_mouth_openness, face, frames[-24:])) or []
    # audio envelope for lip-sync from pending audio (if present)
    audio = state.get("pending_audio")
    if audio and audio.get("audio") is not None:
        import numpy as np
        a = audio["audio"]
        frame_s = 0.033
        n = int(16000 * frame_s)
        env = [float(np.sqrt(np.mean(a[i:i + n] ** 2) + 1e-9))
               for i in range(0, len(a) - n + 1, n)]
    if lipsync and lipsync.ready() and mouth and env:
        r = await _safe("lipsync.score",
                        asyncio.to_thread(lipsync.score, mouth, env[: len(mouth)]))
        if r is not None and r.get("sync_score") is not None and r["sync_score"] < 0.35:
            state["lipsync_mismatch"] = True
    return state


# ------------------------------------------------------------- text nodes
def _analysable_text(state: AnalysisState) -> str:
    """The text this evaluation should score, or "" when there is nothing worth
    scoring.

    The scam/intent/urgency classifiers were fine-tuned on templated full
    sentences and have no reject class, so out-of-distribution input (a bare
    "hi", a media notification, one-word banter) still produces a confident
    label. Gating here means such text never reaches a model, which is why it
    can no longer manufacture a signal — the root cause of "every hi is
    flagged as a scam".
    """
    from ..inference.text.triage import is_low_content

    text = state.get("text") or ""
    if not text:
        tr = state.get("transcript") or []
        if tr:
            text = tr[-1].get("text", "") or ""
    if not text:
        return ""
    if is_low_content(text):
        state["text_skipped"] = "low-content"
        return ""
    return text


@_safe_node
async def text_detector_node(state: AnalysisState) -> AnalysisState:
    text = _analysable_text(state)
    hub = get_hub()
    if not text:
        return state
    sp = hub.get("scam_pattern")
    if sp:
        r = await _safe("scam_pattern.analyze",
                        asyncio.to_thread(sp.analyze, text))
        if r is not None:
            state["scam_prob"] = r.get("scam_prob")
            state["scam_type"] = r.get("scam_type")

    beh = hub.get("behavioral")
    sender = state.get("caller_id") or state.get("callee_id")
    if beh and sender:
        emb = await _safe("behavioral.embed_text",
                          asyncio.to_thread(beh.embed_text, text))
        if emb is not None:
            d = await _safe("behavioral.deviation",
                            asyncio.to_thread(beh.deviation, sender, text, emb))
            if d is not None:
                state["deviation"] = d.get("deviation")
            await _safe("behavioral.update_profile",
                        asyncio.to_thread(beh.update_profile, sender, text, emb))
    return state


# ------------------------------------------------------------ identity node
@_safe_node
async def identity_claim_node(state: AnalysisState) -> AnalysisState:
    # claimed identity provided by runner (from contact tag / ASR NER / user tap)
    claim = state.get("claimed_identity_id")
    if claim is None:
        state["identity_mismatch"] = False
    return state


# ------------------------------------------------------ collective-db check
@_safe_node
async def collective_check_node(state: AnalysisState) -> AnalysisState:
    hub_eng = None
    from ..config import get_config
    if not get_config().pipeline.collective_check_enabled:
        return state
    db = get_db()
    # lookup by caller phone hash if provided via state (set by runner)
    phone_hash = state.get("collective_phone_hash")
    if phone_hash:
        flag = db.lookup_flag(phone_hash)
        if flag and flag.count >= get_config().collective.min_flags_before_shortcircuit:
            state["collective_flagged"] = True
            state["collective_confidence"] = flag.confidence
    return state


# ------------------------------------------------------------- urgency node
@_safe_node
async def urgency_node(state: AnalysisState) -> AnalysisState:
    text = _analysable_text(state)
    hub = get_hub()
    ue = hub.get("urgency")
    if ue and text:
        r = await asyncio.to_thread(ue.score, text)
        state["urgency"] = r.get("urgency")
    return state


# ------------------------------------------------------------ intent node
@_safe_node
async def intent_node(state: AnalysisState) -> AnalysisState:
    text = _analysable_text(state)
    hub = get_hub()
    ie = hub.get("intent")
    if ie and text:
        r = await asyncio.to_thread(ie.classify, text)
        state["request_detected"] = r.get("request_detected", False)
        state["request_type"] = r.get("request_type")
        state["request_confidence"] = r.get("confidence")
    else:
        state.setdefault("request_detected", False)
    return state


# --------------------------------------------------------------- fusion node
# Severity of the thing being asked for. A vague "link" ask is much weaker
# evidence than an explicit OTP or money ask, so it must not score the same.
_REQUEST_WEIGHT = {
    "money": 1.0,
    "otp": 1.0,
    "credential": 1.0,
    "remote-access": 0.9,
    "link": 0.5,
}


def _above(value, floor: float, ceil: float, weight: float) -> float:
    """Contribution that is 0 at or below `floor` and `weight` at `ceil`.

    Every model signal goes through here rather than being multiplied directly,
    which gives each one a dead zone. Without a dead zone, a classifier that
    idles around 0.3 on out-of-distribution text still adds risk on every single
    message, and enough of those add up to an alert on a "hi".
    """
    if value is None:
        return 0.0
    try:
        v = float(value)
    except (TypeError, ValueError):
        return 0.0
    if v <= floor:
        return 0.0
    span = max(1e-6, ceil - floor)
    return weight * min(1.0, (v - floor) / span)


def fusion_node(state: AnalysisState) -> AnalysisState:
    """Combine signals into a 0..100 risk plus a band.

    Two rules keep false positives down. First, every continuous signal has a
    dead zone (see `_above`), so a miscalibrated model contributes nothing until
    it is genuinely confident. Second — and this is the important one — risk is
    capped in the passive band unless either a HARD signal fired or at least two
    independent SOFT signals agree. Previously `request_detected` alone added
    exactly 40, which landed precisely on the verify boundary, so one boolean
    from one out-of-distribution classifier was enough to alarm the user.
    """
    from ..config import get_config

    s = state
    risk = 0.0
    hard: list[str] = []   # strong, self-sufficient evidence
    soft: list[str] = []   # suggestive; needs corroboration

    # --- hard signals ------------------------------------------------------
    # caller is impersonating a known contact
    if s.get("identity_mismatch"):
        risk += 50
        hard.append("identity_mismatch")
    # this number was independently reported by other users
    if s.get("collective_flagged"):
        risk += 45
        hard.append("collective_flagged")
    # Deepfake models are miscalibrated near 0.5 on live (compressed/echoed)
    # media, so they contribute nothing below the dead zone. Above it they ramp
    # steeply: a detector that is ~90% sure the voice is synthetic must be able
    # to raise the alarm on its own, because a cloned voice is the single most
    # dangerous thing this product looks for. Calibrated so 0.90 -> ~42 (verify)
    # and >=0.95 -> 60 (critical when anything else agrees).
    vd = _above(s.get("voice_deepfake"), 0.78, 0.95, 60)
    if vd:
        risk += vd
        hard.append("voice_deepfake")
    gd = _above(s.get("video_deepfake"), 0.80, 0.97, 50)
    if gd:
        risk += gd
        hard.append("video_deepfake")

    # --- request-to-act ----------------------------------------------------
    # An explicit ask for money / OTP / credentials is the core of a scam, but
    # only when the classifier is actually confident about it. Confidence
    # defaults to 0.5 (i.e. below the floor, contributing nothing) when the
    # engine did not report one, so a bare boolean can no longer escalate.
    if s.get("request_detected"):
        rtype = s.get("request_type") or "link"
        conf = s.get("request_confidence")
        conf = 0.5 if conf is None else float(conf)
        sev = _REQUEST_WEIGHT.get(rtype, 0.5)
        contrib = _above(conf, 0.60, 0.95, 50 * sev)
        if contrib:
            risk += contrib
            # a high-confidence money/OTP/credential ask stands on its own
            if sev >= 0.9 and conf >= 0.80:
                hard.append(f"request:{rtype}")
            else:
                soft.append(f"request:{rtype}")

    # --- soft signals ------------------------------------------------------
    # Sized so that any TWO agreeing soft signals clear the verify boundary,
    # while no single one ever can.
    sp = _above(s.get("scam_prob"), 0.55, 0.95, 35)
    if sp:
        risk += sp
        soft.append("scam_pattern")
    # urgency is reported on a 0..100 scale by the scorer
    ug = _above(s.get("urgency"), 60, 95, 22)
    if ug:
        risk += ug
        soft.append("urgency")
    dv = _above(s.get("deviation"), 0.55, 1.0, 12)
    if dv:
        risk += dv
        soft.append("behavioral_deviation")
    if s.get("lipsync_mismatch"):
        risk += 12
        soft.append("lipsync_mismatch")

    # --- corroboration requirement ----------------------------------------
    # Use per-scenario thresholds if a scenario is active; fall back to global bands.
    cfg = get_config()
    scenario_name = state.get("scenario")
    scenario = cfg.get_scenario(scenario_name)
    verify_at = scenario.risk_verify_at
    critical_at = scenario.risk_critical_at

    # Context-based risk boost: high-stakes scenarios start with a base penalty
    # so the same voice signals trigger earlier action. This is DECLARED as
    # demo context where used — it never fabricates a detection signal.
    ctx = state.get("context") or {}
    context_boost = float(scenario.context_risk_boost)
    # Additional context signals: large transaction amount bumps risk
    txn_amount = ctx.get("transaction_amount")
    if txn_amount is not None:
        try:
            if float(txn_amount) >= 500000:   # >= 5 lakh INR
                context_boost = min(context_boost + 10.0, 20.0)
        except (TypeError, ValueError):
            pass
    risk = min(100.0, risk + context_boost)

    capped = False
    if not hard and len(soft) < 2:
        # a single suggestive signal is never enough to interrupt the user
        ceiling = max(0.0, verify_at - 1.0)
        if risk > ceiling:
            risk = ceiling
            capped = True

    state["risk"] = round(risk, 1)
    state["band"] = ("passive" if risk < verify_at else
                     "verify" if risk < critical_at else "critical")
    # kept for the debug endpoint / logs so a surprising verdict is explainable
    state["risk_signals"] = {"hard": hard, "soft": soft, "capped": capped}
    return state



# ---------------------------------------------------------- decision node
async def decision_node(state: AnalysisState) -> AnalysisState:
    risk = state["risk"]
    band = state["band"]
    if risk >= 70:
        if state.get("request_detected"):
            state["decision"] = "freeze"
        else:
            state["decision"] = "escalate"
    elif band == "verify":
        state["decision"] = "verify"
    else:
        state["decision"] = "log"
    return state


# --------------------------------------------------------------- LLM node
@_safe_node
async def llm_reasoning_node(state: AnalysisState) -> AnalysisState:
    from ..inference.llm.reasoner import generate_verdict
    risk = state.get("risk", 0)
    signals = {
        "identity_mismatch": state.get("identity_mismatch"),
        "voice_deepfake": state.get("voice_deepfake"),
        "video_deepfake": state.get("video_deepfake"),
        "video_votes": state.get("video_votes"),
        "video_agreement": state.get("video_agreement"),
        "lipsync_mismatch": state.get("lipsync_mismatch"),
        "scam_prob": state.get("scam_prob"),
        "scam_type": state.get("scam_type"),
        "deviation": state.get("deviation"),
        "urgency": state.get("urgency"),
        "request_detected": state.get("request_detected"),
        "request_type": state.get("request_type"),
        "collective_flagged": state.get("collective_flagged"),
    }
    ctx = {"context": (f"Call between user {state.get('caller_id')} and "
                       f"{state.get('callee_id')} ({state.get('kind')}).")}
    verdict = await asyncio.to_thread(generate_verdict, risk, signals,
                                      state.get("transcript") or [], ctx)
    state["verdict"] = verdict
    return state
