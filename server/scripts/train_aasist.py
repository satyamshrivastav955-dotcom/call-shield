"""Train AASIST-L (voice-spoof detector) on ASVspoof-style data.

Server-rig job: the phone ships the exported INT8 artifact; this script is
where the weights come from. Runs on the GPU machine.

Usage:
    python scripts/train_aasist.py --data_dir <dir> [--epochs 20] [--batch 16]
    [--val-split 0.2] [--out models/voice_deepfake_aasist/aasist_l.pt]

Data layout (any of):
    <dir>/bonafide/*.wav  +  <dir>/spoof/*.wav     (flat two-class dirs)
    OR ASVspoof5-style: <dir>/flac/D*/*.flac + <dir>/ASVspoof5.protocols...
    — ponytail: the flat two-class layout is what we commit to; if you have
    raw ASVspoof, flatten it first (bonafide/ + spoof/ by the protocol file).

Pipeline: 4s random crops @16k -> AASISTL GAT -> 2-class CE (spoof-weighted)
-> AdamW -> checkpoint + EER on held-out split.

The EER print is the point: it decides whether the trained checkpoint
actually joins the ensemble on the server (drop the dir in and the
deepfake_voice loader picks it up) and whether voice_alert_threshold needs
recalibration for the on-device INT8 export.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

WINDOW = 4 * 16000  # 4s @16k, same as the on-device rolling window


# ----------------------------------------------------------------- data
def _load_wav(path: Path, sr: int = 16000) -> np.ndarray:
    import soundfile as sf
    try:
        audio, rate = sf.read(str(path), dtype="float32")
    except Exception:
        return np.zeros(0, dtype="float32")
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    if rate != sr:  # ponytail: linear resample; soxr/librosa if quality matters
        n = int(len(audio) * sr / rate)
        x = np.arange(n) * (rate / sr)
        audio = np.interp(x, np.arange(len(audio)), audio).astype("float32")
    return audio


class WavDataset:
    """Flat two-class wav dirs -> (4s crop, label) tensors."""

    def __init__(self, data_dir: Path):
        self.items: list[tuple[Path, int]] = []
        bon = sorted((data_dir / "bonafide").glob("*.wav"))
        spo = sorted((data_dir / "spoof").glob("*.wav"))
        if not bon and not spo:
            # also accept .flac (ASVspoof ships flac)
            bon = sorted((data_dir / "bonafide").glob("*.flac"))
            spo = sorted((data_dir / "spoof").glob("*.flac"))
        if not bon and not spo:
            sys.exit(f"no wavs under {data_dir}/bonafide or {data_dir}/spoof")
        self.items = [(p, 0) for p in bon] + [(p, 1) for p in spo]
        self.n_bonafide, self.n_spoof = len(bon), len(spo)

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        import torch
        path, label = self.items[i]
        audio = _load_wav(path)
        if len(audio) < WINDOW:
            audio = np.pad(audio, (0, WINDOW - len(audio)))
        else:
            start = np.random.randint(0, len(audio) - WINDOW + 1)
            audio = audio[start:start + WINDOW]
        # light spec-augment-ish: random gain + occasional time shift
        audio = audio * (0.8 + 0.4 * np.random.rand())
        return torch.from_numpy(audio.copy()), label


# ----------------------------------------------------------------- EER
def compute_eer(scores: np.ndarray, labels: np.ndarray) -> float:
    """Equal Error Rate via interpolation on the ROC.

    score = P(spoof). For a threshold t on the score:
      FAR(t) = fraction of SPOOFS scored BELOW t (accepted as bonafide)
      FRR(t) = fraction of BONAFIDES scored ABOVE t (rejected as spoof)
    EER = the operating point where FAR == FRR.
    """
    order = np.argsort(scores)
    s, y = scores[order], labels[order]
    bon_total = (y == 0).sum()
    spo_total = (y == 1).sum()
    # at index i: everything <= s[i] is "below threshold"
    bon_below = np.cumsum(y == 0)
    spo_below = np.cumsum(y == 1)
    far = spo_below / max(1, spo_total)        # spoofs slipping through
    frr = 1.0 - bon_below / max(1, bon_total)  # bonafides wrongly flagged
    diff = far - frr
    idx = int(np.argmin(np.abs(diff)))
    return float(0.5 * (far[idx] + frr[idx]))


# ----------------------------------------------------------------- train
def train(args) -> None:
    import torch
    import torch.nn as nn
    from torch.utils.data import DataLoader, Subset
    from antai.inference.voice._aasist import AASISTL, count_params

    torch.manual_seed(0)
    np.random.seed(0)

    ds = WavDataset(Path(args.data_dir))
    print(f"data: {ds.n_bonafide} bonafide / {ds.n_spoof} spoof")
    # spoof class is scarce in the field -> upweight its loss
    weight = torch.tensor([1.0, max(1.0, ds.n_bonafide / max(1, ds.n_spoof))])

    idx = np.random.permutation(len(ds))
    n_val = max(1, int(len(ds) * args.val_split))
    val_idx, tr_idx = idx[:n_val], idx[n_val:]
    tr_loader = DataLoader(Subset(ds, tr_idx.tolist()), batch_size=args.batch,
                           shuffle=True, num_workers=0)
    val_set = [ds[i] for i in val_idx.tolist()]

    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = AASISTL().to(device)
    print(f"AASIST-L: {count_params(model)} params on {device}")
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr)
    lossf = nn.CrossEntropyLoss(weight=weight.to(device))

    best_eer = 1.0
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    for epoch in range(args.epochs):
        model.train()
        total, seen = 0.0, 0
        for audio, label in tr_loader:
            audio, label = audio.to(device), label.to(device)
            opt.zero_grad()
            logits = model(audio).logits
            loss = lossf(logits, label)
            loss.backward()
            opt.step()
            total += loss.item() * label.shape[0]
            seen += label.shape[0]
        eer = evaluate(model, val_set, device)
        marker = ""
        if eer < best_eer:
            best_eer = eer
            torch.save(model.state_dict(), out_path)
            marker = "  <- saved"
        print(f"epoch {epoch + 1}/{args.epochs}  train_loss={total / max(1, seen):.4f}"
              f"  val_EER={eer:.4f}{marker}")

    print(f"done. best val EER = {best_eer:.4f} -> {out_path}")
    print("NOTE: EER decides membership. If it is worse than the wav2vec2 "
          "cross-check on your data, do NOT drop the dir into the ensemble — "
          "retrain or port the full AASIST repo (see _aasist.py ponytail note).")


def evaluate(model, val_set, device) -> float:
    import torch
    model.eval()
    scores, labels = [], []
    with torch.no_grad():
        for audio, label in val_set:
            logits = model(audio.unsqueeze(0).to(device)).logits
            p = torch.softmax(logits.float(), dim=-1)[0, 1].item()
            scores.append(p)
            labels.append(int(label))
    return compute_eer(np.array(scores), np.array(labels))


def main() -> None:
    ap = argparse.ArgumentParser(description="Train AASIST-L voice-spoof detector")
    ap.add_argument("--data_dir", required=True)
    ap.add_argument("--epochs", type=int, default=20)
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--lr", type=float, default=3e-4)
    ap.add_argument("--val-split", type=float, default=0.2)
    ap.add_argument("--out", default=str(ROOT / "models" / "voice_deepfake_aasist"
                                         / "aasist_l.pt"))
    args = ap.parse_args()
    train(args)


if __name__ == "__main__":
    main()
