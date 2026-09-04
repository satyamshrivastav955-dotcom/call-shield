"""Per-request-type freeze directive payloads (MD 4.6)."""
from __future__ import annotations

from typing import Optional


def build_directive(request_type: str, state: dict) -> dict:
    """Map request_type -> the hold directive pushed to the client."""
    base = {
        "request_type": request_type,
        "risk_score": state.get("risk", 0),
        "reason": "This action matches a high-risk scam pattern.",
        "created_at": None,
    }
    if request_type == "otp":
        return {
            **base,
            "action": "delay",
            "title": "OTP visibility held",
            "message": "This looks like a high-pressure request to share a "
                       "one-time password. We've delayed showing it for 60 "
                       "seconds so you can think before sharing.",
            "hold_seconds": 60,
            "confirm_label": "I understand, proceed anyway",
        }
    if request_type == "money":
        return {
            **base,
            "action": "confirm",
            "title": "Money transfer held",
            "message": "This looks like a high-pressure money request. Please "
                       "confirm you really want to send money to this contact.",
            "hold_seconds": 90,
            "confirm_label": "I confirm, send the money",
            "override_label": "Cancel the transfer",
        }
    if request_type in ("credential", "remote-access"):
        return {
            **base,
            "action": "block",
            "title": "Access request blocked",
            "message": "This looks like an attempt to get your passwords, bank "
                       "details, or remote access to your device. We've blocked "
                       "it. Legitimate companies never ask for this.",
            "confirm_label": "I still want to proceed",
            "override_label": "Cancel",
        }
    if request_type in ("link", "gift_card"):
        return {
            **base,
            "action": "confirm",
            "title": "Link / purchase held",
            "message": "This request asks you to click a link or buy a gift "
                       "card - a common scam step. Confirm you want to proceed.",
            "hold_seconds": 30,
            "confirm_label": "Proceed anyway",
            "override_label": "Cancel",
        }
    return {**base, "action": "none"}
