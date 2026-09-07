"""LLM serving — Groq / Gemini / OpenRouter / llama-cpp-python (local Qwen GGUF).

Backend is selected per-call from ``providers.llm_fallback_chain`` in config
(default: groq -> gemini -> openrouter -> local). The first entry that
returns non-empty text wins; entries without a key are skipped. ``local``
needs no key, so it is the always-available final entry and lazy-loads the
GGUF on first use (tens of seconds for the 3B model — acceptable as the
last-ditch fallback). Setting ``providers.llm: local`` preloads the GGUF at
startup instead so the chain's tail is warm from the start.

``complete()`` / ``complete_stream()`` keep the same signatures and return
types, so the reasoner (verdict / live guidance / post-call report) is
unchanged. Streaming falls back to the next backend only if the first fails
BEFORE yielding any token (never mid-stream — a half-delivered answer that
switches backends is worse than a clean template).
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

    def __init__(self):
        super().__init__()
        # which chain entry produced the last successful complete(); exposed
        # (not underscore-prefixed) so GET /api/debug/models reports it and a
        # silent fallback to a weaker backend is visible.
        self.last_backend_used: str | None = None
        self._lock: threading.Lock | None = None

    # ------------------------------------------------------------------ chain
    def _chain(self) -> list[str]:
        prov = get_config().providers
        chain = list(prov.llm_fallback_chain)
        if "local" not in chain:
            chain.append("local")   # local is the always-available last resort
        return chain

    def _load(self) -> bool:
        prov = get_config().providers
        # Engine is ready when ANY chain entry is usable. Keys are cheap to
        # check; the local GGUF is only validated by existence here (actual
        # load stays lazy), except when providers.llm == "local", which
        # preloads it at startup so the chain's tail is warm.
        usable = bool(prov.groq_api_key or prov.gemini_api_key
                      or prov.openrouter_api_key)
        if "local" in self._chain():
            if prov.llm == "local":
                if self._load_local():
                    usable = True
            elif self._gguf_exists():
                usable = True   # local stays lazy; loaded on first use
        if usable:
            if not getattr(self, "_backend", None):
                self.device = "api-chain"
            log.info("LLM fallback chain: %s", " -> ".join(self._chain()))
            return True
        self._unavailable_reason = "llm: no API keys set and no local GGUF"
        return False

    def _gguf_exists(self) -> bool:
        cfg = get_config()
        llm_dir = Path(cfg.models.root) / "llm"
        return llm_dir.exists() and bool(list(llm_dir.glob("*.gguf")))

    # -------------------------------------------------------------- Groq (API)
    # (kept from the pre-chain implementation: Groq needs no load, just a key)
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
        if getattr(self, "_backend", None) == "local":
            return True   # already loaded
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
            log.info("LLM loaded (fallback): %s on %s", path, self.device)
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

    def _complete_backend(self, backend: str, messages: list[dict],
                          max_tokens: int, temperature: float) -> str:
        """Run one chain entry. Returns "" on failure/skip so the chain moves on."""
        prov = get_config().providers
        from ..providers import gemini_chat, groq_chat, openrouter_chat
        if backend == "groq" and prov.groq_api_key:
            return groq_chat(messages, api_key=prov.groq_api_key,
                             model=prov.groq_model, endpoint=prov.groq_endpoint,
                             max_tokens=max_tokens, temperature=temperature)
        if backend == "gemini" and prov.gemini_api_key:
            return gemini_chat(messages, api_key=prov.gemini_api_key,
                               model=prov.gemini_model, endpoint=prov.gemini_endpoint,
                               max_tokens=max_tokens, temperature=temperature)
        if backend == "openrouter" and prov.openrouter_api_key:
            return openrouter_chat(messages, api_key=prov.openrouter_api_key,
                                   model=prov.openrouter_model,
                                   endpoint=prov.openrouter_endpoint,
                                   max_tokens=max_tokens, temperature=temperature)
        if backend == "local":
            if not self._load_local():
                return ""
            try:
                with self._lock:
                    resp = self.model.create_chat_completion(
                        messages=messages, max_tokens=max_tokens,
                        temperature=temperature)
                return resp["choices"][0]["message"]["content"].strip()
            except Exception as e:
                log.warning("LLM complete (local) failed: %s", e)
                return ""
        return ""   # entry not configured (no key) — skipped

    def complete(self, messages: list[dict], max_tokens: int = 256,
                 temperature: float = 0.3) -> str:
        if not self.ready():
            return ""
        for backend in self._chain():
            result = self._complete_backend(backend, messages,
                                            max_tokens, temperature)
            if result:
                if self.last_backend_used != backend:
                    log.info("LLM served by backend '%s' (chain fallback)",
                             backend)
                self.last_backend_used = backend
                return result
        self.last_backend_used = None
        return ""

    def complete_stream(self, messages: list[dict], max_tokens: int = 256,
                        temperature: float = 0.3):
        if not self.ready():
            return
        # Streaming: try the first usable backend; fall back to the next only
        # if it fails BEFORE yielding any token (no mid-stream switching).
        for backend in self._chain():
            produced = False
            for delta in self._stream_backend(backend, messages,
                                              max_tokens, temperature):
                produced = True
                yield delta
            if produced:
                self.last_backend_used = backend
                return
        self.last_backend_used = None

    def _stream_backend(self, backend: str, messages: list[dict],
                        max_tokens: int, temperature: float):
        """Stream from one chain entry; yields nothing on failure/skip so the
        caller can move to the next backend."""
        prov = get_config().providers
        if backend == "groq" and prov.groq_api_key:
            from ..providers import groq_chat_stream
            yield from groq_chat_stream(
                messages, api_key=prov.groq_api_key, model=prov.groq_model,
                endpoint=prov.groq_endpoint, max_tokens=max_tokens,
                temperature=temperature)
            return
        if backend == "gemini" and prov.gemini_api_key:
            # ponytail: no gemini streaming adapter — non-streaming fallback
            # only; add gemini SSE if live-guidance latency on Gemini matters.
            text = self._complete_backend("gemini", messages,
                                          max_tokens, temperature)
            if text:
                yield text
            return
        if backend == "openrouter" and prov.openrouter_api_key:
            text = self._complete_backend("openrouter", messages,
                                          max_tokens, temperature)
            if text:
                yield text
            return
        if backend == "local":
            if not self._load_local():
                return
            try:
                with self._lock:
                    stream = self.model.create_chat_completion(
                        messages=messages, max_tokens=max_tokens,
                        temperature=temperature, stream=True)
                    for chunk in stream:
                        delta = chunk["choices"][0]["delta"].get("content")
                        if delta:
                            yield delta
            except Exception as e:
                log.warning("LLM stream failed: %s", e)
