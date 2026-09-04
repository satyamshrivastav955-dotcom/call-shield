import sys, numpy as np
sys.path.insert(0, "src")
from antai.inference.hub import get_hub
from antai.config import get_config

def load_wav(path, sr=16000):
    import av
    import subprocess
    # use ffmpeg via av to decode to mono float32 16k
    container = av.open(path)
    resampler = av.AudioResampler(format="s16", layout="mono", rate=sr)
    chunks = []
    for frame in container.decode(audio=0):
        for f in resampler.resample(frame):
            a = f.to_ndarray()
            if a.ndim == 2: a = a[0]
            chunks.append(np.asarray(a, dtype=np.float32))
    if not chunks:
        return None
    out = np.concatenate(chunks) if len(chunks) > 1 else chunks[0]
    return out / 32768.0

hub = get_hub()
vd = hub.get("voice_deepfake")
print("voice_deepfake ready:", vd.ready() if vd else None)
real = load_wav("audio_samples/Satyam_real_voice_1.wav")
ai = load_wav("audio_samples/Satyam_ai_voice_0(english).mp3")
for name, arr in [("REAL", real), ("AI", ai)]:
    if arr is None:
        print(name, "load failed"); continue
    mid = arr[16000:16000+48000]  # take a 3s slice
    r = vd.analyze(mid, 16000)
    print(f"{name}: spoof_prob={r.get('spoof_prob')} per_model={r.get('per_model')} label={r.get('label')}")
