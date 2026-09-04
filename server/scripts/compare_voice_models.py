import sys, logging, glob, os
logging.disable(logging.CRITICAL)
sys.path.insert(0, "src")
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
    m.eval().to("cuda")
    return proc, m

models = {
    "AST5": load_model(r"C:\Users\satya\OneDrive\Desktop\antAI\server\models\voice_deepfake"),
    "W2V-MM": load_model(r"C:\Users\satya\OneDrive\Desktop\antAI\server\models\voice_deepfake_cross1"),
    "W2V-GS": load_model(r"C:\Users\satya\OneDrive\Desktop\antAI\server\models\voice_deepfake_cross2"),
}

def score(proc, m, a):
    inp = proc(a, sampling_rate=16000, return_tensors="pt")
    inp = {k: v.to("cuda") for k, v in inp.items()}
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

print(f"{'file':42s} {'AST5':>6s} {'W2V-MM':>6s} {'W2V-GS':>6s}  expected")
print("-" * 100)
rows = []
for f in sorted(glob.glob(r"audio_samples\*.mp3") + glob.glob(r"audio_samples\*.wav") + glob.glob(r"audio_samples\*.m4a")):
    name = os.path.basename(f)
    a = load(f)
    s = {k: score(proc, m, a) for k, (proc, m) in models.items()}
    exp = "SPOOF" if "ai" in name.lower() else "real"
    print(f"{name:42s} {s['AST5']:6.3f} {s['W2V-MM']:6.3f} {s['W2V-GS']:6.3f}  {exp}")
    rows.append((name, s, exp))

print("\nVOTES (fake if >0.5):")
for name, s, exp in rows:
    votes = [k for k, v in s.items() if v > 0.5]
    flag = "FAKE" if len(votes) >= 2 else "real"
    ok = (flag == "FAKE") == (exp == "SPOOF")
    print(f"  {name:42s} votes={votes} -> {flag}  {'CORRECT' if ok else 'WRONG'}")