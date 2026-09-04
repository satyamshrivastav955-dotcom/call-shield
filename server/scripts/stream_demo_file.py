"""P0.3 — prerecorded-file streamer for the external streaming API.

Reads an audio file, splits it into fixed rolling windows, and streams them to
``/api/stream/ws`` in real time (paced to wall-clock) so you can watch the risk
score evolve exactly as it would on a live call — this is the demo that proves
the streaming pipeline end-to-end without a phone.

Usage:
    # start the server first:  python run_dev.py
    python scripts/stream_demo_file.py audio_samples/Satyam_ai_voice_0(english).mp3
    python scripts/stream_demo_file.py audio_samples/Satyam_real_voice_1.wav --window 2.0

    # compare a spoof vs a bonafide sample:
    python scripts/stream_demo_file.py <spoof.wav>
    python scripts/stream_demo_file.py <real.wav>

Acceptance (per the roadmap): a spoof sample should push several distinct risk
updates and cross into medium/high; a bonafide sample should stay low.
"""
from __future__ import annotations

import argparse
import asyncio
import io
import json
import sys
import time
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))


def _load_audio(path: Path, target_sr: int = 16000) -> tuple[np.ndarray, int]:
    """Load wav/flac/ogg (soundfile) or mp3/m4a (audioread) -> mono float32."""
    try:
        import soundfile as sf
        audio, sr = sf.read(str(path), dtype="float32")
        if getattr(audio, "ndim", 1) > 1:
            audio = audio.mean(axis=1)
        return np.ascontiguousarray(audio, dtype=np.float32), int(sr)
    except Exception:
        pass
    import audioread
    with audioread.audio_open(str(path)) as f:
        sr, chans = f.samplerate, f.channels
        buf = bytearray()
        for b in f:
            buf.extend(b)
    arr = np.frombuffer(bytes(buf), dtype="<i2").astype(np.float32) / 32768.0
    if chans > 1:
        arr = arr.reshape(-1, chans).mean(axis=1)
    return np.ascontiguousarray(arr, dtype=np.float32), int(sr)


def _fmt_signal(v, pct=True):
    if v is None:
        return "  — "
    return f"{v * 100:4.0f}%" if pct else f"{v:.2f}"


async def stream_file(path: Path, url: str, window_s: float,
                      claimed_identity_id: int | None, realtime: bool):
    import websockets

    audio, sr = _load_audio(path)
    dur = len(audio) / sr
    print(f"\nStreaming {path.name}: {dur:.1f}s @ {sr} Hz  "
          f"(window={window_s}s, realtime={realtime})")
    print(f"Connecting to {url} ...\n")

    win = int(window_s * sr)
    updates = 0
    peak = 0.0

    async with websockets.connect(url, max_size=None) as ws:
        start = {"type": "start", "sample_rate": sr, "format": "f32le",
                 "speaker_id": 1}
        if claimed_identity_id:
            start["claimed_identity_id"] = claimed_identity_id
        await ws.send(json.dumps(start))

        async def _reader():
            nonlocal updates, peak
            try:
                async for raw in ws:
                    msg = json.loads(raw)
                    mt = msg.get("type")
                    if mt in ("update", "final"):
                        updates += 1
                        risk = msg.get("risk", 0)
                        peak = max(peak, risk)
                        ac = msg.get("acoustic", {})
                        pr = msg.get("prosody", {})
                        vp = msg.get("voiceprint", {})
                        tag = "FINAL" if mt == "final" else f"{msg.get('t','')}"
                        print(f"[{tag}] risk={risk:5.1f} ({msg.get('risk_level','?'):>6}) "
                              f"voice={_fmt_signal(ac.get('voice_deepfake'))} "
                              f"urgency={_fmt_signal(pr.get('urgency'), pct=False)} "
                              f"scam={_fmt_signal(pr.get('scam_prob'))} "
                              f"vprint={_fmt_signal(vp.get('similarity'), pct=False)}")
                        rec = msg.get("recommendation")
                        if mt == "final" and rec:
                            print(f"        recommendation: {rec}")
                    elif mt == "started":
                        print(f"  session={msg.get('session_key')}\n")
                    elif mt == "error":
                        print(f"  ERROR: {msg.get('message')}")
            except Exception:
                pass

        reader = asyncio.create_task(_reader())

        # feed rolling windows, paced to real time so the demo streams live
        for i in range(0, len(audio), win):
            chunk = audio[i:i + win].astype("<f4")
            await ws.send(chunk.tobytes())
            if realtime:
                await asyncio.sleep(len(audio[i:i + win]) / sr)
            else:
                await asyncio.sleep(0.05)

        # let the final segment drain, then close cleanly
        await asyncio.sleep(1.5)
        await ws.send(json.dumps({"type": "stop"}))
        try:
            await asyncio.wait_for(reader, timeout=5.0)
        except asyncio.TimeoutError:
            reader.cancel()

    print(f"\nDone. {updates} risk updates, peak risk {peak:.1f}.")
    if peak >= 40:
        print("=> crossed the alert line (medium/high) — treated as suspicious.")
    else:
        print("=> stayed low — treated as bonafide.")


def main():
    ap = argparse.ArgumentParser(description="Stream an audio file to the antAI "
                                             "external streaming API.")
    ap.add_argument("file", help="path to an audio file (wav/flac/ogg/mp3/m4a)")
    ap.add_argument("--url", default="ws://127.0.0.1:8765/api/stream/ws")
    ap.add_argument("--window", type=float, default=2.0,
                    help="chunk size in seconds fed per step (default 2.0)")
    ap.add_argument("--claimed-identity-id", type=int, default=None,
                    help="contact id the caller claims to be (runs voiceprint check)")
    ap.add_argument("--fast", action="store_true",
                    help="do not pace to wall-clock (stream as fast as possible)")
    args = ap.parse_args()

    path = Path(args.file)
    if not path.is_absolute():
        path = (ROOT / path).resolve()
    if not path.exists():
        print(f"file not found: {path}")
        sys.exit(1)

    asyncio.run(stream_file(path, args.url, args.window,
                            args.claimed_identity_id, realtime=not args.fast))


if __name__ == "__main__":
    main()
