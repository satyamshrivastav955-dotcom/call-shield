"""Build the labeled on-device eval set from datasets/ + ad-hoc files.

Reads server/datasets/* (see fetch_datasets.py) and writes:
  audio_samples/spoof/<file> + audio_samples/bonafide/<file>
  audio_samples/labels.csv  (filename,split,label,language,speaker,source)

ASVspoof5 note (IMPORTANT): the current HF release is PARQUET-based with
embedded audio — there is NO flac/ directory layout. This script discovers
the schema at runtime (audio-bytes column, label column, speaker column) via
pyarrow/datasets instead of assuming column names, so it works whether the
mirror uses {audio,label,speaker}, {wav,spoof,bona-fide...} or similar.
Do NOT use scripts/build_parquet.py (hard-coded Linux source path).

Rules: speaker-disjoint train/val/test (speakers hashed 70/15/15), per-source
caps so no corpus dominates, 16kHz mono WAV output. Ad-hoc files already in
spoof|bonafide/ are kept as seed test rows.

Usage:
  python scripts/build_eval_set.py [--max-per-source N] [--dry-run]
  python scripts/build_eval_set.py --only-seed     # just (re)write labels.csv
                                                   # from current folders
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "datasets"
OUT = ROOT / "audio_samples"
SPOOF = OUT / "spoof"
BONAFIDE = OUT / "bonafide"

AUDIO_EXTS = {".wav", ".flac", ".ogg", ".mp3", ".m4a", ".opus"}

SPOOF_HINTS = ("spoof", "fake", "synth", "clone", "tts", "ai_voice", "deepfake")
BONAFIDE_HINTS = ("bonafide", "bona-fide", "genuine", "real", "human")


def split_for(speaker: str) -> str:
    h = int(hashlib.sha256(speaker.encode()).hexdigest(), 16) % 100
    return "train" if h < 70 else ("val" if h < 85 else "test")


def label_of(name: str) -> str | None:
    low = name.lower()
    if any(k in low for k in SPOOF_HINTS):
        return "spoof"
    if any(k in low for k in BONAFIDE_HINTS):
        return "bonafide"
    return None


def seed_rows() -> list[dict]:
    rows: list[dict] = []
    for sub, label in ((SPOOF, "spoof"), (BONAFIDE, "bonafide")):
        if not sub.exists():
            continue
        for f in sorted(sub.iterdir()):
            if f.suffix.lower() in AUDIO_EXTS and f.is_file():
                rows.append({
                    "filename": f.name, "split": "test", "label": label,
                    "language": "hi" if "hindi" in f.name.lower() else "en",
                    "speaker": "satyam" if "satyam" in f.name.lower()
                               else ("tushar" if "tushar" in f.name.lower() else "seed"),
                    "source": "tts-clone" if label == "spoof" else "human",
                })
    return rows


def decode_audio_bytes(raw: bytes, target_sr: int = 16000):
    """bytes (wav/flac/ogg/mp3) -> mono float32 @target_sr. Returns None on failure."""
    import numpy as np
    import soundfile as sf
    try:
        audio, sr = sf.read(io.BytesIO(raw), dtype="float32", always_2d=True)
    except Exception:
        return None
    mono = audio.mean(axis=1).astype(np.float32)
    if sr == target_sr:
        return mono
    # light linear resample (no librosa dependency)
    ratio = sr / target_sr
    n = max(1, int(len(mono) / ratio))
    idx = (np.arange(n) * ratio)
    j = np.clip(idx.astype(int), 0, len(mono) - 2)
    frac = (idx - j).astype(np.float32)
    return (mono[j] * (1 - frac) + mono[j + 1] * frac).astype(np.float32)


def parquet_files(root: Path) -> list[Path]:
    return sorted(root.rglob("*.parquet")) if root.exists() else []


def describe_parquet(pq: Path) -> None:
    """Print schema of first parquet file (for operator inspection)."""
    import pyarrow.parquet as papq
    schema = papq.read_schema(pq)
    print(f"  {pq.name}: {[(f.name, str(f.type)) for f in schema]}")


def ingest_parquet_corpus(corpus_dir: Path, tag: str, lang: str,
                          max_n: int, rows: list[dict], dry_run: bool) -> int:
    """Schema-discovery ingest: finds audio/label/speaker columns by name hints.
    Returns clips added. Never assumes fixed column names."""
    import pyarrow.parquet as papq
    files = parquet_files(corpus_dir)
    if not files:
        print(f"  [{tag}] no .parquet yet (download in progress?) — skipped")
        return 0
    print(f"  [{tag}] {len(files)} parquet files; schema of first:")
    describe_parquet(files[0])

    added = 0
    per_label = max_n // 2
    counts = {"spoof": 0, "bonafide": 0}
    for pq in files:
        if added >= max_n:
            break
        try:
            table = papq.read_table(pq)
        except Exception as e:
            print(f"    {pq.name}: unreadable ({e}) — skipped")
            continue
        cols = {f.name: f.name for f in table.schema}
        low = {c.lower(): c for c in cols}
        audio_c = next((low[c] for c in low
                        if any(k in c for k in ("audio", "wav", "waveform", "speech", "bytes"))), None)
        label_c = next((low[c] for c in low
                        if any(k in c for k in ("label", "spoof", "bonafide", "target", "class"))), None)
        spk_c = next((low[c] for c in low
                      if any(k in c for k in ("speaker", "spk", "utt", "speaker_id", "file_id", "key"))), None)
        if audio_c is None:
            print(f"    {pq.name}: no audio column found {sorted(cols)} — skipped")
            continue
        d = table.to_pydict()
        n = len(d[audio_c])
        for i in range(n):
            if added >= max_n:
                break
            raw_label = str(d[label_c][i]).lower() if label_c else ""
            label = label_of(raw_label) or label_of(f"{pq.stem}_{i}")
            if label is None:
                # numeric convention: ASVspoof protocol — 1 = spoof? unknown -> skip
                # rather than mislabel (a mislabeled clip is worse than a missing one)
                continue
            if counts[label] >= per_label:
                continue
            cell = d[audio_c][i]
            raw = cell.get("bytes") if isinstance(cell, dict) else cell
            if isinstance(raw, memoryview):
                raw = raw.tobytes()
            if not isinstance(raw, (bytes, bytearray)):
                continue
            speaker = f"{tag}-{d[spk_c][i]}" if spk_c else f"{tag}-{pq.stem}-{i}"
            dest = (SPOOF if label == "spoof" else BONAFIDE) / f"{tag}_{pq.stem}_{i}.wav"
            if not dry_run:
                import soundfile as sf
                pcm = decode_audio_bytes(bytes(raw))
                if pcm is None or len(pcm) < 8000:
                    continue
                try:
                    sf.write(dest, pcm, 16000)
                except OSError:
                    continue
            rows.append({"filename": dest.name, "split": split_for(str(speaker)),
                         "label": label, "language": lang,
                         "speaker": str(speaker), "source": tag})
            counts[label] += 1
            added += 1
    print(f"  [{tag}] +{added} clips {counts}")
    return added


def ingest_loose_audio(corpus_dir: Path, tag: str, lang: str,
                       max_n: int, rows: list[dict], dry_run: bool) -> int:
    """Fallback for corpora that land as loose audio files (WaveFake/ITW)."""
    if not corpus_dir.exists():
        return 0
    hits = [f for f in sorted(corpus_dir.rglob("*"))
            if f.suffix.lower() in AUDIO_EXTS and f.is_file()][:max_n]
    added = 0
    for src in hits:
        label = label_of(src.name) or ("spoof" if tag in ("wavefake", "in-the-wild") else None)
        if label is None:
            continue
        dest = (SPOOF if label == "spoof" else BONAFIDE) / f"{tag}_{src.name}"
        if not dry_run and not dest.exists():
            try:
                shutil.copy2(src, dest)
            except OSError:
                continue
        rows.append({"filename": dest.name, "split": split_for(f"{tag}-{src.stem}"),
                     "label": label, "language": lang,
                     "speaker": f"{tag}-{src.stem.split('_')[0]}", "source": tag})
        added += 1
    print(f"  [{tag}] +{added} loose files")
    return added


def write_labels(rows: list[dict]) -> None:
    with open(OUT / "labels.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["filename", "split", "label",
                                          "language", "speaker", "source"])
        w.writeheader()
        w.writerows(rows)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-per-source", type=int, default=400)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--only-seed", action="store_true",
                    help="only (re)write labels.csv from current folders")
    args = ap.parse_args()

    SPOOF.mkdir(parents=True, exist_ok=True)
    BONAFIDE.mkdir(parents=True, exist_ok=True)
    rows = seed_rows()

    if not args.only_seed:
        ingest_parquet_corpus(DATA / "asvspoof5", "asvspoof5", "en",
                              args.max_per_source, rows, args.dry_run)
        ingest_parquet_corpus(DATA / "cv-hi", "commonvoice-hi", "hi",
                              args.max_per_source, rows, args.dry_run)
        ingest_loose_audio(DATA / "wavefake", "wavefake", "en",
                           args.max_per_source, rows, args.dry_run)
        ingest_loose_audio(DATA / "in-the-wild", "in-the-wild", "en",
                           args.max_per_source // 2, rows, args.dry_run)

    if not args.dry_run:
        write_labels(rows)
    n_spoof = sum(1 for r in rows if r["label"] == "spoof")
    print(f"eval set: {len(rows)} files ({n_spoof} spoof, {len(rows)-n_spoof} bonafide)")
    for s in ("train", "val", "test"):
        print(f"  {s}: {sum(1 for r in rows if r['split']==s)}")
    print("Next: python scripts/quick_eval.py --dir audio_samples  (no server needed)")


if __name__ == "__main__":
    main()
