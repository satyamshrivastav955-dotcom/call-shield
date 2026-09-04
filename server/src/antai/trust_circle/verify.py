"""'Verify with trusted contact' flow (MD 4.7).

When the app/server detects an identity claim ("this is your mother") for a
linked trusted contact, we push a silent verification ping to that contact's
device: "[Name] is claiming to be calling them - are you really calling?
[Yes/No]". Yes -> verified, clear warning. No / timeout -> HIGH-RISK warning.
"""
from __future__ import annotations

import asyncio
import logging
import time

from ..gateway.push import get_push
from ..realtime import get_hub

log = logging.getLogger(__name__)

_pending: dict[str, dict] = {}  # verify_token -> {session_key, responder_id, protected_id}

# how long the claimed contact has to answer before we treat silence as "unverified"
_TIMEOUT_S = 45.0


async def trigger_verify(session_key: str, protected_user_id: int,
                         claim_peer_id: int) -> dict:
    """Send a verify ping to the claimed contact (claim_peer_id).

    Returns {prompt_sent, reason} so a user-initiated "Verify caller" tap can be
    told honestly whether the other side was actually reachable, instead of the
    button silently doing nothing.
    """
    # only ping if the claimed contact is actually linked to the protected user
    from .roster import is_linked
    if not is_linked(protected_user_id, claim_peer_id):
        return {"prompt_sent": False, "reason": "not_linked"}

    token = f"verify:{session_key}:{time.time_ns()}"
    _pending[token] = {"session_key": session_key, "responder_id": claim_peer_id,
                       "protected_id": protected_user_id,
                       "claimed_at": time.time()}
    push = get_push()
    ok = await push.send(claim_peer_id, "verify.prompt", {
        "verify_token": token,
        "session_key": session_key,
        "question": "Someone on antAI is claiming to be you and is talking to "
                    "a family member right now. Are you actually on that call?",
        "options": ["Yes, it's me", "No, that's not me"],
    })
    # tell the asking side that the challenge is out and we are waiting
    await get_hub().send(protected_user_id, "verify.result", {
        "session_key": session_key, "state": "pending",
        "delivered": bool(ok), "timeout_s": _TIMEOUT_S,
        "message": ("Asking your contact to confirm…" if ok else
                    "Your contact's phone is offline — we sent the request but "
                    "they may not see it right now."),
    })
    if not ok:
        # no live socket for the responder -> fallback: warn protected user
        await _fallback_warning(session_key, protected_user_id)
    # auto-resolve after N seconds if no response
    asyncio.create_task(_timeout(token))
    return {"prompt_sent": True, "delivered": bool(ok), "reason": ""}


async def _timeout(token: str, timeout_s: float = _TIMEOUT_S):
    await asyncio.sleep(timeout_s)
    p = _pending.pop(token, None)
    if p:
        await get_hub().send(p["protected_id"], "verify.result", {
            "session_key": p["session_key"], "state": "timeout",
            "verified": False,
            "message": "No answer from your contact. Treat this call as unverified.",
        })
        await _fallback_warning(p["session_key"], p["protected_id"])


async def _fallback_warning(session_key: str, protected_user_id: int):
    await get_hub().send(protected_user_id, "verdict.update", {
        "session_key": session_key, "band": "critical",
        "verdict": "We could not verify the caller's identity.",
        "why": "No confirmation from the person they claim to be.",
        "action": "Hang up and call the person directly using their saved number. "
                  "Do not send money or share codes.",
    })


async def resolve_verify(session_key: str, responder_id: int, answer: bool) -> dict:
    """Called from the trusted contact's app (Yes/No)."""
    # find a pending token for this session/responder
    token = None
    for t, p in _pending.items():
        if p["session_key"] == session_key and p["responder_id"] == responder_id:
            token = t
            break
    protected_id = None
    if token:
        protected_id = _pending.pop(token)["protected_id"]
    else:
        # No live challenge for this session/responder: either it already timed
        # out (and the asker was warned) or this is a replay. Never report success
        # here — a stale "yes" must not be able to un-deny a call, and the app
        # needs to know the answer arrived too late to help.
        log.warning("verify: no pending challenge for session=%s responder=%s",
                    session_key, responder_id)
        return {"ok": False, "verified": False, "reason": "no_pending_challenge"}

    if protected_id:
        # dedicated result event so the in-call UI can show a clear
        # confirmed/denied state; the verdict.update below still drives risk.
        await get_hub().send(protected_id, "verify.result", {
            "session_key": session_key,
            "state": "confirmed" if answer else "denied",
            "verified": bool(answer),
            "message": ("Your contact confirmed it is really them."
                        if answer else
                        "Your contact says this is NOT them. Hang up now and do "
                        "not send money or share any codes."),
        })

    if answer:
        if protected_id:
            await get_hub().send(protected_id, "verdict.update", {
                "session_key": session_key, "band": "passive",
                "verdict": "Caller verified by your trusted contact.",
                "why": "Your family member confirmed they are really calling.",
                "action": "Continue safely.",
                "verified": True,
            })
        return {"ok": True, "verified": True}
    if protected_id:
        await _fallback_warning(session_key, protected_id)
    return {"ok": True, "verified": False}
