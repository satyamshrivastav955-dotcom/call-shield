"""Self-check for the AASIST-L model + ONNX round-trip (no GPU, no data).

Run:  python scripts/check_aasist.py

Checks (one runnable check per non-trivial piece, per repo convention):
  1. forward: [B, 64000] -> logits [B, 2], batch + dynamic length
  2. param budget: < 200K (the "1000x smaller than AST" claim)
  3. learnability: 3-epoch overfit on synthetic tone-vs-noise — loss must drop
     (proves the GAT graph actually trains, not just runs)
  4. ONNX round-trip: torch vs onnxruntime logits agree < 1e-3 (proves the
     phone's SpoofEngine.score() parse will work)
  5. EER helper sanity: perfect scores -> EER 0.0
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import torch  # noqa: E402

from antai.inference.voice._aasist import AASISTL, count_params  # noqa: E402

WINDOW = 4 * 16000


def check_forward() -> None:
    m = AASISTL()
    out = m(torch.randn(1, WINDOW))
    assert out.logits.shape == (1, 2), out.logits.shape
    out2 = m(torch.randn(3, WINDOW))
    assert out2.logits.shape == (3, 2), out2.logits.shape
    # dynamic (non-4s) length
    out3 = m(torch.randn(1, WINDOW + 1600))
    assert out3.logits.shape == (1, 2), out3.logits.shape
    print(f"1. forward OK ({count_params(m)} params)")


def check_params() -> None:
    n = count_params(AASISTL())
    assert n < 200_000, f"param budget blown: {n}"
    print(f"2. param budget OK ({n} < 200K)")


def _synthetic(batch: int, spoof: bool) -> torch.Tensor:
    """Tone (spoof) vs noise (bonafide) — trivially separable synthetic classes."""
    t = torch.arange(WINDOW, dtype=torch.float32) / 16000.0
    if spoof:
        base = 0.5 * torch.sin(2 * np.pi * 440.0 * t).unsqueeze(0)
        return (base + 0.05 * torch.randn(batch, WINDOW))
    return 0.2 * torch.randn(batch, WINDOW)


def check_learns() -> None:
    torch.manual_seed(0)
    m = AASISTL()
    opt = torch.optim.AdamW(m.parameters(), lr=1e-2)
    lossf = torch.nn.CrossEntropyLoss()
    x = torch.cat([_synthetic(16, spoof=False), _synthetic(16, spoof=True)])
    y = torch.tensor([0] * 16 + [1] * 16)
    first = last = None
    for epoch in range(10):
        opt.zero_grad()
        loss = lossf(m(x).logits, y)
        loss.backward()
        opt.step()
        last = loss.item()
        if first is None:
            first = last
    with torch.no_grad():
        acc = (m(x).logits.argmax(-1) == y).float().mean().item()
    assert acc > 0.9, f"no learning: acc {acc:.2f}"
    assert last < first * 0.5, f"loss not dropping: {first:.4f} -> {last:.4f}"
    print(f"3. learns OK (loss {first:.4f} -> {last:.4f}, acc {acc:.0%})")


def check_onnx_roundtrip() -> None:
    torch.manual_seed(1)
    m = AASISTL().eval()
    x = torch.randn(1, WINDOW)

    class _Wrapper(torch.nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, audio):
            return self.m(audio).logits

    with tempfile.TemporaryDirectory() as td:
        path = Path(td) / "aasist_l.onnx"
        torch.onnx.export(_Wrapper(m).eval(), (x,), str(path),
                          input_names=["audio"], output_names=["logits"],
                          opset_version=14,
                          dynamic_axes={"audio": {1: "length"}})
        import onnxruntime as ort
        sess = ort.InferenceSession(str(path))
        ort_logits = sess.run(["logits"], {"audio": x.numpy()})[0]
    with torch.no_grad():
        torch_logits = m(x).logits.numpy()
    diff = float(np.abs(ort_logits - torch_logits).max())
    assert diff < 1e-3, f"ONNX round-trip diff {diff}"
    # same parse the phone's SpoofEngine.score() does:
    parsed = float(max(ort_logits[0]))
    assert 0 <= parsed, parsed
    print(f"4. ONNX round-trip OK (max diff {diff:.2e})")


def check_eer() -> None:
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "train_aasist", ROOT / "scripts" / "train_aasist.py")
    ta = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(ta)
    scores = np.array([0.1, 0.2, 0.3, 0.8, 0.9, 0.95])
    labels = np.array([0, 0, 0, 1, 1, 1])
    eer = ta.compute_eer(scores, labels)
    assert eer == 0.0, eer
    # fully inverted scores -> worst case EER 1.0
    assert ta.compute_eer(-scores, labels) > 0.9
    # random labels -> EER near 0.5
    rng = np.random.default_rng(2)
    r = ta.compute_eer(rng.random(200), rng.integers(0, 2, 200))
    assert 0.2 < r < 0.8, r
    print(f"5. EER helper OK (random baseline {r:.2f})")


def check_ensemble_failsoft() -> None:
    """No models/ dir -> engine stays not-ready, no crash from _aasist import."""
    from antai.inference.voice.deepfake_voice import VoiceDeepfakeEngine
    res = VoiceDeepfakeEngine.analyze(VoiceDeepfakeEngine(), np.zeros(16000))
    assert res == {"spoof_prob": None, "label": None, "per_model": {},
                   "ready": False, "backend": "none"}, res
    print("6. ensemble fail-soft OK (no weights -> clean not-ready dict)")


if __name__ == "__main__":
    check_forward()
    check_params()
    check_learns()
    check_onnx_roundtrip()
    check_eer()
    check_ensemble_failsoft()
    print("ALL AASIST CHECKS PASSED")
