"""External streaming inference API (SIH #13 — generic near-real-time entry).

Unlike ``/ws/tap`` (which requires a WebRTC PeerConnection from the bundled
Android app), this endpoint accepts raw PCM audio chunks over a plain WebSocket
and returns a live 0-100 risk stream with the three explainable signal families
(acoustic / prosody / voiceprint) and a deterministic recommendation. It is the
integration surface a bank/call-center backend — or the demo file-streamer —
uses to stream a call and watch risk evolve.

It drives a real :class:`StreamingSession` (same VAD -> SessionRunner ->
LangGraph pipeline as a live call), so every number is genuinely computed. No
scores are fabricated; a model without weights streams ``null``.

WebSocket contract  (``/api/stream/ws``)
----------------------------------------
client -> server (JSON control frames OR binary audio):
    {"type":"start", "sample_rate":16000, "format":"f32le"|"s16le",
     "speaker_id":1, "claimed_identity_id":<int|null>}
    <binary frame>                 raw PCM samples in the declared format
    {"type":"pcm", "samples":[...]} JSON PCM fallback (float32 in [-1,1])
    {"type":"stop"}
server -> client (JSON):
    {"type":"started", "session_key":"..."}
    {"type":"update", ...normalized_result...}      (after each analyzed segment
                                                      + on a periodic heartbeat)
    {"type":"final",   ...normalized_result...}
    {"type":"error",   "message":"..."}

REST contract  (``POST /api/stream/analyze``)
---------------------------------------------
Multipart upload of a whole audio file -> single final normalized_result. Handy
for quick one-shot scoring / eval; the WS path is the real streaming demo.

Security note: this endpoint is UNAUTHENTICATED by design for the local demo /
dashboard. Do NOT expose it on a public interface without adding auth (bearer
token like the REST API, or network isolation) — it runs the full model
pipeline and accepts arbitrary audio.
"""
from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid

import numpy as np
from fastapi import APIRouter, File, Form, UploadFile, WebSocket, WebSocketDisconnect

from ..orchestration.streaming import StreamingSession, normalized_result

log = logging.getLogger(__name__)
router = APIRouter()


def _decode_binary(data: bytes, fmt: str) -> np.ndarray:
    """Raw PCM bytes -> mono float32 in [-1, 1]."""
    if fmt == "s16le":
        arr = np.frombuffer(data, dtype="<i2").astype(np.float32) / 32768.0
    elif fmt == "f32le":
        arr = np.frombuffer(data, dtype="<f4").astype(np.float32)
    else:
        raise ValueError(f"unsupported format {fmt!r}")
    return np.ascontiguousarray(arr)


@router.websocket("/stream/ws")
async def stream_ws(ws: WebSocket):
    await ws.accept()
    session: StreamingSession | None = None
    fmt = "f32le"
    sample_rate = 16000
    hb_task: asyncio.Task | None = None

    async def _send(obj: dict):
        try:
            await ws.send_text(json.dumps(obj, ensure_ascii=False))
        except Exception:
            pass

    async def _heartbeat():
        # Even during buffered silence the pipeline ticks (periodic loop). Stream
        # a snapshot on an interval so a dashboard shows the engines live and the
        # risk decaying/holding, not just on segment boundaries.
        try:
            while True:
                await asyncio.sleep(1.0)
                if session is not None:
                    await _send({"type": "update", **session.snapshot()})
        except asyncio.CancelledError:
            pass
        except Exception:
            log.debug("stream heartbeat stopped", exc_info=True)

    try:
        while True:
            msg = await ws.receive()
            if msg.get("type") == "websocket.disconnect":
                break

            # binary audio frame
            if msg.get("bytes") is not None:
                if session is None:
                    await _send({"type": "error",
                                 "message": "send a 'start' frame first"})
                    continue
                try:
                    pcm = _decode_binary(msg["bytes"], fmt)
                    await session.push_pcm(pcm, sample_rate)
                except Exception as e:
                    await _send({"type": "error", "message": f"bad audio: {e}"})
                continue

            # text control frame
            text = msg.get("text")
            if not text:
                continue
            try:
                ctrl = json.loads(text)
            except Exception:
                await _send({"type": "error", "message": "invalid JSON"})
                continue

            mtype = ctrl.get("type")
            if mtype == "start":
                sample_rate = int(ctrl.get("sample_rate", 16000))
                fmt = ctrl.get("format", "f32le")
                if fmt not in ("f32le", "s16le"):
                    await _send({"type": "error",
                                 "message": f"unsupported format {fmt!r}"})
                    fmt = "f32le"
                speaker_id = int(ctrl.get("speaker_id", 1))
                claimed = ctrl.get("claimed_identity_id")
                claimed = int(claimed) if claimed not in (None, "") else None
                session_key = f"stream:{uuid.uuid4().hex[:12]}"

                # P1.2 / P1.3: scenario + caller context forwarded from the
                # dashboard / call-center backend into the detection pipeline.
                scenario = ctrl.get("scenario") or None
                context_raw = ctrl.get("context") or {}
                if isinstance(context_raw, str):
                    try:
                        context_raw = json.loads(context_raw)
                    except Exception:
                        context_raw = {}

                async def _on_update(snap: dict):
                    await _send({"type": "update", **snap})

                # normalized_result is sync; wrap the callback to schedule a send
                loop = asyncio.get_running_loop()

                def _sync_update(snap: dict):
                    loop.call_soon_threadsafe(
                        lambda: asyncio.ensure_future(_on_update(snap)))

                session = StreamingSession(session_key, speaker_id=speaker_id,
                                           claimed_identity_id=claimed,
                                           on_update=_sync_update,
                                           scenario=scenario,
                                           context=context_raw)
                await session.begin()
                hb_task = asyncio.create_task(_heartbeat())
                await _send({"type": "started", "session_key": session_key})

            elif mtype == "pcm":
                if session is None:
                    await _send({"type": "error",
                                 "message": "send a 'start' frame first"})
                    continue
                samples = np.asarray(ctrl.get("samples", []), dtype=np.float32)
                await session.push_pcm(samples, sample_rate)

            elif mtype == "stop":
                break

            elif mtype == "ping":
                await _send({"type": "pong", "t": time.time()})

            else:
                await _send({"type": "error", "message": f"unknown type {mtype!r}"})

    except WebSocketDisconnect:
        pass
    except Exception:
        log.exception("stream_ws error")
    finally:
        if hb_task is not None:
            hb_task.cancel()
        if session is not None:
            try:
                final = await session.finish()
                await _send({"type": "final", **final})
            except Exception:
                log.exception("stream finalize failed")
        try:
            await ws.close()
        except Exception:
            pass


@router.post("/stream/analyze")
async def stream_analyze(file: UploadFile = File(...),
                         sample_rate: int = Form(0),
                         claimed_identity_id: int = Form(0),
                         scenario: str = Form(""),
                         context_json: str = Form("{}")):
    """One-shot: upload an audio file, stream it through the real pipeline in
    rolling chunks, and return the final normalized result.

    Decodes via soundfile (wav/flac/ogg) with an audioread/ffmpeg fallback for
    mp3/m4a, so the same labeled ``audio_samples/`` used by the eval harness work
    here too.
    """
    raw = await file.read()
    audio, sr = _decode_upload(raw, file.filename or "upload")
    if audio is None:
        return {"error": "could not decode audio; provide wav/flac or install "
                          "audioread+ffmpeg for mp3/m4a"}
    if sample_rate:
        sr = sample_rate

    claimed = claimed_identity_id or None
    # P1.2 / P1.3: optional scenario + caller context from the form upload
    scenario_name = scenario.strip() or None
    try:
        context_dict = json.loads(context_json) if context_json.strip() else {}
    except Exception:
        context_dict = {}
    session = StreamingSession(f"stream:file:{uuid.uuid4().hex[:8]}",
                               speaker_id=1, claimed_identity_id=claimed,
                               scenario=scenario_name, context=context_dict)
    await session.begin()

    # feed in ~1s chunks so the rolling window + periodic tick behave as live
    chunk = max(1, int(sr))
    for i in range(0, len(audio), chunk):
        await session.push_pcm(audio[i:i + chunk], sr)
        await asyncio.sleep(0)   # yield to the eval drain between chunks
    # give the coalesced graph runs a moment to drain before snapshotting
    await asyncio.sleep(0.2)
    final = await session.finish()
    return final



def _decode_upload(raw: bytes, filename: str):
    """Bytes -> (mono float32, sr). Returns (None, 0) if it cannot decode."""
    import io
    try:
        import soundfile as sf
        audio, sr = sf.read(io.BytesIO(raw), dtype="float32")
        if getattr(audio, "ndim", 1) > 1:
            audio = audio.mean(axis=1)
        return np.ascontiguousarray(audio, dtype=np.float32), int(sr)
    except Exception:
        pass
    # mp3 / m4a fallback
    try:
        import tempfile
        import os
        import audioread
        suffix = os.path.splitext(filename)[1] or ".bin"
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tf:
            tf.write(raw)
            tmp = tf.name
        try:
            with audioread.audio_open(tmp) as f:
                sr = f.samplerate
                chans = f.channels
                buf = bytearray()
                for b in f:
                    buf.extend(b)
            arr = np.frombuffer(bytes(buf), dtype="<i2").astype(np.float32) / 32768.0
            if chans > 1:
                arr = arr.reshape(-1, chans).mean(axis=1)
            return np.ascontiguousarray(arr, dtype=np.float32), int(sr)
        finally:
            try:
                os.unlink(tmp)
            except Exception:
                pass
    except Exception:
        log.exception("audio decode failed for %s", filename)
        return None, 0
