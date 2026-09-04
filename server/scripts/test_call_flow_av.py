"""Two-client WebRTC VIDEO call through the antAI SFU.

Verifies actual media flow (not just track events): counts received audio
frames and video frames on BOTH sides, and checks audio frame continuity
(pts gaps = dropped content = butchered audio).

Usage: .venv python scripts/test_call_flow_av.py [voice|video]
"""
from __future__ import annotations

import asyncio
import json
import sys
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import httpx  # noqa: E402
import websockets  # noqa: E402
from aiortc import MediaStreamTrack, RTCPeerConnection, RTCSessionDescription
from av import AudioFrame, VideoFrame  # noqa: E402
import numpy as np  # noqa: E402

BASE = "http://127.0.0.1:8765/api"
WS = "ws://127.0.0.1:8765/ws/call"
MODE = sys.argv[1] if len(sys.argv) > 1 else "video"


class ToneTrack(MediaStreamTrack):
    kind = "audio"

    def __init__(self, freq: float = 440.0):
        super().__init__()
        self._freq = freq
        self._t = 0

    async def recv(self):
        sr = 48000
        samples = sr // 50
        phase = 2 * np.pi * self._freq * (self._t + np.arange(samples)) / sr
        pcm = (np.sin(phase) * 8000).astype(np.int16)
        frame = AudioFrame(format="s16", layout="mono", samples=samples)
        frame.sample_rate = sr
        frame.pts = self._t
        frame.time_base = Fraction(1, sr)
        frame.planes[0].update(pcm.tobytes())
        self._t += samples
        await asyncio.sleep(0.02)
        return frame


class ColorTrack(MediaStreamTrack):
    kind = "video"

    def __init__(self, color: int):
        super().__init__()
        self._color = color
        self._t = 0

    async def recv(self):
        arr = np.zeros((480, 640, 3), dtype=np.uint8)
        arr[:, :, self._color] = 255
        frame = VideoFrame.from_ndarray(arr, format="bgr24")
        frame.pts = self._t
        frame.time_base = Fraction(1, 30)
        self._t += 1
        await asyncio.sleep(1 / 30)
        return frame


async def register(phone: str, name: str) -> str:
    async with httpx.AsyncClient(base_url=BASE, timeout=30) as c:
        await c.post("/auth/otp", json={"phone": phone})
        r = await c.post("/auth/verify",
                          json={"phone": phone, "otp": "000000", "display_name": name})
        return r.json()["token"]


async def _wait_ice(pc: RTCPeerConnection, timeout_s: float = 5.0) -> None:
    for _ in range(int(timeout_s * 20)):
        if pc.iceGatheringState == "complete":
            return
        await asyncio.sleep(0.05)


class Collector:
    """Counts received frames + measures audio pts continuity."""

    def __init__(self, tag: str):
        self.tag = tag
        self.audio = 0
        self.video = 0
        self.audio_gaps = 0
        self._last_pts = None
        self._tasks = []

    def on_track(self, track):
        print(f"  [{self.tag}] remote track: {track.kind}")
        self._tasks.append(asyncio.ensure_future(self._run(track)))

    async def _run(self, track):
        try:
            while True:
                frame = await track.recv()
                if track.kind == "audio":
                    self.audio += 1
                    pts = frame.pts * frame.time_base * 48000 if frame.time_base else frame.pts
                    if self._last_pts is not None:
                        delta = float(pts - self._last_pts)
                        if delta != 960:  # 20ms @48k
                            self.audio_gaps += 1
                    self._last_pts = pts
                else:
                    self.video += 1
        except Exception as e:
            print(f"  [{self.tag}] {track.kind} stream ended: {e!r}")


async def main():
    token_a = await register("+919700000011", "CallerA")
    token_b = await register("+919700000012", "CalleeB")
    print(f"mode={MODE}")

    async with websockets.connect(f"{WS}?token={token_a}", max_size=2**22) as ws_a, \
            websockets.connect(f"{WS}?token={token_b}", max_size=2**22) as ws_b:

        pc_a = RTCPeerConnection()
        pc_b = RTCPeerConnection()
        col_a, col_b = Collector("A"), Collector("B")
        pc_a.on("track", col_a.on_track)
        pc_b.on("track", col_b.on_track)

        pc_a.addTrack(ToneTrack(440))
        pc_b.addTrack(ToneTrack(880))
        if MODE == "video":
            pc_a.addTrack(ColorTrack(2))
            pc_b.addTrack(ColorTrack(0))

        offer = await pc_a.createOffer()
        await pc_a.setLocalDescription(offer)
        await _wait_ice(pc_a)
        await ws_a.send(json.dumps({"type": "call.start",
                                    "callee_phone": "+919700000012",
                                    "kind": MODE,
                                    "offer": {"sdp": pc_a.localDescription.sdp,
                                              "type": "offer"}}))

        msg = json.loads(await ws_a.recv())
        assert msg["type"] == "call.answer", msg
        await pc_a.setRemoteDescription(RTCSessionDescription(
            sdp=msg["answer"]["sdp"], type="answer"))
        print(f"  [A] answered, session={msg['session_key']}")

        msg = json.loads(await ws_b.recv())
        assert msg["type"] == "call.incoming", msg
        await pc_b.setRemoteDescription(RTCSessionDescription(
            sdp=msg["offer"]["sdp"], type="offer"))
        answer = await pc_b.createAnswer()
        await pc_b.setLocalDescription(answer)
        await _wait_ice(pc_b)
        await ws_b.send(json.dumps({"type": "call.accept",
                                    "session_key": msg["session_key"],
                                    "answer": {"sdp": pc_b.localDescription.sdp,
                                               "type": "answer"}}))
        print("  [B] accepted")

        # drain control messages for a while
        for ws, tag in ((ws_a, "A"), (ws_b, "B")):
            try:
                while True:
                    m = json.loads(await asyncio.wait_for(ws.recv(), timeout=1.0))
                    if m.get("type") == "call.state":
                        print(f"  [{tag}] state: {m.get('state')}")
            except asyncio.TimeoutError:
                pass

        await asyncio.sleep(12)

        print(f"\nRESULT ({MODE}):")
        print(f"  A received: audio={col_a.audio} frames, video={col_a.video} frames, "
              f"audio gaps={col_a.audio_gaps}")
        print(f"  B received: audio={col_b.audio} frames, video={col_b.video} frames, "
              f"audio gaps={col_b.audio_gaps}")
        ok_audio = col_a.audio > 100 and col_b.audio > 100
        ok = ok_audio and (MODE == "voice" or (col_a.video > 20 and col_b.video > 20))
        print(f"  AUDIO RELAY: {'PASS' if ok_audio else 'FAIL'} "
              f"(continuity: {'OK' if col_a.audio_gaps == 0 and col_b.audio_gaps == 0 else 'GAPS/DROPS'})")
        if MODE == "video":
            print(f"  VIDEO RELAY: {'PASS' if (col_a.video > 20 and col_b.video > 20) else 'FAIL'}")
        print(f"  OVERALL: {'PASS' if ok else 'FAIL'}")

        await ws_a.send(json.dumps({"type": "call.end",
                                    "session_key": msg["session_key"]}))
        await asyncio.sleep(1)
        await pc_a.close()
        await pc_b.close()


if __name__ == "__main__":
    asyncio.run(main())
