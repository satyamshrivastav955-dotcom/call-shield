"""Validate the on-device spoof model on ASVspoof5 -> a defensible accuracy number.

Task 1c. The point is an HONEST number for the SIH deck. So by default this
evaluates the EXACT artifact that ships to the phone — spoof_ast.int8.onnx —
fed through the SAME 4s-window path the on-device pipeline uses. Not the fp32
torch model, not the server. What you measure is what the phone does.

Reports: accuracy@threshold, precision/recall/F1 (spoof=positive), EER, ROC-AUC,
confusion matrix, and the best-accuracy + EER-crossover thresholds. Writes a JSON
you can cite, and prints a one-line deck summary.

DEPENDENCY-LIGHT ON PURPOSE: numpy + onnxruntime + soundfile only. Metrics are
hand-rolled in numpy (no sklearn needed) so this runs in a minimal venv. torch/
transformers are used ONLY for --backend torch (compare shipped int8 vs true fp32).

ASVspoof5 SCHEMA — auto-detected, override anytime:
  A) FLAC + protocol:  --flac-dir <dir> --protocol <metadata.txt>
     (metadata lines are whitespace-separated; the token equal to bonafide/spoof
      is the label, the token naming an existing .flac is the file — order-robust.)
  B) Parquet (HF datasets export):  --data <file_or_dir.parquet>
     (audio col = dict/bytes/path; label col = values in {bonafide,spoof,0,1}.)
  Override detection with --audio-col/--label-col/--flac-dir/--protocol.

WINDOWING (documented so the number is defensible): each utterance is cut into
consecutive 4s @16k windows (=64000 samples, the fixed export input); the last
partial window is zero-padded; per-window spoof probs are aggregated with --agg
(mean default). Utterance score = agg(window probs). This mirrors how the phone
continuously scores 4s windows and uses ALL the audio.

RUN (on satya's host, after ASVspoof5 finishes downloading):
  cd server
  # A) FLAC + protocol:
  python scripts/validate_asvspoof5.py --model onnx/spoof_ast.int8.onnx \
      --flac-dir data/ASVspoof5/flac_D --protocol data/ASVspoof5/ASVspoof5.dev.metadata.txt \
      --out onnx/asvspoof5_report.json
  # B) parquet:
  python scripts/validate_asvspoof5.py --model onnx/spoof_ast.int8.onnx \
      --data data/ASVspoof5/dev.parquet --out onnx/asvspoof5_report.json
  # compare shipped int8 vs true fp32 torch:
  python scripts/validate_asvspoof5.py --backend torch --model models/voice_deepfake ...

Cannot run in the Claude sandbox (no dataset, no onnxruntime/soundfile, no net).
Written for the host; py_compile-clean; numpy metric fns are unit-checkable.
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent          # -> server/
SAMPLES = 64000                                         # 4 s @ 16 kHz


# ----------------------------- metrics (numpy) -----------------------------
def confusion(scores: np.ndarray, labels: np.ndarray, thr: float):
    pred = (scores >= thr).astype(int)                  # 1 = spoof
    tp = int(((pred == 1) & (labels == 1)).sum())
    tn = int(((pred == 0) & (labels == 0)).sum())
    fp = int(((pred == 1) & (labels == 0)).sum())
    fn = int(((pred == 0) & (labels == 1)).sum())
    return tp, tn, fp, fn


def prf1_acc(scores, labels, thr):
    tp, tn, fp, fn = confusion(scores, labels, thr)
    n = tp + tn + fp + fn
    acc = (tp + tn) / n if n else 0.0
    prec = tp / (tp + fp) if (tp + fp) else 0.0
    rec = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * prec * rec / (prec + rec) if (prec + rec) else 0.0
    return acc, prec, rec, f1, (tp, tn, fp, fn)


def roc_auc(scores, labels):
    """AUC via the Mann-Whitney U statistic (rank-based; ties handled)."""
    pos = scores[labels == 1]
    neg = scores[labels == 0]
    if len(pos) == 0 or len(neg) == 0:
        return float("nan")
    order = np.argsort(scores, kind="mergesort")
    ranks = np.empty(len(scores), float)
    ranks[order] = np.arange(1, len(scores) + 1)
    # average ranks for ties
    s_sorted = scores[order]
    i = 0
    while i < len(s_sorted):
        j = i
        while j + 1 < len(s_sorted) and s_sorted[j + 1] == s_sorted[i]:
            j += 1
        if j > i:
            ranks[order[i:j + 1]] = (i + 1 + j + 1) / 2.0
        i = j + 1
    sum_pos = ranks[labels == 1].sum()
    n_pos, n_neg = len(pos), len(neg)
    auc = (sum_pos - n_pos * (n_pos + 1) / 2.0) / (n_pos * n_neg)
    return float(auc)


def compute_eer(scores, labels):
    """Equal Error Rate + its threshold. spoof(=1) is the positive class."""
    thrs = np.unique(scores)
    if len(thrs) < 2:
        return float("nan"), float("nan")
    best = None
    n_pos = max(1, int((labels == 1).sum()))
    n_neg = max(1, int((labels == 0).sum()))
    for t in thrs:
        pred = (scores >= t).astype(int)
        far = ((pred == 1) & (labels == 0)).sum() / n_neg     # false accept (spoof) rate
        frr = ((pred == 0) & (labels == 1)).sum() / n_pos     # false reject rate
        gap = abs(far - frr)
        if best is None or gap < best[0]:
            best = (gap, (far + frr) / 2.0, float(t))
    return best[1], best[2]


def best_accuracy_threshold(scores, labels):
    thrs = np.unique(scores)
    best = (0.0, 0.5)
    for t in thrs:
        acc = prf1_acc(scores, labels, t)[0]
        if acc > best[0]:
            best = (acc, float(t))
    return best[1], best[0]


# ----------------------------- audio -----------------------------
def _resample_linear(x: np.ndarray, src: int, dst: int) -> np.ndarray:
    if src == dst or len(x) == 0:
        return x
    n = max(1, int(round(len(x) * dst / src)))
    xp = np.linspace(0, 1, len(x), endpoint=False)
    fp = np.linspace(0, 1, n, endpoint=False)
    return np.interp(fp, xp, x).astype(np.float32)


def load_mono16k(source) -> np.ndarray:
    """source: path str OR raw bytes -> mono float32 @16k in [-1,1]."""
    import soundfile as sf
    if isinstance(source, (bytes, bytearray)):
        data, sr = sf.read(io.BytesIO(source), dtype="float32", always_2d=False)
    else:
        data, sr = sf.read(source, dtype="float32", always_2d=False)
    data = np.asarray(data, dtype=np.float32)
    if data.ndim == 2:
        data = data.mean(axis=1)
    return _resample_linear(data, sr, 16000)


def to_windows(pcm: np.ndarray, samples: int = SAMPLES):
    if len(pcm) == 0:
        return [np.zeros(samples, np.float32)]
    out = []
    for off in range(0, len(pcm), samples):
        w = pcm[off:off + samples]
        if len(w) < samples:
            w = np.pad(w, (0, samples - len(w)))
        out.append(w.astype(np.float32))
    return out


# ----------------------------- model backends -----------------------------
class OnnxSpoof:
    def __init__(self, path: str):
        import onnxruntime as ort
        self.s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        self.iname = self.s.get_inputs()[0].name
        o = self.s.get_outputs()[0]
        # Contract-aware: --emit logit exports name the output "spoof_logit" and
        # emit the spoof-vs-bonafide LOGIT GAP; sigmoid(gap) is the spoof prob this
        # harness scores. --emit prob (shipped) names it "spoof_prob" and is used
        # as-is. Detecting by name keeps accuracy numbers correct for BOTH exports.
        self.is_logit = "logit" in str(o.name).lower()
        print(f"[model] onnx input='{self.iname}' output='{o.name}' shape={o.shape} "
              f"({'logit gap -> sigmoid' if self.is_logit else 'prob'})")

    def prob(self, window: np.ndarray) -> float:
        out = self.s.run(None, {self.iname: window.reshape(1, -1).astype(np.float32)})[0]
        arr = np.asarray(out).reshape(-1)
        if arr.size != 1:
            raise SystemExit(f"[FAIL] model output has {arr.size} elems; expected [1,1]. "
                             f"Re-export with export_ast_spoof_onnx.py (single-column contract).")
        v = float(arr[0])
        return float(1.0 / (1.0 + np.exp(-v))) if self.is_logit else v


class TorchSpoof:
    """True fp32 reference (feature_extractor + AST). For int8-vs-fp32 comparison."""
    def __init__(self, src: str):
        import torch
        from transformers import ASTForAudioClassification, ASTFeatureExtractor
        self.torch = torch
        self.model = ASTForAudioClassification.from_pretrained(src).eval()
        self.fe = ASTFeatureExtractor.from_pretrained(src)
        self.sr = int(getattr(self.fe, "sampling_rate", 16000))
        id2 = {int(k): v for k, v in self.model.config.id2label.items()}
        self.spoof_idx = next((k for k, v in id2.items()
                               if str(v).lower().startswith("spoof")), 1)
        print(f"[model] torch AST labels={id2} spoof_idx={self.spoof_idx}")

    def prob(self, window: np.ndarray) -> float:
        with self.torch.no_grad():
            inp = self.fe(window, sampling_rate=self.sr, return_tensors="pt")
            logits = self.model(**inp).logits
            return float(self.torch.softmax(logits, dim=-1)[0, self.spoof_idx])


def make_backend(backend: str, model: str):
    if backend == "auto":
        backend = "torch" if os.path.isdir(model) else "onnx"
    print(f"[model] backend={backend} path={model}")
    return TorchSpoof(model) if backend == "torch" else OnnxSpoof(model)


# ----------------------------- dataset loaders -----------------------------
def _label_to_int(v) -> int | None:
    s = str(v).strip().lower()
    if s in ("spoof", "1", "fake", "deepfake"):
        return 1
    if s in ("bonafide", "bona-fide", "genuine", "real", "0"):
        return 0
    return None


def load_from_protocol(flac_dir: str, protocol: str, limit: int):
    """Order-robust ASVspoof metadata parse: find label + flac tokens by content."""
    items = []
    flac_dir_p = Path(flac_dir)
    with open(protocol, "r", encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            toks = line.split()
            if not toks:
                continue
            label = None
            fpath = None
            for t in toks:
                if label is None and _label_to_int(t) is not None:
                    label = _label_to_int(t)
            # find the token that names an existing flac (with or without ext)
            for t in toks:
                cand = t if t.lower().endswith((".flac", ".wav")) else t + ".flac"
                p = flac_dir_p / cand
                if p.exists():
                    fpath = str(p)
                    break
            if label is None or fpath is None:
                continue
            items.append((fpath, label))
            if limit and len(items) >= limit:
                break
    if not items:
        raise SystemExit(f"[FAIL] no (flac,label) pairs from {protocol} + {flac_dir}. "
                         f"Check --flac-dir/--protocol or pass --audio-col/--label-col for parquet.")
    return items


def load_from_parquet(data: str, audio_col: str | None, label_col: str | None, limit: int):
    import pandas as pd
    files = [data] if data.endswith(".parquet") else sorted(glob.glob(os.path.join(data, "*.parquet")))
    if not files:
        raise SystemExit(f"[FAIL] no parquet found at {data}")
    dfs = []
    total_loaded = 0
    for f in files:
        chunk = pd.read_parquet(f)
        dfs.append(chunk)
        total_loaded += len(chunk)
        if limit and total_loaded >= limit:
            break
    df = pd.concat(dfs, ignore_index=True)
    if limit:
        df = df.head(limit)
    cols = list(df.columns)
    # auto-detect label col
    if label_col is None:
        for c in cols:
            vals = df[c].dropna().astype(str).str.lower().unique()[:8]
            if len(vals) and all(_label_to_int(v) is not None for v in vals):
                label_col = c
                break
    if label_col is None:
        raise SystemExit(f"[FAIL] could not find a bonafide/spoof label column in {cols}; pass --label-col")
    # auto-detect audio col
    if audio_col is None:
        for c in cols:
            v = df[c].iloc[0]
            if isinstance(v, dict) and ("bytes" in v or "array" in v or "path" in v):
                audio_col = c
                break
            if isinstance(v, (bytes, bytearray)):
                audio_col = c
                break
        if audio_col is None and "audio" in cols:
            audio_col = "audio"
    if audio_col is None:
        raise SystemExit(f"[FAIL] could not find an audio column in {cols}; pass --audio-col")
    print(f"[data] parquet rows={len(df)} audio_col='{audio_col}' label_col='{label_col}'")

    items = []
    for _, row in df.iterrows():
        lab = _label_to_int(row[label_col])
        if lab is None:
            continue
        a = row[audio_col]
        if isinstance(a, dict):
            if a.get("bytes") is not None:
                src = bytes(a["bytes"])
            elif a.get("array") is not None:
                arr = np.asarray(a["array"], dtype=np.float32)
                sr = int(a.get("sampling_rate", 16000))
                src = _resample_linear(arr, sr, 16000)
            else:
                src = a.get("path")
        else:
            src = a
        items.append((src, lab))
    return items


# ----------------------------- main eval -----------------------------
def evaluate(items, backend, agg: str, samples: int):
    scores, labels = [], []
    n = len(items)
    for i, (src, lab) in enumerate(items):
        try:
            if isinstance(src, np.ndarray):
                pcm = _resample_linear(src, 16000, 16000)
            else:
                pcm = load_mono16k(src)
            probs = [backend.prob(w) for w in to_windows(pcm, samples)]
            if agg == "max":
                utt = max(probs)
            elif agg == "first":
                utt = probs[0]
            else:
                utt = float(np.mean(probs))
            scores.append(utt)
            labels.append(lab)
        except Exception as e:
            print(f"[warn] skip item {i}: {e}")
        if (i + 1) % 100 == 0 or i + 1 == n:
            print(f"[eval] {i + 1}/{n}", flush=True)
    return np.asarray(scores, float), np.asarray(labels, int)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=str(ROOT / "onnx" / "spoof_ast.int8.onnx"),
                    help="onnx path (default, what ships) OR HF dir for --backend torch")
    ap.add_argument("--backend", choices=["onnx", "torch", "auto"], default="auto")
    ap.add_argument("--data", help="parquet file or dir")
    ap.add_argument("--flac-dir", help="dir of .flac files")
    ap.add_argument("--protocol", help="ASVspoof metadata/protocol txt")
    ap.add_argument("--audio-col", help="override parquet audio column")
    ap.add_argument("--label-col", help="override parquet label column")
    ap.add_argument("--agg", choices=["mean", "max", "first"], default="mean",
                    help="how to combine per-4s-window spoof probs into one utterance score")
    ap.add_argument("--threshold", type=float, default=0.5, help="decision threshold for acc/F1")
    ap.add_argument("--samples", type=int, default=SAMPLES, help="window length (4s@16k=64000)")
    ap.add_argument("--limit", type=int, default=0, help="cap #utterances (0=all)")
    ap.add_argument("--out", default=str(ROOT / "onnx" / "asvspoof5_report.json"))
    args = ap.parse_args()

    if args.flac_dir and args.protocol:
        items = load_from_protocol(args.flac_dir, args.protocol, args.limit)
    elif args.data:
        items = load_from_parquet(args.data, args.audio_col, args.label_col, args.limit)
    else:
        raise SystemExit("Provide either --flac-dir + --protocol, or --data <parquet>.")
    print(f"[data] {len(items)} utterances "
          f"(spoof={sum(1 for _, l in items if l == 1)}, bonafide={sum(1 for _, l in items if l == 0)})")

    backend = make_backend(args.backend, args.model)
    scores, labels = evaluate(items, backend, args.agg, args.samples)
    if len(scores) == 0:
        raise SystemExit("[FAIL] no utterances scored.")

    acc, prec, rec, f1, (tp, tn, fp, fn) = prf1_acc(scores, labels, args.threshold)
    auc = roc_auc(scores, labels)
    eer, eer_thr = compute_eer(scores, labels)
    best_thr, best_acc = best_accuracy_threshold(scores, labels)

    report = {
        "model": args.model, "backend": args.backend, "n": int(len(scores)),
        "n_spoof": int((labels == 1).sum()), "n_bonafide": int((labels == 0).sum()),
        "agg": args.agg, "threshold": args.threshold,
        "accuracy": round(acc, 4), "precision_spoof": round(prec, 4),
        "recall_spoof": round(rec, 4), "f1_spoof": round(f1, 4),
        "roc_auc": round(auc, 4), "eer": round(eer, 4), "eer_threshold": round(eer_thr, 4),
        "best_acc_threshold": round(best_thr, 4), "best_accuracy": round(best_acc, 4),
        "confusion": {"tp": tp, "tn": tn, "fp": fp, "fn": fn},
    }
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(report, indent=2))

    print("\n================ ASVspoof5 — ON-DEVICE SPOOF MODEL ================")
    for k, v in report.items():
        print(f"  {k}: {v}")
    print("==================================================================")
    print(f"[deck] {report['model'].split('/')[-1]}: "
          f"acc={acc*100:.2f}%  F1={f1:.3f}  EER={eer*100:.2f}%  AUC={auc:.3f}  "
          f"(n={len(scores)}, @thr={args.threshold}, agg={args.agg})")
    print(f"[deck] wrote {args.out}")


if __name__ == "__main__":
    main()
