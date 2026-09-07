"""Hosted-API backends for the voice pipeline (Deepgram / Velma / Groq).

The default deployment runs the LOCAL models (faster-whisper, the SSL
voice-deepfake ensemble, the Qwen GGUF LLM). For time-boxed demos the voice
path can instead delegate to hosted APIs — the engine NAMES and their
call-site interfaces are unchanged, so the orchestrator, dispatcher and the
in-call UI ("Models n/m loaded") behave exactly as before; only the backend
that produces each number changes.

Keys are read from the environment (see ``server/.env``, which is gitignored)
so they are never committed. Every call runs off the event loop
(``asyncio.to_thread`` at the call sites), so a slow API can never freeze the
SFU media relay.

No hard third-party dependency: we use ``requests`` when it is installed and
fall back to the stdlib ``urllib`` otherwise, so this works on any Python.
"""
from __future__ import annotations

import asyncio
import io
import json
import logging
import re
import wave
from typing import Any, Iterable, Optional

import numpy as np

log = logging.getLogger(__name__)

_DEF_TIMEOUT = 20.0


# --------------------------------------------------------------------- audio
def pcm_to_wav_bytes(audio: np.ndarray, sample_rate: int = 16000) -> bytes:
    """float32 mono [-1,1] -> 16-bit PCM WAV container (in memory)."""
    pcm = (np.clip(audio, -1, 1) * 32767).astype(np.int16)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(int(sample_rate))
        w.writeframes(pcm.tobytes())
    return buf.getvalue()


def pcm_to_s16le_bytes(audio: np.ndarray) -> bytes:
    """float32 mono [-1,1] -> raw 16-bit little-endian PCM (no container)."""
    return (np.clip(audio, -1, 1) * 32767).astype(np.int16).tobytes()


# ---------------------------------------------------------------- HTTP layer
def _http_post(url: str, headers: dict, *, data: bytes | None = None,
               json_body: dict | None = None,
               timeout: float = _DEF_TIMEOUT) -> tuple[int, bytes]:
    """POST returning (status_code, body_bytes). Prefers requests, falls back
    to stdlib urllib so no extra dependency is required."""
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers = {**headers, "Content-Type": "application/json"}
    try:
        import requests  # type: ignore
        resp = requests.post(url, headers=headers, data=data, timeout=timeout)
        return resp.status_code, resp.content
    except ImportError:
        import urllib.error
        import urllib.request
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.getcode(), r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()


def _http_post_stream(url: str, headers: dict, json_body: dict,
                      timeout: float = _DEF_TIMEOUT) -> Iterable[bytes]:
    """POST returning an iterator of raw SSE lines. Streaming only works with
    requests; without it we fall back to a single full-body read."""
    body = json.dumps(json_body).encode("utf-8")
    headers = {**headers, "Content-Type": "application/json"}
    try:
        import requests  # type: ignore
        resp = requests.post(url, headers=headers, data=body, timeout=timeout,
                             stream=True)
        for line in resp.iter_lines():
            if line:
                yield line
    except ImportError:
        status, content = _http_post(url, headers, data=body, timeout=timeout)
        for line in content.splitlines():
            if line:
                yield line


def _dig(obj: Any, dotted: str) -> Any:
    """Fetch a value from nested dicts/lists by a dotted path.

    Supports numeric indices for lists: ``a.0.b``. Returns None if any hop
    is missing, so a slightly-off configured path degrades to "no signal"
    instead of throwing."""
    cur = obj
    for part in dotted.split("."):
        if cur is None:
            return None
        if isinstance(cur, list):
            try:
                cur = cur[int(part)]
            except (ValueError, IndexError):
                return None
        elif isinstance(cur, dict):
            cur = cur.get(part)
        else:
            return None
    return cur


# ------------------------------------------------------------------ Deepgram
def deepgram_transcribe(audio: np.ndarray, sample_rate: int, *, api_key: str,
                        model: str, endpoint: str,
                        language: str | None) -> dict:
    """Deepgram pre-recorded STT. Returns {text, language, avg_logprob, ready}.

    Sends a WAV container (Deepgram sniffs the format, so no encoding/rate
    params needed). Locks to ``language`` when the caller already detected one;
    otherwise asks Deepgram to detect it and echoes the result back so the
    dispatcher's per-session language lock keeps working unchanged.
    """
    wav = pcm_to_wav_bytes(audio, sample_rate)
    params = f"model={model}&smart_format=true&punctuate=true"
    if language:
        params += f"&language={language}"
    else:
        params += "&detect_language=true"
    url = f"{endpoint}?{params}"
    headers = {"Authorization": f"Token {api_key}", "Content-Type": "audio/wav"}
    try:
        status, body = _http_post(url, headers, data=wav, timeout=15.0)
        if status != 200:
            log.warning("Deepgram HTTP %s: %s", status, body[:200])
            return {"text": "", "language": None, "avg_logprob": 0.0, "ready": False}
        data = json.loads(body)
        ch = _dig(data, "results.channels.0") or {}
        alt = (ch.get("alternatives") or [{}])[0]
        text = (alt.get("transcript") or "").strip()
        # channel-level detected language when we asked Deepgram to detect;
        # otherwise the language we pinned.
        det = ch.get("detected_language") or language
        conf = float(alt.get("confidence") or 0.0)
        return {"text": text, "language": det, "avg_logprob": conf, "ready": True}
    except Exception as e:
        log.warning("Deepgram transcribe failed: %s", e)
        return {"text": "", "language": None, "avg_logprob": 0.0, "ready": False}


# ---------------------------------------------------------------------- Groq
# The LLM used to fail silently: any non-200 returned "" and logged at WARNING,
# so a revoked key or a decommissioned model name looked exactly like "the model
# had nothing to say" and the reasoner quietly served template text forever.
# We now log at ERROR with the actual cause and remember it so
# GET /api/debug/models can show why the reason/advice is missing.
_LAST_LLM_ERROR: Optional[str] = None


def last_llm_error() -> Optional[str]:
    """Most recent Groq failure, or None if the last call succeeded."""
    return _LAST_LLM_ERROR


def _explain_llm_status(provider: str, status: int, body: bytes, model: str) -> str:
    """Shared HTTP-status explainer for the OpenAI-compatible LLM providers."""
    snippet = body[:300].decode("utf-8", "ignore")
    if status in (401, 403):
        hint = f"{provider} API key is missing, wrong or revoked (check server/.env)"
    elif status == 404 or "decommissioned" in snippet or "does not exist" in snippet:
        hint = (f"model '{model}' was not accepted by {provider} — it may have been "
                f"renamed or decommissioned; update providers config in config.yaml")
    elif status == 429:
        hint = f"{provider} rate limit / quota exhausted"
    elif status >= 500:
        hint = f"{provider} server-side error; retry later"
    else:
        hint = f"unexpected {provider} response"
    return f"HTTP {status}: {hint} :: {snippet}"


# Kept under its historical name for any external callers.
def _explain_groq_status(status: int, body: bytes, model: str) -> str:
    return _explain_llm_status("Groq", status, body, model)


def groq_chat(messages: list[dict], *, api_key: str, model: str, endpoint: str,
              max_tokens: int = 256, temperature: float = 0.3) -> str:
    """Groq chat completion (OpenAI-compatible). Returns the message content.

    Returns "" on failure (the reasoner then falls back to template text), but
    the reason is logged loudly and retained in ``last_llm_error()``.
    """
    global _LAST_LLM_ERROR
    if not api_key:
        _LAST_LLM_ERROR = "GROQ_API_KEY is not set (see server/.env)"
        log.error("Groq chat skipped: %s", _LAST_LLM_ERROR)
        return ""
    headers = {"Authorization": f"Bearer {api_key}"}
    payload = {"model": model, "messages": messages,
               "max_tokens": max_tokens, "temperature": temperature}
    try:
        status, body = _http_post(endpoint, headers, json_body=payload, timeout=30.0)
        if status != 200:
            _LAST_LLM_ERROR = _explain_llm_status("Groq", status, body, model)
            log.error("Groq chat failed — %s", _LAST_LLM_ERROR)
            return ""
        data = json.loads(body)
        content = (_dig(data, "choices.0.message.content") or "").strip()
        if not content:
            _LAST_LLM_ERROR = (f"Groq returned 200 but no message content "
                               f"(finish_reason={_dig(data, 'choices.0.finish_reason')})")
            log.error("Groq chat empty — %s", _LAST_LLM_ERROR)
            return ""
        _LAST_LLM_ERROR = None
        return content
    except Exception as e:
        _LAST_LLM_ERROR = f"{type(e).__name__}: {e}"
        log.error("Groq chat raised — %s", _LAST_LLM_ERROR, exc_info=True)
        return ""


# ------------------------------------------------------------------ OpenRouter
def openrouter_chat(messages: list[dict], *, api_key: str, model: str, endpoint: str,
                    max_tokens: int = 256, temperature: float = 0.3) -> str:
    """OpenRouter chat completion (OpenAI-compatible). Returns "" on failure.

    OpenRouter routes to many underlying providers under one key, so it is the
    LLM fallback #3 after Groq and Gemini. Failure reasons flow into
    ``last_llm_error()`` just like Groq's, so /api/debug/models shows them.
    """
    global _LAST_LLM_ERROR
    if not api_key:
        _LAST_LLM_ERROR = "OPENROUTER_API_KEY is not set (see server/.env)"
        log.error("OpenRouter chat skipped: %s", _LAST_LLM_ERROR)
        return ""
    # OpenRouter's attribution headers (optional but recommended by their docs)
    headers = {"Authorization": f"Bearer {api_key}",
               "HTTP-Referer": "https://antai.local", "X-Title": "antAI"}
    payload = {"model": model, "messages": messages,
               "max_tokens": max_tokens, "temperature": temperature}
    try:
        status, body = _http_post(endpoint, headers, json_body=payload, timeout=30.0)
        if status != 200:
            _LAST_LLM_ERROR = _explain_llm_status("OpenRouter", status, body, model)
            log.error("OpenRouter chat failed — %s", _LAST_LLM_ERROR)
            return ""
        content = (_dig(json.loads(body), "choices.0.message.content") or "").strip()
        if not content:
            _LAST_LLM_ERROR = ("OpenRouter returned 200 but no message content "
                               f"(finish_reason={_dig(json.loads(body), 'choices.0.finish_reason')})")
            log.error("OpenRouter chat empty — %s", _LAST_LLM_ERROR)
            return ""
        _LAST_LLM_ERROR = None
        return content
    except Exception as e:
        _LAST_LLM_ERROR = f"{type(e).__name__}: {e}"
        log.error("OpenRouter chat raised — %s", _LAST_LLM_ERROR, exc_info=True)
        return ""


# --------------------------------------------------------------------- Gemini
def gemini_chat(messages: list[dict], *, api_key: str, model: str, endpoint: str,
                max_tokens: int = 256, temperature: float = 0.3) -> str:
    """Gemini generateContent. Converts OpenAI-style messages -> Gemini format.

    Gemini does not take a ``messages`` array: system messages become
    ``systemInstruction``, user/assistant turns become ``contents`` with role
    "model" for assistant. Auth is the ``x-goog-api-key`` header (NOT a query
    param, so keys never appear in URLs or request logs). Returns "" on
    failure, mirroring groq_chat/openrouter_chat.
    """
    global _LAST_LLM_ERROR
    if not api_key:
        _LAST_LLM_ERROR = "GEMINI_API_KEY is not set (see server/.env)"
        log.error("Gemini chat skipped: %s", _LAST_LLM_ERROR)
        return ""
    system_parts = [m["content"] for m in messages if m.get("role") == "system"]
    contents = [{"role": "model" if m.get("role") == "assistant" else "user",
                 "parts": [{"text": m["content"]}]}
                for m in messages if m.get("role") in ("user", "assistant")]
    payload = {"contents": contents,
               "generationConfig": {"maxOutputTokens": max_tokens,
                                    "temperature": temperature}}
    if system_parts:
        payload["systemInstruction"] = {"parts": [{"text": "\n".join(system_parts)}]}
    url = f"{endpoint.rstrip('/')}/{model}:generateContent"
    headers = {"x-goog-api-key": api_key}
    try:
        status, body = _http_post(url, headers, json_body=payload, timeout=30.0)
        if status != 200:
            _LAST_LLM_ERROR = _explain_llm_status("Gemini", status, body, model)
            log.error("Gemini chat failed — %s", _LAST_LLM_ERROR)
            return ""
        data = json.loads(body)
        content = (_dig(data, "candidates.0.content.parts.0.text") or "").strip()
        if not content:
            # empty usually means MAX_TOKENS/SAFETY blocked the candidate
            finish = _dig(data, "candidates.0.finishReason")
            _LAST_LLM_ERROR = (f"Gemini returned 200 but no text "
                               f"(finishReason={finish})")
            log.error("Gemini chat empty — %s", _LAST_LLM_ERROR)
            return ""
        _LAST_LLM_ERROR = None
        return content
    except Exception as e:
        _LAST_LLM_ERROR = f"{type(e).__name__}: {e}"
        log.error("Gemini chat raised — %s", _LAST_LLM_ERROR, exc_info=True)
        return ""


def groq_chat_stream(messages: list[dict], *, api_key: str, model: str,
                     endpoint: str, max_tokens: int = 256,
                     temperature: float = 0.3):
    """Groq streaming chat completion; yields content deltas (str)."""
    headers = {"Authorization": f"Bearer {api_key}"}
    payload = {"model": model, "messages": messages, "max_tokens": max_tokens,
               "temperature": temperature, "stream": True}
    try:
        for raw in _http_post_stream(endpoint, headers, payload, timeout=30.0):
            line = raw.decode("utf-8", "ignore").strip()
            if not line.startswith("data:"):
                continue
            chunk = line[len("data:"):].strip()
            if chunk == "[DONE]":
                break
            try:
                delta = _dig(json.loads(chunk), "choices.0.delta.content")
            except Exception:
                continue
            if delta:
                yield delta
    except Exception as e:
        log.warning("Groq stream failed: %s", e)


# --------------------------------------------------------------------- Velma
# Velma-2 is Modulate's real-time voice-intelligence model, served over a
# STREAMING WebSocket at platform.modulate.ai (NOT a REST endpoint). Protocol:
#   1. connect to  wss://.../api/<model>-streaming?api_key=<key>
#   2. general endpoint only: send the config as the FIRST text frame (JSON).
#      The dedicated synthetic-voice-detection (SVD) endpoint takes no config
#      frame — it reads audio_format/sample_rate/num_channels from the query.
#   3. stream audio bytes in chunks, then signal end-of-stream. WHICH framing
#      the endpoint accepts is not something we can look up, so it is negotiated
#      once (see _EOS_VARIANTS) and the winner reused for the rest of the run.
#   4. read JSON events: partial_clip / clip / clip_update / behavior_detection
#      / frame (SVD) / summary / done / error
# We feed one VAD segment per short-lived session and read events until "done",
# extracting a synthetic-voice probability so the engine's analyze() contract
# (a per-segment score) is preserved.
#
# NON-NEGOTIABLE: if the stream produces no verdict field, this returns None —
# never a low "looks real" score. An invented clearance from a detector that
# never actually examined the audio is the single worst failure mode available
# to this feature, because it silently vouches for a cloned voice.
_DEF_VELMA_KEYWORDS = ("synthetic", "deepfake", "ai_voice", "ai-voice",
                       "aivoice", "fake", "spoof", "clone", "tts")

# Why the last Velma call produced nothing, kept so GET /api/debug/models can say
# so out loud. A hosted detector that quietly answers nothing is indistinguishable
# from one that answers "this voice is real" unless the failure is surfaced.
_LAST_VELMA_ERROR: Optional[str] = None
# Which end-of-stream framing the live endpoint accepted, remembered for the rest
# of the run so we pay the negotiation cost once instead of per segment.
_VELMA_EOS: Optional[str] = None


def last_velma_error() -> Optional[str]:
    """Most recent Velma failure, or None if the last call produced a score."""
    return _LAST_VELMA_ERROR


def velma_eos_in_use() -> Optional[str]:
    """The end-of-stream framing that worked, once one has been established."""
    return _VELMA_EOS


def _redact(text: str) -> str:
    """Strip any api_key=... occurrence from a string bound for a log or an API.

    Diagnostics quote exception messages verbatim, and the key travels in the
    Velma URL query string — so without this the one thing that must never be
    written down is exactly the thing a failure would print.
    """
    return re.sub(r"(api_key=)[^&\s'\"]+", r"\1<redacted>", text or "")


def _norm_prob(v: Any) -> Optional[float]:
    """Coerce a score to [0,1]; accept 0-100 scales and numeric strings."""
    try:
        p = float(v)
    except (TypeError, ValueError):
        return None
    if p > 1.0:
        p = p / 100.0
    return max(0.0, min(1.0, p))


def _first_num(d: dict, keys: list[str]) -> Optional[float]:
    for k in keys:
        if k in d and isinstance(d[k], (int, float)):
            return float(d[k])
    return None


def _extract_score(event: dict, *, is_svd: bool, keywords: list[str],
                   response_path: str) -> Optional[float]:
    """A synthetic-voice probability from one Velma event, or None.

    None means "this event carried no verdict" (a keepalive, a silent frame, a
    summary). That is deliberately distinct from a low score, which means "Velma
    looked and said the voice is real" — conflating the two is how a detector
    that never answered ends up vouching for a cloned voice.
    """
    if response_path:
        v = _dig(event, response_path)
        if v is not None:
            p = _norm_prob(v)
            if p is not None:
                return p

    kind = event.get("type")
    if is_svd and kind == "frame":
        # {verdict: synthetic | non-synthetic | no-content, confidence: 0..1}
        fr = event.get("frame") or {}
        verdict = str(fr.get("verdict", "")).lower()
        conf = _norm_prob(fr.get("confidence"))
        if verdict == "synthetic":
            return conf if conf is not None else 0.9
        if verdict == "non-synthetic":
            return (1.0 - conf) if conf is not None else 0.05
        return None                      # no-content / silence: not a verdict
    if kind == "behavior_detection":
        d = event.get("detection", {}) or {}
        name = str(d.get("behavior_name", "")).lower()
        if any(k in name for k in keywords):
            sc = _first_num(d, ["score", "confidence", "probability", "prob"])
            if sc is not None:
                return _norm_prob(sc)
            return 0.9 if d.get("detected") else 0.05
        return None
    if kind in ("clip", "clip_update"):
        payload = event.get("clip") or event.get("clip_update") or {}
        best = None
        for key, val in payload.items():
            if isinstance(val, (int, float)) and \
                    any(k in key.lower() for k in keywords):
                p = _norm_prob(val)
                if p is not None:
                    best = p if best is None else max(best, p)
        return best
    return None


async def _velma_attempt(audio_bytes: bytes, *, url: str, config_json: str,
                         keywords: list[str], response_path: str,
                         timeout: float, is_svd: bool, eos: str
                         ) -> tuple[Optional[float], str]:
    """One streaming attempt using a specific end-of-stream framing.

    Returns ``(score, diag)``. ``score`` is None when the endpoint produced no
    verdict; ``diag`` always says what happened, including how many audio bytes
    were accepted before the connection closed. That byte count is the thing that
    distinguishes "it rejected our audio framing" (closes almost immediately)
    from "it rejected our end-of-stream marker" (closes after the whole segment).

    The whole attempt is time-boxed, and the diagnostic counters live outside the
    time-boxed coroutine so a timeout still reports how far it got.
    """
    import websockets

    best: Optional[float] = None
    verdict_seen = False
    events = 0
    sent = 0
    stage = "connect"
    err: Optional[str] = None

    async def _run() -> None:
        nonlocal best, verdict_seen, events, sent, stage, err
        async with websockets.connect(url, max_size=None,
                                      open_timeout=timeout) as ws:
            # Only the general velma-2-streaming endpoint takes a config frame;
            # the dedicated SVD endpoint carries its config in the query string.
            if not is_svd and config_json:
                stage = "config"
                await ws.send(config_json)
            stage = "audio"
            chunk = 4096
            for i in range(0, len(audio_bytes), chunk):
                part = audio_bytes[i:i + chunk]
                await ws.send(part)
                sent += len(part)
            stage = f"eos:{eos}"
            if eos == "text":
                await ws.send("")                     # empty TEXT frame
            elif eos == "binary":
                await ws.send(b"")                    # empty BINARY frame
            elif eos == "json":
                await ws.send(json.dumps({"type": "end"}))
            # eos == "none": send nothing; just read until the server is done
            stage = "read"
            async for message in ws:
                try:
                    event = json.loads(message)
                except Exception:
                    continue
                events += 1
                if event.get("type") == "error":
                    err = f"endpoint error: {str(event.get('error'))[:160]}"
                    return
                p = _extract_score(event, is_svd=is_svd, keywords=keywords,
                                   response_path=response_path)
                if p is not None:
                    verdict_seen = True
                    best = p if best is None else max(best, p)
                if event.get("type") == "done":
                    return

    try:
        await asyncio.wait_for(_run(), timeout=timeout)
    except asyncio.TimeoutError:
        return None, f"timed out after {timeout:.0f}s at stage={stage} ({sent}B sent)"
    except Exception as e:
        code = getattr(e, "code", None)
        reason = getattr(e, "reason", "") or ""
        detail = f" close_code={code}" if code is not None else ""
        detail += f" reason={reason!r}" if reason else ""
        return None, (f"{type(e).__name__} at stage={stage} after {sent}B"
                      f"{detail}: {str(e)[:160]}")

    if err:
        return None, f"{err} ({sent}B sent, eos={eos})"
    if verdict_seen:
        return best, f"ok ({events} events, {sent}B, eos={eos})"
    return None, (f"stream completed ({events} events, {sent}B, eos={eos}) but no "
                  f"synthetic-voice verdict was present — treating as NO SIGNAL, "
                  f"not as 'real'")


# End-of-stream framings to try, in the order most likely to be right. The
# original code only ever sent an empty TEXT frame, which the live SVD endpoint
# rejected with close code 1003 (unsupported data) on every single segment.
_EOS_VARIANTS = ("binary", "text", "json", "none")


def velma_planned_attempts(eos: str = "") -> int:
    """How many framings the next call will try — used to size its time budget."""
    return 1 if (eos or _VELMA_EOS) else len(_EOS_VARIANTS)


async def _velma_ws_detect(audio_bytes: bytes, *, url: str, api_key: str,
                           config_json: str, keywords: list[str],
                           response_path: str, timeout: float,
                           is_svd: bool, eos: str = "") -> Optional[float]:
    """Stream one segment to Velma and return a synthetic-voice probability.

    ``timeout`` is the budget for ONE attempt. The end-of-stream framing this
    endpoint wants is negotiated once per process: variants are tried in order
    until one yields a verdict, and the winner is reused for every later segment.
    Returns None when no variant produced a verdict, so the caller reports "no
    signal" instead of inventing a clearance for a voice nothing ever examined.
    """
    global _LAST_VELMA_ERROR, _VELMA_EOS

    full = url
    if api_key and "api_key=" not in full:
        full += ("&" if "?" in full else "?") + f"api_key={api_key}"
    # The dedicated SVD endpoint takes RAW s16le PCM and needs the audio format
    # declared in the query string.
    if is_svd and "audio_format=" not in full:
        full += "&audio_format=s16le&sample_rate=16000&num_channels=1"

    if eos:
        variants = (eos,)                       # operator pinned it explicitly
    elif _VELMA_EOS:
        variants = (_VELMA_EOS,)                # already negotiated
    else:
        variants = _EOS_VARIANTS

    diags: list[str] = []
    for variant in variants:
        score, diag = await _velma_attempt(
            audio_bytes, url=full, config_json=config_json, keywords=keywords,
            response_path=response_path, timeout=timeout, is_svd=is_svd,
            eos=variant)
        if score is not None:
            if _VELMA_EOS != variant:
                log.info("Velma: end-of-stream framing %r accepted (%s)",
                         variant, diag)
            _VELMA_EOS = variant
            _LAST_VELMA_ERROR = None
            return score
        diags.append(f"[{variant}] {diag}")

    _LAST_VELMA_ERROR = _redact(" | ".join(diags))
    log.warning("Velma produced no verdict: %s", _LAST_VELMA_ERROR)
    return None


def velma_detect(audio: np.ndarray, sample_rate: int, *, api_key: str,
                 endpoint: str, model_id: str, auth_style: str,
                 response_path: str, config_json: str = "",
                 keywords: tuple[str, ...] = _DEF_VELMA_KEYWORDS,
                 eos: str = "") -> Optional[float]:
    """Modulate VELMA-2 synthetic-voice detection over one audio segment.

    Streams the segment to the Velma WebSocket and returns a synthetic-voice
    probability in [0,1], or None when unavailable (no endpoint / websockets
    missing / call failed) so the caller reports "no signal" rather than a
    wrong value. Runs its own event loop because analyze() is invoked in a
    worker thread (asyncio.to_thread) that has no running loop.

    ``eos`` pins the end-of-stream framing ("binary" / "text" / "json" / "none");
    leave it empty to let the first call negotiate it and every later call reuse
    the winner.
    """
    global _LAST_VELMA_ERROR
    import asyncio as _aio

    if not endpoint:
        return None
    try:
        import websockets  # noqa: F401
    except ImportError:
        _LAST_VELMA_ERROR = ("the 'websockets' package is not installed "
                             "(pip install websockets)")
        log.warning("Velma: %s — reporting no signal", _LAST_VELMA_ERROR)
        return None

    # The dedicated synthetic-voice-detection endpoint uses raw s16le PCM and
    # no config frame; the general velma-2-streaming endpoint uses a WAV
    # container + a JSON config frame.
    is_svd = ("synthetic-voice-detection" in (endpoint or "")
              or "synthetic-voice-detection" in (model_id or ""))
    if is_svd:
        audio_bytes = pcm_to_s16le_bytes(audio)
    else:
        audio_bytes = pcm_to_wav_bytes(audio, sample_rate)

    cfg = config_json or '{"behaviors": ["preset:synthetic_voice"]}'
    kw = [k.lower() for k in keywords]

    # Budget per attempt, times however many framings this call may try. Only the
    # first call pays the full negotiation cost; after that it is one attempt.
    per_attempt = 8.0
    overall = per_attempt * velma_planned_attempts(eos) + 4.0

    def _run_coro() -> Optional[float]:
        # the coroutine is created HERE (only where no loop is running), so it
        # is always awaited — no "coroutine was never awaited" leak.
        return _aio.run(_velma_ws_detect(
            audio_bytes, url=endpoint, api_key=api_key, config_json=cfg,
            keywords=kw, response_path=response_path, timeout=per_attempt,
            is_svd=is_svd, eos=eos))

    try:
        _aio.get_running_loop()
    except RuntimeError:
        running_loop = False
    else:
        running_loop = True

    try:
        if running_loop:
            # a loop is already running in this thread: offload to a fresh one
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as ex:
                return ex.submit(_run_coro).result(timeout=overall)
        return _run_coro()
    except Exception as e:
        _LAST_VELMA_ERROR = _redact(f"{type(e).__name__}: {str(e)[:200]}")
        log.warning("Velma detect failed: %s", _LAST_VELMA_ERROR)
        return None
