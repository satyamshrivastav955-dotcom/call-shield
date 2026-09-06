"""Fetch training/eval datasets for the on-device plan (Phase A2).

Pulls into server/datasets/ (NOT audio_samples/ — those stay the small,
labeled demo set). After fetching, run:
    python scripts/build_eval_set.py   # populates audio_samples/spoof|bonafide + labels.csv

Sources (all open / gated-approved; ~25-40GB total — resume-capable):
  - ASVspoof5 (train/dev/eval)       : spoof-train core
  - Mozilla Common Voice Hindi (hi)  : bonafide Indian
  - WaveFake                          : vocoder-spoof variety
  - In-the-Wild (subset)              : real-world clones

Usage:
    python scripts/fetch_datasets.py --all            # everything
    python scripts/fetch_datasets.py --only cv-hi     # one source
    python scripts/fetch_datasets.py --list           # show targets + sizes
Requires: huggingface_hub (+ agreed ASVspoof5 terms for that repo).

NOTE: some repos are access-gated (ASVspoof5, In-the-Wild). If a fetch 401s,
the script prints the exact HF page to request access on, then continues with
the rest — rerun later to pick up the missing one.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "datasets"

TARGETS = {
    # ASVspoof5: official HF mirror layout; protocol files map trials->labels.
    "asvspoof5": {
        "repo": "ASVspoof/asvspoof5",
        "kind": "snapshot",
        "note": "GATED: accept terms at https://huggingface.co/ASVspoof/asvspoof5 first.",
    },
    "cv-hi": {
        "repo": "mozilla-foundation/common_voice_17_0",
        "kind": "subset",
        "config": "hi",
        "split": "validated",
        "note": "bonafide Hindi; needs HF token (huggingface-cli login).",
    },
    "wavefake": {
        "repo": "microsoft/wavefake",
        "kind": "snapshot",
        "note": "vocoder spoofs (MelGAN/HiFi-GAN family).",
    },
    "in-the-wild": {
        "repo": "In-the-Wild/In-the-Wild",
        "kind": "snapshot",
        "note": "GATED real-world clones; subset copied by build_eval_set.py.",
    },
    # On-device ASR (Phase D): sherpa-onnx streaming models, en + hi.
    "sherpa-en": {
        "repo": "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
        "kind": "snapshot",
        "note": "on-device English streaming ASR.",
    },
    "sherpa-hi": {
        "repo": "csukuangfj/sherpa-onnx-streaming-zipformer-hindi-2023-06-26",
        "kind": "snapshot",
        "note": "on-device Hindi streaming ASR.",
    },
}


def fetch(name: str) -> bool:
    from huggingface_hub import snapshot_download
    from huggingface_hub.utils import HfHubHTTPError

    t = TARGETS[name]
    dest = DATA / name
    print(f"\n=== [{name}] {t['repo']} -> {dest} ===", flush=True)
    print(f"    {t['note']}", flush=True)
    try:
        if t["kind"] == "subset":
            from datasets import load_dataset
            ds = load_dataset(t["repo"], t["config"], split=t["split"])
            dest.mkdir(parents=True, exist_ok=True)
            ds.save_to_disk(str(dest))
        else:
            snapshot_download(
                repo_id=t["repo"],
                local_dir=str(dest),
                local_dir_use_symlinks=False,
                resume_download=True,
            )
        print(f"=== [{name}] done ===", flush=True)
        return True
    except HfHubHTTPError as e:
        print(f"!!! [{name}] gated/login needed ({e}). Request access, then rerun.", flush=True)
        return False
    except ImportError as e:
        print(f"!!! [{name}] missing dep: {e} (pip install datasets huggingface_hub).", flush=True)
        return False
    except Exception as e:
        print(f"!!! [{name}] FAILED: {e}. Rerun resumes.", flush=True)
        return False


def main() -> None:
    ap = argparse.ArgumentParser(description="Fetch training/eval datasets.")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--only", choices=list(TARGETS))
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()

    if args.list:
        for k, v in TARGETS.items():
            print(f"{k:12s} {v['repo']:60s} {v['note']}")
        return

    DATA.mkdir(parents=True, exist_ok=True)
    names = list(TARGETS) if args.all else ([args.only] if args.only else [])
    if not names:
        sys.exit("pass --all or --only <name> (or --list)")
    ok = {n: fetch(n) for n in names}
    print("\nSummary:", {k: ("OK" if v else "PENDING") for k, v in ok.items()})
    print("Next: python scripts/build_eval_set.py")


if __name__ == "__main__":
    main()
