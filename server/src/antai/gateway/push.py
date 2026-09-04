"""Push abstraction: WS delivery to live sockets, real FCM (HTTP v1) otherwise.

provider=ws  -> delivered over the live WebSocket (realtime hub).
provider=fcm -> delivered via Firebase Cloud Messaging using firebase-admin
                (requires `push.fcm_credentials` = path to a service-account
                JSON and device tokens registered via POST /api/devices).
"""
from __future__ import annotations

import logging

from ..config import get_config
from ..realtime import get_hub
from ..storage import get_db

log = logging.getLogger(__name__)


class PushService:
    """Delivers push-style notifications (verify pings, report-ready) to users."""

    def __init__(self) -> None:
        cfg = get_config()
        self.provider = cfg.push.provider
        self._fcm_app = None
        if self.provider == "fcm":
            self._init_fcm(cfg.push.fcm_credentials)

    def _init_fcm(self, credentials_path: str | None) -> None:
        if not credentials_path:
            log.error("push.provider=fcm but push.fcm_credentials is not set")
            return
        try:
            import firebase_admin
            from firebase_admin import credentials
            cred = credentials.Certificate(credentials_path)
            self._fcm_app = firebase_admin.initialize_app(cred)
            log.info("FCM initialized")
        except Exception as e:
            log.error("FCM init failed: %s", e)
            self._fcm_app = None

    async def send(self, user_id: int, kind: str, data: dict) -> bool:
        if self.provider == "fcm":
            return await self._send_fcm(user_id, kind, data)
        return await get_hub().send(user_id, kind, data)

    async def _send_fcm(self, user_id: int, kind: str, data: dict) -> bool:
        if self._fcm_app is None:
            return False
        try:
            from firebase_admin import messaging
            tokens = get_db().device_tokens_for(user_id)
            if not tokens:
                return False
            payload = {"type": kind, **data}
            message = messaging.MulticastMessage(
                notification=messaging.Notification(
                    title=_title_for(kind),
                    body=_body_for(kind, data),
                ),
                data={k: _as_str(v) for k, v in payload.items()},
                tokens=tokens,
            )
            resp = messaging.send_each_for_multicast(message)
            if resp.failure_count:
                log.warning("FCM failures: %s/%s", resp.failure_count, len(responses(resp)))
            return resp.success_count > 0
        except Exception as e:
            log.warning("FCM send failed: %s", e)
            return False


def responses(resp):
    return resp.responses


def _as_str(v) -> str:
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    return str(v)


def _title_for(kind: str) -> str:
    return {
        "verify.prompt": "Identity verification",
        "report.ready": "Safety report ready",
        "verdict.update": "antAI alert",
        "freeze.request": "Action held",
        "guidance.update": "antAI guidance",
    }.get(kind, "antAI")


def _body_for(kind: str, data: dict) -> str:
    if kind == "verify.prompt":
        return "Someone is claiming to be you in a call. Tap to confirm."
    if kind == "report.ready":
        return f"Your report is ready: {data.get('title', '')}"
    if kind == "verdict.update":
        return data.get("verdict", "Please check the latest safety alert.")
    if kind == "freeze.request":
        return data.get("message", "An action was held pending your confirmation.")
    return "You have a new antAI notification."


_push: PushService | None = None


def get_push() -> PushService:
    global _push
    if _push is None:
        _push = PushService()
    return _push