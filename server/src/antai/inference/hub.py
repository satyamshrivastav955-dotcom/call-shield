"""Model hub: lazy-loads each engine once, respects config device placement,
and degrades gracefully when a model's weights are not yet downloaded.

Every engine exposes:
  .available() -> bool
  .device -> str
and detection methods return plain dicts with scores in [0,1] (or 0-100
where the API documents it), plus a "model_ready" flag so the orchestrator
can cite "signal unavailable" rather than a wrong value.
"""
from __future__ import annotations

import logging
import threading
from typing import Optional

from ..config import get_config

log = logging.getLogger(__name__)


class BaseEngine:
    name = "base"
    fallback_enabled = True   # engine may fall back to heuristic/stub when weights missing

    def __init__(self):
        self._loaded = False
        self._load_failed = False
        self._load_lock = threading.Lock()
        self.device = "cpu"
        self._unavailable_reason: str | None = None

    # ----- lazy lifecycle -----
    def load(self) -> bool:
        with self._load_lock:
            if self._loaded:
                return True
            # A previous attempt failed definitively (missing deps / weights /
            # bad checkpoint). Do NOT retry on every ready() call: the detector
            # nodes call ready() on every eval, and re-attempting a heavy
            # (multi-hundred-MB) load each time would stall the pipeline. The
            # failure is sticky until the process restarts.
            if self._load_failed:
                return False
            try:
                ok = self._load()
                self._loaded = ok
                if not ok:
                    self._load_failed = True
                    self._unavailable_reason = self._unavailable_reason \
                        or f"{self.name}: weights not found"
                return ok
            except Exception as e:  # pragma: no cover
                log.warning("%s failed to load: %s", self.name, e)
                self._loaded = False
                self._load_failed = True
                self._unavailable_reason = f"{self.name}: {e}"
                return False

    def _load(self) -> bool:  # override
        raise NotImplementedError

    def available(self) -> bool:
        return self._loaded

    def ready(self) -> bool:
        return self.load()

    def unavailable_reason(self) -> str | None:
        return self._unavailable_reason

    def _device_for(self) -> str:
        cfg = get_config()
        if cfg.models.device == "auto":
            try:
                import torch
                return "cuda" if torch.cuda.is_available() else "cpu"
            except Exception:
                return "cpu"
        return cfg.models.device


class ModelHub:
    def __init__(self):
        self._engines: dict[str, BaseEngine] = {}
        self._lock = threading.Lock()

    def register(self, name: str, engine: BaseEngine):
        with self._lock:
            self._engines[name] = engine
        return engine

    def get(self, name: str) -> Optional[BaseEngine]:
        with self._lock:
            return self._engines.get(name)

    def ensure(self, name: str):
        eng = self.get(name)
        if eng is not None:
            eng.ready()
        return eng

    def summary(self) -> dict:
        return {n: (e.available(), e.unavailable_reason()) for n, e in self._engines.items()}


_hub: ModelHub | None = None


def get_hub() -> ModelHub:
    global _hub
    if _hub is None:
        _hub = ModelHub()
        _register_all(_hub)
    return _hub


def _register_all(hub: ModelHub):
    cfg = get_config()
    from .voice.asr import AsrEngine
    from .voice.deepfake_voice import VoiceDeepfakeEngine
    from .voice.speaker_verify import SpeakerVerifyEngine
    from .video.face import FaceEngine
    from .video.deepfake_video import VideoDeepfakeEngine
    from .video.community_vit import CommunityVitEngine
    from .video.dicome import DicomeEngine
    from .video.decof import DeCoFEngine
    from .video.lipsync import LipsyncEngine
    from .text.scam_pattern import ScamPatternEngine
    from .text.behavioral import BehavioralEngine
    from .urgency.scorer import UrgencyEngine
    from .request.intent import IntentEngine
    from .llm.llm import LlmEngine

    enabled = cfg.models.engines
    registry = [
        ("asr", AsrEngine, "asr"),
        ("voice_deepfake", VoiceDeepfakeEngine, "voice_deepfake"),
        ("speaker_verify", SpeakerVerifyEngine, "speaker_verify"),
        ("face", FaceEngine, "face"),
        ("video_deepfake", VideoDeepfakeEngine, "video_deepfake"),
        ("community_vit", CommunityVitEngine, "community_vit"),
        ("dicome", DicomeEngine, "dicome"),
        ("decof", DeCoFEngine, "decof"),
        ("lipsync", LipsyncEngine, "lipsync"),
        ("scam_pattern", ScamPatternEngine, "scam_pattern"),
        ("behavioral", BehavioralEngine, "behavioral"),
        ("urgency", UrgencyEngine, "urgency"),
        ("intent", IntentEngine, "intent"),
        ("llm", LlmEngine, "llm"),
    ]
    for name, cls, cfg_key in registry:
        if enabled.get(cfg_key, {}).get("enabled", True):
            hub.register(name, cls())
