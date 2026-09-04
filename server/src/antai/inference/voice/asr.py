"""Streaming ASR — Deepgram (hosted) or faster-whisper (local, CTranslate2).

Segments arrive from the ingestion VAD; each ~0.4-2s chunk is transcribed for
low latency. The backend is selected by ``providers.asr`` in config:

  * ``deepgram``        -> Deepgram pre-recorded STT (no local weights loaded)
  * ``faster_whisper``  -> local multilingual model in ``models/asr``

Either way ``transcribe()`` returns the SAME dict shape, so the dispatcher's
per-session language lock and the transcript push path are unchanged.
"""
from __future__ import annotations

import logging
import os
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config

log = logging.getLogger(__name__)


class AsrEngine(BaseEngine):
    name = "asr"

    def _load(self) -> bool:
        if get_config().providers.asr == "deepgram":
            return self._load_deepgram()
        return self._load_faster_whisper()

    # --------------------------------------------------------- Deepgram (API)
    def _load_deepgram(self) -> bool:
        prov = get_config().providers
        if not prov.deepgram_api_key:
            self._unavailable_reason = "asr: DEEPGRAM_API_KEY not set"
            return False
        self._backend = "deepgram"
        self.device = "deepgram-api"
        log.info("ASR backend: Deepgram (model=%s)", prov.deepgram_model)
        return True

    # ----------------------------------------------------- faster-whisper (local)
    def _load_faster_whisper(self) -> bool:
        # Windows cuDNN DLL clash: if CTranslate2 enters the process before
        # torch's first cuDNN use, torch CUDA convs crash with
        # "Could not load symbol cudnnGetLibConfig" (error 127). Warm torch
        # cuDNN FIRST (before importing faster_whisper) so the safe
        # 'torch-first' DLL order always holds.
        try:
            import torch
            if torch.cuda.is_available():
                _x = torch.zeros(1, 3, 16, 16, device="cuda")
                _w = torch.zeros(3, 3, 3, 3, device="cuda")
                torch.nn.functional.conv2d(_x, _w).sum().item()
        except Exception:
            pass
        from faster_whisper import WhisperModel  # lazy (after torch warmup)
        cfg = get_config()
        root = cfg.models.root
        model_dir = Path(root) / "asr"
        if not model_dir.exists():
            return False
        # ASR runs on CPU int8: robust, keeps real-time budget for 1-4s chunks.
        device = "cpu"
        compute = "int8"
        try:
            self.model = WhisperModel(str(model_dir), device=device, compute_type=compute)
            self._backend = "faster_whisper"
            self.device = device
            return True
        except Exception as e:
            log.warning("ASR load failed: %s", e)
            return False

    def transcribe(self, audio: np.ndarray, sample_rate: int = 16000,
                   language: str | None = None) -> dict:
        """audio: float32 mono. Returns {text, language, avg_logprob, ready}."""
        if not self.ready():
            return {"text": "", "language": None, "avg_logprob": 0.0, "ready": False}
        if getattr(self, "_backend", None) == "deepgram":
            from ..providers import deepgram_transcribe
            prov = get_config().providers
            return deepgram_transcribe(
                audio, sample_rate, api_key=prov.deepgram_api_key,
                model=prov.deepgram_model, endpoint=prov.deepgram_endpoint,
                language=language or get_config().pipeline.asr_language)
        # ---- local faster-whisper path ----
        import wave
        import tempfile
        fd, path = tempfile.mkstemp(suffix=".wav")
        try:
            with wave.open(path, "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(sample_rate)
                pcm = (np.clip(audio, -1, 1) * 32767).astype(np.int16)
                w.writeframes(pcm.tobytes())
            lang = language or get_config().pipeline.asr_language
            segments, info = self.model.transcribe(
                path, beam_size=get_config().pipeline.asr_beam_size,
                language=lang, vad_filter=False)
            text = " ".join(s.text.strip() for s in segments)
            return {"text": text, "language": getattr(info, "language", None),
                    "avg_logprob": getattr(info, "average_logprob", 0.0), "ready": True}
        except Exception as e:
            log.warning("ASR transcribe failed: %s", e)
            return {"text": "", "language": None, "avg_logprob": 0.0, "ready": False}
        finally:
            try:
                os.close(fd)
                os.remove(path)
            except OSError:
                pass
