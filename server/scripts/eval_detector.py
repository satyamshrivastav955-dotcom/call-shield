"""Detection evaluation harness (SIH Phase-2 Step 3).

Reads a labeled audio sample directory and scores each file through the real
AI pipeline, then reports accuracy, precision, recall, F1, FPR, and FNR.

Directory layout expected:
    audio_samples/
        spoof/   <- files known to be synthetic/AI-generated
        bonafide/ <- files known to be real human speech

Usage:
    # start the server first: python run_dev.py
    python scripts/eval_detector.py
    python scripts/eval_detector.py --dir audio_samples --server http://localhost:8765
    python scripts/eval_detector.py --threshold 40   # change decision boundary
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

AUDIO_EXTS = {".wav", ".flac", ".ogg", ".mp3", ".m4a", ".opus"}


async def score_file(path: Path, server: str) -> dict | None:
    """POST one file to /api/stream/analyze and return the normalized result."""
    import aiohttp
    url = f"{server.rstrip('/')}/api/stream/analyze"
    try:
        async with aiohttp.ClientSession() as session:
            data = aiohttp.FormData()
            data.add_field("file", open(path, "rb"),
                           filename=path.name,
                           content_type="application/octet-stream")
            async with session.post(url, data=data, timeout=aiohttp.ClientTimeout(total=120)) as r:
                if r.status != 200:
                    print(f"  ERROR {r.status} for {path.name}")
                    return None
                return await r.json()
    except Exception as e:
        print(f"  FAILED {path.name}: {e}")
        return None


def _collect_files(directory: Path) -> tuple[list[Path], list[Path]]:
    """Return (spoof_files, bonafide_files) from the directory layout."""
    spoof_dir = directory / "spoof"
    bonafide_dir = directory / "bonafide"
    if not spoof_dir.exists() and not bonafide_dir.exists():
        # flat directory: look for name hints
        all_files = [f for f in directory.iterdir()
                     if f.suffix.lower() in AUDIO_EXTS]
        spoof = [f for f in all_files if any(
            k in f.name.lower() for k in ("fake", "synth", "ai_voice", "spoof", "clone", "tts"))]
        bonafide = [f for f in all_files if f not in spoof and any(
            k in f.name.lower() for k in ("real", "genuine", "bonafide", "human"))]
        if not spoof and not bonafide:
            # Last resort: treat all as unknown (will report N/A for metrics)
            print("WARNING: no spoof/bonafide sub-dirs found and name-hints empty.")
            print("Create audio_samples/spoof/ and audio_samples/bonafide/ directories.")
            return all_files, []
        return spoof, bonafide
    spoof = [f for f in spoof_dir.iterdir() if f.suffix.lower() in AUDIO_EXTS] \
        if spoof_dir.exists() else []
    bonafide = [f for f in bonafide_dir.iterdir() if f.suffix.lower() in AUDIO_EXTS] \
        if bonafide_dir.exists() else []
    return spoof, bonafide


async def main_async(args):
    sample_dir = Path(args.dir)
    if not sample_dir.is_absolute():
        sample_dir = ROOT / sample_dir
    spoof_files, bonafide_files = _collect_files(sample_dir)
    print(f"Found {len(spoof_files)} spoof + {len(bonafide_files)} bonafide samples")
    if not spoof_files and not bonafide_files:
        print("No audio files found. Exiting.")
        return

    threshold = args.threshold
    tp = fp = tn = fn = 0
    results = []

    # Score spoof files (ground truth = positive)
    for path in spoof_files:
        print(f"  [SPOOF] {path.name} ...", end=" ", flush=True)
        result = await score_file(path, args.server)
        if result is None:
            continue
        risk = result.get("risk", 0)
        predicted_spoof = risk >= threshold
        label = "TP" if predicted_spoof else "FN"
        print(f"risk={risk:.1f} -> {label}")
        results.append({"file": path.name, "ground_truth": "spoof",
                        "risk": risk, "predicted_spoof": predicted_spoof})
        if predicted_spoof:
            tp += 1
        else:
            fn += 1

    # Score bonafide files (ground truth = negative)
    for path in bonafide_files:
        print(f"  [REAL ] {path.name} ...", end=" ", flush=True)
        result = await score_file(path, args.server)
        if result is None:
            continue
        risk = result.get("risk", 0)
        predicted_spoof = risk >= threshold
        label = "FP" if predicted_spoof else "TN"
        print(f"risk={risk:.1f} -> {label}")
        results.append({"file": path.name, "ground_truth": "bonafide",
                        "risk": risk, "predicted_spoof": predicted_spoof})
        if predicted_spoof:
            fp += 1
        else:
            tn += 1

    # Metrics
    total = tp + fp + tn + fn
    accuracy = (tp + tn) / total if total else 0.0
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
    fpr = fp / (fp + tn) if (fp + tn) else 0.0
    fnr = fn / (fn + tp) if (fn + tp) else 0.0

    print("\n" + "=" * 60)
    print(f"Detection Evaluation Results (threshold={threshold}%)")
    print("=" * 60)
    print(f"  Total samples : {total}  (spoof={tp+fn}, bonafide={fp+tn})")
    print(f"  True Positives: {tp}   False Negatives: {fn}")
    print(f"  True Negatives: {tn}   False Positives: {fp}")
    print("  ---")
    print(f"  Accuracy      : {accuracy * 100:.1f}%")
    print(f"  Precision     : {precision * 100:.1f}%")
    print(f"  Recall        : {recall * 100:.1f}%  (sensitivity / TPR)")
    print(f"  F1 Score      : {f1 * 100:.1f}%")
    print(f"  False Pos Rate: {fpr * 100:.1f}%  (false alarm on real speech)")
    print(f"  False Neg Rate: {fnr * 100:.1f}%  (missed clone detections)")
    print("=" * 60)

    if args.output:
        out_path = Path(args.output)
        out_path.write_text(json.dumps({"threshold": threshold,
                                         "metrics": {"accuracy": accuracy,
                                                      "precision": precision,
                                                      "recall": recall,
                                                      "f1": f1,
                                                      "fpr": fpr,
                                                      "fnr": fnr},
                                         "results": results}, indent=2),
                            encoding="utf-8")
        print(f"Full results written to {out_path}")


def main():
    ap = argparse.ArgumentParser(description="antAI detection evaluation harness")
    ap.add_argument("--dir", default="audio_samples",
                    help="Directory with spoof/ and bonafide/ sub-dirs")
    ap.add_argument("--server", default="http://localhost:8765",
                    help="antAI server URL")
    ap.add_argument("--threshold", type=float, default=40.0,
                    help="Risk score threshold for 'spoof' prediction (default 40)")
    ap.add_argument("--output", default="",
                    help="Optional JSON output file for full per-sample results")
    args = ap.parse_args()
    asyncio.run(main_async(args))


if __name__ == "__main__":
    main()
