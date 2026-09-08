"""Fit the ON-DEVICE softmax TEMPERATURE for the int8 AST spoof head (Phase 1.2).

WHY THIS EXISTS (and why it is SEPARATE from fit_temperature.py)
fit_temperature.py calibrates the SERVER's fp32 torch head on the whole-clip single
forward pass. The PHONE runs a different artifact — spoof_ast.int8.onnx — over fixed
4s windows, and int8 quantization shifts the logit distribution, so its temperature
is a DIFFERENT number that must be fit on the int8 model's OWN outputs, on the SAME
4s-window path the device uses. Copying the server T onto the phone would be a
fabricated calibration (project HARD RULE). This script measures the on-device T and
writes ``<onnx_dir>/spoof_ast.calibration.json``; ModelManager.temperatureFor() loads
it and SpoofEngine.kt divides the spoof-vs-bonafide logit gap by T before the sigmoid.

WHAT IT FITS ON (mirrors the device exactly)
Each utterance -> consecutive 4s @16k windows (to_windows, the fixed export input);
each window is scored by the int8 ONNX and inherits its utterance's label — because
the device scores each 4s window independently and feeds THAT window's score straight
to FusionEngine (it does not aggregate windows). So we calibrate per-window, as shipped.

TWO EXPORT CONTRACTS (auto-detected by the ONNX output name)
  * "spoof_logit"  (export_ast_spoof_onnx.py --emit logit): the [1,1] value IS the
    spoof-vs-bonafide LOGIT GAP. Used directly -> a full-range, faithful fit. RECOMMENDED.
  * "spoof_prob"   (the shipped default): the [1,1] value is the post-softmax spoof
    prob. The gap is recovered as logit(clamp(p)). HONEST LIMITATION: if the int8
    post-softmax output already saturated to ~1.0 for BOTH classes, the recovered gaps
    pile up at the clamp bound and carry little class information — the fit is then
    degenerate. This script MEASURES the clamped fraction + gap spread and warns
    LOUDLY when that happens, recommending a `--emit logit` re-export. It does not
    pretend a clamp-limited fit is a clean calibration.

HONESTY (project HARD RULE)
* Writes the fitted T only WITH the measured NLL/ECE/saturation deltas that justify it.
* Refuses to write if the fit does not improve validation NLL (unless --force).
* Refuses (unless --force) when the recovered gaps are degenerate (prob-emit clamp
  pile-up), because a T fit on no information is not a calibration.
* Cannot run in the Claude sandbox (needs onnxruntime + the int8 model + a dataset).

RUN (on satya's host):
  cd server
  # recommended: re-export the logit contract first, then fit on it
  python scripts/export_ast_spoof_onnx.py --emit logit --out-dir onnx
  python scripts/fit_temperature_ondevice.py --model onnx/spoof_ast.int8.onnx \
      --flac-dir data/ASVspoof5/flac_D --protocol data/ASVspoof5/ASVspoof5.dev.metadata.txt --limit 400
  # or fit on the currently-shipped prob model (may warn about clamp-limit):
  python scripts/fit_temperature_ondevice.py --model onnx/spoof_ast.int8.onnx --data dev.parquet
  # inspect only:
  python scripts/fit_temperature_ondevice.py --model onnx/spoof_ast.int8.onnx --data ... --dry-run

After it writes spoof_ast.calibration.json, push it next to the model on device
(adb push onnx/spoof_ast.calibration.json /data/local/tmp/  OR into filesDir/onnx),
then RE-RUN the device 5-scenario harness (Phase 0.D): temperature de-saturation
shifts the score distribution, so the fusion soft/hard bands (0.90/0.97/0.995) must be
re-confirmed against real casual-vs-scam calls at the new operating point.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fit_temperature import ece, fit_temperature, nll, softmax_T  # noqa: E402
from validate_asvspoof5 import (  # noqa: E402
    OnnxSpoof, load_from_parquet, load_from_protocol, to_windows, load_mono16k,
    _resample_linear,
)

ROOT = Path(__file__).resolve().parent.parent          # -> server/
SAMPLES = 64000                                         # 4 s @ 16 kHz (device window)
CLAMP_EPS = 1e-6                                         # matches SpoofEngine.PROB_EPS
GAP_CLAMP = float(np.log((1.0 - CLAMP_EPS) / CLAMP_EPS))  # logit(1-eps) ~= 13.8155


def _run_raw(backend: OnnxSpoof, window: np.ndarray) -> float:
    """Raw [1,1] value from the int8 ONNX (NOT sigmoided) — a prob or a logit gap."""
    out = backend.s.run(None, {backend.iname: window.reshape(1, -1).astype(np.float32)})[0]
    arr = np.asarray(out).reshape(-1)
    if arr.size != 1:
        raise SystemExit(f"[FAIL] model output has {arr.size} elems; expected [1,1].")
    return float(arr[0])


def collect_gaps(backend: OnnxSpoof, items, samples: int = SAMPLES):
    """Per-window spoof-vs-bonafide LOGIT GAPS + labels, on the device 4s path.

    For a logit-emit ONNX the raw value IS the gap; for a prob-emit ONNX the gap is
    recovered as logit(clamp(p)) (clamp-limited where the post-softmax already
    saturated — tracked via `clamped`).
    """
    gaps, labels = [], []
    clamped = 0
    n = len(items)
    for i, (src, lab) in enumerate(items):
        try:
            pcm = _resample_linear(src, 16000, 16000) if isinstance(src, np.ndarray) else load_mono16k(src)
            for w in to_windows(pcm, samples):
                v = _run_raw(backend, w)
                if backend.is_logit:
                    gap = v
                else:                                   # v is a post-softmax prob
                    p = min(max(v, CLAMP_EPS), 1.0 - CLAMP_EPS)
                    if v <= CLAMP_EPS or v >= 1.0 - CLAMP_EPS:
                        clamped += 1
                    gap = float(np.log(p / (1.0 - p)))
                gaps.append(gap)
                labels.append(int(lab))
        except Exception as e:
            print(f"[warn] skip item {i}: {e}")
        if (i + 1) % 100 == 0 or i + 1 == n:
            print(f"[fit] {i + 1}/{n} utterances -> {len(gaps)} windows", flush=True)
    if not gaps:
        raise SystemExit("[FAIL] no windows scored — check --model / dataset.")
    return np.asarray(gaps, float), np.asarray(labels, int), clamped


def summarise(gaps, labels, T) -> dict:
    """2-class logits [0, gap]; report NLL/ECE/bonafide-saturation before vs after T."""
    logits = np.stack([np.zeros_like(gaps), gaps], axis=1)
    ps1 = softmax_T(logits, 1.0)[:, 1]
    psT = softmax_T(logits, T)[:, 1]
    bona = labels == 0
    return {
        "nll_before": round(nll(logits, labels, 1.0, 1), 4),
        "nll_after": round(nll(logits, labels, T, 1), 4),
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
    ap.add_argument("--model", default=str(ROOT / "onnx" / "spoof_ast.int8.onnx"),
                    help="int8 ONNX to calibrate (the artifact that ships to the phone)")
    ap.add_argument("--data", help="parquet file or dir")
    ap.add_argument("--flac-dir", help="dir of .flac files")
    ap.add_argument("--protocol", help="ASVspoof metadata/protocol txt")
    ap.add_argument("--audio-col")
    ap.add_argument("--label-col")
    ap.add_argument("--limit", type=int, default=400, help="cap #utterances (0=all)")
    ap.add_argument("--samples", type=int, default=SAMPLES, help="window length (4s@16k=64000)")
    ap.add_argument("--out", help="calibration.json path (default: <model_dir>/spoof_ast.calibration.json)")
    ap.add_argument("--dry-run", action="store_true", help="print, do not write")
    ap.add_argument("--force", action="store_true",
                    help="write even if the fit does not improve NLL or the gaps are degenerate")
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
    if n_bona == 0 or n_spoof == 0:
        raise SystemExit("[FAIL] need BOTH classes to fit a temperature; got "
                         f"spoof={n_spoof}, bonafide={n_bona}.")

    backend = OnnxSpoof(args.model)
    gaps, labels, clamped = collect_gaps(backend, items, args.samples)
    # 2-class logits [0, gap]: softmax_T(...)[:,1] == sigmoid(gap/T) (the device path).
    two_col = np.stack([np.zeros_like(gaps), gaps], axis=1)
    T = fit_temperature(two_col, labels, spoof_index=1)
    if T <= 0.26 or T >= 49.0:
        print(f"[warn] fitted T={T:.3f} is against the search bound — inspect the deltas below.")
    stats = summarise(gaps, labels, T)

    # Degeneracy check (the honest prob-emit caveat, measured not assumed).
    clamp_frac = round(clamped / max(1, len(gaps)), 4)
    gap_std = float(np.std(gaps))
    degenerate = (not backend.is_logit) and (clamp_frac > 0.5 or gap_std < 0.5)
    improved = stats["nll_after"] <= stats["nll_before"] + 1e-6

    report = {
        "temperature": round(T, 4),
        "model": os.path.basename(args.model),
        "contract": "spoof_logit" if backend.is_logit else "spoof_prob",
        "fit": {
            "n_windows": int(len(gaps)),
            "n_spoof": int((labels == 1).sum()), "n_bonafide": int((labels == 0).sum()),
            "method": "on-device temperature-scaling (per-4s-window, NLL, grid+golden-section)",
            "gap_source": "raw logit gap" if backend.is_logit else "logit(clamp(post-softmax prob))",
            "clamped_frac": clamp_frac, "gap_std": round(gap_std, 4),
            **stats,
            "improved_nll": bool(improved),
            "degenerate_prob_fit": bool(degenerate),
        },
        "scope": "ON-DEVICE int8 ONNX (SpoofEngine.kt), 4s-window path; NOT the server fp32 head",
        "note": ("Loaded by ModelManager.temperatureFor(); SpoofEngine applies sigmoid(gap/T). "
                 "T=1.0 is identity. Re-run the device 5-scenario harness after applying, since "
                 "de-saturation shifts the fusion band operating point."),
    }

    print("\n============ ON-DEVICE AST TEMPERATURE FIT ============")
    print(json.dumps(report, indent=2))
    print("=======================================================")
    print(f"[fit] T={T:.4f}  NLL {stats['nll_before']}->{stats['nll_after']}  "
          f"ECE {stats['ece_before']}->{stats['ece_after']}  "
          f"bonafide sat {stats['bonafide_saturated_frac_before']}->"
          f"{stats['bonafide_saturated_frac_after']}  "
          f"[contract={report['contract']} clamped={clamp_frac} gap_std={gap_std:.3f}]")

    if degenerate and not args.force:
        raise SystemExit(
            "[REFUSE] the recovered logit gaps are degenerate (clamp pile-up / near-zero "
            f"spread: clamped_frac={clamp_frac}, gap_std={gap_std:.3f}). The int8 post-softmax "
            "output already saturated, so this prob-emit fit carries little class information "
            "and would not be an honest calibration. RE-EXPORT with `--emit logit` and fit on "
            "that (full-range gaps), or re-run with --force if you understand the limitation.")
    if not improved and not args.force:
        raise SystemExit("[REFUSE] fit did NOT improve validation NLL — not writing. "
                         "(Model may already be calibrated, or the set is too small/one-sided.) "
                         "Re-run with --force to override.")

    out = Path(args.out) if args.out else Path(args.model).parent / "spoof_ast.calibration.json"
    if args.dry_run:
        print(f"[dry-run] would write {out}")
        return
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2))
    print(f"[fit] wrote {out} — push it beside spoof_ast.int8.onnx on device, then re-run Phase 0.D.")


if __name__ == "__main__":
    main()
