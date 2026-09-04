"""DiCoME (ICML 2026) — secondary frame-level classifier.

Divide-and-Conquer evidential learning: LoRA-CLIP semantic view + beta-VAE
manifold + geometric artifact view, fused with Dempster-Shafer evidence.
Weights: models/video_dicome/dicome-best.ckpt (vendored code in _dicome/).

The ckpt is self-contained (full CLIP backbone + LoRA adapters), so we
instantiate CLIP from config only (no 1.2 GB backbone download) and
overwrite with the ckpt state dict.
"""
from __future__ import annotations

import logging
import sys
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config

log = logging.getLogger(__name__)

_DICOME_DIR = Path(__file__).resolve().parent / "_dicome"


class DicomeEngine(BaseEngine):
    name = "dicome"

    def _load(self) -> bool:
        cfg = get_config()
        ckpt = Path(cfg.models.root) / "video_dicome" / "dicome-best.ckpt"
        if not ckpt.exists():
            log.warning("DiCoME ckpt not downloaded (%s)", ckpt)
            return False
        try:
            import torch
            import yaml
            from transformers import CLIPConfig, CLIPModel, CLIPProcessor

            from ._dicome.config import Config
            from ._dicome.core_model import EvidentialFusionModel

            # prevent the 1.2GB CLIP backbone download: build from config only
            clip_cfg = CLIPConfig.from_pretrained("openai/clip-vit-large-patch14")
            _orig = CLIPModel.from_pretrained
            CLIPModel.from_pretrained = classmethod(
                lambda cls, *a, **k: CLIPModel(clip_cfg))
            try:
                model_cfg = Config(**yaml.safe_load(
                    open(_DICOME_DIR / "dicome_default.yaml", encoding="utf-8")))
                self.model = EvidentialFusionModel(model_cfg)
            finally:
                CLIPModel.from_pretrained = _orig

            # small processor files only (configs, few KB)
            self.processor = CLIPProcessor.from_pretrained(
                "openai/clip-vit-large-patch14")

            state = torch.load(str(ckpt), map_location="cpu", weights_only=False)
            sd = state.get("state_dict", state)
            sd = {k[len("model."):] if k.startswith("model.") else k: v
                  for k, v in sd.items()}
            # the released ckpt predates a repo refactor; remap old names
            remap = {
                "beta_vae.": "semantic_manifold_projector.",
                "evidential_head_1.": "semantic_head.",
                "evidential_head_2.": "artifact_head.",
                "view1_norm.": "semantic_norm.",
                "view2_norm.": "artifact_norm.",
            }
            for old, new in remap.items():
                sd = {k.replace(old, new, 1) if old in k else k: v
                      for k, v in sd.items()}
            self.model.load_state_dict(sd, strict=True)
            self.model.eval()
            self.device = self._device_for()
            self.model.to(self.device)
            if self.device == "cuda":
                self.model = self.model.half()
            self._ckpt = str(ckpt)
            return True
        except Exception as e:
            log.warning("DiCoME load failed: %s", e)
            return False

    def analyze(self, frame_bgr: np.ndarray) -> dict:
        """Returns {fake_prob (softmax over fused evidence, class 1),
        uncertainty, heuristic, ready}."""
        if not self.ready():
            return {"fake_prob": None, "heuristic": True, "ready": False}
        try:
            import torch
            from PIL import Image
            rgb = frame_bgr[:, :, ::-1]
            inputs = self.processor(images=Image.fromarray(rgb),
                                    return_tensors="pt")["pixel_values"]
            if self.device == "cuda":
                inputs = inputs.half().to(self.device)
            else:
                inputs = inputs.float()
            with torch.no_grad():
                fused_evidence = self.model(inputs)[0]
            alpha = fused_evidence + 1
            strength = alpha.sum(dim=1, keepdim=True)
            probs = alpha / strength
            fake_prob = float(probs[0, 1].cpu())
            uncertainty = float((2.0 / strength[0, 0]).cpu())
            return {"fake_prob": fake_prob, "uncertainty": uncertainty,
                    "heuristic": False, "ready": True}
        except Exception as e:
            log.warning("DiCoME analyze failed: %s", e)
            return {"fake_prob": None, "heuristic": True, "ready": True}