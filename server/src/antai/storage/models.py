"""SQLAlchemy ORM models for the antAI server.

Sensitive fields (phone numbers, voiceprint embeddings, message bodies,
behavioral profiles) are stored as encrypted byte blobs via Crypto.
Raw audio/video is never persisted.
"""
from __future__ import annotations

import datetime as dt

from sqlalchemy import (Boolean, DateTime, Float, ForeignKey, Integer, JSON,
                        LargeBinary, String, Text)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

# BLOB type that stores encrypted bytes
EncBlob = LargeBinary


class Base(DeclarativeBase):
    pass


def _utcnow() -> dt.datetime:
    return dt.datetime.utcnow()


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    phone_enc: Mapped[bytes] = mapped_column(LargeBinary, nullable=True)
    phone_hash: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(128))
    avatar: Mapped[str | None] = mapped_column(String(512), nullable=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)

    contacts: Mapped[list["Contact"]] = relationship(
        back_populates="user", cascade="all, delete-orphan",
        foreign_keys="Contact.user_id")
    messages: Mapped[list["Message"]] = relationship(
        back_populates="sender", foreign_keys="Message.sender_id")


class SessionToken(Base):
    __tablename__ = "session_tokens"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
    expires_at: Mapped[dt.datetime] = mapped_column(DateTime)


class Contact(Base):
    """Directory entry: a user A has saved about another user B."""
    __tablename__ = "contacts"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    peer_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    label: Mapped[str] = mapped_column(String(64))          # "Mom", "Bank", "Son"
    relationship_tag: Mapped[str | None] = mapped_column(String(32), nullable=True)
    is_trusted: Mapped[bool] = mapped_column(Boolean, default=False)
    linked: Mapped[bool] = mapped_column(Boolean, default=False)  # mutual trusted-circle link
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)

    user: Mapped["User"] = relationship(back_populates="contacts",
                                        foreign_keys=[user_id])
    peer: Mapped["User"] = relationship(foreign_keys=[peer_id])


class Voiceprint(Base):
    """ECAPA-TDNN embedding of a trusted contact's voice (encrypted)."""
    __tablename__ = "voiceprints"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)   # whose voice
    owner_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)  # who enrolled it
    embedding_enc: Mapped[bytes] = mapped_column(LargeBinary)   # Fernet-encrypted np float32
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)


class BehaviorProfile(Base):
    """Rolling summary of how a contact normally writes/talks."""
    __tablename__ = "behavior_profiles"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    profile_enc: Mapped[bytes] = mapped_column(LargeBinary)   # Fernet-encrypted JSON
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)


class Call(Base):
    __tablename__ = "calls"

    id: Mapped[int] = mapped_column(primary_key=True)
    caller_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    callee_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    kind: Mapped[str] = mapped_column(String(16), default="voice")  # voice | video
    started_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
    ended_at: Mapped[dt.datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="ongoing")
    risk_peak: Mapped[float] = mapped_column(Float, default=0.0)


class Message(Base):
    __tablename__ = "messages"

    id: Mapped[int] = mapped_column(primary_key=True)
    sender_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    recipient_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    body_enc: Mapped[bytes] = mapped_column(LargeBinary)   # encrypted body
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
    risk_score: Mapped[float] = mapped_column(Float, default=0.0)
    intercepted: Mapped[bool] = mapped_column(Boolean, default=False)

    sender: Mapped["User"] = relationship(back_populates="messages",
                                          foreign_keys=[sender_id])


class Verdict(Base):
    """Plain-language verdict output from the LLM Reasoning node."""
    __tablename__ = "verdicts"

    id: Mapped[int] = mapped_column(primary_key=True)
    session_key: Mapped[str] = mapped_column(String(128), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    kind: Mapped[str] = mapped_column(String(16))            # call | message | live
    risk_score: Mapped[float] = mapped_column(Float, default=0.0)
    band: Mapped[str] = mapped_column(String(16), default="passive")
    verdict_text: Mapped[str] = mapped_column(Text)
    why: Mapped[str] = mapped_column(Text, default="")
    action: Mapped[str] = mapped_column(Text, default="")
    scam_type: Mapped[str | None] = mapped_column(String(64), nullable=True)
    signals: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)


class Report(Base):
    """Post-call / post-chat incident report generated by the LLM."""
    __tablename__ = "reports"

    id: Mapped[int] = mapped_column(primary_key=True)
    session_key: Mapped[str] = mapped_column(String(128), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    kind: Mapped[str] = mapped_column(String(16))
    title: Mapped[str] = mapped_column(String(256), default="")
    body: Mapped[str] = mapped_column(Text)
    scam_type: Mapped[str | None] = mapped_column(String(64), nullable=True)
    signals_fired: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)


class Flag(Base):
    """Collective flagging DB (hashed identifiers only, never raw PII)."""
    __tablename__ = "flags"

    id: Mapped[int] = mapped_column(primary_key=True)
    phone_hash: Mapped[str] = mapped_column(String(128), index=True)
    voice_hash: Mapped[str | None] = mapped_column(String(128), nullable=True, index=True)
    count: Mapped[int] = mapped_column(Integer, default=1)
    confidence: Mapped[float] = mapped_column(Float, default=0.0)
    first_seen: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
    last_seen: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)


class FreezeSession(Base):
    """Intercept hold state: an action frozen pending explicit user confirm."""
    __tablename__ = "freeze_sessions"

    id: Mapped[int] = mapped_column(primary_key=True)
    session_key: Mapped[str] = mapped_column(String(128), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    request_type: Mapped[str] = mapped_column(String(32))   # money|otp|credential|remote-access|link
    payload: Mapped[dict] = mapped_column(JSON, default=dict)
    state: Mapped[str] = mapped_column(String(16), default="frozen")  # frozen|confirmed|overridden
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
    resolved_at: Mapped[dt.datetime | None] = mapped_column(DateTime, nullable=True)


class FlagSubmission(Base):
    """One collective-flag action by a user (rate-limit + reputation tracking)."""
    __tablename__ = "flag_submissions"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    phone_hash: Mapped[str] = mapped_column(String(128), index=True)
    kind: Mapped[str] = mapped_column(String(32), default="scam")
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)


class DeviceToken(Base):
    """FCM/APNs device tokens per user for push delivery."""
    __tablename__ = "device_tokens"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    platform: Mapped[str] = mapped_column(String(16), default="android")  # android | ios | web
    token: Mapped[str] = mapped_column(String(512), unique=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, default=_utcnow)
