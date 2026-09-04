"""Behavioral-deviation model.

Compares a message's embedding against the sender's rolling profile
(mean embedding + lexical style stats stored server-side). A contact tagged
"Dad" suddenly writing in a different register asking for money -> high
deviation.

Primary: sentence-transformers paraphrase-multilingual-MiniLM-L12-v2.
Fallback: lexical style stats (no transformer weights needed).
"""
from __future__ import annotations

import logging
import math
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config
from ...storage import get_db

log = logging.getLogger(__name__)


class BehavioralEngine(BaseEngine):
    name = "behavioral"

    def _load(self) -> bool:
        from sentence_transformers import SentenceTransformer  # lazy
        cfg = get_config()
        # load from the local download dir (setup/download_models.py) only;
        # never hit the network at runtime
        local_dir = Path(cfg.models.root) / "behavioral_embeddings"
        if not (local_dir / "config.json").exists():
            log.warning("behavioral embeddings not downloaded (%s)", local_dir)
            return False
        try:
            self._encoder = SentenceTransformer(str(local_dir), device="cpu")
            self.device = "cpu"
            return True
        except Exception as e:
            log.warning("behavioral model load failed: %s", e)
            return False

    # ---- profile maintenance ----
    def _empty_profile(self) -> dict:
        return {"n": 0, "mean_emb": None, "avg_len": 8.0, "avg_excl": 0.2,
                "avg_caps_ratio": 0.1, "last_updated": None}

    def embed_text(self, text: str) -> np.ndarray | None:
        if not self.ready():
            return None
        try:
            return self._encoder.encode(text, normalize_embeddings=True)
        except Exception:
            return None

    def update_profile(self, owner_id: int, text: str, emb: np.ndarray | None):
        db = get_db()
        prof = db.get_behavior_profile(owner_id) or self._empty_profile()
        if emb is not None:
            if prof.get("mean_emb") is None:
                prof["mean_emb"] = emb.tolist()
                prof["n"] = 1
            else:
                n = prof.get("n", 0) + 1
                old = np.asarray(prof["mean_emb"])
                prof["mean_emb"] = ((old * n + emb) / (n + 1)).tolist()
                prof["n"] = n + 1
        prof["avg_len"] = 0.9 * prof.get("avg_len", 8.0) + 0.1 * len(text)
        prof["avg_excl"] = 0.9 * prof.get("avg_excl", 0.2) + 0.1 * (text.count("!") / (len(text) + 1))
        prof["avg_caps_ratio"] = 0.9 * prof.get("avg_caps_ratio", 0.1) + 0.1 * (
            sum(1 for c in text if c.isupper()) / (len(text) + 1))
        db.save_behavior_profile(owner_id, prof)
        return prof

    def deviation(self, owner_id: int, text: str, emb: np.ndarray | None) -> dict:
        """Returns {deviation (0..1), style_gap, embedding_gap, ready}."""
        db = get_db()
        prof = db.get_behavior_profile(owner_id) or self._empty_profile()
        n = prof.get("n", 0)
        # embedding gap
        emb_gap = 0.5
        if emb is not None and prof.get("mean_emb") is not None and n > 0:
            mean = np.asarray(prof["mean_emb"])
            emb_gap = 1.0 - float(np.dot(emb, mean) /
                                  (np.linalg.norm(emb) * np.linalg.norm(mean) + 1e-9))
        # lexical style gap
        len_ratio = min(3.0, len(text) / (prof.get("avg_len", 8.0) + 1e-6))
        excl = min(1.0, text.count("!") / (len(text) + 1))
        caps = sum(1 for c in text if c.isupper()) / (len(text) + 1)
        style_gap = 0.4 * float(abs(len_ratio - 1.0)) + \
                    0.3 * float(abs(excl - prof.get("avg_excl", 0.2))) + \
                    0.3 * float(abs(caps - prof.get("avg_caps_ratio", 0.1)))
        deviation = 0.5 * emb_gap + 0.5 * min(1.0, style_gap * 2.0)
        if n == 0:
            deviation = 0.0  # no baseline yet -> no deviation signal
        return {"deviation": float(np.clip(deviation, 0, 1)), "style_gap": style_gap,
                "embedding_gap": emb_gap, "samples": n, "ready": self.ready()}
