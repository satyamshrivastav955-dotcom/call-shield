"""LLM serving — Groq (hosted) or llama-cpp-python (local Qwen2.5 GGUF).

Backend is selected by ``providers.llm`` in config:

  * ``groq``   -> Groq chat completions (OpenAI-compatible); no GGUF loaded
  * ``local``  -> self-hosted Qwen2.5 GGUF via llama-cpp-python

``complete()`` / ``complete_stream()`` keep the same signatures and return
types, so the reasoner (verdict / live guidance / post-call report) is
unchanged.
"""
from __future__ import annotations

import logging
import threading
from pathlib import Path

from ..hub import BaseEngine
from ...config import get_config

log = logging.getLogger(__name__)


class LlmEngine(BaseEngine):
    name = "llm"

    def _load(self) -> bool:
        if get_config().providers.llm == "groq":
            if self._load_groq():
                return True
            log.warning("Groq not available (%s); falling back to local GGUF", self._unavailable_reason)
        return self._load_local()

    # -------------------------------------------------------------- Groq (API)
    def _load_groq(self) -> bool:
        prov = get_config().providers
        if not prov.groq_api_key:
            self._unavailable_reason = "llm: GROQ_API_KEY not set"
            return False
        self._backend = "groq"
        # kept for interface parity with the local path (HTTP is thread-safe,
        # but callers never inspect this)
        self._lock = threading.Lock()
        self.device = "groq-api"
        log.info("LLM backend: Groq (model=%s)", prov.groq_model)
        return True

    # ------------------------------------------------------ llama-cpp (local)
    def _load_local(self) -> bool:
        cfg = get_config()
        size = cfg.models.llm_size  # 3b | 1.5b
        llm_dir = Path(cfg.models.root) / "llm"
        if not llm_dir.exists():
            return False
        candidates = sorted(llm_dir.glob(f"*{size}*.gguf")) + sorted(llm_dir.glob("*.gguf"))
        if not candidates:
            return False
        path = str(candidates[0])
        try:
            from llama_cpp import Llama
            n_gpu = self._gpu_layers()
            kwargs = dict(model_path=path, n_ctx=2048, n_gpu_layers=n_gpu,
                          verbose=False)
            self.model = Llama(**kwargs)
            # llama-cpp's chat completion is NOT thread-safe: serialize every
            # call so the verdict + live-guidance + report paths can never run
            # concurrently and corrupt/hang the model.
            self._lock = threading.Lock()
            self._backend = "local"
            self.path = path
            self.device = "cuda" if n_gpu > 0 else "cpu"
            log.info("LLM loaded: %s on %s", path, self.device)
            return True
        except Exception as e:
            log.warning("LLM load failed: %s", e)
            return False

    def _gpu_layers(self) -> int:
        try:
            import torch
            from llama_cpp import llama_supports_gpu_offload
            if torch.cuda.is_available() and self._device_for() == "cuda" \
                    and llama_supports_gpu_offload():
                return -1  # offload all layers
        except Exception:
            pass
        return 0

    def complete(self, messages: list[dict], max_tokens: int = 256,
                 temperature: float = 0.3) -> str:
        if not self.ready():
            return ""
        if getattr(self, "_backend", None) == "groq":
            from ..providers import groq_chat
            prov = get_config().providers
            return groq_chat(messages, api_key=prov.groq_api_key,
                             model=prov.groq_model, endpoint=prov.groq_endpoint,
                             max_tokens=max_tokens, temperature=temperature)
        try:
            with self._lock:
                resp = self.model.create_chat_completion(
                    messages=messages, max_tokens=max_tokens, temperature=temperature)
            return resp["choices"][0]["message"]["content"].strip()
        except Exception as e:
            log.warning("LLM complete failed: %s", e)
            return ""

    def complete_stream(self, messages: list[dict], max_tokens: int = 256,
                        temperature: float = 0.3):
        if not self.ready():
            return
        if getattr(self, "_backend", None) == "groq":
            from ..providers import groq_chat_stream
            prov = get_config().providers
            yield from groq_chat_stream(
                messages, api_key=prov.groq_api_key, model=prov.groq_model,
                endpoint=prov.groq_endpoint, max_tokens=max_tokens,
                temperature=temperature)
            return
        try:
            with self._lock:
                stream = self.model.create_chat_completion(
                    messages=messages, max_tokens=max_tokens, temperature=temperature,
                    stream=True)
                for chunk in stream:
                    delta = chunk["choices"][0]["delta"].get("content")
                    if delta:
                        yield delta
        except Exception as e:
            log.warning("LLM stream failed: %s", e)
