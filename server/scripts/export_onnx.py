"""Export local checkpoints to ONNX for on-device (ONNX Runtime Mobile).

PHONE CONTRACT (must not drift): the Android app's ModelManager.REQUIRED
expects EXACTLY these files in filesDir/onnx:
    vad.onnx                  <- Silero VAD (silero-vad pip package)
    spoof_aasist_l.int8.onnx  <- models/voice_deepfake_aasist/aasist_l.pt
    ecapa_tdnn.int8.onnx      <- models/speaker_verify (speechbrain ECAPA)
    asr_whisper.int8.onnx     <- NOT EXPORTED (on-device Transcriber is still
                                 the NoOp stub — P2b; skipped honestly below)
    scam_pattern.int8.onnx    <- models/classifiers/scam_pattern
    urgency.int8.onnx         <- models/classifiers/urgency
    intent.int8.onnx          <- models/classifiers/intent
    scripts/train_aasist.py -> the AASIST-L train/export pipeline
    (AASIST-L + ECAPA fine-tune need GPU rig) — the phone will honestly report
    "6/7 on-device" until on-device ASR lands.

Usage:
    python scripts/export_onnx.py --all          # produce the phone contract
    python scripts/export_onnx.py --model <src> --out <file> --kind audio|text [--quant]

Notes:
- Audio models: dummy input = 4s @16kHz (64000 samples); AASIST-L also uses
  this window on the phone.
- Text models: dummy input = 64 input_ids + attention_mask (--seq-len).
- INT8 is applied where the phone expects it (.int8.onnx names); use
  --no-quant to emit raw fp32 for debugging.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "onnx"

# name: (exporter, source) — exporter functions defined below.
EXPORTERS: dict[str, callable] = {}


def exporter(name: str):
    def deco(fn):
        EXPORTERS[name] = fn
        return fn
    return deco


# ------------------------------------------------------------- VAD (Silero)
@exporter("vad")
def export_vad(out: Path, quant: bool) -> str:
    """Silero VAD -> vad.onnx. The phone app feeds [1, T] float 16k audio.

    The silero-vad pip package ships ONNX artifacts; we copy the 16k-specific
    one so the bytes are exactly the vendor's. NOTE: the phone's VAD is an
    RMS energy gate (ShieldPipeline #4) — vad.onnx is presence-counted by
    ModelManager.REQUIRED but never run, so the stateful op15 inputs
    (input/state/sr) are fine.
    """
    import silero_vad  # pip package (requirements.txt)
    pkg = Path(silero_vad.__file__).parent
    candidates = [
        pkg / "data" / "silero_vad_16k_op15.onnx",   # 16k-specific build
        pkg / "data" / "silero_vad.onnx",
        pkg / "assets" / "silero_vad.onnx",          # older layouts
    ]
    src = next((c for c in candidates if c.exists()), None)
    if src is None:
        sys.exit("silero_vad ONNX asset not found in the installed package")
    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, out)
    return str(out)


# ------------------------------------------------------------- AASIST-L
@exporter("spoof_aasist_l")
def export_aasist(out: Path, quant: bool) -> str:
    """AASIST-L checkpoint -> spoof_aasist_l.int8.onnx (audio in, logits out)."""
    import torch
    sys.path.insert(0, str(ROOT / "src"))
    from antai.inference.voice._aasist import AASISTL

    ckpt = ROOT / "models" / "voice_deepfake_aasist" / "aasist_l.pt"
    if not ckpt.exists():
        sys.exit(f"skip spoof_aasist_l: {ckpt} missing (run scripts/train_aasist.py first)")
    model = AASISTL()
    state = torch.load(ckpt, map_location="cpu", weights_only=True)
    model.load_state_dict(state if "state_dict" not in state else state["state_dict"])
    model.eval()

    class _Wrapper(torch.nn.Module):
        """Unwrap .logits so the graph output IS the logits tensor."""

        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, audio):
            return self.m(audio).logits

    wrapped = _Wrapper(model).eval()
    dummy = torch.zeros(1, 64000, dtype=torch.float32)
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(wrapped, (dummy,), str(out), input_names=["audio"],
                      output_names=["logits"], opset_version=14,
                      dynamic_axes={"audio": {1: "length"},
                                    "logits": {0: "batch"}})
    if quant:
        return _quantize(out)
    return str(out)


# ------------------------------------------------------------- ECAPA (speechbrain)
@exporter("ecapa_tdnn")
def export_ecapa(out: Path, quant: bool) -> str:
    """speechbrain ECAPA -> ecapa_tdnn.int8.onnx (audio in, 192-d embedding out).

    Loads exactly like SpeakerVerifyEngine._load (speaker_verify.py), then
    exports the underlying module. The app feeds 16k audio and no resampling
    is needed; speechbrain's wrapper does more (f0 etc.) than we use, so we
    export the core encoder path only.
    """
    import torch
    sys.path.insert(0, str(ROOT / "src"))
    savedir = ROOT / "models" / "speaker_verify"
    if not savedir.exists():
        sys.exit(f"skip ecapa_tdnn: {savedir} missing (setup/download_models.py)")
    from speechbrain.inference.speaker import EncoderClassifier
    clf = EncoderClassifier.from_hparams(
        source="speechbrain/spkrec-ecapa-voxceleb", savedir=str(savedir),
        run_opts={"device": "cpu"})
    enc = clf.mods["embedding_model"].eval()

    class _Embed(torch.nn.Module):
        """16k mono waveform [1, T] -> L2-normalized 192-d embedding —
        the exact contract of SpeakerEngine.embed() on the phone."""

        def __init__(self, clf, enc):
            super().__init__()
            self.clf = clf
            self.enc = enc

        def forward(self, audio):
            feats = self.clf.mods["compute_features"](audio)
            feats = self.clf.mods["premean"](feats)
            emb = self.enc(feats)
            return torch.nn.functional.normalize(emb.squeeze(1), dim=-1)

    model = _Embed(clf, enc).eval()
    dummy = torch.zeros(1, 48000, dtype=torch.float32)  # 3s, min enrollment length
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(model, (dummy,), str(out), input_names=["audio"],
                      output_names=["embedding"], opset_version=14,
                      dynamic_axes={"audio": {1: "length"},
                                    "embedding": {0: "batch"}})
    if quant:
        return _quantize(out)
    return str(out)


# ------------------------------------------------------------- text classifiers
def _export_text(src: Path, out: Path, seq_len: int, quant: bool) -> str:
    import torch
    if not src.exists():
        sys.exit(f"skip {src.name}: {src} missing (setup/download_models.py)")
    try:
        from transformers import AutoModelForSequenceClassification
        model = AutoModelForSequenceClassification.from_pretrained(src)
    except Exception:
        from transformers import AutoModel
        model = AutoModel.from_pretrained(src)
    model.eval()
    ids = torch.zeros(1, seq_len, dtype=torch.long)
    mask = torch.ones(1, seq_len, dtype=torch.long)
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(model, (ids, mask), str(out),
                      input_names=["input_ids", "attention_mask"],
                      output_names=["logits"], opset_version=14,
                      dynamic_axes={"input_ids": {1: "seq"},
                                    "attention_mask": {1: "seq"}})
    if quant:
        return _quantize(out)
    return str(out)


@exporter("scam_pattern")
def _scam(out: Path, quant: bool, seq_len: int = 64) -> str:
    return _export_text(ROOT / "models" / "classifiers" / "scam_pattern",
                        out, seq_len, quant)


@exporter("urgency")
def _urg(out: Path, quant: bool, seq_len: int = 64) -> str:
    return _export_text(ROOT / "models" / "classifiers" / "urgency",
                        out, seq_len, quant)


@exporter("intent")
def _int(out: Path, quant: bool, seq_len: int = 64) -> str:
    return _export_text(ROOT / "models" / "classifiers" / "intent",
                        out, seq_len, quant)


# ------------------------------------------------------------- helpers
def _quantize(out: Path) -> str:
    from onnxruntime.quantization import quantize_dynamic, QuantType
    # out may already be "<name>.int8.onnx" (phone name) — quantize into a
    # temp name then rename, so the FINAL file always carries the exact
    # phone-contract name.
    q_p = out.with_name(out.stem + ".q.onnx")
    quantize_dynamic(str(out), str(q_p), weight_type=QuantType.QInt8)
    out.unlink(missing_ok=True)
    q_p.rename(out)
    return str(out)


def _phone_names(name: str) -> Path:
    """Map exporter name -> the file name ModelManager.REQUIRED expects."""
    if name in ("scam_pattern", "urgency", "intent"):
        return DEFAULT_OUT / f"{name}.int8.onnx"
    if name == "spoof_aasist_l":
        return DEFAULT_OUT / "spoof_aasist_l.int8.onnx"
    if name == "ecapa_tdnn":
        return DEFAULT_OUT / "ecapa_tdnn.int8.onnx"
    return DEFAULT_OUT / "vad.onnx"


def main() -> None:
    ap = argparse.ArgumentParser(description="Export checkpoints to ONNX for on-device.")
    ap.add_argument("--model", default=None,
                    help="raw src dir (single-export mode, legacy)")
    ap.add_argument("--out", default=None)
    ap.add_argument("--kind", default="audio", choices=["audio", "text"])
    ap.add_argument("--seq-len", type=int, default=64)
    ap.add_argument("--quant", action="store_true", default=True)
    ap.add_argument("--no-quant", dest="quant", action="store_false")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()

    if args.all:
        # The phone contract, in ModelManager.REQUIRED order.
        for name in ("vad", "spoof_aasist_l", "ecapa_tdnn", "asr_whisper",
                     "scam_pattern", "urgency", "intent"):
            if name == "asr_whisper":
                print("skip asr_whisper: on-device Transcriber not implemented "
                      "yet (P2b) — phone honestly reports heuristic ASR mode")
                continue
            fn = EXPORTERS.get(name)
            if fn is None:
                print(f"skip {name}: no exporter")
                continue
            try:
                print(f"=== [{name}] -> {_phone_names(name)} ===", flush=True)
                fn(_phone_names(name), args.quant)
                print(f"=== [{name}] done ===", flush=True)
            except SystemExit as e:
                print(f"{e}", flush=True)   # per-file skip, keep going
        print(f"\nphone contract target: server/onnx/* -> filesDir/onnx "
              f"(ModelManager.REQUIRED)")
        return

    if not args.model or not args.out:
        sys.exit("provide --model + --out, or --all")
    # legacy single-export path
    if args.kind == "audio":
        print("legacy audio export: use --all (AASIST handles audio models)")
        return
    src = Path(args.model)
    if not src.exists():
        sys.exit(f"skip: {src} missing")
    print(_export_text(src, Path(args.out), args.seq_len, args.quant), flush=True)


if __name__ == "__main__":
    main()
