"""DeCoF (arXiv 2402.02085) — temporal frame-consistency detector for fully
AI-generated video (Sora/Veo/Kling). Auto-enables the moment the AIGVDBench
release publishes weights (models/video_decof/). Until then: unavailable.
"""
from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config

log = logging.getLogger(__name__)


class DeCoFEngine(BaseEngine):
    name = "decof"

    def _load(self) -> bool:
        cfg = get_config()
        w = Path(cfg.models.root) / "video_decof"
        if not (w / "decof.ckpt").exists():
            log.info("DeCoF weights not released yet (AIGVDBench pending) -> inactive")
            return False
        try:
            import torch
            from transformers import CLIPProcessor
            from _decof.model import Detector  # vendored when weights land
            sys.path_insert = Path(__file__).resolve().parent / "_decof"
            import sys
            sys.path.insert(0, str(sys.path_insert))
            self.processor = CLIPProcessor.from_pretrained("openai/clip-vit-large-patch14")
            self.model = Detector()
            state = torch.load(str(w / "decof.ckpt"), map_location="cpu",
                               weights_only=False)
            self.model.load_state_dict(state.get("state_dict", state), strict=False)
            self.model.eval()
            self.model.to(self._device_for())
            self.device = self._device_for()
            return True
        except Exception as e:
            log.warning("DeCoF load failed: %s", e)
            return False

    def score(self, frames_bgr: list[np.ndarray]) -> dict:
        if not self.ready() or len(frames_bgr) < 8:
            return {"fake_prob": None, "ready": self.ready()}
        try:
            import torch
            from PIL import Image
            sample = [frames_bgr[i] for i in
                      np.linspace(0, len(frames_bgr) - 1, 8).astype(int)]
            xs = []
            for f in sample:
                img = Image.fromarray(f[:, :, ::-1])
                xs.append(self.processor(images=img, return_tensors="pt")["pixel_values"][0])
            x = torch.stack(xs).unsqueeze(0)  # (1, 8, 3, 224, 224)
            if self.device == "cuda":
                x = x.half().to(self.device)
            with torch.no_grad():
                logits = self.model(x)
            p = torch.softmax(logits.float(), dim=-1)[0]
            return {"fake_prob": float(p[1]), "ready": True}
        except Exception as e:
            log.warning("DeCoF score failed: %s", e)
            return {"fake_prob": None, "ready": True}