"""Dispatcher: drives the orchestration graph from real-time events.

- dispatch_message(): text-only pass (chat send-time tap).
- SessionRunner: per-call runtime that transcribes audio, batches video
  frames, runs the graph, and pushes verdict/guidance/freeze/verify/report
  to the client over the realtime hub.
"""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Optional

import numpy as np

from ..config import get_config
from ..inference.hub import get_hub
from ..realtime import get_hub as get_rt_hub
from ..storage import get_db
from .state import AnalysisState

log = logging.getLogger(__name__)

_RELATIONSHIP_KW = {
    "mom": ["mom", "mother", "mummy", "maa", "mata", "amma"],
    "dad": ["dad", "father", "papa", "abba", "pitaji", "baap"],
    "son": ["son", "beta", "bete", "ladka", "munda"],
    "daughter": ["daughter", "beti", "ladki", "bitiya"],
    "grandma": ["grandma", "grandmother", "dadi", "nani", "dadi ma"],
    "grandpa": ["grandpa", "grandfather", "dada", "nana"],
    "aunt": ["aunt", "aunty", "bua", "mausi", "chachi"],
    "uncle": ["uncle", "chacha", "mama", "tau"],
    "brother": ["brother", "bhai", "bhaiya"],
    "sister": ["sister", "behen", "didi"],
}

# Why a voiceprint cross-check could not produce an answer, in words a worried
# person can act on. Lives here, next to the code that emits the reason codes, and
# is reused by the REST endpoint so the WS push and the HTTP reply can never drift
# into telling the user two different stories.
CROSS_VERIFY_HINTS = {
    "no_audio_buffered": "The caller hasn't spoken long enough yet — try again "
                         "after a few seconds of speech.",
    "speaker_verify_unavailable": "The voiceprint model is not loaded on the "
                                  "server (models/speaker_verify).",
    "no_voiceprint": "That contact hasn't recorded a voiceprint yet, so there is "
                     "nothing to compare against.",
    "embed_failed": "Could not extract a voiceprint from the call audio.",
}


def _cross_verify_failure(reason: str) -> dict:
    """A cross-check that could not run. Never implies a verdict either way."""
    return {"ok": False, "reason": reason,
            "hint": CROSS_VERIFY_HINTS.get(reason, ""),
            "similarity": None, "matches": None}


async def dispatch_message(sender_id: int, recipient_id: int, body: str) -> dict:
    """Text-message path: runs the graph (text branch) and enforces intercept."""
    cfg = get_config()
    db = get_db()
    state: AnalysisState = {
        "kind": "message", "session_key": f"msg:{sender_id}:{recipient_id}",
        "caller_id": sender_id, "callee_id": recipient_id,
        "text": body, "transcript": [],
        "events": [], "request_detected": False, "identity_mismatch": False,
        "lipsync_mismatch": False, "collective_flagged": False,
        "intercept_handled": False, "risk": 0.0,
    }
    result = await _run_graph(state)

    risk = result.get("risk", 0.0)
    intercepted = False
    freeze_payload = None
    if cfg.pipeline.freeze_enabled and result.get("decision") == "freeze":
        freeze = _build_freeze(state, recipient_id)
        if freeze:
            intercepted = True
            freeze_payload = freeze["directive"]

    # Surface a verdict ONLY when it is actionable (verify/critical) or the
    # message was intercepted. A benign message still runs the full pipeline,
    # but generate_verdict always returns a (passive, empty) verdict dict; the
    # client's `if verdict:` push would then raise an alert on normal text.
    # Gating here is what stops "scam alerts on normal messages".
    verdict = result.get("verdict")
    band = (verdict or {}).get("band") or result.get("band", "passive")
    actionable = bool(verdict) and (band in ("verify", "critical") or intercepted)
    surfaced_verdict = verdict if actionable else None
    if actionable:
        db.save_verdict(state["session_key"], recipient_id, "message",
                        risk, band,
                        verdict.get("verdict", ""), verdict.get("why", ""),
                        verdict.get("action", ""), verdict.get("scam_type"),
                        _signals(state))

    return {"risk_score": risk, "intercepted": intercepted,
            "verdict": surfaced_verdict, "freeze": freeze_payload}


async def _run_graph(state: AnalysisState) -> AnalysisState:
    from .graph import get_graph
    g = get_graph()
    if g is not None:
        try:
            out = await g.ainvoke(state)
            if isinstance(out, dict):
                return out
        except Exception as e:
            log.warning("graph run failed (%s); using nodes sequentially", e)
    return await _run_sequential(state)


async def _run_sequential(state: AnalysisState) -> AnalysisState:
    # P3-C FIX: log every model's ready() state before the pipeline runs.
    # If all models show ready=False here, outputs will be risk=0 / "passive"
    # regardless of the actual call content — that is why results look hardcoded.
    from ..inference.hub import get_hub
    hub = get_hub()
    _MODEL_KEYS = [
        "asr", "voice_deepfake", "speaker_verify",
        "face", "video_deepfake", "lipsync",
        "scam_pattern", "behavioral", "urgency", "intent",
    ]
    ready_states = {k: bool(hub.get(k) and hub.get(k).ready()) for k in _MODEL_KEYS}
    log.info("pipeline model ready states (kind=%s): %s", state.get("kind"), ready_states)
    if not any(ready_states.values()):
        log.warning("ALL models are not-ready — pipeline will produce risk=0 (no real signals). "
                    "Check model load errors above and hit GET /api/debug/models.")

    from . import nodes
    await nodes.router_node(state)
    if state.get("kind") == "message":
        await nodes.text_detector_node(state)
    else:
        await nodes.voice_detector_node(state)
        await nodes.video_detector_node(state)
        # calls are also a text stream — run scam/behavioral on the transcript
        if state.get("text"):
            await nodes.text_detector_node(state)
    await nodes.identity_claim_node(state)
    await nodes.collective_check_node(state)
    await nodes.urgency_node(state)
    await nodes.intent_node(state)
    nodes.fusion_node(state)
    log.info("fusion result: risk=%.1f band=%s decision=<pending>", state.get("risk", 0), state.get("band", "?"))
    await nodes.decision_node(state)
    log.info("decision: %s (session=%s)", state.get("decision"), state.get("session_key"))
    await nodes.llm_reasoning_node(state)
    return state


def _signals(state: AnalysisState) -> dict:
    return {
        "identity_mismatch": state.get("identity_mismatch"),
        "speaker_similarity": state.get("speaker_similarity"),
        "voice_similarity": state.get("voice_similarity"),
        "voice_deepfake": state.get("voice_deepfake"),
        "voice_per_model": state.get("voice_per_model") or {},
        # dual-backend provenance: how many detectors scored this segment, whether
        # they agreed, and which backend(s) are live. Surfaced so an AI-voice
        # warning can explain itself instead of showing a bare number.
        "voice_sources": state.get("voice_sources"),
        "voice_agreement": state.get("voice_agreement"),
        "voice_backend": state.get("voice_backend"),
        "voice_label": state.get("voice_label"),
        "is_ai_voice": state.get("is_ai_voice"),
        "video_deepfake": state.get("video_deepfake"),
        "video_votes": state.get("video_votes"),        # # of ensemble models that flagged (int)
        "video_agreement": state.get("video_agreement"),  # fraction in agreement (0..1)
        "lipsync_mismatch": state.get("lipsync_mismatch"),
        "scam_prob": state.get("scam_prob"),
        "scam_type": state.get("scam_type"),
        "deviation": state.get("deviation"),
        "urgency": state.get("urgency"),
        "request_detected": state.get("request_detected"),
        "request_type": state.get("request_type"),
        "collective_flagged": state.get("collective_flagged"),
    }


def _build_freeze(state: AnalysisState, protected_user_id: int) -> Optional[dict]:
    from ..intercept.controller import create_intercept
    return create_intercept(state["session_key"], protected_user_id, state)


# ================================================================== calls
class SessionRunner:
    """Real-time runner for one call/video-call session."""

    def __init__(self, session_key: str):
        self.session_key = session_key
        self.kind = "voice"
        self.caller_id = None
        self.callee_id = None
        # every user that should receive realtime pushes for this session.
        # Defaults to [callee_id] (classic SFU behavior: the protected user);
        # the analysis-tap path passes BOTH participants.
        self.participants: list[int] = []
        # optional display names per user id (the analysis-tap path passes the
        # two app usernames so transcript pushes are self-describing)
        self.participant_names: dict[int, str] = {}
        self.transcript: list[dict] = []
        self.signals: dict = {}
        # last-known continuous per-engine values, carried across single-modality
        # evals so voice/video/scam never flicker back to 0 when not re-measured.
        self._last_signals: dict = {}
        self.risk = 0.0
        self.risk_peak = 0.0
        self.band = "passive"
        self.last_verdict: dict | None = None
        self.last_guidance: str | None = None
        self._last_guidance_t = 0.0
        self._last_signals_t = 0.0
        self._last_alert_t: dict[str, float] = {}   # source -> last alert time
        # ASR language stability: faster-whisper auto-detects the language on
        # every segment when unset, and on short (0.4-2s) call segments that
        # detection is noisy — so the transcript flips between English and Hindi
        # mid-call. We lock to the first confidently-detected language (or an
        # operator-pinned config value) and reuse it for the whole session.
        self._locked_lang: str | None = None
        self._guidance_running = False
        self._frames: list = []
        self._frame_count = 0
        # Rolling per-speaker audio kept for on-demand voiceprint cross-checks:
        # when the user gets an AI-voice alert they can ask us to compare what the
        # caller actually said against the enrolled voiceprint of the person the
        # caller claims to be. Bounded so a long call cannot grow without limit
        # (~20s of 16kHz float32 per speaker, a few MB at most), and dropped in
        # finish() so nothing outlives the call.
        self._voice_buf: dict[int, list[np.ndarray]] = {}
        self._voice_buf_sr: int = 16000
        self._voice_buf_max_s: float = 20.0
        self._begun = False
        self._finished = False
        self.events: list[dict] = []
        # Serialize evaluations: the audio tap and video tap are independent
        # asyncio tasks that both call _evaluate, and the detection models are
        # shared singletons that are not safe to call concurrently from the
        # to_thread worker pool. This lock keeps only one graph run in flight
        # per session; it does NOT touch the media relay (which runs on separate
        # MediaRelay tasks), so call audio/video stays smooth.
        self._eval_lock = asyncio.Lock()
        # coalesced background evaluation: transcript/ASR must NEVER wait on the
        # heavy graph, so evaluations run fire-and-forget with the latest state.
        self._eval_pending = False
        self._pending_state: AnalysisState | None = None
        # ASR runs on its own worker so the audio/VAD ingestion loop never blocks
        # on transcription (keeps the live transcript smooth, not bursty).
        self._asr_queue: asyncio.Queue = asyncio.Queue()
        self._asr_task: asyncio.Task | None = None
        # segments whose transcription raised. Counted (not just logged) because a
        # non-zero value with a healthy-looking model list is the fingerprint of a
        # dead STT provider, and it is the difference between "the caller said
        # nothing" and "we could not hear the caller".
        self._asr_failures = 0
        # audio segments actually handed to the detection graph. The client needs
        # this to tell "no AI-voice score because nothing has been listened to
        # yet" apart from "audio was analysed and the detector still said
        # nothing" — only the second is a fault, and only it should warn.
        self._audio_segments = 0
        self._periodic_task: asyncio.Task | None = None

    async def begin(self, kind: str, caller_id: int, callee_id: int,
                    participants: list[int] | None = None,
                    participant_names: dict[int, str] | None = None):
        self.kind = kind
        self.caller_id = caller_id
        self.callee_id = callee_id
        self.participants = list(participants) if participants else [callee_id]
        self.participant_names = dict(participant_names or {})
        self._begun = True
        self.events.append({"t": time.strftime("%H:%M:%S"), "what": "Call started"})
        # periodic pipeline tick: even during silence, run a chunk so the SSL
        # models + fusion + LLM keep producing fresh output every few seconds
        if self._periodic_task is None:
            self._periodic_task = asyncio.create_task(self._periodic_loop())

    async def _periodic_loop(self):
        cfg = get_config()
        interval = max(1.0, cfg.pipeline.eval_interval_s)
        while not self._finished:
            await asyncio.sleep(interval)
            if self._finished:
                break
            if not self.transcript and not self._frames:
                continue
            state = self._base_state()
            self._schedule_evaluate(state)

    def _push_targets(self) -> list[int]:
        """Users receiving transcript/verdict/guidance/report pushes."""
        return [u for u in self.participants if u] or (
            [self.callee_id] if self.callee_id else [])

    # ------------------------------------------------------------ audio
    async def on_audio_segment(self, speaker_id: int, audio: np.ndarray,
                               sample_rate: int, meta: dict):
        # Non-blocking: hand the segment to the ASR worker and return
        # immediately so the VAD/audio ingestion loop never stalls on
        # transcription. (audio is already a copy from AudioIngestor.)
        self._remember_voice(speaker_id, audio, sample_rate)
        self._asr_queue.put_nowait((speaker_id, audio, sample_rate))
        if self._asr_task is None or self._asr_task.done():
            self._asr_task = asyncio.create_task(self._asr_worker())

    def _remember_voice(self, speaker_id: int, audio: np.ndarray,
                        sample_rate: int) -> None:
        """Append a segment to the bounded rolling buffer for cross-verification.

        Pure bookkeeping on an already-copied array — no model runs here, so the
        media/VAD path is unaffected.
        """
        try:
            self._voice_buf_sr = int(sample_rate)
            buf = self._voice_buf.setdefault(speaker_id, [])
            buf.append(audio)
            limit = int(self._voice_buf_max_s * max(1, self._voice_buf_sr))
            total = sum(len(a) for a in buf)
            while len(buf) > 1 and total > limit:
                total -= len(buf.pop(0))
        except Exception:
            log.debug("voice buffer append skipped", exc_info=True)

    def detector_window(self, speaker_id: int, seconds: float | None = None) -> tuple:
        """The last ~N seconds of ONE speaker's speech, and its sr.

        Why the synthetic-voice detectors don't just score the segment they were
        handed: VAD segments are 0.4-2.0s because that keeps the live transcript
        responsive, but the SSL models behind AI-voice detection were trained on
        utterances several seconds long. Given 0.4s they behave close to chance,
        so a genuinely cloned voice scores ~0.5 "uncertain", never crosses the 0.7
        label / 0.78 fusion line, and the detector looks perfectly alive while
        missing every clone. A few seconds of speech is the cheapest change that
        lets the models perform the way they were trained.

        Only that one speaker's own segments are concatenated, so the window can
        never blend two voices into a single clip. Returns (audio, sample_rate),
        or (None, sr) when nothing is buffered yet.
        """
        buf = self._voice_buf.get(speaker_id) or []
        sr = max(1, self._voice_buf_sr)
        if not buf:
            return None, sr
        span = get_config().pipeline.voice_window_s if seconds is None else seconds
        want = int(max(0.0, span) * sr)
        if want <= 0:                     # window disabled in config
            return buf[-1], sr
        chunks: list[np.ndarray] = []
        total = 0
        for a in reversed(buf):           # newest first; stop once we have enough
            chunks.append(a)
            total += len(a)
            if total >= want:
                break
        chunks.reverse()
        win = chunks[0] if len(chunks) == 1 else np.concatenate(chunks)
        return (win[-want:] if len(win) > want else win), sr

    def buffered_voice(self, speaker_id: int | None = None) -> tuple:
        """Concatenated buffered audio for a speaker (default: the peer), and sr.

        Returns (audio, sample_rate) or (None, sr) when nothing is buffered yet.
        """
        if speaker_id is None:
            # default to whoever is NOT the protected user, i.e. the caller
            speaker_id = self.caller_id
        buf = self._voice_buf.get(speaker_id) or []
        if not buf:
            # fall back to any speaker we do have audio for
            for sid, b in self._voice_buf.items():
                if b:
                    speaker_id, buf = sid, b
                    break
        if not buf:
            return None, self._voice_buf_sr
        return np.concatenate(buf), self._voice_buf_sr

    async def cross_verify_voice(self, claimed_user_id: int,
                                 speaker_id: int | None = None) -> dict:
        """Compare the buffered call audio against a contact's enrolled voiceprint.

        This is the "second opinion" behind an AI-voice alert. The synthetic-voice
        detectors answer "was this voice generated?"; ECAPA answers the different
        and often more useful question "is this the person it claims to be?". The
        two are independent, so a clone flagged by one and a voiceprint mismatch
        confirmed by the other is about as certain as this system gets.

        Returns {ok, similarity, matches, threshold, reason, hint, seconds_of_audio}.
        Every failure carries a [hint] the app can show verbatim: "couldn't check"
        with no explanation reads to a frightened user like a hidden verdict.
        """
        audio, sr = self.buffered_voice(speaker_id)
        if audio is None or len(audio) == 0:
            return _cross_verify_failure("no_audio_buffered")
        eng = get_hub().get("speaker_verify")
        if eng is None or not eng.ready():
            return _cross_verify_failure("speaker_verify_unavailable")

        secs = round(len(audio) / float(max(1, sr)), 1)
        r = await asyncio.to_thread(eng.verify_against, audio, claimed_user_id, sr)
        sim = r.get("similarity")
        reason = r.get("reason")
        out = {"ok": sim is not None,
               "similarity": sim,
               "matches": r.get("matches") if sim is not None else None,
               "threshold": r.get("threshold"),
               "reason": reason,
               # carried on the WS push too, not just the REST reply, so the
               # in-call dialog explains itself no matter which path delivered it
               "hint": "" if sim is not None else CROSS_VERIFY_HINTS.get(reason or "", ""),
               "seconds_of_audio": secs,
               "session_key": self.session_key}

        # A completed comparison is real evidence either way, so let it move the
        # risk: a confirmed mismatch is a hard signal, and a confirmed MATCH
        # clears the impersonation suspicion instead of leaving it hanging.
        if sim is not None:
            mismatch = not r.get("matches")
            self.signals["identity_mismatch"] = mismatch
            self.signals["speaker_similarity"] = sim
            self._last_signals["identity_mismatch"] = mismatch
            self._last_signals["speaker_similarity"] = sim
            self.events.append({
                "t": time.strftime("%H:%M:%S"),
                "what": ("Voiceprint MISMATCH" if mismatch else "Voiceprint matched")})

        rt = get_rt_hub()
        for uid in self._push_targets():
            await rt.send(uid, "voiceprint.result", out)
        log.info("cross-verify session=%s claimed=%s sim=%s matches=%s (%.1fs audio)",
                 self.session_key, claimed_user_id,
                 f"{sim:.3f}" if sim is not None else "n/a",
                 out["matches"], secs)
        return out

    async def _asr_worker(self):
        """Transcribe queued segments in order, push transcript, then schedule
        the (coalesced) detection-graph evaluation.

        Transcription and synthetic-voice detection are deliberately kept in
        SEPARATE try blocks. They used to share one: a single raised exception
        from ``asr.transcribe`` (hosted STT key rejected, socket reset, decode
        error) skipped straight past ``state["pending_audio"] = ...``, so that
        segment was never handed to the voice-deepfake detector. With a
        consistently failing ASR provider that meant AI-voice detection received
        NO audio for the whole call while every model still reported "loaded" —
        a cloned voice could not possibly be flagged. Synthetic-voice detection
        is the core promise of this app; it must not depend on whether we
        managed to turn the same audio into words.
        """
        hub = get_hub()
        asr = hub.get("asr")
        while True:
            speaker_id, audio, sample_rate = await self._asr_queue.get()
            self._asr_busy = True
            try:
                text = ""
                try:
                    if asr and asr.ready():
                        cfg = get_config()
                        # Reuse the session's locked language (or an operator-pinned
                        # config value) so the transcript can't flip languages between
                        # segments. Falls back to auto-detect (None) only until the
                        # first confident detection.
                        lang = self._locked_lang or cfg.pipeline.asr_language
                        r = await asyncio.to_thread(asr.transcribe, audio,
                                                    sample_rate, language=lang)
                        text = r.get("text", "")
                        # Lock the language on the first non-empty transcription
                        # (unless the operator already pinned one in config).
                        if self._locked_lang is None and not cfg.pipeline.asr_language:
                            detected = r.get("language")
                            if detected and text.strip():
                                self._locked_lang = detected
                                log.info("ASR language locked to '%s' (session=%s)",
                                         detected, self.session_key)
                except Exception:
                    self._asr_failures += 1
                    log.exception(
                        "ASR failed on segment %d of session %s — this line is missing "
                        "from the transcript, but the audio still goes to the "
                        "synthetic-voice detectors", self._asr_failures, self.session_key)
                try:
                    entry = {"speaker": self.participant_names.get(speaker_id)
                             or ("caller" if speaker_id == self.caller_id else "callee"),
                             "speaker_id": speaker_id, "text": text,
                             "t": time.strftime("%H:%M:%S")}
                    if text:
                        self.transcript.append(entry)
                        self.events.append({"t": entry["t"],
                                            "what": f"{entry['speaker']} spoke"})
                        rt = get_rt_hub()
                        for uid in self._push_targets():
                            await rt.send(uid, "transcript.update", {
                                "session_key": self.session_key,
                                "speaker": entry["speaker"], "text": entry["text"],
                                "t": entry["t"]})
                    state = self._base_state()
                    state["pending_audio"] = {"speaker_id": speaker_id, "audio": audio,
                                              "sample_rate": sample_rate}
                    # Separate, longer clip for the synthetic-voice / speaker-verify
                    # models (see detector_window). pending_audio stays the raw
                    # segment because lip-sync correlates it against the video frames
                    # of that same moment.
                    win, win_sr = self.detector_window(speaker_id)
                    if win is not None and len(win):
                        state["detector_audio"] = {"speaker_id": speaker_id,
                                                   "audio": win,
                                                   "sample_rate": win_sr}
                    # Long unpadded context for the AST head (see
                    # voice_long_window_s): padded short windows read as spoof.
                    try:
                        long_s = float(get_config().pipeline.voice_long_window_s)
                    except Exception:
                        long_s = 10.0
                    win_long, _ = self.detector_window(speaker_id, seconds=long_s)
                    if win_long is not None and len(win_long):
                        state["detector_audio_long"] = {
                            "speaker_id": speaker_id, "audio": win_long,
                            "sample_rate": win_sr}
                    self._audio_segments += 1
                    state["claimed_identity_id"] = self._detect_identity_claim(text)
                    self._schedule_evaluate(state)
                except Exception:
                    log.exception("ASR worker error")
            finally:
                self._asr_busy = False

    # ------------------------------------------------------------ video
    async def on_video_frame(self, speaker_id: int, frame, meta):
        self._frames.append(frame)
        self._frame_count += 1
        if len(self._frames) >= 24:  # ~4s at 6fps
            state = self._base_state()
            state["pending_frames"] = self._frames
            self._frames = []
            state["claimed_identity_id"] = None
            if self.transcript:
                state["text"] = self.transcript[-1].get("text", "")
            self._schedule_evaluate(state)

    # ------------------------------------------------------------ state
    def _base_state(self) -> AnalysisState:
        db = get_db()
        phone_hash = None
        if self.caller_id:
            c = db.get_user(self.caller_id)
            phone_hash = c.phone_hash if c else None
        # latest spoken line drives the text-based detectors (scam/urgency/intent)
        # during a call, and seed the last-known signals so a single-modality
        # eval (audio-only or video-only) never resets the other modality to 0.
        latest_text = self.transcript[-1].get("text", "") if self.transcript else ""
        state: AnalysisState = {
            "kind": self.kind, "session_key": self.session_key,
            "caller_id": self.caller_id, "callee_id": self.callee_id,
            "transcript": self.transcript,
            "text": latest_text,
            "collective_phone_hash": phone_hash,
            "events": self.events, "request_detected": False,
            "identity_mismatch": False, "lipsync_mismatch": False,
            "collective_flagged": False, "risk": 0.0,
            "intercept_handled": False,
            # P1.2 / P1.3: scenario + caller context (set by StreamingSession or
            # the stream API; fall back to None / {} for calls without them).
            "scenario": getattr(self, "active_scenario", None),
            "context": getattr(self, "active_context", {}),
        }
        self._seed_last_signals(state)
        return state


    # Persisted last-known per-engine signal values. Each eval only measures the
    # modality it has input for (audio OR video OR periodic-text); seeding the
    # state with the previous values means the detectors that DON'T run this pass
    # keep their last real value instead of collapsing to None/0, and fusion sees
    # the full picture (voice + video + scam together) every time.
    def _seed_last_signals(self, state: AnalysisState) -> None:
        for k, v in self._last_signals.items():
            if v is not None and k not in state:
                state[k] = v

    def _persist_last_signals(self, out: AnalysisState) -> None:
        # Persist ONLY the continuous, modality-specific detector outputs. The
        # momentary booleans (request_detected / identity_mismatch /
        # lipsync_mismatch) are intentionally NOT persisted so a one-off flag
        # can't pin the risk high forever — they re-evaluate from the current
        # transcript on every pass.
        for k in ("voice_deepfake", "voice_per_model", "video_deepfake",
                  "video_votes", "video_agreement", "speaker_similarity",
                  "scam_prob", "scam_type", "deviation", "urgency"):
            v = out.get(k)
            if v is not None:
                self._last_signals[k] = v

    def _detect_identity_claim(self, text: str) -> Optional[int]:
        """Map spoken identity claims to a trusted contact with a voiceprint."""
        # An explicit override (e.g. the external streaming API declaring which
        # contact the caller claims to be) wins over text NER, so speaker-verify
        # runs against the enrolled voiceprint even before the caller says a
        # relationship word. Set to None to fall back to transcript detection.
        override = getattr(self, "identity_claim_override", None)
        if override is not None:
            return override
        if not text or not self.callee_id:
            return None
        db = get_db()
        lower = text.lower()
        for tag, kws in _RELATIONSHIP_KW.items():
            if any(k in lower for k in kws):
                for c in db.list_contacts(self.callee_id):
                    if (c.relationship_tag or c.label or "").lower() in (
                            tag, tag, tag) and c.is_trusted:
                        return c.peer_id
                # fallback: caller itself claims a relationship
                break
        return None

    # ------------------------------------------------------------ evaluate
    def _schedule_evaluate(self, state: AnalysisState):
        """Fire-and-forget graph evaluation (coalesced to the latest state).

        The transcript is pushed BEFORE this is called, so the audio path never
        waits on the (heavy) detection graph. Only one background drain loop
        runs at a time and always evaluates the most recent state, skipping
        stale intermediate snapshots.

        Coalescing keeps only the newest state, but must NOT drop unevaluated
        media: the periodic tick carries no media, and audio/video batches
        arrive on independent tasks. Without carry-forward, a periodic tick (or
        the other modality) could overwrite a segment/frame-batch before it was
        ever analyzed, so that modality's signal would never leave 0. Preserve
        any pending audio/frames the superseded state still had.
        """
        prev = self._pending_state
        if prev is not None:
            if not state.get("pending_audio") and prev.get("pending_audio"):
                state["pending_audio"] = prev["pending_audio"]
                # carried together: without the window the voice detectors would
                # silently fall back to the 0.4s segment, i.e. back to the
                # near-chance scores this window exists to avoid.
                if prev.get("detector_audio"):
                    state["detector_audio"] = prev["detector_audio"]
                if prev.get("detector_audio_long"):
                    state["detector_audio_long"] = prev["detector_audio_long"]
            if not state.get("pending_frames") and prev.get("pending_frames"):
                state["pending_frames"] = prev["pending_frames"]
        self._pending_state = state
        if self._eval_pending:
            return
        self._eval_pending = True
        asyncio.create_task(self._eval_drain())

    async def _eval_drain(self):
        try:
            while self._pending_state is not None:
                state = self._pending_state
                self._pending_state = None
                await self._evaluate_locked(state)
        except Exception:
            log.exception("evaluation drain failed")
        finally:
            self._eval_pending = False
            # race guard: a state may have arrived just as we drained; re-arm
            if self._pending_state is not None:
                self._schedule_evaluate(self._pending_state)

    async def wait_idle(self, timeout: float = 30.0):
        """Wait until any in-flight ASR and evaluation completely finishes."""
        t0 = time.time()
        while time.time() - t0 < timeout:
            busy = (
                not self._asr_queue.empty()
                or getattr(self, "_asr_busy", False)
                or self._eval_pending
                or getattr(self, "_eval_busy", False)
                or self._pending_state is not None
            )
            if not busy and self._audio_segments > 0:
                await asyncio.sleep(0.08)
                busy = (
                    not self._asr_queue.empty()
                    or getattr(self, "_asr_busy", False)
                    or self._eval_pending
                    or getattr(self, "_eval_busy", False)
                    or self._pending_state is not None
                )
                if not busy:
                    break
            await asyncio.sleep(0.05)

    async def _evaluate_locked(self, state: AnalysisState):
        self._eval_busy = True
        try:
            out = await _run_graph(state)
            self._persist_last_signals(out)
            new_risk = out.get("risk", 0.0)
            new_band = out.get("band", "passive")
            self.signals = _signals(out)
            self.risk = new_risk
            self.risk_peak = max(self.risk_peak, new_risk)
            self.band = new_band
            decision = out.get("decision", "log")
        finally:
            self._eval_busy = False

        # Per-chunk certification log (Task 8): ONE unconditional line per graph
        # evaluation, so a two-device live call produces a readable, grep-able
        # stream of REAL per-chunk model scores — not just the throttled
        # signals.update / band-gated verdict.update pushes the client receives.
        # Every value here is live model output; a signal no engine produced
        # prints "n/a" (never a fabricated 0). Grep it with: `-s tap-eval` is
        # server-side (logger "antai...dispatcher"); filter logs on "tap-eval".
        def _s(v: object) -> str:
            return f"{v:.2f}" if isinstance(v, (int, float)) and not isinstance(v, bool) else "n/a"
        log.info(
            "tap-eval session=%s audio_seg=%d risk=%.1f band=%s decision=%s "
            "voice_df=%s video_df=%s scam=%s urgency=%s transcript_len=%d asr_fail=%d",
            self.session_key, self._audio_segments, new_risk, new_band, decision,
            _s(self.signals.get("voice_deepfake")), _s(self.signals.get("video_deepfake")),
            _s(self.signals.get("scam_prob")), _s(self.signals.get("urgency")),
            len(self.transcript or ""), self._asr_failures,
        )

        rt = get_rt_hub()

        # 1) Stream the live model signals continuously (every evaluation,
        #    lightly throttled) so the client window shows all engines running.
        await self._push_signals(rt)

        # 2) Verdict push — whenever the band changes or risk moves enough.
        #    A verdict now accompanies every meaningful state change, not just
        #    critical ones, so the LLM's "what's wrong" streams live too.
        verdict = out.get("verdict")
        prev_band = self.last_verdict.get("band") if self.last_verdict else None
        changed = (self.last_verdict is None or
                   new_band != prev_band or
                   abs(new_risk - self.last_verdict.get("risk_score", 0)) >= 8)
        if verdict and changed:
            self.last_verdict = verdict
            verdict["risk_score"] = new_risk
            verdict["band"] = new_band
            verdict["session_key"] = self.session_key
            db = get_db()
            for uid in self._push_targets():
                await rt.send(uid, "verdict.update", verdict)
                db.save_verdict(self.session_key, uid, "call", new_risk,
                                new_band, verdict.get("verdict", ""),
                                verdict.get("why", ""),
                                verdict.get("action", ""), verdict.get("scam_type"),
                                self.signals)

        # 3) Live LLM guidance — throttled, NO risk gate, and NON-BLOCKING:
        #    the CPU-bound LLM runs in a background task so it can never stall
        #    transcript/verdict/signals streaming (the eval lock is released
        #    immediately; the throttle slot is reserved up-front to prevent
        #    pile-up).
        self._maybe_schedule_guidance()

        # 4) Deepfake pause-and-alert: if the synthetic-voice / deepfake-video
        #    detectors cross their margins, push an alert that pauses the call.
        await self._maybe_deepfake_alert()

        # decision enforcement
        if decision == "freeze":
            await self._maybe_freeze(out)
        elif decision == "verify":
            await self._maybe_verify(out)

    def _maybe_schedule_guidance(self):
        cfg = get_config()
        now = time.time()
        if now - self._last_guidance_t < cfg.pipeline.live_guidance_interval_s:
            return
        if not self.transcript:
            return
        if self._guidance_running:   # single-threaded CPU LLM: no overlap
            return
        self._last_guidance_t = now   # reserve the slot immediately
        self._guidance_running = True
        log.info("scheduling live guidance (transcript=%d, risk=%.0f, voice=%.3f, video=%s)",
                 len(self.transcript), self.risk,
                 self.signals.get("voice_deepfake") if self.signals.get("voice_deepfake") is not None else -1,
                 f"{self.signals.get('video_deepfake'):.3f}" if self.signals.get("video_deepfake") is not None else "n/a")
        asyncio.create_task(self._guidance_task())

    async def _guidance_task(self):
        from ..inference.llm.reasoner import generate_live_guidance
        try:
            guidance = await asyncio.to_thread(
                generate_live_guidance, self.risk, self.signals,
                self.transcript, self.last_guidance)
        except Exception:
            log.exception("live guidance generation failed")
            guidance = ""
        finally:
            self._guidance_running = False
        if guidance and guidance != self.last_guidance:
            self.last_guidance = guidance
            rt = get_rt_hub()
            for uid in self._push_targets():
                await rt.send(uid, "guidance.update",
                              {"session_key": self.session_key,
                               "guidance": guidance, "risk_score": self.risk})
            log.info("pushed guidance: %s", guidance)

    async def _maybe_deepfake_alert(self):
        """Pause the call + warn (disclaimer popup) on any high-risk signal:
        synthetic voice, deepfake video, a scammy/credential ask, or an
        impersonated identity."""
        cfg = get_config()
        if not cfg.pipeline.deepfake_alert_enabled:
            return
        voice = self.signals.get("voice_deepfake")
        video = self.signals.get("video_deepfake")
        request = self.signals.get("request_detected")
        rtype = self.signals.get("request_type")
        identity = self.signals.get("identity_mismatch")

        source = None
        title = None
        message = None
        if voice is not None and voice > cfg.pipeline.voice_alert_threshold:
            source = "voice"
            title = "AI-generated voice detected"
            message = ("This caller's voice appears to be AI-generated or cloned "
                       f"({voice * 100:.0f}% confidence). A real person's voice does "
                       "not normally score this high. Do not share OTPs, passwords "
                       "or money on this call. Ask something only the real person "
                       "would know, or hang up and call them back on their saved "
                       "number.")
        elif video is not None and video > cfg.pipeline.video_alert_threshold:
            source = "video"
            title = "AI-generated video detected"
            message = ("This caller's video appears to be synthetic or altered "
                       f"({video * 100:.0f}% confidence). Do not trust what you see "
                       "on this call.")
        elif request and self.risk >= 70:
            source = "request"
            title = "Suspicious request"
            message = (f"Possible scam: the caller is asking for {rtype or 'sensitive information'} — "
                       "this is a common scam tactic.")
        elif identity:
            source = "identity"
            title = "Voice does not match your contact"
            message = ("This caller's voice does not match the voiceprint of the "
                       "person they claim to be.")
        if source is None:
            return

        now = time.time()
        last = self._last_alert_t.get(source, 0.0)
        if now - last < cfg.pipeline.deepfake_alert_cooldown_s:
            return
        self._last_alert_t[source] = now

        # Offer the voiceprint cross-check only when it can actually run: the
        # ECAPA engine is loaded AND we have buffered call audio to compare.
        sv = get_hub().get("speaker_verify")
        buffered, _ = self.buffered_voice()
        can_cross_verify = bool(
            source in ("voice", "identity") and buffered is not None
            and sv is not None and sv.ready())

        payload = {
            "session_key": self.session_key,
            "source": source,
            "title": title,
            "voice_deepfake": voice,
            "video_deepfake": video,
            "video_votes": self.signals.get("video_votes"),
            # which detectors produced the voice number, so the popup can say
            # "confirmed by 2 of 2 detectors" instead of an unexplained score
            "voice_per_model": self.signals.get("voice_per_model"),
            "voice_sources": self.signals.get("voice_sources"),
            "voice_agreement": self.signals.get("voice_agreement"),
            "voice_backend": self.signals.get("voice_backend"),
            "can_cross_verify": can_cross_verify,
            "message": message,
        }
        rt = get_rt_hub()
        for uid in self._push_targets():
            await rt.send(uid, "deepfake.alert", payload)
        self.events.append({"t": time.strftime("%H:%M:%S"),
                            "what": f"Alert: {source}"})
        log.info("alert (%s) session=%s risk=%.0f voice=%s video=%s "
                 "agreement=%s cross_verify=%s",
                 source, self.session_key, self.risk,
                 f"{voice:.2f}" if voice is not None else "n/a",
                 f"{video:.2f}" if video is not None else "n/a",
                 self.signals.get("voice_agreement"), can_cross_verify)

    async def _push_signals(self, rt):
        """Stream the live per-engine signals to the client (throttled).

        This is the "all models running continuously" feed: every evaluation
        emits a compact snapshot of each detector's current output so the app
        can show them live, independent of the risk verdict.
        """
        cfg = get_config()
        now = time.time()
        if now - self._last_signals_t < cfg.pipeline.live_signals_interval_s:
            return
        self._last_signals_t = now
        payload = {"session_key": self.session_key, "risk": self.risk,
                   "band": self.band, **self.signals}
        # Diagnostic: which engines actually loaded. Lets the client show
        # "n/m models loaded" and distinguish a 0% that means "model is running
        # and confidently sees nothing" from a 0% that means "model never
        # loaded". Uses available() via summary() — a plain bool read, so it
        # never triggers a (heavy) load on the streaming path.
        payload["engines_ready"] = self._engines_ready()
        # Listening evidence. "voice_deepfake": null is ambiguous on its own —
        # it means either "no audio has arrived yet" (fine, stay quiet) or
        # "audio WAS analysed and no synthetic-voice detector answered" (the
        # failure that lets a cloned voice through). audio_segments separates
        # the two, and asr_failures > 0 says the transcript is incomplete
        # because speech-to-text is erroring, not because nobody spoke.
        payload["audio_segments"] = self._audio_segments
        payload["asr_failures"] = self._asr_failures
        for uid in self._push_targets():
            await rt.send(uid, "signals.update", payload)

    def _engines_ready(self) -> dict:
        """Compact {engine_name: loaded_bool} map from the model hub.

        Cheap: hub.summary() just reads each engine's cached _loaded flag (a
        dict comprehension of attribute reads, microseconds for ~14 engines)
        and never attempts a load, so it is safe on the throttled streaming
        path. Computed fresh each push — NOT cached — so an engine with a slow
        startup load (e.g. the LLM GGUF, tens of seconds) flips to True in the
        client the moment it finishes, even if the call began mid-warmup.

        Engines exposing detection_live() get that answer instead of the load
        flag: voice_deepfake can load successfully and then lose its only backend
        mid-call, and "loaded" would keep saying everything is fine while nothing
        is being scored."""
        try:
            out = {name: bool(avail)
                   for name, (avail, _reason) in get_hub().summary().items()}
        except Exception:
            return {}
        for name in list(out):
            if not out[name]:
                continue
            live = getattr(get_hub().get(name), "detection_live", None)
            if live is None:
                continue
            try:
                out[name] = bool(live())
            except Exception:      # a health probe must never break the push
                pass
        return out

    async def _maybe_freeze(self, state: AnalysisState):
        if not get_config().pipeline.freeze_enabled:
            return
        from ..intercept.controller import create_intercept, has_pending_freeze
        if has_pending_freeze(self.session_key):
            return
        frz = create_intercept(self.session_key, self.callee_id, state)
        if frz:
            for uid in self._push_targets():
                await get_rt_hub().send(uid, "freeze.request", frz["directive"])
            self.events.append({"t": time.strftime("%H:%M:%S"),
                                "what": f"Froze {frz['directive'].get('request_type')} request"})

    async def _maybe_verify(self, state: AnalysisState):
        """Automatic identity-claim verification ("this is your mother").

        NOTE: this path only fires when the trust link exists for `self.callee_id`,
        which is the user row the TAP socket provisioned (phone `app:<username>`),
        while /trust-circle/link writes links for the phone/OTP row. Until those two
        identities are unified, links made in the app are invisible here and this
        returns not_linked. The user-initiated "Verify caller" button does not go
        through here — it authenticates over REST, so it is unaffected.
        """
        claim_id = state.get("claimed_identity_id")
        if claim_id is None:
            return
        from ..trust_circle.verify import trigger_verify
        r = await trigger_verify(self.session_key, self.callee_id, claim_id)
        if not r.get("prompt_sent"):
            # Never fail silently: a claimed identity we could not challenge is
            # exactly the case a user would assume had been checked.
            log.warning("auto-verify skipped for session=%s callee=%s claim=%s: %s",
                        self.session_key, self.callee_id, claim_id,
                        r.get("reason") or "unknown")

    # ------------------------------------------------------------- finish
    async def finish(self):
        if self._finished:
            return
        self._finished = True
        db = get_db()
        cfg = get_config()

        # flush any queued ASR segments so the report sees the full transcript
        try:
            if self._asr_task is not None and not self._asr_task.done():
                await asyncio.wait_for(asyncio.shield(self._asr_task), timeout=10.0)
        except Exception:
            pass
        if self._periodic_task is not None:
            self._periodic_task.cancel()

        # drop the cross-verification audio buffer: it exists only to answer an
        # in-call "is this really them?" question, so it must not outlive the call
        self._voice_buf.clear()

        # store peak risk on the call record
        from ..media_sfu import get_call_manager
        cm = get_call_manager()
        s = cm.get(self.session_key)
        if s:
            db.end_call(s.call_id, risk_peak=self.risk_peak)

        if not self.transcript:
            return
        if not cfg.pipeline.report_after:
            return

        from ..inference.llm.reasoner import generate_report
        # Report generation is a heavy blocking LLM call; run it off the loop.
        report = await asyncio.to_thread(generate_report, self.kind, self.transcript,
                                         self.signals, self.events,
                                         self.signals.get("scam_type"), self.risk_peak)
        for uid in self._push_targets():
            saved = db.save_report(self.session_key, uid, self.kind,
                                   report["title"], report["body"],
                                   report["scam_type"], report["signals_fired"])
            await get_rt_hub().send(uid, "report.ready",
                                    {"session_key": self.session_key,
                                     "report_id": saved.id,
                                     "title": saved.title})


_runners: dict[str, SessionRunner] = {}


def get_session_runner(session_key: str) -> SessionRunner:
    if session_key not in _runners:
        _runners[session_key] = SessionRunner(session_key)
    return _runners[session_key]
