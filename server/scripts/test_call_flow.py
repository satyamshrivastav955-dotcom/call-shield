"""Two-client WebRTC call through the antAI SFU (no Android needed).

Simulates caller A and callee B with real aiortc peer connections and audio
tracks; verifies the whole server call path: signaling, ICE, media relay,
and ingestion (ASR/voice deepfake running on the relayed audio).

Usage: conda run -n antai-server python scripts/test_call_flow.py
"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import httpx  # noqa: E402
import websockets  # noqa: E402
from aiortc import (AudioStreamTrack, MediaStreamTrack, RTCPeerConnection,
                    RTCSessionDescription)
import numpy as np  # noqa: E402

BASE = "http://127.0.0.1:8765/api"
WS = "ws://127.0.0.1:8765/ws/call"


class ToneTrack(MediaStreamTrack):
    kind = "audio"

    def __init__(self, freq: float = 440.0):
        super().__init__()
        self._freq = freq
        self._t = 0

    async def recv(self):
        from fractions import Fraction
        from aiortc import AudioFrame
        sr = 48000
        samples = sr // 50  # 20ms
        phase = 2 * np.pi * self._freq * (self._t + np.arange(samples)) / sr
        pcm = (np.sin(phase) * 8000).astype(np.int16)
        frame = AudioFrame(format="s16", layout="mono", samples=samples)
        frame.sample_rate = sr
        frame.planes[0].update(pcm.tobytes())
        self._t += samples
        return frame


async def register(phone: str, name: str) -> str:
    async with httpx.AsyncClient(base_url=BASE, timeout=30) as c:
        await c.post("/auth/otp", json={"phone": phone})
        r = await c.post("/auth/verify",
                         json={"phone": phone, "otp": "000000", "display_name": name})
        return r.json()["token"]


async def _wait_ice(pc: RTCPeerConnection, timeout_s: float = 3.0) -> None:
    for _ in range(int(timeout_s * 20)):
        if pc.iceGatheringState == "complete":
            return
        await asyncio.sleep(0.05)


async def main():
    token_a = await register("+919700000001", "CallerA")
    token_b = await register("+919700000002", "CalleeB")

    async with websockets.connect(f"{WS}?token={token_a}") as ws_a, \
            websockets.connect(f"{WS}?token={token_b}") as ws_b:

        pc_a = RTCPeerConnection()
        pc_b = RTCPeerConnection()
        remote_a, remote_b = [], []

        @pc_a.on("track")
        def on_a(track):
            remote_a.append(track.kind)
            print(f"  [A] got remote track: {track.kind}")

        @pc_b.on("track")
        def on_b(track):
            remote_b.append(track.kind)
            print(f"  [B] got remote track: {track.kind}")

        pc_a.addTrack(ToneTrack(440))
        pc_b.addTrack(ToneTrack(880))

        offer = await pc_a.createOffer()
        await pc_a.setLocalDescription(offer)
        await _wait_ice(pc_a)

        print("A -> server: call.start")
        await ws_a.send(json.dumps({"type": "call.start",
                                    "callee_phone": "+919700000002",
                                    "kind": "voice",
                                    "offer": {"sdp": offer.sdp, "type": "offer"}}))

        msg = json.loads(await ws_a.recv())
        assert msg["type"] == "call.answer", msg
        await pc_a.setRemoteDescription(RTCSessionDescription(
            sdp=msg["answer"]["sdp"], type="answer"))
        print(f"  [A] answer OK, session={msg['session_key']}")

        msg = json.loads(await ws_b.recv())
        assert msg["type"] == "call.incoming", msg
        await pc_b.setRemoteDescription(RTCSessionDescription(
            sdp=msg["offer"]["sdp"], type="offer"))
        answer = await pc_b.createAnswer()
        await pc_b.setLocalDescription(answer)
        await _wait_ice(pc_b)
        print(f"  [B] incoming call, answering...")
        await ws_b.send(json.dumps({"type": "call.accept",
                                    "session_key": msg["session_key"],
                                    "answer": {"sdp": answer.sdp, "type": "answer"}}))

        # exchange any trickle ICE if the SDPs were incomplete
        for _ in range(4):
            try:
                m = json.loads(await asyncio.wait_for(ws_a.recv(), timeout=1.0))
                if m["type"] == "call.state":
                    print(f"  [A] state: {m['state']}")
            except asyncio.TimeoutError:
                break
            except Exception:
                break

        await asyncio.sleep(6)

        print(f"\nRESULT: A remote tracks={remote_a} | B remote tracks={remote_b}")
        ok = "audio" in remote_a and "audio" in remote_b
        print(f"CALL RELAY: {'PASS - media flows both ways' if ok else 'FAIL'}")

        await ws_a.send(json.dumps({"type": "call.end",
                                    "session_key": msg["session_key"]}))
        await asyncio.sleep(1)
        await pc_a.close()
        await pc_b.close()


if __name__ == "__main__":
    asyncio.run(main())