"""Export local HF checkpoints to ONNX (+ optional dynamic INT8 quant) for on-device.

On-device plan P0: the phone (ONNX Runtime Mobile / QNN EP) cannot load
transformers checkpoints directly. This rig converts them once on the dev
machine; the APK ships only the .onnx (+ tokenizer) files.

Usage:
    python scripts/export_onnx.py --model models/voice_deepfake --out onnx/voice_deepfake.onnx
    python scripts/export_onnx.py --model models/classifiers/scam_pattern --out onnx/scam_pattern.onnx --quant
    python scripts/export_onnx.py --all   # exports known map below into onnx/

Notes:
- Audio-classification models: dummy input = 4s @16kHz (64000 samples).
- Text models: dummy input = 64 input_ids + attention_mask (override with --seq-len).
- Quant uses onnxruntime dynamic quantization (CPU). QNN-EP QDQ quant happens
  later on the Snapdragon bench (P4), not here.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "onnx"

EXPORT_MAP = {
    # name: (src_dir, kind)
    "voice_deepfake": ("models/voice_deepfake", "audio"),
    "voice_deepfake_cross2": ("models/voice_deepfake_cross2", "audio"),
    "scam_pattern": ("models/classifiers/scam_pattern", "text"),
    "urgency": ("models/classifiers/urgency", "text"),
    "intent": ("models/classifiers/intent", "text"),
}


def export_one(src: str, out: str, kind: str, seq_len: int, quant: bool) -> str:
    import torch
    from transformers import AutoModelForAudioClassification, AutoModelForSequenceClassification

    src_p, out_p = Path(src), Path(out)
    out_p.parent.mkdir(parents=True, exist_ok=True)
    if kind == "audio":
        model = AutoModelForAudioClassification.from_pretrained(src_p)
        model.eval()
        dummy = torch.zeros(1, 64000, dtype=torch.float32)  # 4s @16k
        torch.onnx.export(model, (dummy,), str(out_p), input_names=["audio"],
                          output_names=["logits"], opset_version=14,
                          dynamic_axes={"audio": {1: "length"}})
    else:
        try:
            model = AutoModelForSequenceClassification.from_pretrained(src_p)
        except Exception:
            from transformers import AutoModel
            model = AutoModel.from_pretrained(src_p)
        model.eval()
        ids = torch.zeros(1, seq_len, dtype=torch.long)
        mask = torch.ones(1, seq_len, dtype=torch.long)
        torch.onnx.export(model, (ids, mask), str(out_p),
                          input_names=["input_ids", "attention_mask"],
                          output_names=["logits"], opset_version=14,
                          dynamic_axes={"input_ids": {1: "seq"}, "attention_mask": {1: "seq"}})
    if quant:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        q_p = out_p.with_name(out_p.stem + ".int8.onnx")
        quantize_dynamic(str(out_p), str(q_p), weight_type=QuantType.QInt8)
        return str(q_p)
    return str(out_p)


def main() -> None:
    ap = argparse.ArgumentParser(description="Export HF checkpoints to ONNX for on-device.")
    ap.add_argument("--model", default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--kind", default="audio", choices=["audio", "text"])
    ap.add_argument("--seq-len", type=int, default=64)
    ap.add_argument("--quant", action="store_true")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()
    if args.all:
        for name, (src, kind) in EXPORT_MAP.items():
            s = ROOT / src
            if not s.exists():
                print(f"skip {name}: {s} missing")
                continue
            print(export_one(str(s), str(DEFAULT_OUT / f"{name}.onnx"), kind, args.seq_len, args.quant), flush=True)
        return
    if not args.model or not args.out:
        sys.exit("provide --model + --out, or --all")
    print(export_one(args.model, args.out, args.kind, args.seq_len, args.quant), flush=True)


if __name__ == "__main__":
    main()
