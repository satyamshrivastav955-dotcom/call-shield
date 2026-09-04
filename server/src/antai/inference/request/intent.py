"""Request / intent classifier.

Does the text contain an actionable ASK? -> money | otp | credential |
remote-access | link | none. Drives the Freeze/Intercept mechanism.
"""
from __future__ import annotations

import logging
import re
from pathlib import Path

from ..hub import BaseEngine
from ...config import get_config
from ...ingestion.text_tap import TEXT_PATTERNS

log = logging.getLogger(__name__)

REQUEST_TYPES = ["money", "otp", "credential", "remote-access", "link", "none"]

_KEYWORDS = {
    "money": ["send money", "transfer", "send me", "pay", "deposit", "bank transfer",
              "paise bhej", "rupaye", "transfer kar", "payment karo", "cash send",
              "money transfer", "upi", "gpay", "paytm", "neft", "give me the money"],
    "otp": ["otp", "verification code", "one-time password", "share the otp",
            "send the otp", "code batao", "otp bhej", "the code", "6 digit code"],
    "credential": ["password", "username", "login id", "user id", "account number",
                   "bank details", "card number", "cvv", "aadhaar", "pan number",
                   "password batao", "id and password", "internet banking"],
    "remote-access": ["teamviewer", "anydesk", "install the app", "screen share",
                      "remote access", "download the app", "allow access",
                      "anydesh install", "screen share karo", "app install karo"],
    "link": ["click this link", "open the link", "click here", "visit this site",
             "download link", "tap the link", "link par click karo", "http", "www.",
             "bit.ly", "tinyurl"],
}


class IntentEngine(BaseEngine):
    name = "intent"

    def _load(self) -> bool:
        cfg = get_config()
        model_dir = Path(cfg.models.root) / "classifiers" / "intent"
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
            log.warning("intent model load failed: %s", e)
            return False

    def classify(self, text: str) -> dict:
        """Returns {request_detected, request_type, confidence, heuristic, ready}."""
        if self.ready():
            try:
                import torch
                inputs = self._tok(text, truncation=True, max_length=256,
                                   return_tensors="pt")
                if self.device == "cuda":
                    inputs = {k: v.to("cuda") for k, v in inputs.items()}
                with torch.no_grad():
                    logits = self._model(**inputs).logits
                probs = torch.softmax(logits.float(), dim=-1)[0].cpu().numpy()
                idx = int(probs.argmax())
                rtype = REQUEST_TYPES[idx] if idx < len(REQUEST_TYPES) else "none"
                conf = float(probs[idx])
                return {"request_detected": rtype != "none", "request_type": rtype,
                        "confidence": conf, "heuristic": False, "ready": True}
            except Exception as e:
                log.warning("intent infer failed: %s", e)
        return self._heuristic(text)

    def _heuristic(self, text: str) -> dict:
        lower = text.lower()
        scores = {}
        for rtype, kws in _KEYWORDS.items():
            scores[rtype] = sum(1 for k in kws if k in lower)
        scores["none"] = 0
        total = sum(scores.values())
        if total == 0:
            return {"request_detected": False, "request_type": "none",
                    "confidence": 0.6, "heuristic": True, "ready": self.ready()}
        top = max(scores, key=scores.get)
        conf = min(0.95, 0.4 + 0.2 * scores[top])
        if top == "none":
            return {"request_detected": False, "request_type": "none",
                    "confidence": 0.6, "heuristic": True, "ready": self.ready()}
        return {"request_detected": True, "request_type": top, "confidence": conf,
                "heuristic": True, "ready": self.ready()}
