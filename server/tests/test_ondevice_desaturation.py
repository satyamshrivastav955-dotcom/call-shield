"""Unit tests for the ON-DEVICE AST temperature de-saturation math (Phase 1.2).

These lock the arithmetic that SpoofEngine.kt::desaturate and
fit_temperature_ondevice.py implement, on synthetic numbers — no ONNX, no model,
no dataset — so the logic is verified in the sandbox even though the actual
on-device T can only be FIT on the host (needs the int8 model + ASVspoof5). Per
the project HARD RULE this is evidence for the MATH and the two export contracts'
equivalence, NOT a claim about the shipped model's calibrated accuracy (host gate).

`kt_desaturate` below is a line-for-line mirror of SpoofEngine.desaturate; if you
change one, change the other.

Run:  python server/tests/test_ondevice_desaturation.py     (prints PASS/FAIL)
  or:  pytest server/tests/test_ondevice_desaturation.py
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "scripts"))
from fit_temperature import fit_temperature, nll, softmax_T  # noqa: E402
from fit_temperature_ondevice import summarise, GAP_CLAMP, CLAMP_EPS  # noqa: E402


def sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


def kt_desaturate(raw: float, T: float, emits_logit: bool, eps: float = 1e-6) -> float:
    """EXACT mirror of SpoofEngine.desaturate (OrtEngines.kt). raw is the [1,1]
    ONNX value: a logit gap when emits_logit, else a post-softmax spoof prob."""
    if emits_logit:
        return sigmoid(raw / T)                      # raw is the logit gap
    if T == 1.0:
        return raw                                   # prob pass-through, no precision loss
    p = min(max(raw, eps), 1.0 - eps)                # recover gap from post-softmax prob
    z = math.log(p / (1.0 - p))                      # logit(p) = spoof-vs-bonafide gap
    return sigmoid(z / T)


# --------------------------- identity (T=1) is a true no-op ---------------------------
def test_T1_prob_is_exact_passthrough():
    """Uncalibrated prob-emit device returns the model's raw prob byte-for-byte."""
    for raw in (0.0, 0.13, 0.5, 0.90, 0.9997, 1.0):
        assert kt_desaturate(raw, 1.0, emits_logit=False) == raw


def test_T1_logit_equals_original_prob():
    """Uncalibrated logit-emit device == the shipped prob device: sigmoid(gap) is
    exactly the 2-class softmax spoof prob, so re-exporting --emit logit + T=1 is a
    drop-in identity (no behavior change until a real T is applied)."""
    for gap in (-8.0, -1.0, 0.0, 2.5, 8.0, 13.8):
        p_original = sigmoid(gap)                    # what the prob-emit ONNX would output
        assert abs(kt_desaturate(gap, 1.0, emits_logit=True) - p_original) < 1e-9


# --------------------------- T>1 de-saturates, monotonically ---------------------------
def test_prob_contract_desaturates_and_is_monotone():
    raw = 0.9997                                     # saturated casual-call-style score
    p1 = kt_desaturate(raw, 1.0, emits_logit=False)
    p3 = kt_desaturate(raw, 3.0, emits_logit=False)
    p8 = kt_desaturate(raw, 8.0, emits_logit=False)
    assert p1 == raw
    assert p1 > p3 > p8, f"not monotone in T: {p1} {p3} {p8}"
    assert abs(kt_desaturate(raw, 1e6, emits_logit=False) - 0.5) < 1e-2, "T->inf should approach 0.5"


def test_logit_contract_desaturates_and_is_monotone():
    gap = 8.0                                        # -> prob ~0.99966
    p1 = kt_desaturate(gap, 1.0, emits_logit=True)
    p3 = kt_desaturate(gap, 3.0, emits_logit=True)
    p8 = kt_desaturate(gap, 8.0, emits_logit=True)
    assert p1 > p3 > p8
    assert abs(p8 - 0.5) < abs(p1 - 0.5)


# --------------- the two contracts agree away from the saturation clamp ---------------
def test_prob_and_logit_contracts_converge_unsaturated():
    """Where the post-softmax prob has NOT saturated, recovering the gap from the
    prob (shipped contract) gives the same de-saturated result as reading the raw
    gap (logit export). This is why the prob path is exact except at the boundary."""
    for gap in (-3.0, -0.5, 0.5, 2.0, 4.0):          # |gap|<~5 => prob within (0.007, 0.993)
        p = sigmoid(gap)                             # the prob-emit ONNX output
        for T in (1.5, 2.0, 3.5, 6.0):
            via_prob = kt_desaturate(p, T, emits_logit=False)
            via_logit = kt_desaturate(gap, T, emits_logit=True)
            assert abs(via_prob - via_logit) < 1e-5, f"diverge gap={gap} T={T}: {via_prob} vs {via_logit}"


def test_logit_export_recovers_more_range_than_saturated_prob():
    """The concrete reason --emit logit matters: for a LARGE true gap the prob-emit
    output saturates and its recovered gap is clamped (bounded by GAP_CLAMP), so the
    logit export de-saturates strictly further. Honest limitation, made explicit."""
    big_gap = 25.0                                   # sigmoid(25) rounds to ~1.0 in fp
    T = 4.0
    p_saturated = min(sigmoid(big_gap), 1.0 - CLAMP_EPS)  # what fp32/int8 would store, clamped
    via_prob = kt_desaturate(p_saturated, T, emits_logit=False)
    via_logit = kt_desaturate(big_gap, T, emits_logit=True)
    assert via_logit > via_prob, "logit path should preserve more of the gap"
    # the prob path's recovered gap is capped at GAP_CLAMP
    assert abs(kt_desaturate(1.0, T, emits_logit=False) - sigmoid(GAP_CLAMP / T)) < 1e-6


def test_ranking_preserved_both_contracts():
    """Temperature never reorders scores (ROC-AUC invariant) — for both contracts."""
    gaps = np.array([-4.0, -1.0, 0.0, 0.7, 2.0, 5.0, 9.0])
    probs = 1.0 / (1.0 + np.exp(-gaps))
    for T in (1.5, 3.0, 7.0):
        out_logit = [kt_desaturate(float(g), T, emits_logit=True) for g in gaps]
        out_prob = [kt_desaturate(float(p), T, emits_logit=False) for p in probs]
        assert out_logit == sorted(out_logit), "logit-contract ranking changed"
        assert out_prob == sorted(out_prob), "prob-contract ranking changed"


# --------------------------- fit summarise (server-mirror on gaps) ---------------------------
def _biased_saturated_gaps(n=4000, seed=7):
    """Real-AST-like: bonafide gap ~N(6,1), spoof ~N(8,1) — both saturated-high."""
    rng = np.random.default_rng(seed)
    half = n // 2
    labels = np.array([0] * half + [1] * (n - half))
    gaps = np.where(labels == 0, rng.normal(6.0, 1.0, size=n), rng.normal(8.0, 1.0, size=n))
    return gaps, labels


def test_ondevice_summarise_desaturates_bonafide():
    """fit_temperature_ondevice.summarise reports a real bonafide de-saturation on a
    biased-saturated head, with ranking preserved (same honest caveat as the server:
    T calibrates confidence, the fusion gate removes the FP)."""
    gaps, labels = _biased_saturated_gaps()
    two_col = np.stack([np.zeros_like(gaps), gaps], axis=1)
    T = fit_temperature(two_col, labels, spoof_index=1)
    assert T > 1.5, f"a saturated head should fit T>1, got {T:.3f}"
    stats = summarise(gaps, labels, T)
    assert stats["nll_after"] <= stats["nll_before"], "NLL did not improve"
    assert stats["bonafide_mean_spoof_prob_after"] < stats["bonafide_mean_spoof_prob_before"], \
        "bonafide mean spoof-prob did not drop"
    assert stats["bonafide_saturated_frac_after"] <= stats["bonafide_saturated_frac_before"]


def test_degenerate_gap_detection_matches_fit_guard():
    """The prob-emit clamp pile-up the fitter refuses to write: when the recovered
    gaps are all pinned at GAP_CLAMP, their spread collapses (the degeneracy signal)."""
    clamped_gaps = np.full(1000, GAP_CLAMP)          # every window saturated to the clamp
    assert float(np.std(clamped_gaps)) < 0.5, "clamp pile-up should have ~0 spread"
    healthy_gaps, _ = _biased_saturated_gaps()
    assert float(np.std(healthy_gaps)) >= 0.5, "a real fit should have real gap spread"


def _run():
    tests = [v for k, v in sorted(globals().items())
             if k.startswith("test_") and callable(v)]
    failed = 0
    for t in tests:
        try:
            t()
            print(f"PASS {t.__name__}")
        except AssertionError as e:
            failed += 1
            print(f"FAIL {t.__name__}: {e}")
        except Exception as e:  # noqa: BLE001
            failed += 1
            print(f"ERROR {t.__name__}: {type(e).__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return failed


if __name__ == "__main__":
    sys.exit(1 if _run() else 0)
