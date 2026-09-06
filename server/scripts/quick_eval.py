"""Small-sample detector eval — NO server needed, direct local-engine scoring.

Scores audio files through the real VoiceDeepfakeEngine (local SSL ensemble,
forced local-only so no network is touched) and reports accuracy / precision /
recall / F1 / FPR / FNR against labels.csv (or filename heuristic).

This is the workflow for testing WITHOUT converting the full 680k-clip
ASVspoof5 set: sample a few dozen clips, score, iterate.

Usage:
  python scripts/quick_eval.py --dir audio_samples            # seed set (works NOW)
  python scripts/quick_eval.py --dir audio_samples --max-n 40 # cap files
  python scripts/quick_eval.py --dir audio_samples --split test
  python scripts/quick_eval.py --file path/to/clip.wav       # single clip
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

AUDIO_EXTS = {".wav", ".flac", ".ogg", ".mp3", ".m4a", ".opus"}
HEUR_SPOOF = ("fake", "synth", "spoof", "clone", "tts", "ai_voice", "ai-voice")


def load_labels(directory: Path) -> dict[str, dict]:
    labels: dict[str, dict] = {}
    lp = directory / "labels.csv"
    if lp.exists():
        with open(lp) as f:
            for row in csv.DictReader(f):
                labels[row["filename"]] = row
    return labels


def collect(directory: Path, split: str | None, max_n: int) -> list[tuple[Path, str]]:
    labels = load_labels(directory)
    out: list[tuple[Path, str]] = []
    for sub, fallback in (("spoof", "spoof"), ("bonafide", "bonafide")):
        d = directory / sub
        if not d.exists():
            continue
        for f in sorted(d.iterdir()):
            if f.suffix.lower() not in AUDIO_EXTS or not f.is_file():
                continue
            row = labels.get(f.name)
            if row:
                if split and row.get("split") != split:
                    continue
                out.append((f, row.get("label", fallback)))
            else:
                low = f.name.lower()
                out.append((f, "spoof" if any(k in low for k in HEUR_SPOOF) else "bonafide"))
    return out[:max_n] if max_n else out


def load_audio(path: Path):
    from antai.inference.voice.audio_io import decode_audio_bytes, to_sample_rate
    try:
        audio, sr = decode_audio_bytes(path.read_bytes())
    except Exception as e:
        print(f"  SKIP {path.name}: unreadable ({e})")
        return None
    if audio is None:
        print(f"  SKIP {path.name}: undecodable")
        return None
    audio, sr = to_sample_rate(audio, sr)
    return audio, sr


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="audio_samples")
    ap.add_argument("--split", default=None)
    ap.add_argument("--max-n", type=int, default=0)
    ap.add_argument("--file", default=None, help="score one file and exit")
    ap.add_argument("--threshold", type=float, default=0.70)
    args = ap.parse_args()

    from antai.inference.voice.deepfake_voice import VoiceDeepfakeEngine
    eng = VoiceDeepfakeEngine()
    if not eng.ready():
        sys.exit(f"engine not ready: {eng.unavailable_reason()}")
    # force local-only: eval must not touch the network
    eng._use_velma = False
    eng._use_local = True
    eng.backend = "local"
    print(f"engine ready on {eng.device} (forced local-only)")

    import numpy as np
    if args.file:
        audio, sr = load_audio(Path(args.file))
        if audio is None:
            sys.exit(1)
        full = np.asarray(audio, dtype=np.float32)
        r = eng._analyze_local(full[: 8 * 16000], sr, context_audio=full)
        print(f"{Path(args.file).name}: spoof_prob={r.get('spoof_prob')} "
              f"label={r.get('label')} per_model={r.get('per_model')}")
        return

    items = collect(Path(args.dir), args.split, args.max_n)
    if not items:
        sys.exit(f"no audio files in {args.dir}")
    tp = fp = tn = fn = skipped = 0
    for path, expected in items:
        loaded = load_audio(path)
        if loaded is None:
            skipped += 1
            continue
        audio, sr = loaded
        full = np.asarray(audio, dtype=np.float32)
        clip = full[: 8 * 16000]
        # AST head needs long unpadded context (short padded windows read as
        # spoof); w2v scores the window. Pass both.
        r = eng._analyze_local(clip, sr, context_audio=full)
        p = r.get("spoof_prob")
        if p is None:
            skipped += 1
            print(f"  NO-SIGNAL {path.name} (expected {expected})")
            continue
        pred_spoof = p >= args.threshold
        exp_spoof = expected == "spoof"
        ok = pred_spoof == exp_spoof
        tp += pred_spoof and exp_spoof
        fp += pred_spoof and not exp_spoof
        tn += (not pred_spoof) and (not exp_spoof)
        fn += (not pred_spoof) and exp_spoof
        print(f"  {'OK ' if ok else 'MISS'} {path.name:44s} p={p:.3f} "
              f"[{r.get('label')}] expected={expected}")

    total = tp + fp + tn + fn
    acc = (tp + tn) / total if total else 0
    prec = tp / (tp + fp) if (tp + fp) else 0
    rec = tp / (tp + fn) if (tp + fn) else 0
    f1 = 2 * prec * rec / (prec + rec) if (prec + rec) else 0
    fpr = fp / (fp + tn) if (fp + tn) else 0
    fnr = fn / (fn + tp) if (fn + tp) else 0
    print(f"\nn={total} skipped={skipped} thr={args.threshold}")
    print(f"acc={acc:.3f} prec={prec:.3f} rec={rec:.3f} F1={f1:.3f} "
          f"FPR={fpr:.3f} FNR={fnr:.3f}")


if __name__ == "__main__":
    main()
