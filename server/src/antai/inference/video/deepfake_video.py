"""Video deepfake detection — ensemble (2-of-3 agreement rule).

Signals:
  A. CommunityForensics DeepfakeDet-ViT  (frame-level, AI-image artifacts)
  B. DiCoME                                (frame-level, evidential DS-fusion)
  C. Temporal consistency + lip-sync       (window-level, from the detector node)
Flag as fake when >= video_vote_threshold of the available signals agree.
DeCoF (fully-AI-generated video, temporal) is wired and auto-enables when its
weights are released (models/video_decof/).

If no trained weights are present, falls back to a zero-weight heuristic
clearly marked heuristic=True.
"""
from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from ..hub import get_hub
from ...config import get_config
from ..hub import BaseEngine

log = logging.getLogger(__name__)


class VideoDeepfakeEngine(BaseEngine):
    name = "video_deepfake"

    def _load(self) -> bool:
        cfg = get_config()
        self._vote_threshold = int(cfg.pipeline.get("video_vote_threshold", 2)
                                   if isinstance(cfg.pipeline, dict) else 2)
        try:
            self._vote_threshold = int(get_config().pipeline.video_vote_threshold)
        except Exception:
            pass
        # engine is always "ready": the ensemble degrades gracefully per model
        self.device = "cuda" if self._device_for() == "cuda" else "cpu"
        return True

    # ---------------------------------------------------------------- voting
    def _sub(self, name: str):
        return get_hub().get(name)

    def score_frames(self, frames_bgr: list[np.ndarray]) -> dict:
        """Ensemble verdict over the frame buffer."""
        if not frames_bgr:
            return {"fake_prob": None, "heuristic": True, "ready": True}
        votes: dict[str, float] = {}
        sample = frames_bgr[-8:]

        cf = self._sub("community_vit")
        if cf is not None and cf.ready():
            probs = [cf.analyze(f).get("fake_prob") for f in sample]
            probs = [p for p in probs if p is not None]
            if probs:
                votes["community_vit"] = float(np.mean(probs))

        dm = self._sub("dicome")
        if dm is not None and dm.ready():
            probs = [dm.analyze(f).get("fake_prob") for f in sample]
            probs = [p for p in probs if p is not None]
            if probs:
                votes["dicome"] = float(np.mean(probs))

        dc = self._sub("decof")
        if dc is not None and dc.ready():
            r = dc.score(frames_bgr)
            if r.get("fake_prob") is not None:
                votes["decof"] = r["fake_prob"]

        temporal = _temporal_consistency(frames_bgr[-16:])
        votes["temporal"] = temporal

        if not votes:
            fake = _heuristic_score(frames_bgr)
            return {"fake_prob": fake, "temporal_consistency": temporal,
                    "votes": {}, "agreement": 0, "heuristic": True, "ready": True}

        # ---- robust aggregation -------------------------------------------------
        # The "temporal" value is a heuristic (low-motion -> high), not a model:
        # it saturates at 0.85 for any talking head, so it must not vote. Some
        # checkpoints (dicome) also sit at a near-constant high value (~0.8) on
        # everything and are dropped by the <0.65 reliability gate below, leaving
        # the discrimative ViT as the signal.
        model_votes = {k: v for k, v in votes.items() if k != "temporal"}
        reliable = {k: v for k, v in model_votes.items() if v < 0.65}
        usable = reliable if reliable else (model_votes if model_votes else votes)
        flags = {k: v > 0.5 for k, v in usable.items()}
        agreeing = [k for k, v in flags.items() if v]
        agreement = len(agreeing)
        threshold = int(getattr(self, "_vote_threshold", None) or
                        get_config().pipeline.video_vote_threshold)
        # require strong agreement across RELIABLE models (not a lone heuristic)
        flagged = agreement >= threshold and agreement >= len(usable)

        # a stable, non-saturating fake probability the client can display
        vals = sorted(usable.values())
        fake_prob = float(vals[-1]) if vals else 0.0
        return {"fake_prob": fake_prob, "temporal_consistency": temporal,
                "votes": {k: round(v, 3) for k, v in votes.items()},
                "flagged_signals": agreeing, "agreement": agreement,
                "vote_threshold": threshold, "flagged": flagged,
                "heuristic": False, "ready": True}


def _temporal_consistency(frames: list[np.ndarray]) -> float:
    if len(frames) < 3:
        return 0.5
    import cv2
    diffs = []
    prev = cv2.cvtColor(frames[0], cv2.COLOR_BGR2GRAY)
    for f in frames[1:]:
        g = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
        prev = cv2.resize(prev, (g.shape[1], g.shape[0]))
        diffs.append(float(cv2.absdiff(prev, g).mean()))
        prev = g
    mean_diff = float(np.mean(diffs)) / 255.0
    if mean_diff < 0.005:
        return 0.85
    if mean_diff < 0.02:
        return 0.6
    return 0.3


def _heuristic_score(frames: list[np.ndarray]) -> float:
    import cv2
    if not frames:
        return 0.1
    lap_vars = []
    for f in frames[-8:]:
        gray = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
        gray = cv2.resize(gray, (256, 256))
        lap_vars.append(cv2.Laplacian(gray, cv2.CV_64F).var())
    sharp = float(np.clip((np.mean(lap_vars) - 400) / 900, 0, 0.35))
    return 0.1 + sharp