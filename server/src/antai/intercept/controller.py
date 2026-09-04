"""Freeze / Intercept controller (MD 4.6).

The server pushes a 'hold' directive onto the exact in-app action the user is
about to take (OTP share, money transfer, credential/remote-access install,
link/gift-card tap). Nothing is auto-blocked - the user must explicitly
confirm or override.
"""
from __future__ import annotations

import logging
import time
from typing import Optional

from ..config import get_config
from ..storage import get_db
from .directives import build_directive

log = logging.getLogger(__name__)


def create_intercept(session_key: str, protected_user_id: int, state: dict) -> Optional[dict]:
    db = get_db()
    if has_pending_freeze(session_key):
        return None
    request_type = state.get("request_type") or "none"
    if request_type == "none":
        return None
    directive = build_directive(request_type, state)
    frz = db.create_freeze(session_key, protected_user_id, request_type,
                           {"risk": state.get("risk", 0), "directive": directive})
    directive["freeze_id"] = frz.id
    directive["session_key"] = session_key
    return {"freeze_id": frz.id, "directive": directive}


def has_pending_freeze(session_key: str) -> bool:
    return get_db().pending_freeze(session_key) is not None


def decide_freeze(freeze_id: int, decision: str) -> dict:
    """decision: 'confirm' (proceed anyway) or 'override' (dismiss hold)."""
    db = get_db()
    if decision not in ("confirm", "override"):
        return {"ok": False, "message": "invalid decision"}
    db.resolve_freeze(freeze_id, "confirmed" if decision == "confirm" else "overridden")
    return {"ok": True, "freeze_id": freeze_id, "decision": decision,
            "note": "action released" if decision == "confirm" else "hold cancelled"}
