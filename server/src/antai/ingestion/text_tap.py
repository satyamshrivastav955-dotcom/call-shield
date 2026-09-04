"""Text ingestion tap: message text is captured at send-time by the chat
service and pushed here before delivery. Actual detection runs through the
orchestration dispatcher; this module normalizes and annotates the text.
"""
from __future__ import annotations

import logging

log = logging.getLogger(__name__)

TEXT_PATTERNS = {
    "family_emergency": ["accident", "hospital", "urgent", "police", "son", "daughter",
                         "mom", "dad", "grandma", "grandpa", "aunt", "uncle",
                         "mummy", "papa", "beta", "bache", "dadi", "nani"],
    "otp": ["otp", "verification code", "one-time password", "code", "pin",
            "ackhoo", "otp bhej", "code batao", "verification"],
    "money": ["money", "transfer", "send", "pay", "loan", "bank", "account",
              "paise", "rupaye", "transfer kar", "payment", "cash", "gift card"],
    "pressure": ["now", "immediately", "right away", "urgent", "don't tell", "secret",
                 "abhi", "turant", "jaldi", "kisi ko mat batana", "time running out",
                 "act fast", "hurry"],
    "credentials": ["password", "username", "login", "id", "credential", "bank details",
                    "password batao", "user id", "aadhaar", "pan", "account number"],
    "remote_access": ["teamviewer", "anydesk", "install", "app download", "remote access",
                      "screen share", "anydesk install", "download karo", "access de"],
    "link": ["click", "link", "url", "open this", "download link", "http", "https",
             "link par click karo", "visit"],
}


def tap_text(body: str) -> dict:
    """Lightweight lexical pre-annotations used to seed detection hints."""
    lower = body.lower()
    hints = []
    for key, kws in TEXT_PATTERNS.items():
        if any(k in lower for k in kws):
            hints.append(key)
    return {"hints": hints, "length": len(body), "language_guess": _guess_lang(body)}


def _guess_lang(text: str) -> str:
    devanagari = any("\u0900" <= ch <= "\u097F" for ch in text)
    return "hi" if devanagari else "en"
