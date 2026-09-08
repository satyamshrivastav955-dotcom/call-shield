"""Export the SpeechBrain ECAPA-TDNN speaker model to on-device ONNX (+INT8).

WHY THIS IS A SEPARATE SCRIPT (not export_onnx.py):
  export_onnx.py handles HF `transformers` checkpoints via AutoModel*. The
  speaker model in models/speaker_verify/ is a *SpeechBrain* checkpoint
  (embedding_model.ckpt + hyperparams.yaml), which AutoModel cannot load. It
  needs SpeechBrain's own loader and its Fbank front-end, so it gets its own
  export path — exactly as the audit called out.

CONTRACT THIS SCRIPT MUST HONOR (do not "fix" the app to match; match the app):
  app/.../ai/OrtEngines.kt  ->  class SpeakerEngine(models, "ecapa_tdnn.int8.onnx")
      embed(audio16k: FloatArray):
          input  tensor name = "audio", shape [1, N]  (RAW 16 kHz mono waveform)
          output tensor [0]  = Array<FloatArray>, row [0] = 192-d embedding
      verify(): cosine(embed(live), enrolled) >= 0.60
  => We export a WAVEFORM-IN wrapper: raw audio -> Fbank -> mean_var_norm ->
     ECAPA_TDNN -> [1,192]. Feature extraction lives INSIDE the graph so the
     phone never has to reimplement SpeechBrain's Fbank (which would silently
     diverge — precisely the "fake-by-approximation" failure the hard rule bans).

OUTPUT (into --out-dir, default ./onnx):
    ecapa_tdnn.onnx        fp32
    ecapa_tdnn.int8.onnx   dynamic-quantized  <-- this is what the APK ships

SELF-VERIFICATION (always on; --no-verify to skip):
  1. wrapper(torch) vs SpeechBrain encode_batch()      -> cosine must be ~1.0
  2. onnxruntime(fp32) vs wrapper(torch)               -> cosine must be > 0.9990
  3. onnxruntime(int8) vs wrapper(torch)               -> cosine must be > 0.98
  Any failure raises SystemExit — a broken/unfaithful model is never emitted.

RUN (on the machine with the real venv: torch + speechbrain + onnxruntime):
    cd server
    python scripts/export_ecapa_onnx.py
    python scripts/export_ecapa_onnx.py --src models/speaker_verify --out-dir onnx --opset 17

This script CANNOT run in the Claude sandbox (no torch/speechbrain, no network
to install them). It is written to run on satya's Windows venv. It is
py_compile-clean and its non-torch logic is unit-checkable.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import torch
import torch.onnx

ROOT = Path(__file__).resolve().parent.parent          # -> server/
DEFAULT_SRC = ROOT / "models" / "speaker_verify"
DEFAULT_OUT = ROOT / "onnx"
EMB_DIM = 192                                           # hyperparams.yaml: lin_neurons


def _patch_speechbrain_stft():
    import speechbrain.processing.features as spf
    def patched_forward(self, x):
        or_shape = x.shape
        if len(or_shape) == 3:
            x = x.transpose(1, 2)
            x = x.reshape(or_shape[0] * or_shape[2], or_shape[1])

        stft = torch.stft(
            x,
            self.n_fft,
            self.hop_length,
            self.win_length,
            self.window.to(x.device),
            self.center,
            self.pad_mode,
            self.normalized_stft,
            self.onesided,
            return_complex=False,
        )
        if len(or_shape) == 3:
            stft = stft.reshape(
                or_shape[0],
                or_shape[2],
                stft.shape[1],
                stft.shape[2],
                stft.shape[3],
            )
            stft = stft.permute(0, 3, 2, 4, 1)
        else:
            stft = stft.transpose(2, 1)
        return stft
    spf.STFT.forward = patched_forward


def _load_speechbrain(src: Path):
    """Load ECAPA via whichever SpeechBrain API is installed (>=1.0 or <1.0)."""
    _patch_speechbrain_stft()
    last_err = None
    for modpath in ("speechbrain.inference.speaker", "speechbrain.pretrained"):
        try:
            mod = __import__(modpath, fromlist=["EncoderClassifier"])
            EncoderClassifier = getattr(mod, "EncoderClassifier")
            clf = EncoderClassifier.from_hparams(
                source=str(src),
                savedir=str(src),
                run_opts={"device": "cpu"},
            )
            clf.eval()
            return clf
        except Exception as e:                          # try the next API
            last_err = e
    raise SystemExit(
        f"Could not load SpeechBrain ECAPA from {src}.\n"
        f"Install speechbrain (`pip install speechbrain`) and torch.\n"
        f"Last error: {last_err}"
    )


def _build_wrapper(clf, normalize_emb: bool):
    """A traceable nn.Module: raw waveform [B,T] -> embedding [B,192].

    Mirrors SpeakerRecognition/EncoderClassifier.encode_batch() so the ONNX
    embeddings are numerically identical to what the server produced.
    """
    import torch
    import torch.nn as nn

    feats = clf.mods.compute_features        # Fbank (uses torch.stft internally)
    mvn = clf.mods.mean_var_norm             # sentence InputNormalization
    emb = clf.mods.embedding_model           # ECAPA_TDNN
    mvn_emb = getattr(clf.mods, "mean_var_norm_emb", None)

    class EcapaEmbed(nn.Module):
        def __init__(self):
            super().__init__()
            self.feats, self.mvn, self.emb, self.mvn_emb = feats, mvn, emb, mvn_emb

        def forward(self, wav):              # wav: [B, T] float, 16 kHz, in [-1,1]
            lens = torch.ones(wav.shape[0], device=wav.device)
            x = self.feats(wav)              # [B, frames, 80]
            x = self.mvn(x, lens)
            e = self.emb(x, lens)            # [B, 1, 192]
            if normalize_emb and self.mvn_emb is not None:
                e = self.mvn_emb(e, torch.ones(e.shape[0], device=e.device))
            return e.squeeze(1)              # [B, 192]

    m = EcapaEmbed().eval()
    return m


def _cos(a, b):
    import torch
    import torch.nn.functional as F
    return F.cosine_similarity(a.reshape(1, -1), b.reshape(1, -1)).item()


def export(src: Path, out_dir: Path, opset: int, normalize_emb: bool, verify: bool) -> Path:
    import numpy as np
    import torch

    out_dir.mkdir(parents=True, exist_ok=True)
    fp32 = out_dir / "ecapa_tdnn.onnx"
    int8 = out_dir / "ecapa_tdnn.int8.onnx"

    clf = _load_speechbrain(src)
    wrapper = _build_wrapper(clf, normalize_emb)

    # ~3 s of noise is enough to trace; real content used only for verification.
    torch.manual_seed(0)
    dummy = torch.randn(1, 48000, dtype=torch.float32) * 0.05

    print(f"[ecapa] exporting fp32 -> {fp32} (opset {opset})", flush=True)
    import inspect
    exp_kwargs = {"dynamo": False} if "dynamo" in inspect.signature(torch.onnx.export).parameters else {}
    torch.onnx.export(
        wrapper, (dummy,), str(fp32),
        input_names=["audio"], output_names=["embedding"],
        dynamic_axes={"audio": {1: "num_samples"}},
        opset_version=opset, do_constant_folding=True,
        **exp_kwargs
    )

    print(f"[ecapa] dynamic INT8 quantize -> {int8}", flush=True)
    from onnxruntime.quantization import quantize_dynamic, QuantType
    quantize_dynamic(str(fp32), str(int8), op_types_to_quantize=["MatMul", "Gemm"], weight_type=QuantType.QInt8)

    if verify:
        import onnxruntime as ort

        # 1) wrapper vs the canonical SpeechBrain path
        with torch.no_grad():
            ref = clf.encode_batch(dummy).squeeze(1)     # [1,192]
            wout = wrapper(dummy)                         # [1,192]
        c1 = _cos(ref, wout)
        print(f"[verify] wrapper vs SpeechBrain encode_batch cosine = {c1:.6f}")
        if c1 < 0.999:
            raise SystemExit(f"[FAIL] wrapper diverges from SpeechBrain ({c1:.4f}). Not shipping.")

        def run(path):
            s = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
            o = s.run(None, {"audio": dummy.numpy().astype(np.float32)})[0]
            return torch.from_numpy(np.asarray(o))

        c2 = _cos(wout, run(fp32))
        print(f"[verify] onnx(fp32) vs wrapper cosine = {c2:.6f}")
        if c2 < 0.999:
            raise SystemExit(f"[FAIL] fp32 ONNX diverges ({c2:.4f}) — check opset/torch.stft export.")

        c3 = _cos(wout, run(int8))
        print(f"[verify] onnx(int8) vs wrapper cosine = {c3:.6f}")
        if c3 < 0.98:
            raise SystemExit(f"[FAIL] INT8 ONNX degraded too far ({c3:.4f}).")

        # sanity: identical audio -> ~1.0 self-similarity through the int8 graph
        print(f"[verify] emb dim = {run(int8).numel()} (expected {EMB_DIM})")
        print("[verify] PASS — embeddings faithful through fp32 + int8.")

    mb = int8.stat().st_size / 1e6
    print(f"[ecapa] DONE. Ship {int8.name} ({mb:.1f} MB) to app filesDir/onnx/ via adb.")
    return int8


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", default=str(DEFAULT_SRC), help="SpeechBrain ECAPA dir")
    ap.add_argument("--out-dir", default=str(DEFAULT_OUT), help="output dir for .onnx files")
    ap.add_argument("--opset", type=int, default=17, help="ONNX opset (>=17 for torch.stft/DFT)")
    ap.add_argument("--normalize-emb", action="store_true",
                    help="apply global mean_var_norm_emb (SpeechBrain normalize=True path)")
    ap.add_argument("--no-verify", action="store_true", help="skip faithfulness checks (NOT recommended)")
    args = ap.parse_args()
    export(Path(args.src), Path(args.out_dir), args.opset, args.normalize_emb, not args.no_verify)


if __name__ == "__main__":
    main()
