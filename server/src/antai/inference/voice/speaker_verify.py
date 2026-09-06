"""Speaker verification via speechbrain ECAPA-TDNN.

Enrollment: guided recording during onboarding -> embedding stored (encrypted)
in the DB. Live check: compare the caller's embedding against the stored
voiceprint of whoever their profile claims to be (cosine similarity).
"""
from __future__ import annotations

import io
import logging
import tempfile
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config
from ...storage import get_db

log = logging.getLogger(__name__)


class SpeakerVerifyEngine(BaseEngine):
    name = "speaker_verify"

    def _load(self) -> bool:
        cfg = get_config()
        savedir = Path(cfg.models.root) / "speaker_verify"
        if not savedir.exists():
            log.warning("speaker_verify weights not downloaded (%s)", savedir)
            return False
        try:
            from speechbrain.inference.speaker import EncoderClassifier
            self.device = self._device_for()
            # speechbrain needs an indexed device string ("cuda:0"); bare
            # "cuda" fails its parser and silently falls back.
            sb_device = (self.device + ":0") if self.device == "cuda" else self.device
            self.model = EncoderClassifier.from_hparams(
                source="speechbrain/spkrec-ecapa-voxceleb", savedir=str(savedir),
                run_opts={"device": sb_device})
            return True
        except Exception as e:
            log.warning("speaker verify load failed: %s", e)
            return False

    def embed(self, audio: np.ndarray, sample_rate: int = 16000) -> np.ndarray | None:
        if not self.ready():
            return None
        import torch
        try:
            sig = torch.from_numpy(audio.astype(np.float32)).unsqueeze(0)
            emb = self.model.encode_batch(sig)
            emb = emb.detach().cpu().numpy().reshape(-1)
            return emb / (np.linalg.norm(emb) + 1e-9)
        except Exception as e:
            log.warning("embed failed: %s", e)
            return None

    @staticmethod
    def similarity(a: np.ndarray, b: np.ndarray) -> float:
        if a is None or b is None:
            return 0.0
        return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-9))

    def verify_against(self, audio: np.ndarray, voiceprint_owner_id: int,
                       sample_rate: int = 16000) -> dict:
        """Compare live audio against stored voiceprints of a contact."""
        db = get_db()
        vps = db.get_voiceprints_for(voiceprint_owner_id)
        if not vps:
            return {"similarity": None, "matches": False, "ready": self.ready(),
                    "reason": "no_voiceprint"}
        emb = self.embed(audio, sample_rate)
        if emb is None:
            return {"similarity": None, "matches": False, "ready": False,
                    "reason": "embed_failed"}
        sims = [self.similarity(emb, db.get_voiceprint_embedding(v)) for v in vps]
        best = max(sims)
        from ...config import get_config
        threshold = get_config().pipeline.speaker_sim_threshold
        return {"similarity": best, "matches": best >= threshold, "ready": True,
                "threshold": threshold}


# A voiceprint is only useful if it was built from enough real speech. Enrolling a
# 1-second clip, or a near-silent one, produces an embedding that sits near the
# centre of the space and then matches *everybody* — which would turn the
# cross-check from evidence into noise at exactly the moment it matters. So the
# guards below refuse the recording instead of storing a bad reference.
MIN_ENROLL_SECONDS = 3.0
MIN_ENROLL_RMS = 0.008

# Single source of truth for the copy the app shows after an enrolment attempt,
# mirroring CROSS_VERIFY_HINTS in the dispatcher: the reason is a stable code for
# logs, the hint is the sentence a human reads.
ENROLL_HINTS = {
    "ok": "Voice sample saved.",
    "engine_unavailable": "The voice-matching model isn't loaded on the server yet.",
    "undecodable_audio": "That recording couldn't be read. Try recording again.",
    "too_short": f"Too short — speak for at least {MIN_ENROLL_SECONDS:.0f} seconds.",
    "too_quiet": "Too quiet — move somewhere less noisy and speak up.",
    "embed_failed": "The server couldn't process that recording. Try again.",
    "save_failed": "The sample was processed but couldn't be saved.",
}


def enroll_audio(user_id: int, wav_bytes: bytes) -> tuple[bool, int | None, str]:
    """Enroll a voiceprint for ``user_id`` -> ``(ok, embedding_dim, reason)``.

    The reason is returned on success too ("ok") so callers never have to guess
    why a False came back; a silent False was previously indistinguishable from
    "the model isn't loaded", which is the difference between "try again" and
    "stop trying".
    """
    from ...inference.hub import get_hub
    eng = get_hub().get("speaker_verify")
    if eng is None or not eng.ready():
        return False, None, "engine_unavailable"
    audio, sr = _wav_bytes_to_audio(wav_bytes)
    if audio is None or not len(audio):
        return False, None, "undecodable_audio"
    sr = int(sr or 16000)
    if len(audio) / float(sr) < MIN_ENROLL_SECONDS:
        return False, None, "too_short"
    if float(np.sqrt(np.mean(np.square(audio.astype(np.float32))))) < MIN_ENROLL_RMS:
        return False, None, "too_quiet"
    emb = eng.embed(audio, sr)
    if emb is None:
        return False, None, "embed_failed"
    try:
        get_db().save_voiceprint(user_id, user_id, emb)
    except Exception as e:
        log.warning("voiceprint save failed for user %s: %s", user_id, e)
        return False, None, "save_failed"
    return True, int(emb.shape[0]), "ok"


def enrollment_status(user_id: int) -> dict:
    """What the app needs to render the enrolment screen without guessing."""
    from ...inference.hub import get_hub
    eng = get_hub().get("speaker_verify")
    try:
        vps = get_db().get_voiceprints_for(user_id, owner_id=user_id)
    except Exception as e:
        log.warning("voiceprint lookup failed for user %s: %s", user_id, e)
        vps = []
    dim = None
    if vps:
        emb = get_db().get_voiceprint_embedding(vps[-1])
        if emb is not None:
            dim = int(np.asarray(emb).reshape(-1).shape[0])
    return {
        "enrolled": bool(vps),
        "count": len(vps),
        "embedding_dim": dim,
        "engine_ready": bool(eng is not None and eng.ready()),
        "min_seconds": MIN_ENROLL_SECONDS,
    }


def _wav_bytes_to_audio(data: bytes) -> tuple[np.ndarray | None, int]:
    """Kept as a thin alias: the decoder now lives in ``audio_io`` so tools that
    only need to read a file don't have to import the model hub and the DB."""
    from .audio_io import decode_audio_bytes
    return decode_audio_bytes(data)
