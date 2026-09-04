"""WebSocket endpoint for call signaling + realtime control channel.

Message contract (JSON frames):
  caller -> server:
    call.start     {callee_phone, kind, offer{sdp,type}}
    call.ice       {session_key, candidate}
    call.end       {session_key}
  callee -> server:
    call.accept    {session_key, answer{sdp,type}}
    call.ice       {session_key, candidate}
    call.decline   {session_key}
  server -> any:
    call.answer    {session_key, answer}      (server's answer to caller's offer)
    call.incoming  {session_key, kind, caller_phone_hash, offer}
    call.state     {session_key, state}
    verdict.update / guidance.update / freeze.request / verify.prompt / report.ready
"""
from __future__ import annotations

import asyncio
import json
import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..config import get_config
from ..gateway.auth import token_user
from ..media_sfu import get_call_manager
from ..storage import get_db
from ..ingestion.audio import AudioIngestor
from ..ingestion.frames import FrameSampler
from .auth import request_otp  # noqa  (ensure auth module importable)
from .push import get_push

log = logging.getLogger(__name__)
router = APIRouter()


class SignalingHandler:
    """Owns the per-call audio/video ingestors and drives the orchestration."""

    def __init__(self, session_key: str):
        from ..orchestration.dispatcher import get_session_runner
        self.runner = get_session_runner(session_key)
        self.audio = AudioIngestor(session_key, self._on_audio_segment)
        self.frames = FrameSampler(session_key, self._on_video_frame)
        self._started = False

    async def _on_audio_segment(self, speaker_id, audio, sample_rate, meta):
        await self.runner.on_audio_segment(speaker_id, audio, sample_rate, meta)

    async def _on_video_frame(self, speaker_id, frame, meta):
        await self.runner.on_video_frame(speaker_id, frame, meta)

    async def ingest(self, participant_id, media):
        if hasattr(media, "samples"):  # av.AudioFrame
            await self.audio.push_audio(participant_id, media)
        elif hasattr(media, "width"):  # av.VideoFrame
            await self.frames.push_frame(participant_id, media)


async def _send_ws(ws: WebSocket, payload: dict):
    await ws.send_text(json.dumps(payload, ensure_ascii=False))


@router.websocket("/ws/call")
async def ws_call(ws: WebSocket):
    await ws.accept()
    token = ws.query_params.get("token")
    user = token_user(token) if token else None
    if user is None:
        await _send_ws(ws, {"type": "error", "message": "auth required"})
        await ws.close()
        return

    from ..realtime import get_hub
    hub = get_hub()
    queue = await hub.register(user.id)
    sender_task = asyncio.create_task(_drain(queue, ws))
    handlers: dict[str, SignalingHandler] = {}

    try:
        while True:
            raw = await ws.receive_text()
            try:
                msg = json.loads(raw)
                mtype = msg.get("type")
                if mtype == "call.start":
                    await _handle_start(msg, user.id, ws, handlers)
                elif mtype == "call.ice":
                    await _handle_ice(msg, user.id)
                elif mtype == "call.accept":
                    await _handle_accept(msg, user.id, handlers)
                elif mtype == "call.decline":
                    await _handle_decline(msg, user.id)
                elif mtype == "call.end":
                    await _handle_end(msg, user.id, handlers)
                elif mtype == "call.reject":
                    await _handle_reject(msg)
                elif mtype == "ping":
                    await _send_ws(ws, {"type": "pong"})
                else:
                    await _send_ws(ws, {"type": "error", "message": f"unknown {mtype}"})
            except Exception as me:
                # a single bad message must not kill the whole call socket
                log.warning("ws_call message processing failed: %s", me)
    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("ws_call error")
    finally:
        sender_task.cancel()
        await hub.unregister(user.id, queue)
        # end any live sessions this connection was party to (covers a socket
        # just dropping - app killed, network loss - not only explicit call.end)
        cm = get_call_manager()
        for key, h in list(handlers.items()):
            try:
                await h.runner.finish()
            except Exception:
                pass
            _ws_handlers.pop(key, None)
            await cm.remove(key)


async def _drain(queue: asyncio.Queue, ws: WebSocket):
    while True:
        payload = await queue.get()
        await ws.send_text(payload)


async def _handle_start(msg: dict, caller_id: int, ws: WebSocket, handlers: dict):
    cfg = get_config()
    db = get_db()
    callee_phone = msg.get("callee_phone")
    kind = msg.get("kind", "voice")
    offer = msg.get("offer", {})
    if not callee_phone or not offer.get("sdp"):
        await _send_ws(ws, {"type": "error", "message": "invalid call.start"})
        return
    callee = db.get_user_by_phone(callee_phone)
    if callee is None:
        callee = db.create_user(callee_phone, "Contact")  # auto-provision

    call = db.create_call(caller_id, callee.id, kind)
    cm = get_call_manager()

    # create session; tracks are fanned into ingestion when they arrive
    session = await cm.create(call.id, caller_id, callee.id, kind,
                              send=None, on_ingest=None)
    handler = SignalingHandler(session.session_key)

    async def on_ingest(participant_id, media):
        await handler.ingest(participant_id, media)

    session._on_ingest = on_ingest

    # caller offer -> answer
    await session.set_caller_offer(offer["sdp"])
    n_off_cands = offer.get("sdp", "").count("a=candidate")
    log.info("call.start session=%s kind=%s offer_candidates=%d",
             session.session_key, kind, n_off_cands)
    answer = await session.answer_caller()
    await _send_ws(ws, {"type": "call.answer", "session_key": session.session_key,
                        "answer": {"sdp": answer["sdp"], "type": "answer"}})

    # callee offer
    callee_offer = await session.create_callee_offer()
    from ..realtime import get_hub
    await get_hub().send(callee.id, "call.incoming", {
        "session_key": session.session_key,
        "kind": kind,
        "caller_phone_hash": db.get_user(caller_id).phone_hash,
        "offer": {"sdp": callee_offer["sdp"], "type": "offer"},
    })

    _ws_handlers[session.session_key] = handler
    handlers[session.session_key] = handler  # this connection (the caller) owns it too
    await handler.runner.begin(kind=kind, caller_id=caller_id, callee_id=callee.id)


async def _handle_ice(msg: dict, user_id: int):
    key = msg.get("session_key")
    cand = msg.get("candidate", {})
    if not cand or not cand.get("ip"):
        # mDNS/empty/relay candidates the aiortc SFU can't use -> skip
        return
    cm = get_call_manager()
    session = cm.get(key)
    if session is None:
        log.warning("ICE dropped: no session for key=%r (user %s)", key, user_id)
        return
    try:
        if user_id == session.caller_id:
            await session.add_ice("caller", cand)
        else:
            await session.add_ice("callee", cand)
    except Exception as e:
        log.warning("ICE add failed (skipping candidate): %s", e)


async def _handle_accept(msg: dict, user_id: int, handlers: dict):
    key = msg.get("session_key")
    answer = msg.get("answer", {})
    cm = get_call_manager()
    session = cm.get(key)
    if session is None or not answer.get("sdp"):
        return
    h = _ws_handlers.get(key)
    if h:
        handlers[key] = h  # this connection (the callee) owns it too
    await session.set_callee_answer(answer["sdp"])
    from ..realtime import get_hub
    await get_hub().send(session.caller_id, "call.state",
                         {"session_key": key, "state": "connected"})
    await get_hub().send(user_id, "call.state",
                         {"session_key": key, "state": "connected"})


async def _handle_decline(msg: dict, user_id: int):
    key = msg.get("session_key")
    cm = get_call_manager()
    session = cm.get(key)
    if session is None:
        return
    from ..realtime import get_hub
    await get_hub().send(session.caller_id, "call.state",
                         {"session_key": key, "state": "declined"})
    handler = _ws_handlers.pop(key, None)
    if handler:
        await handler.runner.finish()
    await cm.remove(key)


async def _handle_reject(msg: dict):
    # callee rejects (decline handled above); keep symmetric
    pass


async def _handle_end(msg: dict, user_id: int, handlers: dict):
    key = msg.get("session_key")
    cm = get_call_manager()
    session = cm.get(key)
    if session is None:
        return
    db = get_db()
    db.end_call(session.call_id)
    handler = handlers.pop(key, None) or _ws_handlers.pop(key, None)
    if handler:
        await handler.runner.finish()
    await cm.remove(key)
    from ..realtime import get_hub
    await get_hub().send(session.caller_id, "call.state",
                         {"session_key": key, "state": "ended"})
    await get_hub().send(session.callee_id, "call.state",
                         {"session_key": key, "state": "ended"})


# module-level handler registry so callbacks can reach their runner
_ws_handlers: dict[str, SignalingHandler] = {}


@router.websocket("/ws/chat")
async def ws_chat(ws: WebSocket):
    """Chat over WebSocket.

    recv: {type:chat.send, recipient_phone, body} | {type:ping}
    This socket ALSO registers with the realtime hub and drains it, so the
    connected client RECEIVES incoming pushes on the same connection:
      chat.recv {message_id, sender_id, sender_phone, body}
      verdict.update {band, risk_score, verdict, why, action, scam_type, kind, peer_phone, ...}
      freeze.request {...}
    (Registration mirrors /ws/call and /ws/tap; it is additive and does not
    touch the call/SFU media path.)
    """
    await ws.accept()
    token = ws.query_params.get("token")
    user = token_user(token) if token else None
    if user is None:
        await ws.close()
        return
    from ..realtime import get_hub
    hub = get_hub()
    queue = await hub.register(user.id)
    sender_task = asyncio.create_task(_drain(queue, ws))
    try:
        while True:
            raw = await ws.receive_text()
            try:
                msg = json.loads(raw)
            except Exception:
                continue
            mtype = msg.get("type")
            if mtype == "ping":
                await _send_ws(ws, {"type": "pong"})
                continue
            if mtype == "chat.send":
                db = get_db()
                peer = db.get_user_by_phone(msg.get("recipient_phone", ""))
                if peer is None:
                    # auto-provision so messages to un-registered numbers still
                    # flow (parity with REST /api/chat/send)
                    peer = db.create_user(msg.get("recipient_phone", ""), "Contact")
                # INSTANT DELIVERY first; detection runs in the background and
                # pushes verdict/freeze over the hub when it has results.
                saved = db.save_message(user.id, peer.id, msg.get("body", ""))
                await _send_ws(ws, {"type": "chat.sent", "message_id": saved.id,
                                    "risk_score": 0.0, "analysis_pending": True})
                sender_phone = db.phone_of(user)
                await hub.send(peer.id, "chat.recv",
                               {"message_id": saved.id, "sender_id": user.id,
                                "sender_phone": sender_phone,
                                "body": msg.get("body", "")})
                from .rest_api import _spawn, _detect_chat
                _spawn(_detect_chat(saved.id, user.id, peer.id, msg.get("body", "")))
    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("ws_chat error")
    finally:
        sender_task.cancel()
        await hub.unregister(user.id, queue)
