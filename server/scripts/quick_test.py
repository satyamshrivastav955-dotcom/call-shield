"""Quick smoke test: run the voice + video detection models on the bundled
audio/video samples and print clean results."""
import sys, numpy as np
sys.path.insert(0, "src")
import av
from antai.inference.hub import get_hub

def load_audio(path, sr=16000, max_s=5.0):
    c = av.open(path)
    rs = av.AudioResampler(format="s16", layout="mono", rate=sr)
    chunks = []
    for f in c.decode(audio=0):
        for o in rs.resample(f):
            a = o.to_ndarray()
            if a.ndim == 2: a = a[0]
            chunks.append(np.asarray(a, dtype=np.float32))
        if sum(len(x) for x in chunks) >= sr * max_s:
            break
    return (np.concatenate(chunks) / 32768.0).astype(np.float32)

def load_frames(path, maxn=24):
    c = av.open(path)
    frames = []
    for f in c.decode(video=0):
        frames.append(np.asarray(f.to_ndarray(format="bgr24")))
        if len(frames) >= maxn:
            break
    return frames

hub = get_hub()

print("=" * 60)
print("VOICE DEEPFAKE  (backend:", getattr(hub.get("voice_deepfake"), "device", "?"), ")")
print("=" * 60)
vd = hub.get("voice_deepfake")
voices = [
    ("real_satyam1", "audio_samples/Satyam_real_voice_1.wav"),
    ("real_satyam2", "audio_samples/Satyam_real_voice_2.wav"),
    ("real_tushar",  "audio_samples/Tushar_real_voice_1.wav"),
    ("ai_english",   "audio_samples/Satyam_ai_voice_0(english).mp3"),
    ("ai_hindi",     "audio_samples/Satyam_ai_Voice_1(hindi).mp3"),
]
for name, path in voices:
    try:
        a = load_audio(path)
        r = vd.analyze(a, 16000)
        p = r.get("spoof_prob")
        print(f"  {name:14s}  spoof={('%.3f' % p) if p is not None else 'None':>6}  "
              f"label={r.get('label')}  per_model={r.get('per_model')}")
    except Exception as e:
        print(f"  {name:14s}  ERROR: {e}")

print()
print("=" * 60)
print("VIDEO DEEPFAKE  (local ensemble)")
print("=" * 60)
dv = hub.get("video_deepfake")
videos = [
    ("real_zuck",      "video_samples/original_zuck.mp4"),
    ("deepfake_zuck",  "video_samples/deepfake_zuck.mp4"),
    ("deepfake_full",  "video_samples/FULL_deepfake_video_1.mp4"),
]
for name, path in videos:
    try:
        fr = load_frames(path)
        r = dv.score_frames(fr)
        fp = r.get("fake_prob")
        print(f"  {name:14s}  fake_prob={('%.3f' % fp) if fp is not None else 'None':>6}  "
              f"flagged={r.get('flagged')}  votes={r.get('votes')}")
    except Exception as e:
        print(f"  {name:14s}  ERROR: {e}")

print()
print("=" * 60)
print("ASR  (backend:", getattr(hub.get("asr"), "device", "?"), ")")
print("=" * 60)
asr = hub.get("asr")
a = load_audio("audio_samples/Satyam_real_voice_1.wav")
try:
    r = asr.transcribe(a, 16000)
    print("  real_satyam1 ->", repr(r.get("text", "")))
except Exception as e:
    print("  ASR ERROR:", e)
