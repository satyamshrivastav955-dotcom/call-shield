"""Comprehensive evaluation of voice deepfake detection models on sample audio files."""
from __future__ import annotations

import glob
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import numpy as np
import av
from antai.inference.hub import get_hub

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
    print("=" * 100, flush=True)
    print("antAI - Voice Deepfake Model Evaluation on Audio Samples", flush=True)
    print("=" * 100, flush=True)

    hub = get_hub()
    vdf = hub.get("voice_deepfake")
    asr = hub.get("asr")
    
    print("Loading voice models...", flush=True)
    vdf.ready()
    asr.ready()
    print("Models loaded successfully!\n", flush=True)

    samples = sorted(
        glob.glob(str(ROOT / "audio_samples" / "*.mp3")) +
        glob.glob(str(ROOT / "audio_samples" / "*.wav")) +
        glob.glob(str(ROOT / "audio_samples" / "*.m4a"))
    )

    print(f"{'Audio File':<35} {'Expected':<10} {'Spoof Prob':<12} {'Ensemble Label':<16} {'Model Breakdown':<25} {'ASR Transcription':<30}", flush=True)
    print("-" * 140, flush=True)

    for path in samples:
        filename = os.path.basename(path)
        expected = "AI/SPOOF" if "ai" in filename.lower() else "REAL/BONAFIDE"
        audio = load_audio(path)
        if audio is None:
            print(f"{filename:<35} {expected:<10} {'FAILED TO LOAD':<12}", flush=True)
            continue

        result = vdf.analyze(audio, 16000)
        spoof_prob = result.get("spoof_prob", 0.0)
        label = result.get("label", "unknown")
        breakdown = result.get("models", {})
        breakdown_str = " | ".join([f"{k}: {v:.3f}" for k, v in breakdown.items()])

        trans = asr.transcribe(audio, 16000)
        text = (trans.get("text") or "").strip().replace("\n", " ")[:28]
        lang = trans.get("language") or ""
        lang_text = f"[{lang}] {text}" if lang else text

        print(f"{filename:<35} {expected:<10} {spoof_prob:<12.4f} {label:<16} {breakdown_str:<25} {lang_text:<30}", flush=True)

    print("-" * 140, flush=True)
    print("Voice evaluation completed successfully!\n", flush=True)

if __name__ == "__main__":
    main()
