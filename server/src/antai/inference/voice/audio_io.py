"""Decode arbitrary uploaded audio to mono float32 — no models, no database.

Voiceprint enrollment, the diagnostic tools and any future offline scoring all
need the same thing: bytes from a phone or a file turned into mono float32 at a
known sample rate. That helper used to live inside ``speaker_verify``, which
imports the model hub and the database at module level — so a script that only
wanted to decode a wav had to drag in torch and sqlalchemy first, and the
diagnostic could not run on a machine where those were missing.

Keeping the codec path dependency-free (numpy only, with soundfile/PyAV tried at
call time) means the decoder can never be the reason a check fails to run.
"""
from __future__ import annotations

import io
import logging

import numpy as np

log = logging.getLogger(__name__)

TARGET_SR = 16000


def decode_audio_bytes(data: bytes) -> tuple[np.ndarray | None, int]:
    """Any container -> (mono float32, sample_rate), or (None, 0) on failure.

    Tries soundfile first (wav/flac/ogg, no external binary) and falls back to
    PyAV for compressed containers (mp3/m4a/aac), which is what phone recordings
    and AI-voice tools usually produce.
    """
    # 1) soundfile: wav / flac / ogg
    try:
        import soundfile as sf
        with io.BytesIO(data) as f:
            audio, sr = sf.read(f, dtype="float32")
        if audio.ndim > 1:
            audio = audio.mean(axis=1)
        return np.asarray(audio, dtype=np.float32), int(sr)
    except Exception:
        pass
    # 2) PyAV: anything ffmpeg can open, resampled to TARGET_SR on the way out
    try:
        import av
        with av.open(io.BytesIO(data)) as container:
            astr = container.streams.audio[0]
            res = av.AudioResampler(format="fltp", layout="mono", rate=TARGET_SR)
            chunks = []
            for frame in container.decode(astr):
                for o in res.resample(frame):
                    arr = o.to_ndarray()
                    if arr.ndim == 2:
                        arr = arr.mean(axis=0)
                    chunks.append(np.asarray(arr, dtype=np.float32))
        if not chunks:
            return None, 0
        return np.concatenate(chunks), TARGET_SR
    except Exception as e:
        log.debug("audio decode via PyAV failed: %s", e)
    # 3) stdlib wave: PCM wav only, but it needs nothing installed at all. Worth
    # having because the diagnostics must still be able to read a plain recording
    # on a machine where the audio wheels are missing — which is precisely the
    # kind of environment where something is already wrong.
    try:
        import wave
        with wave.open(io.BytesIO(data), "rb") as w:
            sr = w.getframerate()
            ch = w.getnchannels()
            width = w.getsampwidth()
            raw = w.readframes(w.getnframes())
        dtype = {1: np.uint8, 2: np.int16, 4: np.int32}.get(width)
        if dtype is None:
            return None, 0
        arr = np.frombuffer(raw, dtype=dtype).astype(np.float32)
        if width == 1:
            arr = (arr - 128.0) / 128.0
        else:
            arr = arr / float(2 ** (8 * width - 1))
        if ch > 1:
            arr = arr.reshape(-1, ch).mean(axis=1)
        return np.asarray(arr, dtype=np.float32), int(sr)
    except Exception as e:
        log.debug("audio decode via stdlib wave failed: %s", e)
        return None, 0


def to_sample_rate(audio: np.ndarray, sr: int,
                   target: int = TARGET_SR) -> tuple[np.ndarray, int]:
    """Resample to ``target`` if possible, otherwise return the audio untouched.

    Returning the original rate rather than raising is deliberate: every caller
    passes the rate along with the samples, so scoring at the wrong-but-declared
    rate degrades accuracy, while failing outright would produce no score at all.
    """
    if sr == target or not sr:
        return audio, sr or target
    try:
        from math import gcd
        from scipy.signal import resample_poly
        g = gcd(int(sr), int(target))
        out = resample_poly(audio, target // g, int(sr) // g)
        return np.asarray(out, dtype=np.float32), target
    except Exception as e:
        log.debug("resample %d->%d failed: %s", sr, target, e)
        return audio, sr
