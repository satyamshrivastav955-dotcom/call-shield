"""Synthetic/AI-voice (deepfake) detection - multi-model SSL ensemble.

Two independent SSL models run per clip; no single model decides:
  A. AST-ASVspoof5 (Audio Spectrogram Transformer, ASVspoof5)
       models/voice_deepfake          (primary, strong on EN TTS)
  B. Wav2Vec2 deepfake-voice detector (SSL wav2vec2)
       models/voice_deepfake_cross2   (cross-check, catches non-EN TTS)
  C. AASIST-L (graph-attention, 85K params — optional ensemble member #3)
       models/voice_deepfake_aasist/aasist_l.pt
       (trained by scripts/train_aasist.py; also the on-device model)

Verdict: flag as spoof when max(A, B) > 0.7 (a single model may miss its
training distribution; agreement of two independent SSL models is the signal).
Margins: <0.4 bonafide, >0.7 spoof, in between "uncertain".
"""
from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from ..hub import BaseEngine
from ...config import get_config

log = logging.getLogger(__name__)

LABEL_HI = 0.7
LABEL_LO = 0.4

# After this many consecutive no-signal replies we stop asking Velma and use the
# local ensemble for the rest of the run.
_VELMA_MAX_FAILURES = 3


class VoiceDeepfakeEngine(BaseEngine):
    name = "voice_deepfake"

    # which backend is actually producing numbers right now: "velma" | "local".
    # Exposed (not underscore-prefixed) so GET /api/debug/models reports it and a
    # silent fallback is visible instead of looking like a healthy Velma.
    backend: str | None = None
    _local_ready: bool = False
    _local_device: str | None = None
    _velma_failures: int = 0
    # live per-backend capability; class-level defaults so detection_live() is safe
    # to call before the first load attempt
    _use_velma: bool = False
    _use_local: bool = False
    # why the hosted backend last produced nothing, surfaced by /api/debug/models
    _velma_last_error: str | None = None

    def _load(self) -> bool:
        """Load every synthetic-voice detector the config asks for.

        ``providers.voice_deepfake`` selects the policy:
          both   - hosted Velma API AND the local SSL ensemble on every segment
          velma  - Velma, with the local ensemble as an automatic fallback
          local  - the SSL ensemble only

        The local ensemble is loaded in all three cases. That is deliberate:
        ``_load_velma`` used to return True with an empty endpoint or a dead key,
        so the engine reported ready while every segment came back with
        ``spoof_prob = None``. The UI showed the model as loaded, risk stayed 0,
        and AI-voice detection silently never ran. A flagship detector must never
        fail that quietly, and must never depend on one hosted key.
        """
        prov = get_config().providers
        mode = (prov.voice_deepfake or "local").lower()
        if mode not in ("both", "velma", "local"):
            log.warning("voice_deepfake: unknown backend %r — using 'local'", mode)
            mode = "local"
        self._mode = mode
        self._velma_failures = 0
        self._models = []
        self._local_ready = False
        self._local_device = None
        self._use_velma = False
        self._use_local = False
        self.backend = None

        # local first, so we always know whether a fallback exists
        self._local_ready = self._load_local()
        if self._local_ready:
            self._local_device = self.device
            self._use_local = mode in ("both", "local", "velma")

        if mode in ("both", "velma"):
            ok, reason = self._velma_usable()
            self._use_velma = ok
            if not ok:
                if self._local_ready:
                    log.error("voice_deepfake: Velma unusable (%s) — the local SSL "
                              "ensemble is carrying AI-voice detection. It IS "
                              "active; accuracy is just single-source.", reason)
                else:
                    self._unavailable_reason = (
                        f"Velma unusable ({reason}) and no local ensemble in "
                        f"models/voice_deepfake")
                    log.error("voice_deepfake: %s — AI-VOICE DETECTION IS INACTIVE",
                              self._unavailable_reason)
                    return False

        if not (self._use_velma or self._use_local):
            self._unavailable_reason = ("no synthetic-voice backend available "
                                        "(models/voice_deepfake missing)")
            log.error("voice_deepfake: %s — AI-VOICE DETECTION IS INACTIVE",
                      self._unavailable_reason)
            return False

        if self._use_velma and self._use_local and mode == "both":
            self.backend = "both"
            self.device = f"velma-api + {self._local_device}"
        elif self._use_velma:
            self.backend = "velma"
            self.device = "velma-api"
        else:
            self.backend = "local"
            self.device = self._local_device
        log.info("voice_deepfake backend=%s (velma=%s, local=%s [%s]) on %s",
                 self.backend, self._use_velma, self._use_local,
                 ", ".join(m["name"] for m in self._models) or "-", self.device)
        return True

    def detection_live(self) -> bool:
        """Can this engine still score audio *right now*?

        ``available()`` only reports whether the load succeeded, and it keeps
        reporting True after the hosted backend is dropped mid-run (see
        ``_velma_score``: three no-signal replies set ``_use_velma = False``).
        With no local ensemble behind it that leaves AI-voice detection dead while
        the app cheerfully shows every model as loaded — the precise failure that
        made a cloned voice sail through unflagged. Cheap attribute reads only, so
        it is safe on the throttled signals path.
        """
        return bool(self._loaded and (self._use_velma or self._use_local))

    # ------------------------------------------------------------ Velma (API)
    @staticmethod
    def _velma_usable() -> tuple[bool, str]:
        """Whether the hosted Velma backend has enough config to be worth trying."""
        prov = get_config().providers
        if not prov.velma_api_key:
            return False, "VELMA_API_KEY not set in server/.env"
        if not prov.velma_endpoint:
            return False, "no endpoint (set VELMA_ENDPOINT or providers.velma.endpoint)"
        try:
            import websockets  # noqa: F401
        except ImportError:
            return False, "the 'websockets' package is not installed"
        return True, ""

    # ------------------------------------------------- SSL ensemble (local)
    def _load_local(self) -> bool:
        cfg = get_config()
        device = self._device_for()
        primary_dir = Path(cfg.models.root) / "voice_deepfake"
        if not primary_dir.exists():
            return False
        try:
            self._models: list[dict] = []
            # primary (AST ASVspoof5)
            p = self._load_one(primary_dir, device)
            if p is None:
                return False
            self._models.append({"name": "asv5", **p})
            # cross-check SSL wav2vec2 (optional; fail-soft)
            cross_dir = Path(cfg.models.root) / "voice_deepfake_cross2"
            if cross_dir.exists():
                c = self._load_one(cross_dir, device)
                if c is not None:
                    self._models.append({"name": "w2v", **c})
            # AASIST-L (optional; fail-soft) — the model-change research pick:
            # 85K-param graph-attention detector, trained by
            # scripts/train_aasist.py, and the one that ships on-device
            # (spoof_aasist_l.int8.onnx). Absent file = ensemble unchanged.
            a = self._load_aasist(Path(cfg.models.root) / "voice_deepfake_aasist",
                                  device)
            if a is not None:
                self._models.append(a)
            self.device = device
            return True
        except Exception as e:
            log.warning("voice deepfake load failed: %s", e)
            return False

    def _load_aasist(self, model_dir: Path, device: str) -> dict | None:
        ckpt = model_dir / "aasist_l.pt"
        if not ckpt.exists():
            return None
        try:
            import torch
            from ._aasist import SPOOF_INDEX, AASISTL
            model = AASISTL()
            state = torch.load(ckpt, map_location="cpu", weights_only=True)
            model.load_state_dict(state if "state_dict" not in state else state["state_dict"])
            model.eval()
            if device == "cuda":
                model = model.to("cuda")
            log.info("AASIST-L loaded as ensemble member #3 (%s)", ckpt)
            return {"name": "aasist", "model": model,
                    "spoof_index": SPOOF_INDEX,
                    "processor": None,       # raw-waveform input, no HF processor
                    "raw_waveform": True}
        except Exception as e:
            log.warning("AASIST-L load failed (ignored, ensemble continues): %s", e)
            return None

    def _load_one(self, model_dir: Path, device: str) -> dict | None:
        try:
            from transformers import (AutoFeatureExtractor,
                                      AutoModelForAudioClassification)
            processor = AutoFeatureExtractor.from_pretrained(str(model_dir))
            model = AutoModelForAudioClassification.from_pretrained(str(model_dir))
        except Exception as e1:
            try:
                from transformers import (AutoFeatureExtractor,
                                          Wav2Vec2ForSequenceClassification)
                processor = AutoFeatureExtractor.from_pretrained(str(model_dir))
                model = Wav2Vec2ForSequenceClassification.from_pretrained(str(model_dir))
            except Exception as e2:
                log.warning("voice deepfake sub-model load failed: %s / %s", e1, e2)
                return None
        if device == "cuda":
            model = model.half().to("cuda")
        else:
            model = model.eval()
        return {"processor": processor, "model": model,
                "spoof_index": self._find_spoof_index(model)}

    @staticmethod
    def _find_spoof_index(model) -> int:
        cfg = getattr(model, "config", None)
        id2label = getattr(cfg, "id2label", None) if cfg else None
        if id2label:
            for idx, label in id2label.items():
                low = str(label).lower()
                if "spoof" in low or "fake" in low:
                    return int(idx)
            if len(id2label) >= 2:
                return int(len(id2label) - 1)
        return 1  # ASVspoof convention: last class is spoof

    def _velma_score(self, audio: np.ndarray, sample_rate: int) -> float | None:
        """Raw Velma synthetic-voice probability for one segment, or None.

        A None used to be returned all the way up as "no signal", forever. Now it
        is counted, and after a few consecutive no-signal replies Velma is dropped
        for the rest of the run so a dead key stops costing ~12s of timeout per
        segment. A broken hosted key must degrade the accuracy of AI-voice
        detection, never switch it off.
        """
        if not self._use_velma:
            return None
        from ..providers import last_velma_error, velma_detect
        prov = get_config().providers
        prob = velma_detect(
            audio, sample_rate, api_key=prov.velma_api_key,
            endpoint=prov.velma_endpoint, model_id=prov.velma_model_id,
            auth_style=prov.velma_auth_style,
            response_path=prov.velma_response_path,
            config_json=prov.velma_config_json,
            eos=getattr(prov, "velma_eos", "") or "")

        if prob is None:
            self._velma_failures += 1
            # The reason lives in providers; repeat it here so the one log line an
            # operator actually reads says WHY, not just that it happened.
            why = last_velma_error() or "no reason recorded"
            self._velma_last_error = why
            if self._velma_failures >= _VELMA_MAX_FAILURES:
                self._use_velma = False
                self.backend = "local" if self._use_local else "none"
                self.device = self._local_device or "unavailable"
                log.error("voice_deepfake: Velma returned no signal %d times in a "
                          "row — dropping it for the rest of this run. AI-voice "
                          "detection continues on the local SSL ensemble%s. "
                          "Last reason: %s",
                          self._velma_failures,
                          "" if self._use_local else " — WHICH IS NOT LOADED", why)
            else:
                log.warning("voice_deepfake: Velma gave no signal (%d/%d): %s",
                            self._velma_failures, _VELMA_MAX_FAILURES, why)
            return None

        self._velma_failures = 0
        self._velma_last_error = None
        return prob

    def analyze(self, audio: np.ndarray, sample_rate: int = 16000,
                context_audio: np.ndarray | None = None) -> dict:
        """Returns {spoof_prob, label, per_model, ready, backend, sources}.

        In "both" mode the hosted API and the local ensemble are both scored and
        combined by `_combine`. In "velma" mode the local ensemble is only run
        when Velma produced nothing, so a working key costs no extra compute.

        ``context_audio`` is the longer unpadded context for the AST head (see
        ``voice_long_window_s``): padded short windows read as spoof to AST, so
        it must see seconds of real speech. Falls back to ``audio`` when absent.
        """
        if not self.ready():
            return {"spoof_prob": None, "label": None, "per_model": {},
                    "ready": False, "backend": "none"}

        velma_p = self._velma_score(audio, sample_rate)
        local: dict | None = None
        if self._use_local and (self._mode == "both" or velma_p is None):
            local = self._analyze_local(audio, sample_rate,
                                        context_audio=context_audio)
        return self._combine(velma_p, local)

    def _combine(self, velma_p: float | None, local: dict | None) -> dict:
        """Merge the hosted and local verdicts into one score.

        Either detector alone can raise the alarm — that is the point of running
        two. But two independent models are also the only defence against a single
        miscalibrated one crying wolf, so:
          * both flag  -> escalate toward critical (real corroboration)
          * one flags, the other clears it -> hold the score below the alert line
            and label it uncertain; the risk still rises and the signal is shown,
            but the user is not interrupted on one model's guess
          * ...UNLESS the flagging model is near-certain (>= voice_solo_alert_
            threshold), in which case it still alerts. A cap that applies even to
            near-certainty means a real clone goes unreported whenever the other
            backend happens to disagree, which is a worse outcome than one extra
            warning on a genuine call.
          * one flags, the other is merely unsure -> keep the flag as-is
        """
        cfg = get_config().pipeline
        local_p = (local or {}).get("spoof_prob")

        per: dict[str, float] = {}
        if velma_p is not None:
            per["velma-2"] = round(velma_p, 3)
        if local:
            per.update(local.get("per_model") or {})

        scores = [p for p in (velma_p, local_p) if p is not None]
        if not scores:
            log.error("voice_deepfake: no backend produced a score for this "
                      "segment — AI-voice detection is PRODUCING NOTHING")
            return {"spoof_prob": None, "label": None, "per_model": per,
                    "ready": True, "backend": self.backend, "sources": 0,
                    "agreement": None}

        prob = max(scores)
        agreement = "single-source"
        if len(scores) == 2:
            hi, lo = max(scores), min(scores)
            if lo > LABEL_HI:
                agreement = "both-flag"
                if cfg.voice_agreement_bonus:
                    prob = min(1.0, hi + (1.0 - hi) * 0.5)
            elif hi > LABEL_HI and lo < LABEL_LO:
                solo = float(getattr(cfg, "voice_solo_alert_threshold", 0.95))
                if hi >= solo:
                    # near-certain: report it, but say the backends disagreed so
                    # the warning copy can stay honest about the contradiction
                    agreement = "disagree-strong"
                else:
                    agreement = "disagree"
                    prob = min(hi, float(cfg.voice_disagreement_cap))
            elif hi > LABEL_HI:
                agreement = "one-flag-one-unsure"
            else:
                agreement = "both-clear"

        label = ("spoof" if prob > LABEL_HI else
                 "bonafide" if prob < LABEL_LO else "uncertain")
        if agreement == "disagree":
            # the two models contradict each other: do not call it a clone
            label = "uncertain"
        return {"spoof_prob": prob, "label": label, "per_model": per,
                "ready": True, "backend": self.backend,
                "sources": len(scores), "agreement": agreement}

    def _analyze_local(self, audio: np.ndarray, sample_rate: int,
                       context_audio: np.ndarray | None = None) -> dict:
        if not self._local_ready:
            return {"spoof_prob": None, "label": None, "per_model": {},
                    "ready": False, "backend": "none"}
        import torch
        try:
            per: dict[str, float] = {}
            for m in self._models:
                # AST saturates on zero-padded short windows (measured: 4s/8s
                # padded -> spoof 1.0 on silence AND speech; full ~10s context
                # -> sane). It therefore scores the long context; every other
                # head keeps the short rolling window.
                clip = (context_audio if (m["name"] == "asv5"
                                          and context_audio is not None)
                        else audio)
                if m.get("raw_waveform"):
                    # AASIST-L: raw waveform [1, T] straight in, no HF processor
                    inputs = {"audio": torch.from_numpy(
                        clip.astype("float32")).unsqueeze(0)}
                else:
                    inputs = m["processor"](clip, sampling_rate=sample_rate,
                                            return_tensors="pt")
                if self._local_device == "cuda":
                    inputs = {k: (v.half() if v.dtype == torch.float32 else v)
                              .to("cuda") for k, v in inputs.items()}
                with torch.no_grad():
                    logits = m["model"](**inputs).logits
                probs = torch.softmax(logits.float(), dim=-1)
                idx = m["spoof_index"]
                per[m["name"]] = float(probs[0][idx].cpu().numpy()) \
                    if probs.shape[1] > idx else float(probs[0][-1])

            # Drop degenerate checkpoints: a model that saturates at ~1.0 on
            # EVERYTHING (bad fine-tune) is not a signal and would flood the
            # ensemble with false "spoof" verdicts on real voice.
            raw = {k: round(v, 3) for k, v in per.items()}
            per = {k: v for k, v in per.items() if v < 0.999}
            if not per:
                # every model saturated -> no confident signal; report a neutral
                # "uncertain" value rather than 0 (which reads as "real") or 1
                # (which reads as "fake")
                log.info("voice deepfake: all models saturated (raw=%s) -> uncertain", raw)
                return {"spoof_prob": 0.5, "label": "uncertain",
                        "per_model": raw, "ready": True, "backend": "local"}

            spoof = self._aggregate(per)
            if spoof is None:
                return {"spoof_prob": None, "label": None, "per_model": {},
                        "ready": True, "backend": "local"}
            if spoof > LABEL_HI:
                label = "spoof"
            elif spoof < LABEL_LO:
                label = "bonafide"
            else:
                label = "uncertain"
            return {"spoof_prob": spoof, "label": label,
                    "per_model": {k: round(v, 3) for k, v in per.items()},
                    "ready": True, "backend": "local"}
        except Exception as e:
            log.warning("voice deepfake analyze failed: %s", e)
            return {"spoof_prob": None, "label": None, "per_model": {},
                    "ready": False, "backend": "local"}

    @staticmethod
    def _aggregate(per: dict[str, float]) -> float | None:
        """Combine per-model spoof probs, guarding against degenerate models.

        A checkpoint that saturates at 1.0 on EVERYTHING (bad fine-tune) must
        not dominate the ensemble: if the top model is saturated but another
        model strongly disagrees, trust the disagreeing model instead.
        """
        if not per:
            return None
        vals = sorted(per.values())
        top = vals[-1]
        if len(vals) >= 2 and top > 0.999 and (top - vals[-2]) > 0.3:
            return vals[-2]
        return top