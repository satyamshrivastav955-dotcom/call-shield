"""Prototype phone-number OTP auth.

Dev mode (auto_verify_otp=True): any OTP is accepted and the code is printed
to the server log so a real SMS provider can be swapped in later without
changing the client contract.
"""
from __future__ import annotations

import datetime as dt
import logging
import random
import re

from fastapi import HTTPException, Request
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer

from ..config import get_config
from ..storage import get_db
from ..storage.models import User

log = logging.getLogger(__name__)

_PHONE_RE = re.compile(r"^\+?[0-9]{8,15}$")
_otps: dict[str, tuple[str, dt.datetime]] = {}


def _new_token(user_id: int) -> str:
    cfg = get_config()
    s = URLSafeTimedSerializer(cfg.storage.encryption_key, salt="antai-session")
    return s.dumps({"uid": user_id})


def _read_token(token: str) -> int | None:
    cfg = get_config()
    s = URLSafeTimedSerializer(cfg.storage.encryption_key, salt="antai-session")
    try:
        data = s.loads(token, max_age=cfg.auth.token_ttl_hours * 3600)
        return int(data["uid"])
    except (BadSignature, SignatureExpired, KeyError, ValueError):
        return None


def request_otp(phone: str) -> dict:
    if not _PHONE_RE.match(phone):
        raise HTTPException(400, "invalid phone number")
    cfg = get_config()
    otp = "".join(str(random.randint(0, 9)) for _ in range(cfg.auth.otp_len))
    _otps[phone] = (otp, dt.datetime.now(dt.timezone.utc))
    log.warning("[DEV OTP] phone=%s otp=%s  (auto_verify=%s)",
                phone, otp, cfg.auth.auto_verify_otp)
    if cfg.auth.auto_verify_otp:
        return {"sent": True, "dev_otp": otp, "auto_verify": True}
    return {"sent": True}


def verify_otp(phone: str, otp: str, display_name: str | None = None) -> dict:
    cfg = get_config()
    db = get_db()
    ok = False
    if cfg.auth.auto_verify_otp:
        ok = len(otp) == cfg.auth.otp_len
    else:
        stored = _otps.get(phone)
        if stored:
            code, issued = stored
            age = (dt.datetime.now(dt.timezone.utc) - issued).total_seconds()
            ok = code == otp and age < 600
    if not ok:
        raise HTTPException(401, "invalid or expired OTP")
    user = db.get_user_by_phone(phone)
    if user is None:
        user = db.create_user(phone, display_name or "User")
    token = _new_token(user.id)
    expires = dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=cfg.auth.token_ttl_hours)
    db.create_token(user.id, token, expires)
    return {"token": token, "user_id": user.id, "display_name": user.display_name}


def current_user(request: Request) -> User:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    token = auth[len("Bearer "):].strip()
    uid = _read_token(token)
    if uid is None:
        raise HTTPException(401, "invalid or expired token")
    user = get_db().get_user(uid)
    if user is None or not user.is_active:
        raise HTTPException(401, "unknown user")
    return user


def token_user(token: str) -> User | None:
    uid = _read_token(token)
    if uid is None:
        return None
    return get_db().get_user(uid)
