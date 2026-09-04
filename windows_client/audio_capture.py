"""
antAI Windows Client — Audio Capture Module

Captures mono float32 PCM from the default microphone using sounddevice.
Yields rolling chunks of approximately `chunk_duration_s` seconds.

Usage:
    from audio_capture import MicrophoneCapture

    with MicrophoneCapture(sample_rate=16000, chunk_s=2.0) as cap:
        for chunk in cap:            # numpy float32 array
            process(chunk)
"""
from __future__ import annotations

import logging
import queue
import threading
from typing import Generator

import numpy as np

log = logging.getLogger(__name__)

_sd_available = False
try:
    import sounddevice as sd
    _sd_available = True
except ImportError:
    log.warning("sounddevice not installed — microphone capture unavailable. "
                "Install with: pip install sounddevice")


class MicrophoneCapture:
    """Context-manager that captures PCM from the default microphone.

    Each iteration yields a mono float32 numpy array of approximately
    `chunk_s` seconds at `sample_rate` Hz.

    Falls back to silence (zeros) when sounddevice is unavailable so that the
    rest of the pipeline can run in demo / testing mode on machines without
    audio hardware.
    """

    def __init__(self, sample_rate: int = 16000, chunk_s: float = 2.0):
        self.sample_rate = sample_rate
        self.chunk_size = int(sample_rate * chunk_s)
        self._q: queue.Queue[np.ndarray] = queue.Queue(maxsize=8)
        self._stream = None
        self._stop_event = threading.Event()
        self._fallback = not _sd_available

    # ── sounddevice callback ───────────────────────────────────────────────────
    def _sd_callback(self, indata: np.ndarray, frames: int,
                     time_info, status) -> None:
        if status:
            log.debug("sounddevice status: %s", status)
        mono = indata[:, 0].copy()   # take first channel; sddevice returns (frames, channels)
        try:
            self._q.put_nowait(mono)
        except queue.Full:
            pass   # drop if consumer is slow; caller sees skips, not OOM

    # ── context manager ────────────────────────────────────────────────────────
    def __enter__(self) -> "MicrophoneCapture":
        if not self._fallback:
            try:
                self._stream = sd.InputStream(
                    samplerate=self.sample_rate,
                    channels=1,
                    dtype="float32",
                    blocksize=int(self.sample_rate * 0.1),  # 100ms push
                    callback=self._sd_callback
                )
                self._stream.start()
                log.info("Microphone capture started (rate=%d, chunk=%ds)",
                         self.sample_rate, self.chunk_size // self.sample_rate)
            except Exception as e:
                log.warning("sounddevice stream failed (%s) — using silence fallback", e)
                self._fallback = True
        return self

    def __exit__(self, *args) -> None:
        self._stop_event.set()
        if self._stream is not None:
            try:
                self._stream.stop()
                self._stream.close()
            except Exception:
                pass

    def __iter__(self) -> Generator[np.ndarray, None, None]:
        """Yield PCM chunks until stop() is called."""
        buf = np.zeros(0, dtype=np.float32)

        if self._fallback:
            # Yield silence in real-time for demo/testing
            import time
            chunk_s = self.chunk_size / max(1, self.sample_rate)
            while not self._stop_event.is_set():
                time.sleep(chunk_s)
                yield np.zeros(self.chunk_size, dtype=np.float32)
            return

        while not self._stop_event.is_set():
            try:
                block = self._q.get(timeout=0.5)
            except queue.Empty:
                continue
            buf = np.concatenate([buf, block])
            while len(buf) >= self.chunk_size:
                yield buf[:self.chunk_size].copy()
                buf = buf[self.chunk_size:]

    def stop(self) -> None:
        self._stop_event.set()
