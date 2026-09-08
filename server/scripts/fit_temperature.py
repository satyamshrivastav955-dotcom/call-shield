"""Fit the softmax TEMPERATURE for the local AST spoof head (Phase 1.1 de-saturation).

WHY THIS EXISTS
The AST ASVspoof5 checkpoint in ``models/voice_deepfake`` is badly over-confident:
on real (bonafide) speech its softmax saturates to ~1.000, so a genuine caller reads
as a near-certain clone (ASVspoof5 dev: ~93% of bonafide flagged at thr 0.5,
eer_threshold ~= 1.0). Temperature scaling (Guo et al., "On Calibration of Modern
Neural Networks", ICML 2017) rescales the logits by a single scalar T>1 BEFORE the
softmax, which spreads that piled-up probability mass back toward the middle without
changing the model's RANKING (ROC-AUC is invariant to a monotonic rescale). T is a
per-checkpoint property that must be MEASURED on a labelled set — it is not a number
to guess. This script measures it and writes ``calibration.json`` next to the model;
``deepfake_voice.py`` loads that file and divides its logits by T at inference.

WHAT IT FITS ON (must match the runtime path to be valid)
``_analyze_local`` feeds the AST head the WHOLE context clip in ONE forward pass and
lets the feature extractor truncate to its native ~10.24s span. This script does the
SAME (one pass per utterance, no 4s windowing) so the logits we calibrate on are the
logits the server actually produces. (validate_asvspoof5.py deliberately windows +
mean-aggregates instead — that is a DIFFERENT, per-4s-window measurement path and is
not what the server AST head does, so do not fit on its scores.)

SCOPE — SERVER fp32 TORCH HEAD ONLY. This calibrates the fp32 transformers model the
server runs. The ON-DEVICE int8 ONNX head (SpoofEngine.kt) is a separate artifact with
its own logit distribution and gets its own calibration in the on-device work (Phase
1.2); do NOT copy this T onto the phone.

HONESTY (project HARD RULE)
* Writes the fitted T ONLY together with the measured NLL/ECE/saturation deltas that
  justify it, so the number is never divorced from its evidence.
* If the fit does not actually improve validation NLL it says so and (unless --force)
  refuses to write, rather than shipping a T that makes things worse.
* Default output is a real file on disk you can inspect; nothing here fabricates a
  score, and it CANNOT run in the Claude sandbox (needs torch + the model + a dataset).

RUN (on satya's host):
  cd server
  # A) FLAC + protocol (ASVspoof5 dev):
  python scripts/fit_temperature.py --model models/voice_deepfake \
      --flac-dir data/ASVspoof5/flac_D \
      --protocol data/ASVspoof5/ASVspoof5.dev.metadata.txt --limit 400
  # B) parquet:
  python scripts/fit_temperature.py --model models/voice_deepfake \
      --data data/ASVspoof5/dev.parquet --limit 400
  # inspect only, do not write:
  python scripts/fit_temperature.py --model models/voice_deepfake --data ... --dry-run

Then re-validate at the calibrated operating point and restart the server so
deepfake_voice.py picks up calibration.json.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np

# sibling script provides the (already battle-tested, schema-robust) dataset loaders
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from validate_asvspoof5 import (  # noqa: E402
    load_from_parquet, load_from_protocol, load_mono16k,
)

ROOT = Path(__file__).resolve().parent.parent          # -> server/


# ============================ calibration math (numpy) ============================
# Kept torch-free so the optimiser + metrics are unit-testable in a minimal venv
# (and in the sandbox) on synthetic logits, independent of the model itself.

def softmax_T(logits: np.ndarray, T: float) -> np.ndarray:
    """Row-wise softmax of ``logits / T`` (numerically stable). logits: [n, C]."""
    z = np.asarray(logits, dtype=np.float64) / float(T)
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


def nll(logits: np.ndarray, labels: np.ndarray, T: float,
        spoof_index: int) -> float:
    """Mean negative log-likelihood of the TRUE class under temperature T.

    labels are 1=spoof / 0=bonafide; the spoof class sits at ``spoof_index`` and
    the bonafide class is taken to be the other column of a 2-logit head.
    """
    p = softmax_T(logits, T)
    n, c = p.shape
    bona_index = 1 - spoof_index if c == 2 else (0 if spoof_index != 0 else c - 1)
    true_col = np.where(labels == 1, spoof_index, bona_index)
    p_true = np.clip(p[np.arange(n), true_col], 1e-12, 1.0)
    return float(-np.mean(np.log(p_true)))


def ece(p_spoof: np.ndarray, labels: np.ndarray, n_bins: int = 15) -> float:
    """Expected Calibration Error of the spoof probability vs the spoof label."""
    p_spoof = np.asarray(p_spoof, dtype=np.float64)
    labels = np.asarray(labels, dtype=np.float64)
    edges = np.linspace(0.0, 1.0, n_bins + 1)
    n = len(p_spoof)
    e = 0.0
    for i in range(n_bins):
        lo, hi = edges[i], edges[i + 1]
        m = (p_spoof > lo) & (p_spoof <= hi) if i > 0 else (p_spoof >= lo) & (p_spoof <= hi)
        if not m.any():
            continue
        conf = p_spoof[m].mean()
        acc = labels[m].mean()          # fraction actually spoof in this confidence bin
        e += (m.sum() / n) * abs(acc - conf)
    return float(e)


def fit_temperature(logits: np.ndarray, labels: np.ndarray, spoof_index: int,
                    lo: float = 0.25, hi: float = 50.0,
                    grid: int = 100, refine_iters: int = 60) -> float:
    """Minimise validation NLL over T with a coarse grid bracket + golden-section
    refine. 1-D and robust; no scipy. Returns the best T in [lo, hi].

    If the returned T sits right against ``lo`` or ``hi`` the true optimum is
    outside the searched range — ``main`` warns on that rather than silently
    reporting a clipped value.
    """
    ts = np.geomspace(lo, hi, grid)
    nlls = [nll(logits, labels, float(t), spoof_index) for t in ts]
    k = int(np.argmin(nlls))
    a = ts[max(0, k - 1)]
    b = ts[min(len(ts) - 1, k + 1)]
    gr = (np.sqrt(5.0) - 1.0) / 2.0
    c = b - gr * (b - a)
    d = a + gr * (b - a)
    fc = nll(logits, labels, float(c), spoof_index)
    fd = nll(logits, labels, float(d), spoof_index)
    for _ in range(refine_iters):
        if fc < fd:
            b, d, fd = d, c, fc
            c = b - gr * (b - a)
            fc = nll(logits, labels, float(c), spoof_index)
        else:
            a, c, fc = c, d, fd
            d = a + gr * (b - a)
            fd = nll(logits, labels, float(d), spoof_index)
        if abs(b - a) < 1e-4:
            break
    return float((a + b) / 2.0)


# ============================ logits from the real model ============================
def collect_logits(model_src: str, items, sample_rate_hint: int = 16000):
    """One forward pass per utterance (mirrors _analyze_local's AST path) -> logits.

    Returns (logits [n, C], labels [n], spoof_index). torch/transformers required.
    """
    import torch
    from transformers import ASTFeatureExtractor, ASTForAudioClassification
    model = ASTForAudioClassification.from_pretrained(model_src).eval()
    try:
        fe = ASTFeatureExtractor.from_pretrained(model_src)
    except Exception:
        from transformers import AutoFeatureExtractor
        fe = AutoFeatureExtractor.from_pretrained(model_src)
    sr = int(getattr(fe, "sampling_rate", sample_rate_hint))
    id2 = {int(k): v for k, v in model.config.id2label.items()}
    spoof_index = next((k for k, v in id2.items()
                        if str(v).lower().startswith("spoof")), 1)
    print(f"[model] AST labels={id2} spoof_index={spoof_index} sr={sr}")

    logits_rows, labels = [], []
    n = len(items)
    for i, (src, lab) in enumerate(items):
        try:
            pcm = src if isinstance(src, np.ndarray) else load_mono16k(src)
            with torch.no_grad():
                inp = fe(pcm, sampling_rate=sr, return_tensors="pt")
                out = model(**inp).logits
            logits_rows.append(out.float().cpu().numpy().reshape(-1))
            labels.append(int(lab))
        except Exception as e:
            print(f"[warn] skip item {i}: {e}")
        if (i + 1) % 50 == 0 or i + 1 == n:
            print(f"[logits] {i + 1}/{n}", flush=True)
    if not logits_rows:
        raise SystemExit("[FAIL] no logits collected — check --model / dataset.")
    width = max(len(r) for r in logits_rows)
    if any(len(r) != width for r in logits_rows):
        raise SystemExit("[FAIL] ragged logit widths — head is not fixed-class.")
    return np.vstack(logits_rows), np.asarray(labels, int), spoof_index


def summarise(logits, labels, T, spoof_index) -> dict:
    p1 = softmax_T(logits, 1.0)
    pT = softmax_T(logits, T)
    ps1 = p1[:, spoof_index] if p1.shape[1] > spoof_index else p1[:, -1]
    psT = pT[:, spoof_index] if pT.shape[1] > spoof_index else pT[:, -1]
    bona = labels == 0
    return {
        "nll_before": round(nll(logits, labels, 1.0, spoof_index), 4),
        "nll_after": round(nll(logits, labels, T, spoof_index), 4),
        "ece_before": round(ece(ps1, labels), 4),
        "ece_after": round(ece(psT, labels), 4),
        "bonafide_mean_spoof_prob_before": round(float(ps1[bona].mean()), 4) if bona.any() else None,
        "bonafide_mean_spoof_prob_after": round(float(psT[bona].mean()), 4) if bona.any() else None,
        "bonafide_saturated_frac_before": round(float((ps1[bona] > 0.999).mean()), 4) if bona.any() else None,
        "bonafide_saturated_frac_after": round(float((psT[bona] > 0.999).mean()), 4) if bona.any() else None,
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=str(ROOT / "models" / "voice_deepfake"),
                    help="HF dir of the AST checkpoint to calibrate")
    ap.add_argument("--data", help="parquet file or dir")
    ap.add_argument("--flac-dir", help="dir of .flac files")
    ap.add_argument("--protocol", help="ASVspoof metadata/protocol txt")
    ap.add_argument("--audio-col")
    ap.add_argument("--label-col")
    ap.add_argument("--limit", type=int, default=400,
                    help="cap #utterances used for the fit (0=all)")
    ap.add_argument("--out", help="calibration.json path (default: <model>/calibration.json)")
    ap.add_argument("--dry-run", action="store_true", help="print, do not write")
    ap.add_argument("--force", action="store_true",
                    help="write even if the fit does not improve validation NLL")
    args = ap.parse_args()

    if args.flac_dir and args.protocol:
        items = load_from_protocol(args.flac_dir, args.protocol, args.limit)
    elif args.data:
        items = load_from_parquet(args.data, args.audio_col, args.label_col, args.limit)
    else:
        raise SystemExit("Provide either --flac-dir + --protocol, or --data <parquet>.")
    n_spoof = sum(1 for _, l in items if l == 1)
    n_bona = sum(1 for _, l in items if l == 0)
    print(f"[data] {len(items)} utterances (spoof={n_spoof}, bonafide={n_bona})")
    if n_bona == 0:
        raise SystemExit("[FAIL] no bonafide utterances — temperature would fit to "
                         "noise. Provide a set with both classes.")

    logits, labels, spoof_index = collect_logits(args.model, items)
    T = fit_temperature(logits, labels, spoof_index)
    if T <= 0.26 or T >= 49.0:
        print(f"[warn] fitted T={T:.3f} is against the search bound — the true "
              f"optimum may be outside [0.25, 50]. Treat this value with caution "
              f"and inspect the NLL/ECE deltas below before trusting it.")
    stats = summarise(logits, labels, T, spoof_index)

    improved = stats["nll_after"] <= stats["nll_before"] + 1e-6
    report = {
        "temperature": round(T, 4),
        "model": args.model,
        "fit": {
            "n": int(len(labels)), "n_spoof": int((labels == 1).sum()),
            "n_bonafide": int((labels == 0).sum()),
            "spoof_index": int(spoof_index),
            "method": "temperature-scaling (NLL, grid+golden-section)",
            **stats,
            "improved_nll": bool(improved),
        },
        "scope": "server fp32 AST head (deepfake_voice._analyze_local); NOT the on-device int8 ONNX",
        "note": "Loaded by deepfake_voice._resolve_temperature; overrides voice_ast_temperature.",
    }

    print("\n================ AST TEMPERATURE FIT ================")
    print(json.dumps(report, indent=2))
    print("=====================================================")
    print(f"[fit] T={T:.4f}  NLL {stats['nll_before']}->{stats['nll_after']}  "
          f"ECE {stats['ece_before']}->{stats['ece_after']}  "
          f"bonafide sat {stats['bonafide_saturated_frac_before']}->"
          f"{stats['bonafide_saturated_frac_after']}")

    if not improved and not args.force:
        raise SystemExit("[REFUSE] fit did NOT improve validation NLL — not writing "
                         "calibration.json. (Model may already be calibrated, or the "
                         "set is too small/one-sided.) Re-run with --force to override.")

    out = Path(args.out) if args.out else Path(args.model) / "calibration.json"
    if args.dry_run:
        print(f"[dry-run] would write {out}")
        return
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2))
    print(f"[fit] wrote {out} — restart the server so deepfake_voice.py loads it.")


if __name__ == "__main__":
    main()
