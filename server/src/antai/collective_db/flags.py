"""Collective flagging DB (MD 4.8).

Users mark a number as 'scam'/'AI-generated'; we store only HASHED
identifiers (never raw PII), aggregate counts with reputation weighting, and
rate-limit per account to resist mass false-flagging.
"""
from __future__ import annotations

import hashlib
import logging
import time

from ..config import get_config
from ..storage import get_db

log = logging.getLogger(__name__)


def _hash(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def submit_flag(user_id: int, phone: str, kind: str = "scam", note: str | None = None) -> dict:
    cfg = get_config()
    db = get_db()
    # rate limit: max flags per account per rolling hour
    if not _within_rate_limit(db, user_id, cfg.collective.max_flags_per_account_per_hour):
        return {"ok": False, "message": "rate limited"}
    # reputation weight by account age
    age_days = _account_age_days(db, user_id)
    weight = 0.5 + min(1.5, age_days / 30.0)
    phone_hash = _hash(phone)
    flag = db.add_flag(phone_hash, None, weight)
    db.record_flag_submission(user_id, phone_hash, kind)
    return {"ok": True, "flag_id": flag.id, "count": flag.count,
            "confidence": flag.confidence}


def _within_rate_limit(db, user_id: int, limit: int) -> bool:
    return db.flag_submissions_since(user_id, hours=1.0) < limit


def _account_age_days(db, user_id: int) -> float:
    import datetime as dt
    u = db.get_user(user_id)
    if u is None:
        return 0.0
    return (dt.datetime.utcnow() - u.created_at).total_seconds() / 86400.0
