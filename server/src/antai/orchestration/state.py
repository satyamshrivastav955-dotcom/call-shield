"""Typed graph state shared across orchestration nodes."""
from __future__ import annotations

from typing import Any, Optional, TypedDict


class AnalysisState(TypedDict, total=False):
    # event context
    kind: str                    # voice | video | message
    session_key: str
    caller_id: int
    callee_id: int
    text: str                    # for message events
    # scenario / caller context (P1.2 / P1.3)
    scenario: str | None         # active scenario name (e.g. "high_value_txn")
    context: dict                # caller context: caller_id, transaction_type,
                                 #   transaction_amount, claimed_identity,
                                 #   historical_risk
    # inputs
    transcript: list[dict]       # [{speaker, text, t, id}]
    pending_audio: Optional[dict]      # {speaker_id, audio, sample_rate}
    # Longer rolling window of the SAME speaker's recent speech, for the models
    # that need seconds rather than one VAD segment (synthetic-voice detection,
    # ECAPA speaker verification). Falls back to pending_audio when absent.
    # Kept separate because lip-sync must keep the raw, frame-aligned segment.
    detector_audio: Optional[dict]     # {speaker_id, audio, sample_rate}
    # Longer (unpadded) context of the same speaker for the AST spoof head,
    # which misfires on zero-padded short windows. Falls back to detector_audio.
    detector_audio_long: Optional[dict]  # {speaker_id, audio, sample_rate}
    pending_frames: list[Any]          # recent BGR frames (small batch)
    claimed_identity_id: Optional[int]  # contact whose identity is claimed
    collective_phone_hash: Optional[str]  # caller phone hash for collective-db lookup
    # computed signals
    voice_deepfake: Optional[float]
    voice_per_model: Optional[dict]   # {asv5: prob, w2v: prob} per-SSL breakdown
    voice_sources: Optional[int]      # how many detectors scored this segment (1 or 2)
    voice_agreement: Optional[str]    # both-flag | disagree | one-flag-one-unsure | ...
    voice_backend: Optional[str]      # both | velma | local
    voice_label: Optional[str]        # spoof | bonafide | uncertain
    is_ai_voice: Optional[bool]       # explicit synthetic-voice verdict (None=unknown)
    speaker_similarity: Optional[float]
    voice_similarity: Optional[float] # spec-named alias of speaker_similarity (ECAPA cosine)
    identity_mismatch: bool
    video_deepfake: Optional[float]
    video_votes: Optional[dict]       # {community_vit: prob, temporal: prob, ...} per-model
    video_agreement: Optional[int]    # # of reliable models agreeing "fake"
    lipsync_mismatch: bool
    scam_prob: Optional[float]
    scam_type: Optional[str]
    deviation: Optional[float]
    urgency: Optional[float]
    request_detected: bool
    request_type: Optional[str]
    request_confidence: Optional[float]
    collective_flagged: bool
    collective_confidence: Optional[float]
    # set by the text nodes when input was skipped as not worth scoring
    # ("low-content": a greeting or one-word reply)
    text_skipped: Optional[str]
    # fusion + decision
    risk: float
    band: str
    # which signal families fired, for explaining a verdict in logs/debug:
    # {"hard": [...], "soft": [...], "capped": bool}
    risk_signals: Optional[dict]
    decision: str                # log | verify | freeze | escalate
    freeze: Optional[dict]
    # outputs
    verdict: Optional[dict]
    guidance: Optional[str]
    events: list[dict]
    intercept_handled: bool
