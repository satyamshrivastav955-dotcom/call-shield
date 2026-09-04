"""Full video-call demo: frames+audio ingest -> ensemble -> local LLM guidance.

Pushes the flagged deepfake_zuck clip through the real SessionRunner path
(on_video_frame + on_audio_segment), then shows what the local Qwen LLM
tells the user: live verdict card, live guidance, post-call report.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import av  # noqa: E402
import numpy as np  # noqa: E402


def load_video(path: str, max_frames: int = 120):
    c = av.open(path)
    vs = c.streams.video[0]
    vs.thread_type = "AUTO"
    frames, last = [], -1
    for frame in c.decode(vs):
        ts = float(frame.pts * vs.time_base)
        if last < 0 or ts - last >= 1.0 / 6:
            last = ts
            frames.append(frame.to_ndarray(format="bgr24"))
            if len(frames) >= max_frames:
                break
    c.close()
    audio = None
    c2 = av.open(path)
    astr = c2.streams.audio[0] if c2.streams.audio else None
    if astr:
        res = av.AudioResampler(format="fltp", layout="mono", rate=16000)
        chunks = []
        for af in c2.decode(astr):
            for o in res.resample(af):
                arr = o.to_ndarray()
                if arr.ndim == 2:
                    arr = arr.mean(axis=0)
                chunks.append(np.asarray(arr, dtype=np.float32))
        audio = np.concatenate(chunks) if chunks else None
    c2.close()
    return frames, audio


def line(t):
    print(f"\n{'=' * 76}\n{t}\n{'=' * 76}")


async def main():
    frames, audio = load_video(str(ROOT / "video_samples" / "deepfake_zuck.mp4"))
    line("VIDEO CALL INGESTION (deepfake_zuck.mp4)")
    print(f"frames sampled: {len(frames)} @6fps | audio: {len(audio) / 16000:.1f}s")

    from antai.orchestration.dispatcher import get_session_runner
    runner = get_session_runner("demo-video-call-1")
    await runner.begin(kind="video", caller_id=1, callee_id=2)

    line("AUDIO TAP -> ASR + VOICE DEEPFAKE")
    await runner.on_audio_segment(1, audio, 16000, {})
    if runner.transcript:
        print(f"ASR: {runner.transcript[-1]['text']}")

    line("VIDEO TAP -> FRAME BUFFER -> ENSEMBLE (CF-ViT + DiCoME + temporal)")
    for f in frames:
        await runner.on_video_frame(1, f, {})

    print(f"signals: { {k: round(v, 3) if isinstance(v, float) else v for k, v in runner.signals.items() if v} }")
    print(f"risk: {runner.risk}/100  band: {runner.band}")

    line("LOCAL LLM VERDICT CARD (pushed live to the user)")
    v = runner.last_verdict or {}
    print(f"  verdict: {v.get('verdict')}")
    print(f"  why    : {v.get('why')}")
    print(f"  action : {v.get('action')}")

    line("LOCAL LLM LIVE GUIDANCE (pushed to the user mid-call)")
    from antai.inference.llm.reasoner import generate_live_guidance, generate_report
    guidance = generate_live_guidance(runner.risk, runner.signals, runner.transcript)
    if not guidance:
        print("  (LLM judged the verdict card already covers it; template fallback:)")
        guidance = ("Be careful - this call shows signs of a deepfake video. "
                    "Do not share money, codes, or personal details. "
                    "Verify with a trusted family member before doing anything.")
    print(f"  {guidance}")

    line("LOCAL LLM POST-CALL REPORT")
    report = generate_report("video", runner.transcript, runner.signals,
                             runner.events, runner.signals.get("scam_type"), runner.risk)
    print(f"  [{report['title']}]")
    print(report["body"])

    line("DONE")


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())