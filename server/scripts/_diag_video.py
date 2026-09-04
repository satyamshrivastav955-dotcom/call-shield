import sys, numpy as np, av
sys.path.insert(0, "src")
from antai.inference.hub import get_hub
hub = get_hub()
dv = hub.get("video_deepfake")
print("video_deepfake ready:", dv.ready() if dv else None)

def load_frames(path, maxn=30):
    c = av.open(path)
    frames = []
    for f in c.decode(video=0):
        arr = f.to_ndarray(format="bgr24")
        frames.append(np.asarray(arr))
        if len(frames) >= maxn: break
    return frames

for name, p in [("REAL","video_samples/original_zuck.mp4"), ("FAKE","video_samples/deepfake_zuck.mp4")]:
    fr = load_frames(p)
    r = dv.score_frames(fr)
    print(f"{name}: fake_prob={r.get('fake_prob')} votes={r.get('votes')} agreement={r.get('agreement')} flagged={r.get('flagged')}")
