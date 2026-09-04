"""Database engine, sessions, and high-level repositories."""
from __future__ import annotations

import contextlib
import hashlib
import json
import os
import threading
from typing import Iterator, Optional

import numpy as np
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker

from ..config import AppConfig, get_config
from .crypto import Crypto
from .models import (Base, BehaviorProfile, Call, Contact, Flag, FreezeSession,
                     Message, Report, SessionToken, User, Verdict, Voiceprint)


# MIGRATION NOTE (P1.6): A salt prefix was added to _phone_hash.
# Existing rows hashed without a salt are still found when ANTAI_PHONE_SALT=""
# (the default), which preserves backward compatibility.  Once all clients are
# on a salted deployment, re-hash and rotate: set the env var, run a migration
# script that re-hashes phone_hash for every User row, then clear the old rows.
def _phone_hash(phone: str, salt: str = "") -> str:
    """SHA-256 hash of (salt + phone).  salt comes from ANTAI_PHONE_SALT."""
    return hashlib.sha256((salt + phone).encode("utf-8")).hexdigest()


class Database:
    """Owns the engine, encryption, and repository helpers."""

    def __init__(self, cfg: AppConfig):
        self.cfg = cfg
        self.crypto = Crypto(cfg.storage.encryption_key)
        # Warn loudly when the placeholder key is still in use (P1.6 privacy hardening).
        if cfg.storage.encryption_key == "change-me-please" and not os.environ.get("ANTAI_ALLOW_DEFAULT_KEY"):
            import logging as _logging
            _logging.getLogger(__name__).critical(
                "SECURITY: storage.encryption_key is the default value 'change-me-please'. "
                "Set a real key in server/.env as STORAGE_ENCRYPTION_KEY or change it in config.yaml. "
                "Set ANTAI_ALLOW_DEFAULT_KEY=1 to suppress this warning in dev/test environments."
            )
        # Salt used for phone-number hashing (P1.6).  Defaults to "" so that
        # existing deployments without the env var keep working unchanged.
        self._phone_salt = os.environ.get("ANTAI_PHONE_SALT", "")
        url = cfg.storage.url
        if url.startswith("sqlite:///"):
            self.engine = create_engine(url, connect_args={"check_same_thread": False})
        else:
            self.engine = create_engine(url)
        self._session_factory = sessionmaker(bind=self.engine, expire_on_commit=False)
        self._lock = threading.RLock()
        Base.metadata.create_all(self.engine)


    def session(self) -> Session:
        return self._session_factory()

    # ---------------- users / auth ----------------
    def create_user(self, phone: str, display_name: str) -> User:
        with self._lock, self.session() as s:
            user = User(phone_hash=_phone_hash(phone, self._phone_salt),
                        phone_enc=self.crypto.encrypt(phone),
                        display_name=display_name or "User")
            s.add(user)
            s.commit()
            s.refresh(user)
            return user

    def get_user_by_phone(self, phone: str) -> Optional[User]:
        clean = (phone or "").strip()
        if not clean:
            return None
        with self.session() as s:
            u = s.scalar(select(User).where(User.phone_hash == _phone_hash(clean, self._phone_salt)))
            if u is not None:
                return u
            # Try with +91 if 10 digits
            if len(clean) == 10 and clean.isdigit():
                u = s.scalar(select(User).where(User.phone_hash == _phone_hash(f"+91{clean}", self._phone_salt)))
                if u is not None:
                    return u
            elif clean.startswith("+91") and len(clean) == 13:
                u = s.scalar(select(User).where(User.phone_hash == _phone_hash(clean[3:], self._phone_salt)))
                if u is not None:
                    return u
            elif clean.startswith("+"):
                u = s.scalar(select(User).where(User.phone_hash == _phone_hash(clean[1:], self._phone_salt)))
                if u is not None:
                    return u
            # Fallback: check all users by decrypted phone suffix (last 10 digits)
            for user in s.scalars(select(User)):
                p = self.phone_of(user)
                if p and len(p) >= 10 and len(clean) >= 10:
                    if p[-10:] == clean[-10:]:
                        return user
            return None


    def get_user(self, user_id: int) -> Optional[User]:
        with self.session() as s:
            return s.get(User, user_id)

    def phone_of(self, user: User) -> str:
        return self.crypto.decrypt(user.phone_enc) if user and user.phone_enc else ""

    def create_token(self, user_id: int, token: str, expires_at) -> SessionToken:
        with self._lock, self.session() as s:
            t = SessionToken(user_id=user_id,
                             token_hash=hashlib.sha256(token.encode()).hexdigest(),
                             expires_at=expires_at)
            s.add(t)
            s.commit()
            s.refresh(t)
            return t

    def get_user_by_token(self, token: str) -> Optional[User]:
        th = hashlib.sha256(token.encode()).hexdigest()
        with self.session() as s:
            t = s.scalar(select(SessionToken).where(SessionToken.token_hash == th))
            if t is None or t.expires_at < t.created_at.__class__.now(t.expires_at.tzinfo):
                return None
            return s.get(User, t.user_id)

    # ---------------- contacts ----------------
    def add_contact(self, user_id: int, peer_id: int, label: str,
                    relationship_tag: str | None = None,
                    is_trusted: bool = False) -> Contact:
        with self._lock, self.session() as s:
            c = Contact(user_id=user_id, peer_id=peer_id, label=label,
                        relationship_tag=relationship_tag, is_trusted=is_trusted)
            s.add(c)
            s.commit()
            s.refresh(c)
            return c

    def list_contacts(self, user_id: int) -> list[Contact]:
        with self.session() as s:
            return list(s.scalars(select(Contact).where(Contact.user_id == user_id)))

    def link_trusted(self, user_id: int, peer_id: int) -> None:
        with self._lock, self.session() as s:
            for c in s.scalars(select(Contact).where(Contact.user_id == user_id,
                                                     Contact.peer_id == peer_id)):
                c.linked = True
                c.is_trusted = True
            s.commit()

    # ---------------- voiceprints ----------------
    def save_voiceprint(self, user_id: int, owner_id: int, embedding: np.ndarray) -> Voiceprint:
        blob = self.crypto.encrypt(np.asarray(embedding, dtype=np.float32).tobytes())
        with self._lock, self.session() as s:
            v = Voiceprint(user_id=user_id, owner_id=owner_id, embedding_enc=blob)
            s.add(v)
            s.commit()
            s.refresh(v)
            return v

    def get_voiceprints_for(self, user_id: int, owner_id: int | None = None) -> list[Voiceprint]:
        with self.session() as s:
            q = select(Voiceprint).where(Voiceprint.user_id == user_id)
            if owner_id is not None:
                q = q.where(Voiceprint.owner_id == owner_id)
            return list(s.scalars(q))

    def get_voiceprint_embedding(self, v: Voiceprint) -> np.ndarray:
        raw = self.crypto.decrypt_bytes(v.embedding_enc)
        return np.frombuffer(raw, dtype=np.float32)

    def delete_voiceprints(self, user_id: int, owner_id: int | None = None) -> int:
        """Remove stored voiceprints -> number deleted.

        Enrolment appends rather than replaces (several samples of the same voice
        genuinely improve recall, since matching takes the best similarity), so
        the user needs a way to discard a bad recording — otherwise one noisy
        sample stays in the reference set forever, dragging the match score in a
        direction nobody can see or undo.
        """
        with self._lock, self.session() as s:
            q = select(Voiceprint).where(Voiceprint.user_id == user_id)
            if owner_id is not None:
                q = q.where(Voiceprint.owner_id == owner_id)
            rows = list(s.scalars(q))
            for r in rows:
                s.delete(r)
            s.commit()
            return len(rows)

    # ---------------- behavior profiles ----------------
    def save_behavior_profile(self, user_id: int, profile: dict) -> BehaviorProfile:
        blob = self.crypto.encrypt(json.dumps(profile))
        with self._lock, self.session() as s:
            existing = s.scalar(select(BehaviorProfile).where(
                BehaviorProfile.user_id == user_id))
            if existing is not None:
                existing.profile_enc = blob
                s.commit()
                s.refresh(existing)
                return existing
            p = BehaviorProfile(user_id=user_id, profile_enc=blob)
            s.add(p)
            s.commit()
            s.refresh(p)
            return p

    def get_behavior_profile(self, user_id: int) -> dict | None:
        with self.session() as s:
            p = s.scalar(select(BehaviorProfile).where(BehaviorProfile.user_id == user_id))
            if p is None:
                return None
            raw = self.crypto.decrypt_bytes(p.profile_enc)
            return json.loads(raw)

    # ---------------- calls / messages / verdicts / reports ----------------
    def create_call(self, caller_id: int, callee_id: int, kind: str) -> Call:
        with self._lock, self.session() as s:
            c = Call(caller_id=caller_id, callee_id=callee_id, kind=kind)
            s.add(c)
            s.commit()
            s.refresh(c)
            return c

    def end_call(self, call_id: int, risk_peak: float | None = None) -> None:
        import datetime as dt
        with self._lock, self.session() as s:
            c = s.get(Call, call_id)
            if c:
                c.ended_at = dt.datetime.utcnow()
                c.status = "ended"
                if risk_peak is not None:
                    c.risk_peak = max(c.risk_peak, risk_peak)
                s.commit()

    def save_message(self, sender_id: int, recipient_id: int, body: str,
                     risk_score: float = 0.0, intercepted: bool = False) -> Message:
        with self._lock, self.session() as s:
            m = Message(sender_id=sender_id, recipient_id=recipient_id,
                        body_enc=self.crypto.encrypt(body),
                        risk_score=risk_score, intercepted=intercepted)
            s.add(m)
            s.commit()
            s.refresh(m)
            return m

    def update_message_result(self, message_id: int, risk_score: float,
                              intercepted: bool = False) -> None:
        """Patch risk/intercepted onto an already-delivered message (the
        detection pipeline runs after instant delivery)."""
        with self._lock, self.session() as s:
            m = s.get(Message, message_id)
            if m:
                m.risk_score = risk_score
                m.intercepted = intercepted
                s.commit()

    def list_messages(self, a_id: int, b_id: int, limit: int = 100) -> list[Message]:
        with self.session() as s:
            q = (select(Message)
                 .where(((Message.sender_id == a_id) & (Message.recipient_id == b_id)) |
                        ((Message.sender_id == b_id) & (Message.recipient_id == a_id)))
                 .order_by(Message.created_at.desc()).limit(limit))
            msgs = list(s.scalars(q))
            msgs.reverse()
            return msgs

    def message_body(self, m: Message) -> str:
        return self.crypto.decrypt(m.body_enc) or ""

    def save_verdict(self, session_key: str, user_id: int, kind: str, risk_score: float,
                     band: str, verdict_text: str, why: str, action: str,
                     scam_type: str | None, signals: dict) -> Verdict:
        with self._lock, self.session() as s:
            v = Verdict(session_key=session_key, user_id=user_id, kind=kind,
                        risk_score=risk_score, band=band, verdict_text=verdict_text,
                        why=why, action=action, scam_type=scam_type, signals=signals)
            s.add(v)
            s.commit()
            s.refresh(v)
            return v

    def list_verdicts(self, user_id: int, limit: int = 50) -> list[Verdict]:
        with self.session() as s:
            return list(s.scalars(select(Verdict).where(Verdict.user_id == user_id)
                                  .order_by(Verdict.created_at.desc()).limit(limit)))

    def save_report(self, session_key: str, user_id: int, kind: str, title: str,
                    body: str, scam_type: str | None, signals_fired: dict) -> Report:
        with self._lock, self.session() as s:
            r = Report(session_key=session_key, user_id=user_id, kind=kind, title=title,
                       body=body, scam_type=scam_type, signals_fired=signals_fired)
            s.add(r)
            s.commit()
            s.refresh(r)
            return r

    def list_reports(self, user_id: int, limit: int = 50) -> list[Report]:
        with self.session() as s:
            return list(s.scalars(select(Report).where(Report.user_id == user_id)
                                  .order_by(Report.created_at.desc()).limit(limit)))

    # ---------------- collective flags ----------------
    def add_flag(self, phone_hash: str, voice_hash: str | None, weight: float) -> Flag:
        import datetime as dt
        now = dt.datetime.utcnow()
        with self._lock, self.session() as s:
            f = s.scalar(select(Flag).where(Flag.phone_hash == phone_hash))
            if f is None:
                f = Flag(phone_hash=phone_hash, voice_hash=voice_hash,
                         count=1, confidence=min(1.0, weight), last_seen=now)
                s.add(f)
            else:
                f.count += 1
                f.confidence = min(1.0, f.confidence + weight * 0.25)
                f.last_seen = now
            s.commit()
            s.refresh(f)
            return f

    def lookup_flag(self, phone_hash: str) -> Optional[Flag]:
        with self.session() as s:
            return s.scalar(select(Flag).where(Flag.phone_hash == phone_hash))

    def record_flag_submission(self, user_id: int, phone_hash: str, kind: str) -> None:
        import datetime as dt
        from .models import FlagSubmission
        with self._lock, self.session() as s:
            s.add(FlagSubmission(user_id=user_id, phone_hash=phone_hash, kind=kind))
            s.commit()

    def flag_submissions_since(self, user_id: int, hours: float = 1.0) -> int:
        import datetime as dt
        from sqlalchemy import func
        from .models import FlagSubmission
        since = dt.datetime.utcnow() - dt.timedelta(hours=hours)
        with self.session() as s:
            return int(s.scalar(select(func.count(FlagSubmission.id)).where(
                FlagSubmission.user_id == user_id,
                FlagSubmission.created_at >= since)) or 0)

    # ---------------- device tokens (push) ----------------
    def register_device_token(self, user_id: int, token: str, platform: str = "android") -> None:
        from .models import DeviceToken
        with self._lock, self.session() as s:
            existing = s.scalar(select(DeviceToken).where(
                DeviceToken.token == token))
            if existing is not None:
                existing.user_id = user_id
                existing.platform = platform
            else:
                s.add(DeviceToken(user_id=user_id, token=token, platform=platform))
            s.commit()

    def device_tokens_for(self, user_id: int) -> list[str]:
        from .models import DeviceToken
        with self.session() as s:
            rows = list(s.scalars(select(DeviceToken).where(
                DeviceToken.user_id == user_id)))
            return [r.token for r in rows]

    # ---------------- freeze sessions ----------------
    def create_freeze(self, session_key: str, user_id: int, request_type: str,
                      payload: dict) -> FreezeSession:
        with self._lock, self.session() as s:
            f = FreezeSession(session_key=session_key, user_id=user_id,
                              request_type=request_type, payload=payload)
            s.add(f)
            s.commit()
            s.refresh(f)
            return f

    def resolve_freeze(self, freeze_id: int, new_state: str) -> None:
        import datetime as dt
        with self._lock, self.session() as s:
            f = s.get(FreezeSession, freeze_id)
            if f:
                f.state = new_state
                f.resolved_at = dt.datetime.utcnow()
                s.commit()

    def pending_freeze(self, session_key: str) -> Optional[FreezeSession]:
        with self.session() as s:
            return s.scalar(select(FreezeSession).where(
                FreezeSession.session_key == session_key,
                FreezeSession.state == "frozen"))

    # ---------------- privacy-retention cleanup (P1.6) ----------------
    def cleanup_old_verdicts(self, days: int = 30) -> int:
        """Delete verdict rows older than ``days`` days. Returns deleted count."""
        import datetime as _dt
        cutoff = _dt.datetime.utcnow() - _dt.timedelta(days=days)
        with self._lock, self.session() as s:
            from sqlalchemy import delete
            from .models import Verdict
            result = s.execute(delete(Verdict).where(Verdict.created_at < cutoff))
            s.commit()
            return result.rowcount

    def cleanup_old_reports(self, days: int = 30) -> int:
        """Delete report rows older than ``days`` days. Returns deleted count."""
        import datetime as _dt
        cutoff = _dt.datetime.utcnow() - _dt.timedelta(days=days)
        with self._lock, self.session() as s:
            from sqlalchemy import delete
            from .models import Report
            result = s.execute(delete(Report).where(Report.created_at < cutoff))
            s.commit()
            return result.rowcount

_db: Database | None = None


def get_db() -> Database:
    global _db
    if _db is None:
        _db = Database(get_config())
    return _db


def set_db(db: Database) -> None:
    global _db
    _db = db


@contextlib.contextmanager
def get_db_session() -> Iterator[Session]:
    db = get_db()
    session = db.session()
    try:
        yield session
    finally:
        session.close()
