"""CommunityForensics DeepfakeDet-ViT (CVPR 2025) — primary frame-level
AI-image detector. Single-logit sigmoid output; input 440->crop 384 CLIP-norm.
Weights: models/video_community_vit (setup/download_models.py).
"""
from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config

log = logging.getLogger(__name__)


class CommunityVitEngine(BaseEngine):
    name = "community_vit"

    def _load(self) -> bool:
        cfg = get_config()
        model_dir = Path(cfg.models.root) / "video_community_vit"
        if not (model_dir / "config.json").exists():
            log.warning("CommunityForensics-ViT not downloaded (%s)", model_dir)
            return False
        try:
            from transformers import ViTForImageClassification, ViTImageProcessor
            self.processor = ViTImageProcessor.from_pretrained(str(model_dir))
            self.model = ViTForImageClassification.from_pretrained(str(model_dir))
            self.model.eval()
            self.device = self._device_for()
            if self.device == "cuda":
                self.model = self.model.to("cuda")
            # config fix markers (July 2026 corrected checkpoint)
            c = self.model.config
            if getattr(c, "num_labels", 2) != 1:
                log.warning("CF-ViT: expected num_labels=1, got %s", c.num_labels)
            return True
        except Exception as e:
            log.warning("CF-ViT load failed: %s", e)
            return False

    def analyze(self, frame_bgr: np.ndarray) -> dict:
        """Returns {fake_prob (sigmoid), heuristic, ready}."""
        if not self.ready():
            return {"fake_prob": None, "heuristic": True, "ready": False}
        try:
            import torch
            from PIL import Image
            rgb = frame_bgr[:, :, ::-1]
            img = Image.fromarray(rgb)
            inputs = self.processor(images=img, return_tensors="pt")
            if self.device == "cuda":
                inputs = {k: v.to("cuda") for k, v in inputs.items()}
            with torch.no_grad():
                logits = self.model(**inputs).logits
            if logits.shape[-1] == 1:
                fake_prob = float(torch.sigmoid(logits.float()).cpu().squeeze())
            else:
                p = torch.softmax(logits.float(), dim=-1)[0]
                fake_prob = float(p[1])
            return {"fake_prob": fake_prob, "heuristic": False, "ready": True}
        except Exception as e:
            log.warning("CF-ViT analyze failed: %s", e)
            return {"fake_prob": None, "heuristic": True, "ready": True}