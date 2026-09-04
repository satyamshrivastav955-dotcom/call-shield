"""WebSocket endpoint for the parallel analysis tap (real-time scam detection
on an ongoing P2P video call WITHOUT touching the call itself).

Integration model (see CALL_APP_SETUP.md "next phase"):
  - The app's video call stays pure peer-to-peer, signaled by the Node.js
    server. antAI is NOT in the media path of the call.
  - While a call is running, each phone opens this control socket and a
    SECOND, send-only PeerConnection carrying a copy of its local mic/camera
    tracks. The server decodes that stream and feeds the SAME real-time
    pipeline the SFU path uses: AudioIngestor (Silero VAD -> faster-whisper
    transcript) + FrameSampler (video deepfake ensemble) -> LangGraph
    orchestration -> local Qwen LLM verdict/guidance.
  - Pushes (transcript.update / verdict.update / guidance.update /
    freeze.request / verify.prompt / report.ready) are delivered to BOTH
    participants over the RealtimeHub, rendered in the app's in-call AI
    window.

Identity: no OTP login - users are auto-provisioned from the app's runtime
username (`app:<id>`), so nothing is hardcoded and every device run gets its
own identity. Verdicts/reports still persist to the DB like any other call.

Message contract (JSON frames):
  phone -> server:
    tap.start  {peer: <peer-username>, kind: "video"|"voice", offer{sdp,type}}
    tap.ice    {candidate{...}}
    tap.stop   {}
    ping
  server -> phone:
    tap.started {session_key, peer_joined}
    tap.answer  {session_key, sdp}
    tap.ice     {candidate{...}}
    transcript.update / verdict.update / guidance.update / freeze.request /
    verify.prompt / report.ready        (via RealtimeHub)
    pong / error
"""
from __future__ import annotations

import asyncio
import json
import logging
import re

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..ingestion.audio import AudioIngestor
from ..ingestion.frames import FrameSampler
from ..media_sfu.tap_session import TapSession
from ..orchestration.dispatcher import get_session_runner
from ..realtime import get_hub as get_rt_hub
from ..storage import get_db

log = logging.getLogger(__name__)
router = APIRouter()

_USERNAME_RE = re.compile(r"^[A-Za-z0-9_-]{3,32}$")


def _provision_user(username: str):
    """Auto-provision (or fetch) the DB user backing an app username."""
    db = get_db()
    phone = f"app:{username}"
    user = db.get_user_by_phone(phone)
    if user is None:
        user = db.create_user(phone, f"app:{username}")
        log.info("tap: provisioned user for %r (id=%s)", username, user.id)
    return user


def _pair_key(user_a: int, user_b: int) -> str:
    lo, hi = sorted((user_a, user_b))
    return f"tap:pair:{lo}:{hi}"


class TapCall:
    """One analyzed call: both phones' taps + shared ingestion + runner."""

    def __init__(self, call_id: int, user_ids: list[int], kind: str,
                 names: dict[int, str] | None = None):
        self.call_id = call_id
        self.user_ids = sorted(user_ids)          # stable roles
        self.caller_id, self.callee_id = self.user_ids[0], self.user_ids[1]
        self.names = dict(names or {})
        self.kind = kind
        self.taps: dict[int, TapSession] = {}
        self._lock = asyncio.Lock()
        self._begun = False
        # unique per-call runner key (a repeat call between the same two
        # users must NOT reuse the previous call's transcript)
        self.session_key = f"tap:call:{call_id}"
        self.runner = get_session_runner(self.session_key)
        self.audio = AudioIngestor(self.session_key, self._on_audio_segment)
        self.frames = FrameSampler(self.session_key, self._on_video_frame)

    async def _on_audio_segment(self, speaker_id, audio, sample_rate, meta):
        await self.runner.on_audio_segment(speaker_id, audio, sample_rate, meta)

    async def _on_video_frame(self, speaker_id, frame, meta):
        await self.runner.on_video_frame(speaker_id, frame, meta)

    async def ingest(self, participant_id: int, media):
        if hasattr(media, "samples"):    # av.AudioFrame
            await self.audio.push_audio(participant_id, media)
        elif hasattr(media, "width"):    # av.VideoFrame
            await self.frames.push_frame(participant_id, media)

    async def attach(self, user_id: int, offer_sdp: str) -> str:
        """Replace this user's tap with a fresh one; return the answer SDP."""
        async with self._lock:
            old = self.taps.get(user_id)
            tap = TapSession(user_id, self.ingest)
            self.taps[user_id] = tap
        if old is not None:
            await old.close()
        sdp = await tap.set_offer_and_answer(offer_sdp)
        if not self._begun:
            self._begun = True
            await self.runner.begin(kind=self.kind, caller_id=self.caller_id,
                                    callee_id=self.callee_id,
                                    participants=list(self.user_ids),
                                    participant_names=self.names)
        return sdp

    async def add_ice(self, user_id: int, candidate: dict):
        tap = self.taps.get(user_id)
        if tap is not None:
            await tap.add_ice(candidate)

    async def detach(self, user_id: int) -> bool:
        """Remove one phone's tap. Returns True when the call is now empty."""
        async with self._lock:
            tap = self.taps.pop(user_id, None)
        if tap is not None:
            await tap.close()
        return not self.taps


# registry of live pair calls (deterministic key lets both phones find the
# same call no matter who connected first)
_tap_calls: dict[str, TapCall] = {}


async def _get_or_create_tap_call(user_id: int, peer_id: int, kind: str,
                                  names: dict[int, str] | None = None) -> TapCall:
    key = _pair_key(user_id, peer_id)
    call = _tap_calls.get(key)
    if call is None:
        db = get_db()
        caller, callee = sorted((user_id, peer_id))
        db_call = db.create_call(caller, callee, kind)
        call = TapCall(db_call.id, [user_id, peer_id], kind, names=names)
        _tap_calls[key] = call
        log.info("tap: new analyzed call pair=%s call_id=%s kind=%s",
                 key, db_call.id, kind)
    elif names:
        call.names.update(names)
    return call


async def _finish_tap_call(call: TapCall):
    """Both taps gone: end the DB call, run the LLM post-call report."""
    key = _pair_key(call.caller_id, call.callee_id)
    _tap_calls.pop(key, None)
    try:
        await call.runner.finish()
    except Exception:
        log.exception("tap: runner.finish failed for %s", call.session_key)
    try:
        get_db().end_call(call.call_id, risk_peak=call.runner.risk_peak)
    except Exception:
        log.exception("tap: end_call failed for %s", call.call_id)


async def _send_ws(ws: WebSocket, payload: dict):
    await ws.send_text(json.dumps(payload, ensure_ascii=False))


@router.websocket("/ws/tap")
async def ws_tap(ws: WebSocket):
    await ws.accept()
    username = (ws.query_params.get("user") or "").strip()
    if not _USERNAME_RE.match(username):
        await _send_ws(ws, {"type": "error", "message": "invalid ?user="})
        await ws.close()
        return

    user = _provision_user(username)
    hub = get_rt_hub()
    queue = await hub.register(user.id)
    sender_task = asyncio.create_task(_drain(queue, ws))
    # per-connection state: the call this socket's tap is attached to
    active: dict = {"call": None}

    async def _detach_and_maybe_finish():
        call: TapCall | None = active["call"]
        active["call"] = None
        if call is None:
            return
        empty = await call.detach(user.id)
        if empty:
            await _finish_tap_call(call)

    try:
        while True:
            raw = await ws.receive_text()
            try:
                msg = json.loads(raw)
                mtype = msg.get("type")
                if mtype == "tap.start":
                    await _handle_tap_start(msg, user, ws, active)
                elif mtype == "tap.ice":
                    await _handle_tap_ice(msg, user, active)
                elif mtype == "tap.stop":
                    await _detach_and_maybe_finish()
                elif mtype == "ping":
                    await _send_ws(ws, {"type": "pong"})
                else:
                    await _send_ws(ws, {"type": "error",
                                        "message": f"unknown {mtype}"})
            except Exception as me:
                # one bad frame must not kill the tap socket mid-call
                log.warning("ws_tap message processing failed: %s", me)
    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("ws_tap error")
    finally:
        sender_task.cancel()
        await hub.unregister(user.id, queue)
        # socket dropped (app killed / network loss) == tap stopped
        try:
            await _detach_and_maybe_finish()
        except Exception:
            log.exception("ws_tap cleanup failed")


async def _drain(queue: asyncio.Queue, ws: WebSocket):
    while True:
        payload = await queue.get()
        try:
            await ws.send_text(payload)
        except Exception:
            # socket dropped mid-push: stop draining, the caller will unregister
            break


def _username_of(user) -> str:
    """Recover the app username from a provisioned user's phone ('app:<id>')."""
    phone = get_db().phone_of(user) or ""
    return phone.removeprefix("app:")


async def _handle_tap_start(msg: dict, user, ws: WebSocket, active: dict):
    peer = (msg.get("peer") or "").strip()
    kind = msg.get("kind", "video")
    offer = msg.get("offer", {})
    if not _USERNAME_RE.match(peer) or not offer.get("sdp"):
        await _send_ws(ws, {"type": "error", "message": "invalid tap.start"})
        return
    if kind not in ("video", "voice"):
        kind = "video"

    peer_user = _provision_user(peer)
    if peer_user.id == user.id:
        await _send_ws(ws, {"type": "error", "message": "cannot tap self"})
        return

    call = await _get_or_create_tap_call(
        user.id, peer_user.id, kind,
        names={user.id: _username_of(user), peer_user.id: peer})
    sdp = await call.attach(user.id, offer["sdp"])
    active["call"] = call
    log.info("tap: user=%s attached to call %s (peer=%s)",
             user.id, call.session_key, peer)
    await _send_ws(ws, {"type": "tap.started", "session_key": call.session_key,
                        "kind": kind})
    await _send_ws(ws, {"type": "tap.answer", "session_key": call.session_key,
                        "sdp": sdp})


async def _handle_tap_ice(msg: dict, user, active: dict):
    cand = msg.get("candidate") or {}
    call: TapCall | None = active["call"]
    if call is None or not cand.get("ip"):
        # no session yet, or mDNS/empty candidate aiortc can't use -> skip
        return
    try:
        await call.add_ice(user.id, cand)
    except Exception as e:
        log.warning("tap ICE add failed (skipping candidate): %s", e)
