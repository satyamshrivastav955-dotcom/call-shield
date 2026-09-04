"""Field-level encryption at rest using Fernet (AES-128-CBC + HMAC)."""
from __future__ import annotations

import base64
import hashlib

from cryptography.fernet import Fernet, InvalidToken


class Crypto:
    """Encrypt/decrypt sensitive fields stored in the DB.

    Keys are derived from a passphrase via SHA-256 so we never store the raw
    key in the DB. Raw media is never persisted; only derived embeddings and
    signals are stored, and the most sensitive ones (voiceprints, phone
    numbers if so configured) are encrypted.
    """

    def __init__(self, passphrase: str):
        digest = hashlib.sha256(passphrase.encode("utf-8")).digest()
        self._fernet = Fernet(base64.urlsafe_b64encode(digest))

    def encrypt(self, value: str | bytes | None) -> bytes | None:
        if value is None:
            return None
        if isinstance(value, str):
            value = value.encode("utf-8")
        return self._fernet.encrypt(value)

    def decrypt(self, token: bytes | None) -> str | None:
        if token is None:
            return None
        try:
            return self._fernet.decrypt(token).decode("utf-8")
        except InvalidToken:
            return None

    def decrypt_bytes(self, token: bytes | None) -> bytes | None:
        if token is None:
            return None
        try:
            return self._fernet.decrypt(token)
        except InvalidToken:
            return None
