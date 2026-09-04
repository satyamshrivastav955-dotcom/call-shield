"""Comprehensive evaluation of video deepfake detection models on sample video files."""
from __future__ import annotations

import glob
import os
import sys
import traceback
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import numpy as np
import av
from antai.inference.hub import get_hub

def load_frames(path: str, fps: int = 6, max_frames: int = 60) -> list[np.ndarray]:
    c = av.open(path)
    vs = c.streams.video[0]
    vs.thread_type = "AUTO"
    frames, last = [], -1.0
    for frame in c.decode(vs):
        ts = float(frame.pts * vs.time_base) if frame.pts is not None else 0.0
        if last < 0 or ts - last >= 1.0 / fps:
            last = ts
            frames.append(frame.to_ndarray(format="bgr24"))
            if len(frames) >= max_frames:
                break
    c.close()
    return frames

def load_audio(path: str) -> np.ndarray | None:
    c = av.open(path)
    if not c.streams.audio:
        c.close()
        return None
    astr = c.streams.audio[0]
    res = av.AudioResampler(format="fltp", layout="mono", rate=16000)
    chunks = []
    for f in c.decode(astr):
        for o in res.resample(f):
            arr = o.to_ndarray()
            if arr.ndim == 2:
                arr = arr.mean(axis=0)
            chunks.append(np.asarray(arr, dtype=np.float32))
    c.close()
    return np.concatenate(chunks) if chunks else None

def main():
    print("=" * 110, flush=True)
    print("antAI - Video Deepfake Model Evaluation on Video Samples", flush=True)
    print("=" * 110, flush=True)

    hub = get_hub()
    vdf = hub.get("video_deepfake")
    cf_vit = hub.get("community_vit")
    dicome = hub.get("dicome")
    face = hub.get("face")
    voice = hub.get("voice_deepfake")
    lipsync = hub.get("lipsync")

    print("Loading video & audio models...", flush=True)
    for m in (vdf, cf_vit, dicome, face, voice, lipsync):
        if m:
            m.ready()
    print("Models loaded successfully!\n", flush=True)

    videos = sorted(
        glob.glob(str(ROOT / "video_samples" / "*.mp4")) +
        glob.glob(str(ROOT / "video_samples" / "*.avi")) +
        glob.glob(str(ROOT / "video_samples" / "*.mov"))
    )

    print(f"{'Video File':<28} {'Expected':<10} {'CF-ViT':<10} {'DiCoME':<10} {'Video Ensemble':<16} {'Voice Spoof':<14} {'Final Verdict':<14}", flush=True)
    print("-" * 110, flush=True)

    for path in videos:
        name = os.path.basename(path)
        expected = "DEEPFAKE" if "deepfake" in name.lower() or "fake" in name.lower() else "ORIGINAL"
        try:
            frames = load_frames(path)
            audio = load_audio(path)

            # CF-ViT
            cf_probs = []
            if cf_vit and cf_vit.ready() and frames:
                for fr in frames[-8:]:
                    p = cf_vit.analyze(fr).get("fake_prob")
                    if p is not None:
                        cf_probs.append(p)
            cf_score = float(np.mean(cf_probs)) if cf_probs else None

            # DiCoME
            d_probs = []
            if dicome and dicome.ready() and frames:
                for fr in frames[-8:]:
                    p = dicome.analyze(fr).get("fake_prob")
                    if p is not None:
                        d_probs.append(p)
            dicome_score = float(np.mean(d_probs)) if d_probs else None

            # Video Ensemble
            v_res = vdf.score_frames(frames) if (vdf and frames) else {}
            v_flag = v_res.get("flagged", False)
            v_prob = v_res.get("fake_prob", 0.0)

            # Voice Deepfake
            voice_res = voice.analyze(audio, 16000) if (voice and audio is not None) else {}
            voice_spoof = voice_res.get("spoof_prob")
            voice_label = voice_res.get("label", "no audio")

            # Combined Verdict
            is_deepfake = bool(v_flag) or (voice_spoof is not None and voice_spoof > 0.5)
            verdict = "FLAGGED FAKE" if is_deepfake else "AUTHENTIC"

            cf_str = f"{cf_score:.4f}" if cf_score is not None else "N/A"
            dicome_str = f"{dicome_score:.4f}" if dicome_score is not None else "N/A"
            v_str = f"{'FLAG' if v_flag else 'OK'} (p={v_prob:.2f})" if v_prob is not None else "N/A"
            voice_str = f"{voice_spoof:.4f} ({voice_label})" if voice_spoof is not None else "No audio"

            print(f"{name:<28} {expected:<10} {cf_str:<10} {dicome_str:<10} {v_str:<16} {voice_str:<14} {verdict:<14}", flush=True)

        except Exception as e:
            print(f"{name:<28} {expected:<10} ERROR: {e}", flush=True)
            traceback.print_exc()

    print("-" * 110, flush=True)
    print("Video evaluation completed successfully!\n", flush=True)

if __name__ == "__main__":
    main()
