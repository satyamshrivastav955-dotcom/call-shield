"""Urgency / emotional-pressure scorer (0-100, kept as its own signal)."""
from __future__ import annotations

import logging
from pathlib import Path

from ..hub import BaseEngine
from ...config import get_config
from ...ingestion.text_tap import TEXT_PATTERNS

log = logging.getLogger(__name__)

PRESSURE = TEXT_PATTERNS["pressure"]
EXTRA_PRESSURE = [
    "right now", "asap", "immediately", "don't tell", "dont tell", "keep it secret",
    "don't tell anyone", "this is confidential", "act now", "before it's too late",
    "only today", "final warning", "legal action", "arrested", "frozen",
    "kisi ko mat batana", "abhi", "turant", "jaldi", "sirf aaj", "aakhri",
    "bail", "court", "fine", "penalty", "deleted", "expired",
    "urgent reply", "immediate action", "confirm right away", "time is running out",
]
TIME_PRESSURE = ["by today", "within an hour", "in 5 minutes", "before", "tonight",
                 "within 24 hours", "by tomorrow", "aaj tak", "ek ghante me"]
THREAT = ["police will", "arrested", "court case", "legal", "jail", "frozen your",
          "account closed", "suspended", "lose your", "will lose", "fine", "penalty",
          "threat", "case file", "gaon me case"]


class UrgencyEngine(BaseEngine):
    name = "urgency"

    def _load(self) -> bool:
        cfg = get_config()
        model_dir = Path(cfg.models.root) / "classifiers" / "urgency"
        if not model_dir.exists():
            return False
        try:
            from transformers import AutoModelForSequenceClassification, AutoTokenizer
            self._tok = AutoTokenizer.from_pretrained(str(model_dir))
            self._model = AutoModelForSequenceClassification.from_pretrained(str(model_dir))
            self._model.eval()
            self.device = self._device_for()
            if self.device == "cuda":
                self._model = self._model.to("cuda")
            return True
        except Exception as e:
            log.warning("urgency model load failed: %s", e)
            return False

    def score(self, text: str) -> dict:
        """Returns {urgency (0-100), factors, heuristic, ready}."""
        if self.ready():
            try:
                import torch
                inputs = self._tok(text, truncation=True, max_length=256,
                                   return_tensors="pt")
                if self.device == "cuda":
                    inputs = {k: v.to("cuda") for k, v in inputs.items()}
                with torch.no_grad():
                    logits = self._model(**inputs).logits
                val = float(logits.float().squeeze().cpu().numpy())
                val = max(0.0, min(1.0, val))
                return {"urgency": round(val * 100, 1), "heuristic": False,
                        "ready": True, "factors": {"model": True}}
            except Exception as e:
                log.warning("urgency infer failed: %s", e)
        return self._heuristic(text)

    def _heuristic(self, text: str) -> dict:
        lower = text.lower()
        factors = {}
        hits = sum(1 for k in PRESSURE if k in lower)
        ex = sum(1 for k in EXTRA_PRESSURE if k in lower)
        time_h = sum(1 for k in TIME_PRESSURE if k in lower)
        threat_h = sum(1 for k in THREAT if k in lower)
        excl = text.count("!") / (len(text) + 1)
        factors.update({"pressure_phrases": hits, "strong_phrases": ex,
                        "time_constraints": time_h, "threats": threat_h,
                        "exclamation_ratio": round(excl, 3)})
        score = 10
        score += hits * 12 + ex * 18 + time_h * 14 + threat_h * 22
        score += min(15, excl * 600)
        return {"urgency": round(min(100, score), 1), "factors": factors,
                "heuristic": True, "ready": self.ready()}
