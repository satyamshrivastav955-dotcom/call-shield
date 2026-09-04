"""Recv-only WebRTC analysis tap session (one per phone).

The antAI "parallel tap" integration: while a normal P2P video call runs
between two phones (signaled by the Node.js server), each phone ALSO opens a
second, send-only PeerConnection to the antAI server carrying a copy of its
local mic/camera tracks. The server never relays this media anywhere - it only
DECODES it and forks every frame into the ingestion layer (VAD/ASR audio +
frame-sampled video) that feeds the LangGraph scam-detection pipeline.

Compared to the full SFU CallSession this is deliberately simple:
  - one aiortc RTCPeerConnection per phone, recvonly for the server;
  - the phone's offer contains its sendonly transceivers; the server just
    answers (no replaceTrack dance, no pre-negotiated senders);
  - each incoming track gets a MediaRelay fan-out with a single TAP
    subscriber (no relay subscriber - nothing to forward).
"""
from __future__ import annotations

import asyncio
import logging
from typing import Awaitable, Callable, Optional

from aiortc import RTCPeerConnection, RTCSessionDescription

from .tracks import make_fanout
from .session import _build_rtc_config

log = logging.getLogger(__name__)

IngestFn = Callable[[int, object], Awaitable[None]]


class TapSession:
    """One phone's analysis tap: a recv-only PC + ingestion fan-out."""

    def __init__(self, user_id: int, on_ingest: IngestFn):
        self.user_id = user_id
        self._on_ingest = on_ingest
        self._closed = False
        cfg = _build_rtc_config()
        self.pc = RTCPeerConnection(configuration=cfg) if cfg else RTCPeerConnection()
        self._fanouts = {}
        self.pc.on("track", self._on_track)

    async def _on_track(self, track):
        if self._closed:
            return
        fanout = make_fanout(track)
        if fanout is None:
            return
        self._fanouts[track.kind] = fanout
        # Tap-only subscription: every frame goes to the ingestion layer,
        # nothing is relayed onward (the real call stays P2P).
        fanout.add_tap(lambda frame, uid=self.user_id: self._on_ingest(uid, frame))
        log.info("tap user=%s: %s track fanned into ingestion", self.user_id, track.kind)

    async def set_offer_and_answer(self, sdp: str) -> str:
        await self.pc.setRemoteDescription(RTCSessionDescription(sdp=sdp, type="offer"))
        answer = await self.pc.createAnswer()
        await self.pc.setLocalDescription(answer)
        # give ICE gathering a moment so the answer SDP is self-contained;
        # trickle candidates still work via tap.ice if any straggle in
        for _ in range(120):  # up to 6s
            if self.pc.iceGatheringState == "complete":
                break
            await asyncio.sleep(0.05)
        return self.pc.localDescription.sdp

    async def add_ice(self, candidate: dict):
        from aiortc import RTCIceCandidate
        cand = RTCIceCandidate(
            component=int(candidate.get("component") or 1),
            foundation=candidate.get("foundation") or "",
            ip=candidate.get("ip"),
            port=int(candidate.get("port") or 0),
            priority=int(candidate.get("priority") or 0),
            protocol=candidate.get("protocol") or "udp",
            relatedAddress=candidate.get("relatedAddress"),
            relatedPort=int(candidate.get("relatedPort") or 0),
            sdpMid=candidate.get("sdpMid"),
            sdpMLineIndex=int(candidate.get("sdpMLineIndex") or 0),
            tcpType=candidate.get("tcpType"),
            type=candidate.get("type") or "host",
        )
        await self.pc.addIceCandidate(cand)

    async def close(self):
        if self._closed:
            return
        self._closed = True
        try:
            await self.pc.close()
        except Exception:
            pass
        for f in self._fanouts.values():
            f.stop()
        self._fanouts.clear()
