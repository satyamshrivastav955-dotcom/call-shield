import sys, logging, glob, os, argparse
from pathlib import Path
logging.disable(logging.CRITICAL)
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))
import numpy as np, av
from transformers import AutoFeatureExtractor, AutoModelForAudioClassification
import torch

def load(path):
    c = av.open(path)
    astr = c.streams.audio[0]
    res = av.AudioResampler(format="fltp", layout="mono", rate=16000)
    chunks = []
    for f in c.decode(astr):
        for o in res.resample(f):
            arr = o.to_ndarray()
            if arr.ndim == 2: arr = arr.mean(axis=0)
            chunks.append(np.asarray(arr, dtype=np.float32))
    c.close()
    return np.concatenate(chunks) if chunks else None

def load_model(d):
    proc = AutoFeatureExtractor.from_pretrained(d)
    m = AutoModelForAudioClassification.from_pretrained(d)
    m.eval().to(DEVICE)
    return proc, m

parser = argparse.ArgumentParser(description="Compare local voice-spoof models (portable).")
parser.add_argument("--models-root", default=str(ROOT / "models"),
                    help="Directory containing voice_deepfake, voice_deepfake_cross2, ...")
parser.add_argument("--audio-dir", default=str(ROOT / "audio_samples"),
                    help="Directory with flat audio files for quick compare")
parser.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"],
                    help="Inference device (auto = cuda if available else cpu)")
args = parser.parse_args()
DEVICE = "cuda" if (args.device == "cuda" or (args.device == "auto" and torch.cuda.is_available())) else "cpu"

def _opt(name: str):
    d = os.path.join(args.models_root, name)
    return d if os.path.isdir(d) else None

_model_dirs = {"AST5": _opt("voice_deepfake"), "W2V-GS": _opt("voice_deepfake_cross2"),
               "W2V-MM": _opt("voice_deepfake_cross1")}
_model_dirs = {k: v for k, v in _model_dirs.items() if v}
if not _model_dirs:
    sys.exit(f"no voice models found under {args.models_root}")
models = {k: load_model(d) for k, d in _model_dirs.items()}

def score(proc, m, a):
    inp = proc(a, sampling_rate=16000, return_tensors="pt")
    inp = {k: v.to(DEVICE) for k, v in inp.items()}
    with torch.no_grad():
        logits = m(**inp).logits
    if logits.shape[-1] == 1:
        return float(torch.sigmoid(logits.float()).cpu().squeeze())
    p = torch.softmax(logits.float(), dim=-1)[0]
    id2label = getattr(m.config, "id2label", None) or {}
    fake_idx = 0
    for k, v in id2label.items():
        if "fake" in str(v).lower() or "spoof" in str(v).lower():
            fake_idx = int(k)
            break
    return float(p[fake_idx])

cols = list(models.keys())
print(f"{'file':42s} " + " ".join(f"{c:>7s}" for c in cols) + "  expected")
print("-" * 100)
rows = []
patterns = [os.path.join(args.audio_dir, ext) for ext in ("*.mp3", "*.wav", "*.m4a", "*.ogg", "*.flac")]
files = sorted({f for pat in patterns for f in glob.glob(pat)})
for f in files:
    name = os.path.basename(f)
    a = load(f)
    if a is None:
        print(f"{name:42s}  (no audio stream, skipped)")
        continue
    s = {k: score(proc, m, a) for k, (proc, m) in models.items()}
    exp = "SPOOF" if any(k in name.lower() for k in ("ai", "fake", "synth", "spoof", "clone", "tts")) else "real"
    print(f"{name:42s} " + " ".join(f"{s[c]:7.3f}" for c in cols) + f"  {exp}")
    rows.append((name, s, exp))

print("\nVOTES (fake if >0.5):")
for name, s, exp in rows:
    votes = [k for k, v in s.items() if v > 0.5]
    flag = "FAKE" if len(votes) >= 2 else "real"
    ok = (flag == "FAKE") == (exp == "SPOOF")
    print(f"  {name:42s} votes={votes} -> {flag}  {'CORRECT' if ok else 'WRONG'}")