"""Unit tests for the AST temperature-scaling calibration math (Phase 1.1).

These lock the DE-SATURATION mechanism with real, runnable numbers on synthetic
logits — no torch, no model, no dataset — so the calibration logic is verified in
the sandbox even though the actual T can only be FIT on the host (fit_temperature.py
needs the real model + ASVspoof5). Per the project HARD RULE this is evidence for the
MATH, not a claim about the shipped model's calibrated accuracy (that is a host gate).

Run:  python server/tests/test_temperature_calibration.py     (prints PASS/FAIL)
  or:  pytest server/tests/test_temperature_calibration.py
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np

# import the numpy calibration fns straight from the fitting script
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                "..", "scripts"))
from fit_temperature import ece, fit_temperature, nll, softmax_T  # noqa: E402


def test_softmax_T_desaturates_binary():
    """T>1 pulls an over-confident spoof prob back toward 0.5; monotone in T."""
    logits = np.array([[0.0, 8.0]])          # spoof logit dominates -> ~0.9997
    p1 = softmax_T(logits, 1.0)[0, 1]
    p2 = softmax_T(logits, 2.0)[0, 1]
    p8 = softmax_T(logits, 8.0)[0, 1]
    assert p1 > 0.999, f"expected saturation at T=1, got {p1}"
    assert p1 > p2 > p8, f"not monotone: {p1} {p2} {p8}"
    assert abs(softmax_T(logits, 1e6)[0, 1] - 0.5) < 1e-3, "T->inf should give ~0.5"


def test_softmax_T_preserves_ranking():
    """Temperature never flips the argmax — ROC-AUC is invariant (Guo et al.)."""
    rng = np.random.default_rng(0)
    logits = rng.normal(size=(200, 2)) * 5.0
    for T in (0.5, 1.0, 3.0, 10.0):
        a = softmax_T(logits, 1.0).argmax(axis=1)
        b = softmax_T(logits, T).argmax(axis=1)
        assert np.array_equal(a, b), f"argmax changed at T={T}"
    # spoof-prob ordering (the thing ROC-AUC ranks on) is also preserved
    order1 = np.argsort(softmax_T(logits, 1.0)[:, 1])
    orderT = np.argsort(softmax_T(logits, 4.0)[:, 1])
    assert np.array_equal(order1, orderT), "spoof-prob ranking changed"


def test_nll_sane():
    """Confident-correct -> ~0 NLL; confident-wrong -> large NLL."""
    logits = np.array([[0.0, 12.0], [0.0, 12.0]])   # both scream 'spoof'
    correct = nll(logits, np.array([1, 1]), 1.0, spoof_index=1)
    wrong = nll(logits, np.array([0, 0]), 1.0, spoof_index=1)
    assert correct < 1e-4, f"confident-correct NLL should be ~0, got {correct}"
    assert wrong > 5.0, f"confident-wrong NLL should be large, got {wrong}"


def _make_oversharpened(n=4000, k=3.0, seed=0):
    """Calibrated latent logits scaled by k (over-confident). Optimal T is ~k."""
    rng = np.random.default_rng(seed)
    d = rng.normal(0.0, 2.0, size=n)               # true logit gap spoof-bona
    p_true = 1.0 / (1.0 + np.exp(-d))              # true spoof prob = sigmoid(d)
    labels = (rng.uniform(size=n) < p_true).astype(int)
    logits = np.stack([np.zeros(n), k * d], axis=1)  # fed model is k-sharpened
    return logits, labels, k


def _make_biased_saturated(n=4000, seed=1):
    """Mirror the REAL AST pathology: a positively-BIASED, saturated head where
    BOTH classes score near 1.0 spoof at T=1 (bonafide gap ~6, spoof gap ~8), with
    only weak ranking separation (~AUC 0.7, like the shipped model). Labels are the
    ground truth, NOT sampled from the (miscalibrated) logits."""
    rng = np.random.default_rng(seed)
    half = n // 2
    labels = np.array([0] * half + [1] * (n - half))
    gap = np.where(labels == 0,
                   rng.normal(6.0, 1.0, size=n),    # bonafide: saturated-high (FP)
                   rng.normal(8.0, 1.0, size=n))    # spoof: saturated-higher
    logits = np.stack([np.zeros(n), gap], axis=1)
    return logits, labels


def test_fit_recovers_known_temperature():
    """The fitter should recover the injected over-sharpening factor (~k=3)."""
    logits, labels, k = _make_oversharpened()
    T = fit_temperature(logits, labels, spoof_index=1)
    assert 0.8 * k <= T <= 1.2 * k, f"expected T~{k}, got {T:.3f}"
    nll_before = nll(logits, labels, 1.0, 1)
    nll_after = nll(logits, labels, T, 1)
    assert nll_after < nll_before, f"NLL did not improve: {nll_before}->{nll_after}"


def test_ece_improves_after_fit():
    """Calibration error drops once the fitted temperature is applied."""
    logits, labels, _ = _make_oversharpened()
    T = fit_temperature(logits, labels, spoof_index=1)
    ece_before = ece(softmax_T(logits, 1.0)[:, 1], labels)
    ece_after = ece(softmax_T(logits, T)[:, 1], labels)
    assert ece_after < ece_before, f"ECE did not improve: {ece_before}->{ece_after}"


def test_bonafide_desaturation_on_biased_head():
    """The concrete failure this fixes: on a biased-saturated head (real AST), the
    mean spoof-prob on TRUE bonafide clips and the saturated (>0.999) fraction both
    FALL after calibration.

    HONEST CAVEAT locked by assertion below: temperature scaling calibrates
    CONFIDENCE and preserves RANKING (ROC-AUC unchanged), so it de-saturates the
    false positives but does NOT remove the class bias — a biased bonafide can stay
    just above 0.5. Flipping it below the decision line is the FUSION layer's job
    (the 0.97 hard band + corroboration gate), NOT temperature scaling's."""
    logits, labels = _make_biased_saturated()
    T = fit_temperature(logits, labels, spoof_index=1)
    assert T > 1.5, f"a saturated head should fit T>1, got {T:.3f}"
    bona = labels == 0
    ps1 = softmax_T(logits, 1.0)[:, 1][bona]
    psT = softmax_T(logits, T)[:, 1][bona]
    assert psT.mean() < ps1.mean(), \
        f"bonafide mean spoof-prob did not drop: {ps1.mean():.4f}->{psT.mean():.4f}"
    assert (psT > 0.999).mean() < (ps1 > 0.999).mean(), \
        "saturated (>0.999) bonafide fraction did not drop"
    # ranking invariance holds even here (this is why AUC is unchanged)
    assert np.array_equal(np.argsort(softmax_T(logits, 1.0)[:, 1]),
                          np.argsort(softmax_T(logits, T)[:, 1])), "ranking changed"


def _is_ai_voice(label, prob):
    """Mirror of deepfake_voice._combine's is_ai_voice rule (locks the semantics)."""
    if prob is None:
        return None
    return label == "spoof"


def test_is_ai_voice_mapping():
    assert _is_ai_voice("spoof", 0.91) is True
    assert _is_ai_voice("bonafide", 0.10) is False
    assert _is_ai_voice("uncertain", 0.55) is False   # not asserting a clone
    assert _is_ai_voice(None, None) is None            # unknown, never a fake False


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
