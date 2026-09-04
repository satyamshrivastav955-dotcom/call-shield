"""Scam-pattern text classifier.

Primary: a DistilBERT-multilingual classifier fine-tuned on our bilingual
synthetic scam corpus (setup/train_classifiers.py), weights at
models/classifiers/scam_pattern.

Fallback: weighted keyword/scoring heuristic over scam scripts
(family emergency, OTP, bank, prize, job, romance, remote-access, gift-card)
marked `heuristic=True`.
"""
from __future__ import annotations

import logging
from pathlib import Path

from ..hub import BaseEngine
from ...config import get_config
from ...ingestion.text_tap import TEXT_PATTERNS

log = logging.getLogger(__name__)

SCAM_TYPES = ["family_emergency", "otp", "bank", "prize", "job_offer",
              "romance", "remote_access", "gift_card"]

# Extended phrase sets per scam type. These drive ONLY the fallback heuristic
# (used when the trained classifier can't load); the classifier is primary.
# Kept deliberately scam-SPECIFIC: generic family/among-friends words ("beta",
# "papa", "dear", "task", "limit", "urgent", "bank", "congratulations", ...)
# were removed because they appear constantly in normal conversation and were
# the source of false scam alerts on ordinary messages.
_PHRASES = {
    "family_emergency": ["accident", "in the hospital", "police station", "got arrested",
                         "need bail", "bail money", "pakda gaya", "police me", "hospital me",
                         "met with an accident", "in big trouble", "don't tell mom",
                         "don't tell dad"],
    "otp": ["otp", "verification code", "one-time password", "share the code",
            "code batao", "otp bhej", "send the otp", "confirm the code", "read me the code"],
    "bank": ["account frozen", "account suspended", "account blocked", "card blocked",
             "kyc update", "kyc expired", "aadhaar link", "update your kyc",
             "verify your account", "unauthorized transaction"],
    "prize": ["lottery", "you won", "you have won", "prize money", "lucky draw",
              "jackpot", "claim your prize", "inaam", "selected as winner"],
    "job_offer": ["work from home", "paid tasks", "telegram job", "part time job",
                  "daily income", "earn from home", "prepaid task", "recharge task"],
    "romance": ["marry me", "i am stranded", "send money for ticket",
                "need money for a flight", "stuck at customs"],
    "remote_access": ["teamviewer", "anydesk", "install this app", "screen share",
                      "remote access", "share your screen", "quick support"],
    "gift_card": ["gift card", "amazon card", "google play card", "itunes card",
                  "gift card code", "app store card", "steam card"],
}


class ScamPatternEngine(BaseEngine):
    name = "scam_pattern"

    def _load(self) -> bool:
        cfg = get_config()
        model_dir = Path(cfg.models.root) / "classifiers" / "scam_pattern"
        if not model_dir.exists():
            return False
        try:
            from transformers import AutoModelForSequenceClassification, AutoTokenizer
            self._tokenizer = AutoTokenizer.from_pretrained(str(model_dir))
            self._model = AutoModelForSequenceClassification.from_pretrained(str(model_dir))
            dev = self._device_for()
            if dev == "cuda":
                self._model = self._model.half().to("cuda")
            self._model.eval()
            self.device = dev
            return True
        except Exception as e:
            log.warning("scam classifier load failed: %s", e)
            return False

    def analyze(self, text: str) -> dict:
        """Returns {scam_prob, scam_type, heuristic, ready}."""
        if not self.ready():
            return self._heuristic(text, heuristic=True, ready=False)
        try:
            import torch
            inputs = self._tokenizer(text, truncation=True, max_length=256,
                                     return_tensors="pt")
            if self.device == "cuda":
                inputs = {k: v.to("cuda") for k, v in inputs.items()}
            with torch.no_grad():
                logits = self._model(**inputs).logits
            probs = torch.softmax(logits.float(), dim=-1)[0].cpu().numpy()
            # trained scheme: label 0 = benign, labels 1..8 = scam types
            scam_prob = float(1.0 - probs[0])
            idx = int(probs[1:].argmax()) + 1
            scam_type = SCAM_TYPES[idx - 1] if scam_prob > 0.5 else None
            if scam_prob <= 0.5:
                scam_type = None
            return {"scam_prob": scam_prob, "scam_type": scam_type,
                    "heuristic": False, "ready": True}
        except Exception as e:
            log.warning("scam classifier infer failed: %s", e)
            return self._heuristic(text, heuristic=True, ready=True)

    def _heuristic(self, text: str, heuristic=True, ready=False) -> dict:
        lower = text.lower()
        scores = {}
        for stype, kws in _PHRASES.items():
            hits = sum(1 for k in kws if k in lower)
            scores[stype] = hits
        total = sum(scores.values())
        if total == 0:
            return {"scam_prob": 0.05, "scam_type": None, "heuristic": heuristic,
                    "ready": ready}
        top_type = max(scores, key=scores.get)
        # 1 specific hit stays modest (~0.33) so a lone keyword can't by itself
        # push the fused risk into an alerting band; 2+ hits or pressure escalate.
        base = 0.15 + 0.18 * scores[top_type]
        pressure = sum(1 for k in TEXT_PATTERNS["pressure"] if k in lower)
        base += min(0.25, 0.06 * pressure)
        return {"scam_prob": min(0.95, base), "scam_type": top_type,
                "heuristic": heuristic, "ready": ready}
