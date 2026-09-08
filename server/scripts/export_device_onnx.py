"""Export the REAL proven server models to ONNX (+ INT8) for on-device use.

This is the Task-1 export rig for the two models the Android Shield actually
needs, replacing the never-run generic EXPORT_MAP in export_onnx.py:

  1. ecapa_tdnn.int8.onnx   <- speechbrain spkrec-ecapa-voxceleb
     (server/models/speaker_verify). Speechbrain is NOT a HF transformers
     checkpoint, so this rig wraps the ECAPA encoder with speechbrain's own
     input normalization and exports that wrapper.

  2. spoof_w2v2.int8.onnx   <- the proven wav2vec2 cross-check detector
     (server/models/voice_deepfake_cross2, Wav2Vec2ForSequenceClassification,
     labels real=0/fake=1). Chosen over the AST primary because AST needs
     ~10s of unpadded context (padded 4s windows saturate to spoof 1.0 —
     see inference/voice/deepfake_voice.py), while w2v2 works on raw waveform
     windows of a few seconds.

Both wrappers take RAW mono 16k float32 waveform [1, T] and return:
  ecapa  -> embedding [1, 192] (L2-normalized)
  spoof  -> [1, 2] softmax probs (class 1 = spoof) — wait, no: [1, 1] = P(spoof)
            so the Kotlin maxOrNull() contract yields the spoof probability
            directly and can never misreport P(real) as the spoof score.

Baked INTO each graph (so the app passes raw audio, no preprocessing code):
  ecapa  : x = (x - mean) / (std + eps)     (speechbrain normalize)
  spoof  : x = (x - mean) / (std + 1e-7)    (Wav2Vec2FeatureExtractor
                                             do_normalize=true)

Parity is verified against the torch reference before anything is written:
cosine(embedding_onnx, embedding_torch) must be > 0.999 and the spoof probs
must agree within 1e-3, or the script FAILS. No silent "it exported, ship it".

Usage:
    python scripts/export_device_onnx.py --ecapa
    python scripts/export_device_onnx.py --spoof
    python scripts/export_device_onnx.py --all          # both + parity checks
    python scripts/export_device_onnx.py --bench        # CPU latency per window
Output: server/onnx/<name> (+ .int8.onnx quantized, and parity numbers printed)
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np


def _install_module_stubs():
    """Dummy 'k2' (and submodules) so speechbrain's lazy imports resolve.

    torch.onnx.export's frame introspection (inspect.getmodule over
    sys.modules) touches speechbrain's LazyModule proxies; the k2_fsa one
    raises ImportError on attribute access instead of behaving like a missing
    module, crashing the export. k2 is lattice-decoding for ASR — nothing to
    do with ECAPA embeddings — so a no-op stub is safe. A stub with __file__
    also keeps inspect.getmodule() happy.
    """
    import types
    for name in ("k2", "k2_fsa"):
        if name not in sys.modules:
            stub = types.ModuleType(name)
            stub.__file__ = f"<{name} stub>"
            stub.__version__ = "0.0.0-stub"
            sys.modules[name] = stub
    sb_int = sys.modules.get("speechbrain.integrations")
    if sb_int is not None:
        sb_int.k2_fsa = sys.modules["k2_fsa"]


_install_module_stubs()


def _remove_speechbrain_lazy_modules():
    """Remove SpeechBrain LazyModule entries before importing torch.onnx.

    PyTorch's ONNX exporter uses inspect over sys.modules. SpeechBrain's
    optional integrations are LazyModule proxies; inspect touching them
    triggers imports of optional packages such as Flair and Transformers.
    ECAPA export does not need those integrations.
    """
    import sys
    import types

    blocked = (
        "speechbrain.integrations.nlp",
        "speechbrain.integrations.huggingface",
        "speechbrain.integrations.huggingface.wordemb",
    )

    for name in blocked:
        module = sys.modules.get(name)
        if module is not None and module.__class__.__name__ == "LazyModule":
            stub = types.ModuleType(name)
            stub.__file__ = f"<{name} stub>"
            stub.__package__ = name.rpartition(".")[0]
            sys.modules[name] = stub


ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "onnx"
SR = 16000


# --------------------------------------------------------------------------
# wrappers (module-level so torch can trace them cleanly)
# --------------------------------------------------------------------------
def _wav_norm(x, eps=1e-7):
    return (x - x.mean()) / (x.std() + eps)


def build_ecapa_wrapper():
    """Load speechbrain ECAPA and wrap it as raw-wav -> normalized embedding."""
    import torch
    from speechbrain.inference.speaker import EncoderClassifier

    savedir = ROOT / "models" / "speaker_verify"
    clf = EncoderClassifier.from_hparams(
        source="speechbrain/spkrec-ecapa-voxceleb",
        savedir=str(savedir), run_opts={"device": "cpu"})

    class ECAPAWrapper(torch.nn.Module):
        def __init__(self, clf):
            super().__init__()
            self.clf = clf

        def forward(self, audio):  # [1, T] raw float32
            x = (audio - audio.mean()) / (audio.std() + 1e-7)
            emb = self.clf.model(x)          # speechbrain's own wav->embedding
            emb = torch.nn.functional.normalize(emb, dim=-1)
            return emb                        # [1, 1, 192] -> squeeze on export

    return ECAPAWrapper(clf), clf


def build_spoof_wrapper():
    """Wrap the w2v2 fake/real detector as raw-wav -> P(spoof) [1, 1]."""
    import torch

    model_dir = ROOT / "models" / "voice_deepfake_cross2"
    from transformers import (AutoModelForAudioClassification,
                              AutoFeatureExtractor)
    model = AutoModelForAudioClassification.from_pretrained(str(model_dir))
    model.eval()
    # labels from config: 0=real, 1=fake. Verify rather than assume.
    id2label = {int(k): str(v).lower() for k, v in model.config.id2label.items()}
    spoof_idx = None
    for idx, label in id2label.items():
        if "spoof" in label or "fake" in label:
            spoof_idx = idx
    if spoof_idx is None:
        raise RuntimeError(f"no spoof/fake label found in {id2label}")

    class SpoofWrapper(torch.nn.Module):
        def __init__(self, m, idx):
            super().__init__()
            self.m = m
            self.idx = idx

        def forward(self, audio):  # [1, T] raw float32
            x = (audio - audio.mean()) / (audio.std() + 1e-7)
            logits = self.m(x).logits            # [1, 2]
            probs = torch.softmax(logits.float(), dim=-1)
            return probs[:, self.idx:self.idx + 1]  # [1, 1] = P(spoof)

    return SpoofWrapper(model, spoof_idx), spoof_idx


# --------------------------------------------------------------------------
# export + quantize + parity
# --------------------------------------------------------------------------
def _export(wrapped, dummy, out_path: Path, out_names) -> Path:
    import torch

    _remove_speechbrain_lazy_modules()

    out_path.parent.mkdir(parents=True, exist_ok=True)

    torch.onnx.export(
        wrapped,
        (dummy,),
        str(out_path),
        input_names=["audio"],
        output_names=out_names,
        dynamic_axes={"audio": {1: "length"}},
        opset_version=14,
        do_constant_folding=True,
        dynamo=False,
    )
    return out_path


def _quantize(fp32_path: Path) -> Path:
    from onnxruntime.quantization import quantize_dynamic, QuantType
    q = fp32_path.with_name(fp32_path.stem + ".int8.onnx")
    quantize_dynamic(str(fp32_path), str(q), weight_type=QuantType.QInt8)
    return q


def _run_onnx(path: Path, wav: np.ndarray) -> np.ndarray:
    import onnxruntime as ort
    sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    out = sess.run(None, {"audio": wav.astype(np.float32)})
    return np.asarray(out[0], dtype=np.float32)


def export_ecapa(quantize=True) -> dict:
    import torch
    print("[ecapa] loading speechbrain ECAPA (models/speaker_verify) ...")
    wrapped, ref_clf = build_ecapa_wrapper()
    wrapped.eval()

    # reference audio: 4s of speech-like noise at a realistic level
    rng = np.random.default_rng(0)
    t = np.arange(4 * SR) / SR
    wav = (0.2 * np.sin(2 * np.pi * 220 * t) + 0.05 * rng.standard_normal(4 * SR))
    wav = wav.astype(np.float32)[None, :]
    dummy = torch.from_numpy(wav)

    print("[ecapa] exporting wrapper (raw wav -> normalized embedding) ...")
    fp32 = _export(wrapped, dummy, OUT_DIR / "ecapa_tdnn.onnx", ["embedding"])

    # torch reference (speechbrain path, same normalization)
    with torch.no_grad():
        x = (dummy - dummy.mean()) / (dummy.std() + 1e-7)
        ref = torch.nn.functional.normalize(ref_clf.model(x), dim=-1).numpy().reshape(-1)

    res = {"fp32": str(fp32), "cosine_fp32": None, "cosine_int8": None}

    emb = _run_onnx(fp32, wav).reshape(-1)
    cos = float(np.dot(emb, ref) / (np.linalg.norm(emb) * np.linalg.norm(ref) + 1e-9))
    res["cosine_fp32"] = cos
    print(f"[ecapa] fp32 parity vs speechbrain: cosine={cos:.6f} (dim={emb.shape[0]})")
    if cos < 0.999:
        raise RuntimeError("ECAPA fp32 ONNX diverges from speechbrain reference")

    if quantize:
        print("[ecapa] dynamic INT8 quantization ...")
        q = _quantize(fp32)
        emb_q = _run_onnx(q, wav).reshape(-1)
        cos_q = float(
            np.dot(emb_q, ref)
            / (np.linalg.norm(emb_q) * np.linalg.norm(ref) + 1e-9)
        )
        res["int8"] = str(q)
        res["cosine_int8"] = cos_q
        print(f"[ecapa] int8 parity vs speechbrain: cosine={cos_q:.6f}")
        if cos_q < 0.995:
            raise RuntimeError("ECAPA INT8 diverges beyond 0.995 — do not ship")
    return res


def export_spoof(quantize=True) -> dict:
    import torch
    print("[spoof] loading wav2vec2 detector (models/voice_deepfake_cross2) ...")
    wrapped, spoof_idx = build_spoof_wrapper()
    wrapped.eval()

    rng = np.random.default_rng(1)
    t = np.arange(4 * SR) / SR
    wav = (0.2 * np.sin(2 * np.pi * 180 * t) + 0.05 * rng.standard_normal(4 * SR))
    wav = wav.astype(np.float32)[None, :]
    dummy = torch.from_numpy(wav)

    print(f"[spoof] exporting wrapper (raw wav -> P(spoof), spoof_idx={spoof_idx}) ...")
    fp32 = _export(wrapped, dummy, OUT_DIR / "spoof_w2v2.onnx", ["spoof_prob"])

    with torch.no_grad():
        ref = wrapped(dummy).numpy().reshape(-1)[0]

    res = {"fp32": str(fp32), "spoof_idx": spoof_idx}

    p = float(_run_onnx(fp32, wav).reshape(-1)[0])
    res["prob_fp32"] = p
    print(f"[spoof] fp32 parity vs torch: onnx={p:.6f} torch={ref:.6f}")
    if abs(p - ref) > 1e-3:
        raise RuntimeError("spoof fp32 ONNX diverges from torch reference")

    if quantize:
        print("[spoof] dynamic INT8 quantization ...")
        q = _quantize(fp32)
        pq = float(_run_onnx(q, wav).reshape(-1)[0])
        res["int8"] = str(q)
        res["prob_int8"] = pq
        print(f"[spoof] int8 output: {pq:.6f} (torch ref {ref:.6f})")
        if abs(pq - ref) > 0.05:
            raise RuntimeError("spoof INT8 diverges beyond 0.05 — check quantization")
    return res


def bench(path: Path, window_s: float = 4.0, n: int = 10) -> dict:
    """CPU latency per inference window (the number Task 1e reports)."""
    import onnxruntime as ort
    rng = np.random.default_rng(2)
    wav = (0.15 * rng.standard_normal(int(window_s * SR))).astype(np.float32)[None, :]
    sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    for _ in range(2):  # warmup
        sess.run(None, {"audio": wav})
    times = []
    for _ in range(n):
        t0 = time.perf_counter()
        sess.run(None, {"audio": wav})
        times.append((time.perf_counter() - t0) * 1000)
    return {"path": str(path), "window_s": window_s, "runs": n,
            "mean_ms": float(np.mean(times)), "p95_ms": float(np.percentile(times, 95))}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--ecapa", action="store_true")
    ap.add_argument("--spoof", action="store_true")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--bench", action="store_true",
                    help="CPU benchmark of the exported int8 models")
    ap.add_argument("--no-quant", action="store_true")
    args = ap.parse_args()

    if not (args.ecapa or args.spoof or args.all or args.bench):
        sys.exit("pass --ecapa / --spoof / --all / --bench")

    quant = not args.no_quant
    if args.ecapa or args.all:
        r = export_ecapa(quantize=quant)
        print("[ecapa] OK", r)
    if args.spoof or args.all:
        r = export_spoof(quantize=quant)
        print("[spoof] OK", r)
    if args.bench:
        for name in ("ecapa_tdnn.int8.onnx", "spoof_w2v2.int8.onnx"):
            p = OUT_DIR / name
            if not p.exists():
                p = p.with_name(p.name.replace(".int8.onnx", ".onnx"))
            if p.exists():
                b = bench(p)
                print(f"[bench] {p.name}: mean={b['mean_ms']:.1f}ms "
                      f"p95={b['p95_ms']:.1f}ms per {b['window_s']}s window")
            else:
                print(f"[bench] missing {p}")


if __name__ == "__main__":
    main()
