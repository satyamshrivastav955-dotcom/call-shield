import sys, numpy as np
sys.path.insert(0, "src")
import torch
from transformers import AutoFeatureExtractor, AutoModelForAudioClassification
from pathlib import Path
import av

def load_wav(path, sr=16000):
    container = av.open(path)
    resampler = av.AudioResampler(format="s16", layout="mono", rate=sr)
    chunks = []
    for frame in container.decode(audio=0):
        for f in resampler.resample(frame):
            a = f.to_ndarray()
            if a.ndim == 2: a = a[0]
            chunks.append(np.asarray(a, dtype=np.float32))
    out = np.concatenate(chunks)
    return out / 32768.0

fe = AutoFeatureExtractor.from_pretrained("models/voice_deepfake")
for device, dtype in [("cpu", torch.float32), ("cuda", torch.float16)]:
    model = AutoModelForAudioClassification.from_pretrained("models/voice_deepfake")
    if device == "cuda":
        model = model.half().to("cuda")
    else:
        model = model.eval()
    for name, path in [("REAL","audio_samples/Satyam_real_voice_1.wav"), ("AI","audio_samples/Satyam_ai_voice_0(english).mp3")]:
        a = load_wav(path)[16000:16000+48000]
        inp = fe(a, sampling_rate=16000, return_tensors="pt")
        if device == "cuda":
            inp = {k: v.half().to("cuda") for k,v in inp.items()}
        with torch.no_grad():
            logits = model(**inp).logits
        probs = torch.softmax(logits.float(), dim=-1)[0]
        print(f"{device}/{dtype} {name}: probs(Bonafide, Spoof) = {probs.tolist()}")
