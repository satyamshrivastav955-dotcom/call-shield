"""Windowed spoof eval: 4s windows / 2s hop, mean-pooled per model.

Single-window AST scores are position-brittle (first-8s vs truncated-full can
flip 1.0 -> 0.0 on the same file). This script mirrors the server's rolling
voice_window_s behavior: score every window, mean-pool, then decide.
Works on seed files NOW and on dataset clips later. No server needed.

Usage: python scripts/windowed_eval.py [--dir audio_samples] [--model-dir models/voice_deepfake_upstream]
"""
from __future__ import annotations

import argparse
import glob
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

WIN_S = 4.0
HOP_S = 2.0


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="audio_samples")
    ap.add_argument("--model-dir", default="models/voice_deepfake_upstream")
    ap.add_argument("--cross-dir", default="models/voice_deepfake_cross2")
    ap.add_argument("--threshold", type=float, default=0.70)
    args = ap.parse_args()

    import numpy as np
    import torch
    import torch.nn.functional as F
    from transformers import AutoFeatureExtractor, AutoModelForAudioClassification
    from antai.inference.voice.audio_io import decode_audio_bytes, to_sample_rate

    dev = "cuda" if torch.cuda.is_available() else "cpu"
    ast_p = AutoFeatureExtractor.from_pretrained(args.model_dir)
    ast_m = AutoModelForAudioClassification.from_pretrained(args.model_dir).eval()
    if dev == "cuda":
        ast_m = ast_m.half()
    ast_m = ast_m.to(dev)
    w2v_p = AutoFeatureExtractor.from_pretrained(args.cross_dir)
    w2v_m = AutoModelForAudioClassification.from_pretrained(args.cross_dir).eval()
    if dev == "cuda":
        w2v_m = w2v_m.half()
    w2v_m = w2v_m.to(dev)

    def score(proc, model, clip: np.ndarray) -> float:
        # default processor behavior (pads to model max_length internally)
        inp = proc(clip, sampling_rate=16000, return_tensors="pt")
        if dev == "cuda":
            inp = {k: (v.half() if v.dtype == torch.float32 else v).to(dev)
                   for k, v in inp.items()}
        else:
            inp = {k: v.to(dev) for k, v in inp.items()}
        with torch.no_grad():
            logits = model(**inp).logits.float()
        return float(F.softmax(logits, dim=-1)[0][1])

    files = sorted(glob.glob(os.path.join(args.dir, "spoof", "*")) +
                   glob.glob(os.path.join(args.dir, "bonafide", "*")))
    tp = fp = tn = fn = 0
    for f in files:
        raw = Path(f).read_bytes()
        a, sr = decode_audio_bytes(raw)
        if a is None:
            print(f"  SKIP {Path(f).name}: undecodable")
            continue
        a, sr = to_sample_rate(a, sr)
        n = len(a)
        starts = list(range(0, max(1, n - int(WIN_S * sr) + 1), int(HOP_S * sr))) or [0]
        wins = [a[s:s + int(WIN_S * sr)] for s in starts]
        # AST: single score on the full unpadded context (padded windows read
        # as spoof to it); w2v: mean over short windows.
        sa = score(ast_p, ast_m, a)
        sw = float(np.mean([score(w2v_p, w2v_m, w) for w in wins]))
        p = max(sa, sw)
        exp_spoof = "spoof" in Path(f).parent.name
        pred_spoof = p >= args.threshold
        ok = pred_spoof == exp_spoof
        tp += pred_spoof and exp_spoof
        fp += pred_spoof and not exp_spoof
        tn += (not pred_spoof) and (not exp_spoof)
        fn += (not pred_spoof) and exp_spoof
        print(f"  {'OK ' if ok else 'MISS'} {Path(f).name:36s} "
              f"ast={sa:.3f} w2v={sw:.3f} max={p:.3f} exp={'spoof' if exp_spoof else 'bonafide'}",
              flush=True)
    total = tp + fp + tn + fn
    acc = (tp + tn) / total if total else 0
    fpr = fp / (fp + tn) if (fp + tn) else 0
    fnr = fn / (fn + tp) if (fn + tp) else 0
    print(f"n={total} acc={acc:.3f} FPR={fpr:.3f} FNR={fnr:.3f} (thr={args.threshold})")


if __name__ == "__main__":
    main()
