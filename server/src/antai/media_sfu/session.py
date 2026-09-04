"""Call session: two server-side peer connections acting as a media relay.

Signaling model (server is the SFU hub):
  - CALLER client  -> server: offer  (server answers)
  - server         -> CALLEE client: offer  (callee answers)
  - ICE candidates flow both ways for each participant.
Each participant's remote track is fanned out: relayed to the other
participant AND forked into the ingestion tap for detection.
"""
from __future__ import annotations

import asyncio
import logging
import uuid
from typing import Awaitable, Callable, Optional

from aiortc import RTCPeerConnection, RTCConfiguration, RTCIceServer

from .tracks import make_fanout

log = logging.getLogger(__name__)

# callback types used by the session to emit signaling messages
SendFn = Callable[[dict], Awaitable[None]]

_rtc_config_logged = False


def _build_rtc_config() -> Optional[RTCConfiguration]:
    """Build the SFU's ICE configuration from `ice:` in config.yaml.

    Adds a local TURN relay so call/video media can traverse a USB-only link
    (see IceConfig docs). Non-forcing: if TURN is unreachable aioice skips it and
    same-Wi-Fi host candidates still connect. Returns None to keep aiortc's
    default behavior when ICE is disabled.
    """
    global _rtc_config_logged
    try:
        from ..config import get_config
        ice = get_config().ice
    except Exception:  # pragma: no cover - config always present in practice
        return None
    if not getattr(ice, "enabled", True):
        return None
    servers: list[RTCIceServer] = []
    if getattr(ice, "stun_url", ""):
        servers.append(RTCIceServer(urls=ice.stun_url))
    if getattr(ice, "turn_url", ""):
        servers.append(RTCIceServer(
            urls=ice.turn_url,
            username=(ice.turn_username or None),
            credential=(ice.turn_password or None),
        ))
    if not servers:
        return None
    if not _rtc_config_logged:
        log.info("SFU ICE servers: %s", [s.urls for s in servers])
        _rtc_config_logged = True
    return RTCConfiguration(iceServers=servers)


async def _wait_ice_complete(pc: RTCPeerConnection, timeout_s: float = 6.0) -> None:
    """Wait until ICE candidate gathering finishes (aiortc 1.x gathers async).

    Timeout allows for a TURN-over-TCP allocation (used for USB-only media) to
    complete so the relay candidate lands in the SDP; host-only gathering still
    returns as soon as the state flips to 'complete'.
    """
    for _ in range(int(timeout_s * 20)):
        if pc.iceGatheringState == "complete":
            return
        await asyncio.sleep(0.05)


class CallSession:
    def __init__(self, call_id: int, session_key: str, caller_id: int, callee_id: int,
                 kind: str = "voice", send: SendFn | None = None,
                 on_ingest: Callable[[str, object], Awaitable[None]] | None = None):
        self.call_id = call_id
        self.session_key = session_key
        self.caller_id = caller_id
        self.callee_id = callee_id
        self.kind = kind
        self._send = send
        self._on_ingest = on_ingest

        # PC peers with the CALLER client
        # ICE config adds a local TURN relay so media works over USB-only
        # (no Wi-Fi). Passing configuration=None keeps aiortc's default.
        _cfg = _build_rtc_config()
        self.pc_caller = RTCPeerConnection(configuration=_cfg) if _cfg else RTCPeerConnection()
        # PC peers with the CALLEE client
        self.pc_callee = RTCPeerConnection(configuration=_cfg) if _cfg else RTCPeerConnection()

        self._fanouts: dict[str, dict] = {"caller": {}, "callee": {}}
        self._closed = False

        # P1-C FIX: track which sendonly senders are unclaimed (pre-filled with
        # aiortc dummy tracks, NOT None). Keyed by (pc_id, kind) -> RtpSender.
        # When a relay track arrives we pop from this dict to find the right sender
        # instead of checking sender.track is None (which is always False in aiortc).
        self._unclaimed_senders: dict[tuple, object] = {}

        # placeholder sendonly transceivers (relayed remote media arrives here)
        self._prepare(self.pc_caller, self._on_remote_track("caller"))
        self._prepare(self.pc_callee, self._on_remote_track("callee"))

    # ---------------------------------------------------------------- setup
    def _prepare(self, pc: RTCPeerConnection, on_track):
        pc.on("track", on_track)
        for kind in (["audio", "video"] if self.kind == "video" else ["audio"]):
            tr = pc.addTransceiver(kind, direction="sendrecv")
            # P1-C FIX: register the pre-created sender as unclaimed so
            # _replace_track can find it without relying on sender.track is None.
            self._unclaimed_senders[(id(pc), kind)] = tr.sender

    def _on_remote_track(self, role: str):
        async def handler(track):
            if self._closed:
                return
            fanout = make_fanout(track)
            if fanout is None:
                return
            # remember for cleanup
            self._fanouts[role][track.kind] = fanout
            # 1) relay to the other participant's sendonly transceiver
            other = "callee" if role == "caller" else "caller"
            relay = fanout.add_relay()
            pc_other = self.pc_callee if role == "caller" else self.pc_caller
            await self._replace_track(pc_other, track.kind, relay)
            # 2) fork a copy into the ingestion tap
            if self._on_ingest:
                participant_id = self.caller_id if role == "caller" else self.callee_id
                fanout.add_tap(lambda frame, pid=participant_id: self._on_ingest(pid, frame))
            log.debug("session %s: fanned out %s from %s", self.session_key, track.kind, role)
        return handler

    async def _replace_track(self, pc: RTCPeerConnection, kind: str, track):
        # P1-C FIX: use the unclaimed-sender registry populated in _prepare().
        # In aiortc, sender.replaceTrack and pc.addTrack are synchronous methods.
        key = (id(pc), kind)
        sender = self._unclaimed_senders.pop(key, None)
        if sender is not None:
            ret = sender.replaceTrack(track)
            if asyncio.iscoroutine(ret):
                await ret
            log.info("session %s: replaceTrack kind=%s on pre-negotiated sender ✓",
                     self.session_key, kind)
            return
        # Only reached for unexpected extra tracks (e.g. a second video stream).
        # In normal operation this should not happen.
        log.warning("session %s: no unclaimed sender for kind=%s; falling back to addTrack "
                    "(track will NOT be in the negotiated SDP)", self.session_key, kind)
        ret = pc.addTrack(track)
        if asyncio.iscoroutine(ret):
            await ret

    # ------------------------------------------------------------ signaling
    async def set_caller_offer(self, sdp: str):
        await self.pc_caller.setRemoteDescription(self._desc("offer", sdp))

    async def answer_caller(self) -> dict:
        answer = await self.pc_caller.createAnswer()
        await self.pc_caller.setLocalDescription(answer)
        await _wait_ice_complete(self.pc_caller)
        return {"sdp": self.pc_caller.localDescription.sdp,
                "type": "answer", "session_key": self.session_key}

    async def create_callee_offer(self) -> dict:
        offer = await self.pc_callee.createOffer()
        await self.pc_callee.setLocalDescription(offer)
        await _wait_ice_complete(self.pc_callee)
        return {"sdp": self.pc_callee.localDescription.sdp,
                "type": "offer", "session_key": self.session_key}

    async def set_callee_answer(self, sdp: str):
        await self.pc_callee.setRemoteDescription(_sdp_from(sdp))

    async def add_ice(self, side: str, candidate: dict):
        pc = self.pc_caller if side == "caller" else self.pc_callee
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
        await pc.addIceCandidate(cand)

    # -------------------------------------------------------------- teardown
    async def close(self):
        if self._closed:
            return
        self._closed = True
        for pc in (self.pc_caller, self.pc_callee):
            try:
                await pc.close()
            except Exception:
                pass
        for fans in self._fanouts.values():
            for f in fans.values():
                f.stop()

    @staticmethod
    def _desc(typ: str, sdp: str):
        from aiortc import RTCSessionDescription
        return RTCSessionDescription(sdp=sdp, type=typ)


class CallManager:
    """Registry of live call sessions."""

    def __init__(self):
        self._sessions: dict[str, CallSession] = {}
        self._by_call_id: dict[int, CallSession] = {}
        self._lock = asyncio.Lock()

    async def create(self, call_id: int, caller_id: int, callee_id: int, kind: str,
                     send: SendFn, on_ingest) -> CallSession:
        key = f"call:{call_id}"
        s = CallSession(call_id, key, caller_id, callee_id, kind, send=send,
                        on_ingest=on_ingest)
        async with self._lock:
            self._sessions[key] = s
            self._by_call_id[call_id] = s
        return s

    def get(self, session_key: str) -> Optional[CallSession]:
        return self._sessions.get(session_key)

    def get_by_call_id(self, call_id: int) -> Optional[CallSession]:
        return self._by_call_id.get(call_id)

    async def remove(self, session_key: str):
        async with self._lock:
            s = self._sessions.pop(session_key, None)
            if s:
                self._by_call_id.pop(s.call_id, None)
        if s:
            await s.close()


def _sdp_from(sdp: str):
    from aiortc import RTCSessionDescription
    return RTCSessionDescription(sdp=sdp, type="answer")


def _sdp(typ: str, sdp: str):
    from aiortc import RTCSessionDescription
    return RTCSessionDescription(sdp=sdp, type=typ)


_cm: CallManager | None = None


def get_call_manager() -> CallManager:
    global _cm
    if _cm is None:
        _cm = CallManager()
    return _cm
