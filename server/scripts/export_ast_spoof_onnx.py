"""Export the server's REAL AST ASVspoof5 spoof model to on-device ONNX (+INT8).

This is the model in server/models/voice_deepfake/ (ASTForAudioClassification,
validation acc 0.8489 per eval_results.json). Task 1b: export THIS model — do
NOT source or train AASIST-L. The old on-device filename `spoof_aasist_l.int8.onnx`
was doubly wrong (it named a model that was never shipped, and the real model is
AST, not AASIST). This script emits the honest name `spoof_ast.int8.onnx`, and
OrtEngines.kt/ModelManager.kt are updated to match.

THE ONE CONTRACT FACT THAT DRIVES EVERYTHING (app/.../ai/OrtEngines.kt:36-49):
    SpoofEngine sends input "audio" = raw 16k waveform [1, N]
    and reads the result as:  (r[0].value as Array<FloatArray>)[0].maxOrNull()
  => the ONNX output MUST be a [1,1] tensor holding ONE value. If we output [1,2]
     logits/probs, .maxOrNull() returns max(bonafide,spoof) — so a GENUINE call
     (bonafide≈0.9) would score 0.9 "spoof" and trip a false clone alarm. Shipping
     that would be exactly the silent-fake failure the hard rule forbids.

TWO SINGLE-COLUMN CONTRACTS (--emit), both [1,1], both contract-safe:
  * prob  (default, the shipped contract): softmax -> take the Spoof column ->
    [1,1], output name "spoof_prob". SpoofEngine reads it directly.
  * logit (--emit logit, 2-class heads only): the spoof-minus-bonafide LOGIT GAP,
    output name "spoof_logit". SpoofEngine applies sigmoid(gap / T) for Phase-1.2
    on-device temperature de-saturation. At T=1, sigmoid(gap) == the softmax spoof
    prob EXACTLY, so this export is a drop-in identity for an uncalibrated device,
    and T>1 de-saturates at full logit range (no post-softmax clamp limit). The T
    itself is FIT on the int8 outputs by scripts/fit_temperature_ondevice.py — never
    hand-set (project HARD RULE).

WAVEFORM-IN WRAPPER (feature extraction lives INSIDE the graph):
    raw wav [1,N] -> kaldi fbank (the SAME torchaudio fn ASTFeatureExtractor uses)
                  -> pad/truncate to 1024 frames -> normalize -> AST -> softmax
                  -> spoof-class prob [1,1]
  The front-end (waveform-mean subtraction? divisor std vs std*2?) is NOT guessed:
  _calibrate_frontend() tries the variants and picks whichever reproduces the real
  ASTFeatureExtractor output, then asserts the match is within tolerance. A wrong
  front-end fails LOUDLY instead of silently shipping diverged features.

SELF-VERIFICATION (always on; --no-verify to skip):
  0. front-end calibration: wrapper features vs ASTFeatureExtractor -> MSE < tol
  1. wrapper(torch) spoof prob vs HF (feature_extractor+model) softmax -> |Δ|<1e-2
  2. onnx(fp32) vs wrapper(torch) spoof prob                          -> |Δ|<1e-3
  3. onnx(int8) vs wrapper(torch): reported as INFO (accuracy gate is Task 1c,
     validate_asvspoof5.py — this script only proves the GRAPH is faithful).

INPUT SIZE: defaults to a FIXED [1,64000] (=4 s @16k), which is exactly what the
Shield pipeline always feeds (window = 4*16000). Fixed shapes export far more
reliably (kaldi framing uses as_strided). Use --dynamic to attempt a variable
-length axis; use --samples to change the fixed size.

RUN (on the machine with the real venv: torch, torchaudio, transformers, onnxruntime):
    cd server
    python scripts/export_ast_spoof_onnx.py
    python scripts/export_ast_spoof_onnx.py --src models/voice_deepfake --out-dir onnx

Cannot run in the Claude sandbox (no torch/torchaudio/transformers, no network to
install them). Written for satya's Windows venv; py_compile-clean.

FALLBACK: if the waveform-in export fails because kaldi fbank ops won't lower to
ONNX, `--mode features` exports the classifier-from-features only ([1,1024,128] ->
[1,1]). That is NOT drop-in — it needs a matching Kotlin fbank front-end and its
own re-verification — so it is opt-in and clearly flagged, never a silent default.
"""
from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent          # -> server/
DEFAULT_SRC = ROOT / "models" / "voice_deepfake"
DEFAULT_OUT = ROOT / "onnx"
FIXED_SAMPLES = 64000                                   # 4 s @ 16 kHz (pipeline window)


def _spoof_index(id2label: dict) -> int:
    """Find the Spoof class index from config (never hardcode 1)."""
    for k, v in id2label.items():
        if str(v).strip().lower().startswith("spoof"):
            return int(k)
    # config.json here is {0:Bonafide,1:Spoof}; fall back to 1 but say so.
    print("[warn] no label matched /spoof/i; defaulting spoof index = 1")
    return 1


def _kaldi_fbank(wav, sr, num_mel_bins, sub_mean):
    """The exact torchaudio fn ASTFeatureExtractor calls with traceable DFT matrix."""
    import torchaudio.compliance.kaldi as ta_kaldi
    import torch
    if sub_mean:
        wav = wav - wav.mean()
    orig_rfft = torch.fft.rfft
    class _DftRfft:
        def __init__(self, t):
            self.t = t
        def abs(self):
            N = self.t.shape[-1]
            n_arr = torch.arange(N, dtype=torch.float32, device=self.t.device).unsqueeze(0)
            k_arr = torch.arange(N // 2 + 1, dtype=torch.float32, device=self.t.device).unsqueeze(1)
            M = (2.0 * 3.14159265358979323846 * k_arr * n_arr / float(N)).to(torch.float32)
            cos_mat = torch.cos(M).T.to(self.t.dtype)
            sin_mat = -torch.sin(M).T.to(self.t.dtype)
            real = torch.matmul(self.t, cos_mat)
            imag = torch.matmul(self.t, sin_mat)
            return torch.sqrt(real.pow(2.0) + imag.pow(2.0) + 1e-12)
    def _traceable_rfft(t, *args, **kw):
        return _DftRfft(t)
    torch.fft.rfft = _traceable_rfft
    try:
        return ta_kaldi.fbank(
            wav, htk_compat=True, sample_frequency=sr, use_energy=False,
            window_type="hanning", num_mel_bins=num_mel_bins, dither=0.0, frame_shift=10,
        )  # [frames, num_mel_bins]
    finally:
        torch.fft.rfft = orig_rfft


def _pad_trunc(fbank, max_length):
    """Match ASTFeatureExtractor: zero-pad the tail, or keep the first max_length."""
    import torch.nn.functional as F
    # pad tail by max_length then slice to max_length -> correct for BOTH
    # (frames<max: pads with zeros; frames>=max: keeps first max frames).
    return F.pad(fbank, (0, 0, 0, max_length))[:max_length, :]


def _features(wav, sr, num_mel_bins, max_length, sub_mean, div_fn):
    fb = _kaldi_fbank(wav, sr, num_mel_bins, sub_mean)
    fb = _pad_trunc(fb, max_length)
    return div_fn(fb)                                   # normalized [max_length, mel]


def _calibrate_frontend(fe, wav, sr, num_mel_bins, max_length, mean, std):
    """Empirically match the real ASTFeatureExtractor front-end (no guessing)."""
    import torch
    ref = fe(wav.squeeze(0).numpy(), sampling_rate=sr, return_tensors="pt")["input_values"]  # [1,L,mel]
    variants = [
        ("sub_mean=F div=std*2", False, lambda fb: (fb - mean) / (std * 2)),
        ("sub_mean=T div=std*2", True,  lambda fb: (fb - mean) / (std * 2)),
        ("sub_mean=F div=std",   False, lambda fb: (fb - mean) / std),
        ("sub_mean=T div=std",   True,  lambda fb: (fb - mean) / std),
    ]
    best = None
    for name, sub_mean, div_fn in variants:
        try:
            feats = _features(wav, sr, num_mel_bins, max_length, sub_mean, div_fn).unsqueeze(0)
            mse = torch.mean((feats - ref) ** 2).item()
        except Exception as e:
            print(f"[calib] {name}: FAILED ({e})"); continue
        print(f"[calib] {name}: feature MSE vs ASTFeatureExtractor = {mse:.6e}")
        if best is None or mse < best[0]:
            best = (mse, name, sub_mean, div_fn)
    if best is None:
        raise SystemExit("[FAIL] could not compute fbank for any front-end variant.")
    mse, name, sub_mean, div_fn = best
    print(f"[calib] chosen front-end: {name}  (MSE={mse:.3e})")
    if mse > 1e-2:
        raise SystemExit(
            f"[FAIL] best front-end MSE {mse:.3e} > 1e-2 — my fbank does not match "
            f"ASTFeatureExtractor. Refusing to ship a diverged extractor. "
            f"Check torchaudio/transformers versions."
        )
    return sub_mean, div_fn


def _build_wrapper(model, spoof_idx, sr, num_mel_bins, max_length, sub_mean, div_fn,
                   emit="prob", bona_idx=None):
    import torch
    import torch.nn as nn

    class AstSpoof(nn.Module):
        def __init__(self):
            super().__init__()
            self.model = model

        def forward(self, audio):                       # audio [1, N]
            fb = _kaldi_fbank(audio, sr, num_mel_bins, sub_mean)
            fb = _pad_trunc(fb, max_length)
            fb = div_fn(fb)                             # normalize
            feats = fb.unsqueeze(0)                     # [1, max_length, mel]
            logits = self.model(input_values=feats).logits   # [1, num_labels]
            if emit == "logit":
                # Spoof-vs-bonafide LOGIT GAP as [1,1]. The device applies
                # sigmoid(gap / T): at T=1 sigmoid(gap) == the 2-class softmax
                # spoof prob (an exact identity), and T>1 de-saturates at full
                # range (no post-softmax clamp limit). Single-column, contract-safe.
                return logits[:, spoof_idx:spoof_idx + 1] - logits[:, bona_idx:bona_idx + 1]
            probs = torch.softmax(logits, dim=-1)
            return probs[:, spoof_idx:spoof_idx + 1]    # [1,1] spoof prob ONLY
    return AstSpoof().eval()


def _build_features_model(model, spoof_idx, emit="prob", bona_idx=None):
    """Fallback graph: precomputed features [1,L,mel] -> spoof prob/logit [1,1]."""
    import torch
    import torch.nn as nn

    class AstFromFeats(nn.Module):
        def __init__(self):
            super().__init__()
            self.model = model

        def forward(self, input_values):                # [1, max_length, mel]
            logits = self.model(input_values=input_values).logits
            if emit == "logit":
                return logits[:, spoof_idx:spoof_idx + 1] - logits[:, bona_idx:bona_idx + 1]
            probs = torch.softmax(logits, dim=-1)
            return probs[:, spoof_idx:spoof_idx + 1]
    return AstFromFeats().eval()


def export(src: Path, out_dir: Path, opset: int, samples: int,
           dynamic: bool, mode: str, verify: bool, emit: str = "prob") -> Path:
    import numpy as np
    import torch
    from transformers import ASTForAudioClassification, ASTFeatureExtractor

    out_dir.mkdir(parents=True, exist_ok=True)
    fp32 = out_dir / "spoof_ast.onnx"
    int8 = out_dir / "spoof_ast.int8.onnx"

    print(f"[ast] loading {src}", flush=True)
    model = ASTForAudioClassification.from_pretrained(str(src)).eval()
    fe = ASTFeatureExtractor.from_pretrained(str(src))
    cfg = model.config
    id2label = {int(k): v for k, v in cfg.id2label.items()}
    spoof_idx = _spoof_index(id2label)
    num_labels = int(getattr(cfg, "num_labels", len(id2label)))
    # bona index = the OTHER column of a 2-class head; only then is a single
    # spoof-vs-bonafide logit gap well defined.
    bona_idx = 1 - spoof_idx if num_labels == 2 else None
    if emit == "logit" and num_labels != 2:
        raise SystemExit(
            f"[FAIL] --emit logit needs a 2-class head (got num_labels={num_labels}). "
            f"The single [1,1] logit gap is spoof-minus-bonafide; with >2 classes that "
            f"is undefined. Use --emit prob (the shipped contract) for this checkpoint."
        )
    out_name = "spoof_logit" if emit == "logit" else "spoof_prob"
    num_mel_bins = int(getattr(cfg, "num_mel_bins", 128))
    max_length = int(getattr(cfg, "max_length", 1024))
    sr = int(getattr(fe, "sampling_rate", 16000))
    mean = float(getattr(fe, "mean", -4.2677393))
    std = float(getattr(fe, "std", 4.5689974))
    print(f"[ast] labels={id2label} spoof_idx={spoof_idx} bona_idx={bona_idx} "
          f"emit={emit} out='{out_name}' mel={num_mel_bins} "
          f"max_len={max_length} sr={sr} mean={mean:.4f} std={std:.4f}")

    torch.manual_seed(0)
    wav = (torch.randn(1, samples, dtype=torch.float32) * 0.05).clamp_(-1, 1)

    if mode == "features":
        print("[ast] MODE=features (NOT drop-in — needs a Kotlin fbank front-end).")
        wrapper = _build_features_model(model, spoof_idx, emit=emit, bona_idx=bona_idx)
        feats = fe(wav.squeeze(0).numpy(), sampling_rate=sr, return_tensors="pt")["input_values"]
        dummy = (feats,)
        input_names = ["input_values"]
        dyn = {"input_values": {0: "batch"}} if dynamic else None
    else:
        sub_mean, div_fn = _calibrate_frontend(fe, wav, sr, num_mel_bins, max_length, mean, std)
        wrapper = _build_wrapper(model, spoof_idx, sr, num_mel_bins, max_length,
                                 sub_mean, div_fn, emit=emit, bona_idx=bona_idx)
        dummy = (wav,)
        input_names = ["audio"]
        dyn = {"audio": {1: "num_samples"}} if dynamic else None

    print(f"[ast] exporting fp32 -> {fp32} (opset {opset}, "
          f"{'dynamic' if dynamic else f'fixed {samples}'} input)", flush=True)
    try:
        import inspect
        exp_kwargs = {"dynamo": False} if "dynamo" in inspect.signature(torch.onnx.export).parameters else {}
        torch.onnx.export(
            wrapper, dummy, str(fp32),
            input_names=input_names, output_names=[out_name],
            dynamic_axes=dyn, opset_version=opset, do_constant_folding=True,
            **exp_kwargs
        )
    except Exception as e:
        raise SystemExit(
            f"[FAIL] ONNX export failed: {e}\n"
            f"If this is a kaldi-fbank op-coverage error, retry with a higher --opset, "
            f"or use --mode features (then implement the matching Kotlin front-end)."
        )

    print(f"[ast] dynamic INT8 quantize -> {int8}", flush=True)
    from onnxruntime.quantization import quantize_dynamic, QuantType
    quantize_dynamic(str(fp32), str(int8), op_types_to_quantize=["MatMul"], weight_type=QuantType.QInt8)

    if verify:
        import onnxruntime as ort

        def _to_prob(x: float) -> float:
            # logit-emit graphs output the spoof-vs-bonafide gap; sigmoid(gap) is
            # the spoof prob (== the 2-class softmax value). prob-emit is identity.
            return float(1.0 / (1.0 + np.exp(-x))) if emit == "logit" else float(x)

        with torch.no_grad():
            if mode == "features":
                w_out = wrapper(dummy[0]).item()
                ref_logits = model(input_values=dummy[0]).logits
            else:
                w_out = wrapper(wav).item()
                inp = fe(wav.squeeze(0).numpy(), sampling_rate=sr, return_tensors="pt")
                ref_logits = model(**inp).logits
            ref_spoof = torch.softmax(ref_logits, dim=-1)[0, spoof_idx].item()
        w_spoof = _to_prob(w_out)

        print(f"[verify] HF spoof={ref_spoof:.6f}  wrapper spoof={w_spoof:.6f}  "
              f"(raw out={w_out:.6f}, emit={emit})  |diff|={abs(ref_spoof - w_spoof):.2e}")
        if abs(ref_spoof - w_spoof) > 1e-2:
            raise SystemExit(f"[FAIL] wrapper diverges from HF pipeline ({abs(ref_spoof-w_spoof):.3e}).")

        feed_name = input_names[0]
        feed_val = (dummy[0].numpy().astype(np.float32))

        def run(path):
            s = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
            out = s.run(None, {feed_name: feed_val})[0]
            arr = np.asarray(out).reshape(-1)
            return _to_prob(float(arr[0])), arr.size

        f_spoof, f_sz = run(fp32)
        print(f"[verify] onnx(fp32) spoof={f_spoof:.6f}  size={f_sz} (expected 1)  "
              f"|diff vs wrapper|={abs(f_spoof - w_spoof):.2e}")
        if f_sz != 1:
            raise SystemExit(f"[FAIL] fp32 output has {f_sz} elems; contract needs [1,1]. "
                             f".maxOrNull() over >1 elems would flag genuine audio as spoof.")
        if abs(f_spoof - w_spoof) > 1e-3:
            raise SystemExit(f"[FAIL] fp32 ONNX diverges ({abs(f_spoof-w_spoof):.3e}).")

        i_spoof, i_sz = run(int8)
        print(f"[verify] onnx(int8) spoof={i_spoof:.6f}  size={i_sz}  "
              f"|diff vs fp32|={abs(i_spoof - f_spoof):.2e}  (INFO — accuracy gate is Task 1c)")
        if i_sz != 1:
            raise SystemExit(f"[FAIL] int8 output has {i_sz} elems; contract needs [1,1].")
        print(f"[verify] PASS — graph faithful; output '{out_name}' is [1,1] "
              f"({'logit gap' if emit == 'logit' else 'spoof prob'}), contract-safe.")

    mb = int8.stat().st_size / 1e6
    print(f"[ast] DONE. Ship {int8.name} ({mb:.1f} MB) to app filesDir/onnx/ via adb.")
    print("[ast] NOTE: AST attention runs on the ORT CPU EP on-device (fine). NPU/QNN "
          "acceleration is a separate, deferred optimization (plan.md attention-op risk).")
    return int8


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", default=str(DEFAULT_SRC), help="AST checkpoint dir")
    ap.add_argument("--out-dir", default=str(DEFAULT_OUT), help="output dir for .onnx files")
    ap.add_argument("--opset", type=int, default=17, help="ONNX opset")
    ap.add_argument("--samples", type=int, default=FIXED_SAMPLES, help="fixed input length (4s@16k=64000)")
    ap.add_argument("--dynamic", action="store_true", help="attempt a variable-length input axis")
    ap.add_argument("--mode", choices=["waveform", "features"], default="waveform",
                    help="waveform=drop-in (default); features=classifier-only fallback (needs Kotlin fbank)")
    ap.add_argument("--emit", choices=["prob", "logit"], default="prob",
                    help="prob=[1,1] post-softmax spoof prob (shipped contract); "
                         "logit=[1,1] spoof-minus-bonafide LOGIT gap (2-class heads only) so "
                         "SpoofEngine.kt can de-saturate exactly via sigmoid(gap/T). T=1 is identical.")
    ap.add_argument("--no-verify", action="store_true", help="skip faithfulness checks (NOT recommended)")
    args = ap.parse_args()
    export(Path(args.src), Path(args.out_dir), args.opset, args.samples,
           args.dynamic, args.mode, not args.no_verify, args.emit)


if __name__ == "__main__":
    main()
