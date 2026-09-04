"""Download all models used by the antAI server into server/models/.

Usage:
    conda run -n antai-server python setup/download_models.py [--skip-llm] [--only llm]

Model list:
  - faster-whisper-small        : streaming multilingual ASR (en+hi)
  - wav2vec2-base-ASVSpoof5     : synthetic/deepfake voice detection
  - speechbrain ECAPA           : speaker-embedding voiceprint verification
  - paraphrase-multilingual     : behavioral-deviation text embeddings
  - distilbert-multilingual     : base for fine-tuned scam/urgency/intent classifiers
  - efficientnet-b0-ffpp-c23    : video deepfake frame classifier (FF++ C23)
  - Qwen2.5-3B-Instruct GGUF    : local reasoning LLM (verdicts, live guidance, reports)
  - librispeech_asr_dummy       : sample wavs for offline simulation/tests
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from huggingface_hub import hf_hub_download, snapshot_download

ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = ROOT / "models"

PIPELINE_MODELS = {
    "asr": "Systran/faster-whisper-small",
    "voice_deepfake": "MattyB95/AST-ASVspoof5-Synthetic-Voice-Detection",
    "voice_deepfake_cross2": "garystafford/wav2vec2-deepfake-voice-detector",
    "speaker_verify": "speechbrain/spkrec-ecapa-voxceleb",
    "behavioral_embeddings": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
    "classifier_base": "distilbert-base-multilingual-cased",
}

LLM_REPO = "Qwen/Qwen2.5-3B-Instruct-GGUF"
LLM_FILE = "qwen2.5-3b-instruct-q4_k_m.gguf"

# torchvision EfficientNet-B0 fine-tuned on FaceForensics++ C23 (0=real, 1=fake)
VIDEO_DEEPFAKE_REPO = "Xicor9/efficientnet-b0-ffpp-c23"
VIDEO_DEEPFAKE_FILE = "efficientnet_b0_ffpp_c23.pth"


def dl_pipeline(only: str | None = None) -> None:
    targets = {only: PIPELINE_MODELS[only]} if only else PIPELINE_MODELS
    for name, repo in targets.items():
        print(f"\n=== [{name}] {repo} -> {MODELS_DIR / name} ===")
        snapshot_download(
            repo_id=repo,
            local_dir=str(MODELS_DIR / name),
            local_dir_use_symlinks=False,
        )
        print(f"=== [{name}] done ===")


def dl_video_deepfake() -> Path:
    print(f"\n=== [video_deepfake] {VIDEO_DEEPFAKE_REPO}:{VIDEO_DEEPFAKE_FILE} ===")
    target_dir = MODELS_DIR / "video_deepfake"
    target_dir.mkdir(parents=True, exist_ok=True)
    path = hf_hub_download(
        repo_id=VIDEO_DEEPFAKE_REPO,
        filename=VIDEO_DEEPFAKE_FILE,
        local_dir=str(target_dir),
        local_dir_use_symlinks=False,
    )
    print(f"=== [video_deepfake] done -> {path} ===")
    return Path(path)


def dl_llm() -> Path:
    print(f"\n=== [llm] {LLM_REPO}:{LLM_FILE} ===")
    path = hf_hub_download(
        repo_id=LLM_REPO,
        filename=LLM_FILE,
        local_dir=str(MODELS_DIR / "llm"),
        local_dir_use_symlinks=False,
    )
    print(f"=== [llm] done -> {path} ===")
    return Path(path)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-llm", action="store_true", help="skip the ~2.2GB LLM download")
    ap.add_argument(
        "--only",
        choices=list(PIPELINE_MODELS) + ["llm", "video_deepfake"],
        help="download a single model",
    )
    args = ap.parse_args()

    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    if args.only == "llm":
        dl_llm()
        return
    if args.only == "video_deepfake":
        dl_video_deepfake()
        return
    dl_pipeline(args.only)
    if not args.only:
        dl_video_deepfake()
    if not args.skip_llm and not args.only:
        dl_llm()
    print("\nAll downloads complete.")
    sys.exit(0)


if __name__ == "__main__":
    main()