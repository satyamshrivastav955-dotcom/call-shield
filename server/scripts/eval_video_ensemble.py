"""Ensemble video-deepfake evaluation over a folder of videos.

Usage: conda run -n antai-server python scripts/eval_video_ensemble.py [folder]
Default folder: server/video_samples
"""
from __future__ import annotations

import glob
import logging
import os
import sys
from pathlib import Path

logging.disable(logging.CRITICAL)
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import av  # noqa: E402
import numpy as np  # noqa: E402

from antai.inference.hub import get_hub  # noqa: E402

FPS = 6


def load_frames(path: str, fps: int = FPS, max_frames: int = 120):
    c = av.open(path)
    vs = c.streams.video[0]
    vs.thread_type = "AUTO"
    frames, last = [], -1
    for frame in c.decode(vs):
        ts = float(frame.pts * vs.time_base)
        if last < 0 or ts - last >= 1.0 / fps:
            last = ts
            frames.append(frame.to_ndarray(format="bgr24"))
            if len(frames) >= max_frames:
                break
    c.close()
    return frames


def load_audio(path: str):
    c = av.open(path)
    if not c.streams.audio:
        c.close()
        return None
    astr = c.streams.audio[0]
    res = av.AudioResampler(format="fltp", layout="mono", rate=16000)
    chunks = []
    for f in c.decode(astr):
        for o in res.resample(f):
            arr = o.to_ndarray()
            if arr.ndim == 2:
                arr = arr.mean(axis=0)
            chunks.append(np.asarray(arr, dtype=np.float32))
    c.close()
    return np.concatenate(chunks) if chunks else None


def main() -> None:
    folder = sys.argv[1] if len(sys.argv) > 1 else str(ROOT / "video_samples")
    hub = get_hub()
    vdf = hub.get("video_deepfake")
    face = hub.get("face")
    lipsync = hub.get("lipsync")
    dfv = hub.get("voice_deepfake")
    asr = hub.get("asr")
    for e in (vdf, face, lipsync, dfv, asr):
        e.ready()

    videos = sorted(glob.glob(os.path.join(folder, "*.mp4")) +
                    glob.glob(os.path.join(folder, "*.avi")) +
                    glob.glob(os.path.join(folder, "*.mov")))
    if not videos:
        print(f"no videos in {folder}")
        return

    print(f"{'video':34s} {'CF-ViT':>7s} {'DiCoME':>7s} {'temporal':>8s} "
          f"{'agree':>5s} {'flag':>5s} {'voice':>7s} {'vlabel':>8s} "
          f"{'lip-sync':>8s}  transcript", flush=True)
    print("-" * 140, flush=True)
    for f in videos:
        name = os.path.basename(f)
        frames = load_frames(f)
        audio = load_audio(f)
        v = vdf.score_frames(frames) if frames else {}
        votes = v.get("votes", {})
        # voice deepfake + lip-sync (the full video-call pipeline)
        voice = dfv.analyze(audio, 16000) if audio is not None else {}
        nfaces, mouth = 0, []
        for fr in (frames or [])[-24:]:
            d = face.detect(fr)
            nfaces = max(nfaces, d.get("count", 0))
            if d.get("faces"):
                mouth.append(d["faces"][0]["openness"])
        sync = None
        if audio is not None and len(mouth) >= 4:
            n = int(16000 * 0.033)
            env = [float(np.sqrt(np.mean(audio[i:i + n] ** 2) + 1e-9))
                   for i in range(0, len(audio) - n + 1, n)]
            sync = lipsync.score(mouth, env[:len(mouth)]).get("sync_score")
        t = asr.transcribe(audio, 16000) if audio is not None else {}
        vp = voice.get("spoof_prob")
        vl = str(voice.get("label"))
        cf_score = f"{votes.get('community_vit', 0.0):.3f}" if 'community_vit' in votes else "-"
        dicome_score = f"{votes.get('dicome', 0.0):.3f}" if 'dicome' in votes else "-"
        temp_score = f"{v.get('temporal_consistency', 0.0):.2f}" if 'temporal_consistency' in v else "-"
        agree_str = str(v.get('agreement', '-'))
        flag_str = str(v.get('flagged', False))
        vp_str = f"{vp:.3f}" if vp is not None else "-"
        sync_str = f"{sync:.2f}" if sync is not None else "-"
        
        print(f"{name:34s} {cf_score:>7s} {dicome_score:>7s} {temp_score:>8s} "
              f"{agree_str:>5s} {flag_str:>5s} {vp_str:>7s} {vl:>8s} "
              f"{sync_str:>8s}  [{t.get('language') or '-'}] {t.get('text','')[:38]}", flush=True)
        if v.get("flagged_signals"):
            print(f"    -> video signals agreeing: {v['flagged_signals']} | "
                  f"ensemble fake_prob={round(v.get('fake_prob',0),3)}")
        if vp is not None and vp > 0.5:
            print(f"    -> VOICE deepfake fired: spoof_prob={vp:.3f}")
        video_flagged = bool(v.get("flagged"))
        voice_flagged = vl in ("spoof", "fake") or (vp is not None and vp > 0.5)
        combined = video_flagged or voice_flagged
        print(f"    -> VIDEO CALL verdict: {'FLAG' if combined else 'ok'} "
              f"(video-ensemble {v.get('flagged')} OR voice {vl})")


if __name__ == "__main__":
    main()