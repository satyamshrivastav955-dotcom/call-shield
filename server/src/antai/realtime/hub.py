"""Realtime WebSocket hub: per-user connection management and message routing."""
from __future__ import annotations

import asyncio
import json
import logging
import time
from typing import Any

log = logging.getLogger(__name__)


class RealtimeHub:
    """Tracks authenticated WebSocket connections per user and delivers
    verdict/guidance/freeze/verify messages in real time."""

    def __init__(self) -> None:
        self._conns: dict[int, set[asyncio.Queue]] = {}
        self._lock = asyncio.Lock()

    async def register(self, user_id: int) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue(maxsize=1000)
        async with self._lock:
            self._conns.setdefault(user_id, set()).add(q)
        return q

    async def unregister(self, user_id: int, q: asyncio.Queue) -> None:
        async with self._lock:
            conns = self._conns.get(user_id)
            if conns and q in conns:
                conns.discard(q)
                if not conns:
                    self._conns.pop(user_id, None)

    async def send(self, user_id: int, kind: str, data: dict[str, Any] | None = None,
                   **kw) -> bool:
        """Deliver a message to all live sockets of a user. Returns True if any delivered."""
        payload = json.dumps({"type": kind, **(data or {}), **kw}, ensure_ascii=False)
        async with self._lock:
            conns = list(self._conns.get(user_id, ()))
        delivered = False
        for q in conns:
            try:
                q.put_nowait(payload)
                delivered = True
            except asyncio.QueueFull:
                log.warning("queue full for user %s", user_id)
        return delivered

    async def broadcast(self, user_ids: list[int], kind: str, data: dict[str, Any]) -> None:
        for uid in user_ids:
            await self.send(uid, kind, data)

    def online(self, user_id: int) -> bool:
        return bool(self._conns.get(user_id))


hub: RealtimeHub | None = None


def get_hub() -> RealtimeHub:
    global hub
    if hub is None:
        hub = RealtimeHub()
    return hub
