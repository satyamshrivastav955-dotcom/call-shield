"""Headless streaming session: drive the real detection pipeline from an
external PCM stream (WebSocket / file replay) instead of a WebRTC tap.

Why this exists
---------------
The rolling detection engine already lives in ``SessionRunner`` (per-speaker
window, coalesced fire-and-forget graph runs, live signal pushes). The only
thing tying it to the phone was the *entry point*: audio arrived exclusively as
``av.AudioFrame`` over an authenticated WebRTC tap. ``StreamingSession`` is a
thin adapter that lets any caller feed raw PCM chunks into the SAME
``AudioIngestor`` -> VAD -> ``SessionRunner`` -> LangGraph path, so an external
API and the demo file-streamer exercise the real models, not a parallel copy.

It deliberately owns NO detection logic of its own. It:
  * wires an ``AudioIngestor`` to a ``SessionRunner`` (as ``tap.TapCall`` does),
  * accepts ``push_pcm`` chunks,
  * exposes a normalized, UI-facing result snapshot (:func:`normalized_result`)
    built ONLY from what the pipeline actually computed — never fabricated.

Every field can be ``None``: a model that did not run or has no weights reports
``null``, never a made-up number. That honesty is a hard project invariant.
"""
from __future__ import annotations

import logging
import time
from typing import Callable, Optional

import numpy as np

from ..ingestion.audio import AudioIngestor
from .dispatcher import SessionRunner
from .state import AnalysisState

log = logging.getLogger(__name__)


# --------------------------------------------------------------- risk mapping
def _risk_level(band: str | None, risk: float) -> str:
    """Human-facing severity label. Mirrors fusion's band, with a friendly name."""
    if band == "critical" or risk >= 70:
        return "high"
    if band == "verify" or risk >= 40:
        return "medium"
    return "low"


def _recommendation(decision: str | None, band: str | None, risk: float,
                    signals: dict) -> str:
    """Deterministic, decision-derived recommendation.

    This is POLICY output (from ``decision_node`` + the signals), not the LLM.
    The LLM only ever refines wording elsewhere; the actionable instruction a
    caller/agent sees here is computed from the deterministic decision so it can
    never contradict the freeze/verify machinery.
    """
    if decision == "freeze" or risk >= 70:
        rtype = signals.get("request_type")
        if rtype:
            return (f"Do not act on the {rtype} request. Stop the transaction and "
                    "verify the caller through an independent, trusted channel.")
        return ("Treat this call as high-risk. Do not share codes, money or "
                "credentials; verify identity through a trusted channel.")
    if decision in ("verify", "escalate") or band == "verify":
        return ("Elevated risk — verify the caller's identity before proceeding "
                "with anything sensitive.")
    return "No action needed. Continue monitoring."


def normalized_result(runner: SessionRunner) -> dict:
    """The external streaming contract: one flat, UI-ready snapshot.

    Shared by the WS API and the mock dashboard so both render identical numbers.
    Field grouping follows the three explainable signal families the brief asks
    for — acoustic / prosody / voiceprint — plus the fused risk and a
    deterministic recommendation.
    """
    sig = runner.signals or {}
    risk = round(float(runner.risk or 0.0), 1)
    band = runner.band or "passive"
    decision = (runner.last_verdict or {}).get("decision")

    voice = sig.get("voice_deepfake")
    urgency = sig.get("urgency")
    similarity = sig.get("speaker_similarity")

    return {
        "session_key": runner.session_key,
        "risk": risk,
        "risk_peak": round(float(runner.risk_peak or 0.0), 1),
        "risk_level": _risk_level(band, risk),
        "band": band,
        # --- three explainable signal families -------------------------------
        "acoustic": {
            # synthesis-artifact / AI-voice probability (0..1) or null
            "voice_deepfake": voice,
            "label": sig.get("voice_label"),
            "per_model": sig.get("voice_per_model") or {},
            "sources": sig.get("voice_sources"),
            "agreement": sig.get("voice_agreement"),
            "backend": sig.get("voice_backend"),
        },
        "prosody": {
            # honestly labeled: this is TEXT-derived urgency + behavioral drift,
            # not acoustic prosody. See audit item #2.
            "urgency": urgency,
            "behavioral_deviation": sig.get("deviation"),
            "scam_prob": sig.get("scam_prob"),
            "scam_type": sig.get("scam_type"),
            "kind": "text-derived",
        },
        "voiceprint": {
            "similarity": similarity,
            "identity_mismatch": sig.get("identity_mismatch"),
        },
        # --- request-to-act ---------------------------------------------------
        "request": {
            "detected": bool(sig.get("request_detected")),
            "type": sig.get("request_type"),
        },
        # --- explanation ------------------------------------------------------
        "reasons": _reasons(sig),
        "recommendation": _recommendation(decision, band, risk, sig),
        # --- liveness / honesty telemetry ------------------------------------
        "engines_ready": runner._engines_ready(),
        "audio_segments": runner._audio_segments,
        "asr_failures": runner._asr_failures,
        "transcript_len": len(runner.transcript),
        "latest_text": (runner.transcript[-1].get("text")
                        if runner.transcript else None),
        "verdict": runner.last_verdict,
        "t": time.strftime("%H:%M:%S"),
        # --- scenario / context (P1.2 / P1.3) --------------------------------
        "scenario": getattr(runner, "active_scenario", None),
        "context": getattr(runner, "active_context", {}),
    }


def _reasons(sig: dict) -> list[str]:
    """Plain-language reasons for genuinely-active signals (mirrors the LLM
    reasoner's margins, but computed deterministically so it is always present).
    """
    from ..inference.llm.reasoner import _active_reasons
    try:
        return _active_reasons(sig)
    except Exception:
        return []


class StreamingSession:
    """One external stream mapped onto a real ``SessionRunner``.

    Usage:
        s = StreamingSession("stream:abc", speaker_id=1)
        await s.begin()
        await s.push_pcm(float32_chunk, sample_rate=16000)   # repeat
        snapshot = s.snapshot()                               # normalized_result
        await s.finish()
    """

    def __init__(self, session_key: str, speaker_id: int = 1,
                 claimed_identity_id: Optional[int] = None,
                 on_update: Optional[Callable[[dict], None]] = None,
                 scenario: Optional[str] = None,
                 context: Optional[dict] = None):
        self.session_key = session_key
        self.speaker_id = speaker_id
        self.claimed_identity_id = claimed_identity_id
        self.runner = SessionRunner(session_key)
        self.ingestor = AudioIngestor(session_key, self._on_segment)
        self._on_update = on_update
        self._began = False
        # P1.2 / P1.3: scenario + caller context propagated into every graph state
        self.runner.active_scenario: str | None = scenario
        self.runner.active_context: dict = context or {}

    async def begin(self):
        if self._began:
            return
        self._began = True
        # A headless stream has a single external speaker (the caller under
        # analysis) and no protected "callee" device; reuse caller/callee ids so
        # existing per-speaker bookkeeping in the runner is satisfied.
        if self.claimed_identity_id is not None:
            # verify the caller's voice against this enrolled contact's print
            self.runner.identity_claim_override = self.claimed_identity_id  # type: ignore[attr-defined]
        await self.runner.begin(kind="voice", caller_id=self.speaker_id,
                                callee_id=self.speaker_id,
                                participants=[self.speaker_id])

    async def _on_segment(self, speaker_id: int, audio: np.ndarray,
                          sample_rate: int, meta: dict):
        # Same handoff the tap uses; the runner does ASR + rolling detection.
        await self.runner.on_audio_segment(speaker_id, audio, sample_rate, meta)
        # let the caller stream a fresh snapshot after each analyzed segment
        if self._on_update is not None:
            try:
                self._on_update(self.snapshot())
            except Exception:
                log.debug("stream on_update callback failed", exc_info=True)

    async def push_pcm(self, samples: np.ndarray,
                       sample_rate: Optional[int] = None):
        """Feed a raw mono float32 chunk. Segments emit through the VAD as usual."""
        await self.ingestor.push_pcm(self.speaker_id, samples, sample_rate)

    async def flush(self):
        """Force-emit any buffered partial segment (end-of-file)."""
        await self.ingestor.flush(self.speaker_id)

    def snapshot(self) -> dict:
        return normalized_result(self.runner)

    async def finish(self) -> dict:
        await self.flush()
        snap = self.snapshot()
        try:
            await self.runner.finish()
        except Exception:
            log.exception("streaming runner.finish failed for %s", self.session_key)
        return snap
