"""
antAI Windows Client — WebSocket Streaming Client

Sends PCM audio chunks to the antAI server's /api/stream/ws endpoint (same
protocol as stream_demo_file.py) and receives live normalized_result JSON.

Usage:
    from streaming_client import StreamingClient

    client = StreamingClient(server_url="ws://192.168.1.10:8765",
                             on_update=lambda snap: print(snap["risk"]))
    await client.start(sample_rate=16000, scenario="high_value_txn")
    await client.send_pcm(pcm_chunk)   # numpy float32
    final = await client.stop()
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import Callable, Optional

import numpy as np

log = logging.getLogger(__name__)


class StreamingClient:
    """Asynchronous WebSocket client mirroring the /api/stream/ws protocol.

    The server contract (see gateway/stream_api.py):
      client → {"type":"start", "sample_rate":…, "format":"f32le", "scenario":…}
      client → <binary float32-le PCM>
      server → {"type":"update", "risk":…, ...normalized_result...}
      client → {"type":"stop"}
      server → {"type":"final", …}
    """

    def __init__(
        self,
        server_url: str = "ws://localhost:8765",
        on_update: Optional[Callable[[dict], None]] = None,
        on_final: Optional[Callable[[dict], None]] = None,
    ):
        self.server_url = server_url.rstrip("/")
        self.ws_url = f"{self.server_url}/api/stream/ws"
        self.on_update = on_update
        self.on_final = on_final
        self._ws = None
        self._recv_task: Optional[asyncio.Task] = None
        self._started = False

    async def start(
        self,
        sample_rate: int = 16000,
        scenario: Optional[str] = None,
        context: Optional[dict] = None,
    ) -> bool:
        """Connect to the server and send the 'start' control frame.
        Returns True on success, False on connection failure.
        """
        try:
            import websockets  # type: ignore
        except ImportError:
            log.error("websockets not installed — run: pip install websockets")
            return False
        try:
            self._ws = await websockets.connect(self.ws_url, ping_interval=20)
            start_msg: dict = {
                "type": "start",
                "sample_rate": sample_rate,
                "format": "f32le",
                "speaker_id": 1,
            }
            if scenario:
                start_msg["scenario"] = scenario
            if context:
                start_msg["context"] = context
            await self._ws.send(json.dumps(start_msg, ensure_ascii=False))
            # wait for "started" confirmation
            raw = await asyncio.wait_for(self._ws.recv(), timeout=10.0)
            msg = json.loads(raw)
            if msg.get("type") != "started":
                log.warning("Unexpected response: %s", msg)
            self._started = True
            # kick off receiver task
            self._recv_task = asyncio.create_task(self._receive_loop())
            log.info("Stream session started: %s", msg.get("session_key"))
            return True
        except Exception as e:
            log.error("Could not connect to %s: %s", self.ws_url, e)
            return False

    async def send_pcm(self, audio: np.ndarray) -> None:
        """Send a mono float32 PCM chunk as a binary WebSocket frame."""
        if self._ws is None or not self._started:
            return
        try:
            blob = np.asarray(audio, dtype="<f4").tobytes()
            await self._ws.send(blob)
        except Exception as e:
            log.debug("send_pcm error: %s", e)

    async def stop(self) -> dict:
        """Send 'stop', wait for the final snapshot, and disconnect."""
        if self._ws is None:
            return {}
        try:
            await self._ws.send(json.dumps({"type": "stop"}))
        except Exception:
            pass
        # Give the receive loop a moment to catch the 'final' frame
        if self._recv_task and not self._recv_task.done():
            try:
                await asyncio.wait_for(self._recv_task, timeout=5.0)
            except asyncio.TimeoutError:
                self._recv_task.cancel()
        try:
            await self._ws.close()
        except Exception:
            pass
        self._ws = None
        self._started = False
        return {}

    async def _receive_loop(self) -> None:
        """Background task: receive server frames and dispatch callbacks."""
        try:
            async for raw in self._ws:
                try:
                    msg = json.loads(raw)
                except Exception:
                    continue
                mtype = msg.get("type")
                if mtype == "update" and self.on_update:
                    try:
                        self.on_update(msg)
                    except Exception:
                        log.debug("on_update callback error", exc_info=True)
                elif mtype == "final":
                    if self.on_final:
                        try:
                            self.on_final(msg)
                        except Exception:
                            log.debug("on_final callback error", exc_info=True)
                    break
                elif mtype == "error":
                    log.warning("Server error: %s", msg.get("message"))
        except Exception as e:
            log.debug("receive_loop ended: %s", e)
