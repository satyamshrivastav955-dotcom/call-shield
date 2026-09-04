"""Per-participant audio ingestion with Silero-VAD speech segmentation.

Converts av.AudioFrame -> mono float32 @16kHz, detects speech, and emits
completed speech segments (typically 1-4s) to the orchestration dispatcher.
Because the SFU gives us a separate track per participant, 'who is speaking'
is known natively (no diarization needed).

Design notes (these were the cause of the "butchered/laggy audio" bug):
  * This runs inside the SFU tap, which shares the asyncio event loop with the
    media relay. Therefore push_audio MUST stay cheap and NEVER block the loop.
    Resampling is a fast C call and stays on the loop; the Silero VAD inference
    (and its one-time model load) is offloaded via ``asyncio.to_thread`` so the
    relay is never starved.
  * Silero VAD requires EXACTLY 512 samples per call @16kHz (256 @8kHz). We feed
    it fixed 512-sample windows; the previous 320-sample windows made every VAD
    call raise -> no speech ever detected -> no transcript.
  * Silero VAD is a stateful recurrent model, so each participant gets its OWN
    model instance (same weights, isolated streaming state). A single av
    AudioResampler is likewise cached per participant to preserve continuity
    across frames instead of being rebuilt every frame (which caused artifacts).
"""
from __future__ import annotations

import asyncio
import logging
import time

import numpy as np

from ..config import get_config

log = logging.getLogger(__name__)

SegmentHandler = object  # callable(speaker_id:int, audio:np.ndarray, sample_rate:int, meta:dict) -> Awaitable


class AudioIngestor:
    def __init__(self, session_key: str, on_segment: SegmentHandler):
        cfg = get_config()
        self.session_key = session_key
        self.sr = cfg.pipeline.sample_rate
        self.on_segment = on_segment
        self.vad_aggressiveness = cfg.pipeline.vad_aggressiveness

        # Silero VAD requires an exact window: 512 samples @16kHz, 256 @8kHz.
        self._win = 512 if self.sr >= 16000 else 256
        self._frame_s = self._win / self.sr

        # per-participant state
        self._buf: dict[int, np.ndarray] = {}
        self._seg: dict[int, np.ndarray] = {}
        self._in_speech: dict[int, bool] = {}
        self._silence: dict[int, float] = {}
        self._seg_start: dict[int, float] = {}
        self._resamplers: dict[int, object] = {}   # one av.AudioResampler per participant
        self._vad_models: dict[int, object] = {}    # one Silero VAD per participant (isolated state)

        self._max_seg_s = cfg.pipeline.max_segment_s
        self._min_seg_s = cfg.pipeline.min_segment_s
        self._hangover_s = cfg.pipeline.hangover_s

        self._tasks: dict[int, asyncio.Task] = {}

    async def push_audio(self, participant_id: int, frame) -> None:
        """SFU tap callback: runs for every audio frame. Must not block the loop."""
        samples = self._frame_to_float32(participant_id, frame)
        await self._ingest_samples(participant_id, samples)

    async def push_pcm(self, participant_id: int, samples: np.ndarray,
                       sample_rate: int | None = None) -> None:
        """Raw-PCM entry point for the external streaming API (no av.AudioFrame).

        The WebRTC tap decodes av frames first; a plain WebSocket/file stream
        already has PCM, so it skips that one step and joins the SAME VAD
        segmentation + emit path. Keeping both entry points on one pipeline is
        deliberate: the streaming demo must exercise the exact segmentation the
        live call path does, not a parallel copy. `samples` must be mono
        float32 in [-1, 1]; it is resampled to the pipeline rate when the caller
        reports a different `sample_rate`.
        """
        samples = self._to_pipeline_pcm(samples, sample_rate)
        await self._ingest_samples(participant_id, samples)

    def _to_pipeline_pcm(self, samples: np.ndarray,
                         sample_rate: int | None) -> np.ndarray | None:
        if samples is None:
            return None
        arr = np.asarray(samples, dtype=np.float32).reshape(-1)
        if arr.size == 0:
            return arr
        sr = int(sample_rate or self.sr)
        if sr != self.sr:
            # linear resample: cheap, dependency-free, and adequate for VAD +
            # the SSL detectors (which internally resample to their own rate).
            n_out = int(round(arr.size * self.sr / sr))
            if n_out <= 0:
                return np.zeros(0, dtype=np.float32)
            x_old = np.linspace(0.0, 1.0, num=arr.size, endpoint=False)
            x_new = np.linspace(0.0, 1.0, num=n_out, endpoint=False)
            arr = np.interp(x_new, x_old, arr).astype(np.float32)
        return arr

    async def _ingest_samples(self, participant_id: int,
                              samples: np.ndarray | None) -> None:
        """Buffer mono float32 samples and drain them through the VAD in fixed
        512-sample windows. Shared by both the av-frame and raw-PCM paths."""
        if samples is None or len(samples) == 0:
            return
        buf = self._buf.setdefault(participant_id, np.zeros(0, dtype=np.float32))
        self._buf[participant_id] = np.concatenate([buf, samples])

        # process in fixed 512-sample windows (Silero VAD requirement)
        win = self._win
        while len(self._buf[participant_id]) >= win:
            chunk = self._buf[participant_id][:win]
            self._buf[participant_id] = self._buf[participant_id][win:]
            await self._process_window(participant_id, chunk)

    async def _process_window(self, pid: int, chunk: np.ndarray) -> None:
        speech = await self._is_speech(pid, chunk)
        now = time.time()
        seg = self._seg.setdefault(pid, np.zeros(0, dtype=np.float32))
        in_speech = self._in_speech.get(pid, False)

        if speech:
            if not in_speech:
                # speech onset
                self._seg_start[pid] = now
            self._in_speech[pid] = True
            self._silence[pid] = 0.0
            self._seg[pid] = np.concatenate([seg, chunk])
        else:
            if in_speech:
                self._silence[pid] = self._silence.get(pid, 0.0) + self._frame_s
            # end segment on enough silence or max length
            seg_dur = len(seg) / self.sr
            if in_speech and (self._silence.get(pid, 0.0) >= self._hangover_s or
                              seg_dur >= self._max_seg_s):
                await self._emit(pid, seg, self._seg_start.get(pid, now))
                self._seg[pid] = np.zeros(0, dtype=np.float32)
                self._in_speech[pid] = False
                self._silence[pid] = 0.0
            elif not in_speech:
                pass
        # force flush a very long continuous segment even without silence
        seg = self._seg.get(pid)
        if seg is not None and len(seg) / self.sr >= self._max_seg_s:
            await self._emit(pid, seg, self._seg_start.get(pid, now))
            self._seg[pid] = np.zeros(0, dtype=np.float32)
            self._in_speech[pid] = False
            self._silence[pid] = 0.0

    async def _emit(self, pid: int, audio: np.ndarray, t0: float) -> None:
        if len(audio) / self.sr < self._min_seg_s:
            return
        meta = {"segment_start": t0, "sample_rate": self.sr,
                "session_key": self.session_key}
        try:
            await self.on_segment(pid, audio.copy(), self.sr, meta)
        except Exception:
            log.exception("audio segment handler error")

    async def flush(self, pid: int | None = None) -> None:
        pids = [pid] if pid is not None else list(self._seg.keys())
        for p in pids:
            seg = self._seg.get(p)
            if seg is not None and len(seg) / self.sr >= self._min_seg_s:
                await self._emit(p, seg, self._seg_start.get(p, time.time()))
            self._seg[p] = np.zeros(0, dtype=np.float32)
            self._in_speech[p] = False

    # ------------------------------------------------------------ conversion
    def _frame_to_float32(self, pid: int, frame) -> np.ndarray | None:
        """av.AudioFrame -> mono float32 @self.sr, using a per-participant resampler.

        Reusing one resampler per stream preserves resampling continuity across
        frames (rebuilding it every frame reset the filter state and injected
        audible artifacts). Runs on the event loop: this is a fast C call.
        """
        try:
            import av
        except Exception:
            return None
        if frame is None:
            return None
        resampler = self._resamplers.get(pid)
        if resampler is None:
            resampler = av.AudioResampler(format="s16", layout="mono", rate=self.sr)
            self._resamplers[pid] = resampler
        try:
            frames = resampler.resample(frame)
        except Exception:
            # input format changed or resampler faulted: rebuild once and retry
            resampler = av.AudioResampler(format="s16", layout="mono", rate=self.sr)
            self._resamplers[pid] = resampler
            try:
                frames = resampler.resample(frame)
            except Exception as e:
                log.warning("resample failed: %s", e)
                return None
        if not frames:
            return None
        arrs = []
        for f in frames:
            arr = f.to_ndarray()          # (1, n) int16 for mono s16
            if arr.ndim == 2:
                arr = arr[0]
            arrs.append(np.asarray(arr, dtype=np.float32))
        if not arrs:
            return None
        out = np.concatenate(arrs) if len(arrs) > 1 else arrs[0]
        return (out / 32768.0).astype(np.float32)   # s16 -> [-1, 1]

    # -------------------------------------------------------------------- VAD
    async def _is_speech(self, pid: int, chunk: np.ndarray) -> bool:
        """Run Silero VAD OFF the event loop so the media relay never stalls."""
        try:
            return await asyncio.to_thread(self._vad_prob_sync, pid, chunk)
        except Exception as e:
            log.warning("VAD inference failed: %s", e)
            return False

    def _vad_prob_sync(self, pid: int, chunk: np.ndarray) -> bool:
        """Runs in a worker thread. Lazily loads a per-participant VAD instance.

        A given participant's windows are awaited in order, so this is never
        re-entered concurrently for the same pid -> the lazy load is safe without
        a lock, and each stream keeps its own continuous recurrent state.
        """
        model = self._vad_models.get(pid, None)
        if model is False:
            return False
        if model is None:
            try:
                from silero_vad import load_silero_vad
                model = load_silero_vad()
                self._vad_models[pid] = model
                log.info("silero VAD loaded for participant %s", pid)
            except Exception as e:
                self._vad_models[pid] = False
                log.error("silero VAD unavailable: %s", e)
                return False
        import torch
        with torch.no_grad():
            prob = model(torch.from_numpy(chunk), self.sr).item()
        return prob >= 0.5
