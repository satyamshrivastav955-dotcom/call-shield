"""Lip-sync consistency: mouth-region motion vs audio energy envelope.

Deepfake videos often have audio/visual desync or artificial lip motion.
We compute the correlation between mouth-openness (from FaceMesh landmarks)
and the audio energy envelope over matching time windows. High correlation =
in sync (natural). Low correlation = mismatch (deepfake signal).

Stateless per call: the orchestrator feeds aligned (frames, audio) windows.
"""
from __future__ import annotations

import logging

import numpy as np

from ..hub import BaseEngine

log = logging.getLogger(__name__)


class LipsyncEngine(BaseEngine):
    name = "lipsync"

    def _load(self) -> bool:
        self.device = "cpu"
        return True  # no weights needed

    def score(self, mouth_openness: list[float], audio_envelope: list[float]) -> dict:
        """mouth_openness: per-frame mouth openness; audio_envelope: per-frame
        audio energy. Returns {sync_score (0..1), ready}."""
        if len(mouth_openness) < 4 or len(audio_envelope) < 4:
            return {"sync_score": None, "ready": self.ready()}
        n = min(len(mouth_openness), len(audio_envelope))
        m = np.asarray(mouth_openness[:n], dtype=np.float32)
        a = np.asarray(audio_envelope[:n], dtype=np.float32)
        # normalize
        m = (m - m.mean()) / (m.std() + 1e-6)
        a = (a - a.mean()) / (a.std() + 1e-6)
        # small lag search (speech -> lip motion lag ~ few frames)
        best = -1.0
        for lag in range(-3, 4):
            if lag >= 0:
                ma, aa = m[: n - lag], a[lag:]
            else:
                ma, aa = m[-lag:], a[: n + lag]
            if len(ma) < 4:
                continue
            c = float(np.corrcoef(ma, aa)[0, 1]) if ma.std() > 0 and aa.std() > 0 else 0.0
            best = max(best, c)
        # map correlation [-1,1] -> sync score [0,1]; negative corr = strong desync
        sync = float(np.clip((best + 1) / 2, 0, 1))
        return {"sync_score": sync, "ready": True}
