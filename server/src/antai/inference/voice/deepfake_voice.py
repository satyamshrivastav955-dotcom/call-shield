"""Synthetic/AI-voice (deepfake) detection - multi-model SSL ensemble.

Two independent SSL models run per clip; no single model decides:
  A. AST-ASVspoof5 (Audio Spectrogram Transformer, ASVspoof5)
       models/voice_deepfake          (primary, strong on EN TTS)
  B. Wav2Vec2 deepfake-voice detector (SSL wav2vec2)
       models/voice_deepfake_cross2   (cross-check, catches non-EN TTS)

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
            # primary (AST ASVspoof5) — the head that saturates, so it takes the
            # configured de-saturation temperature (calibration.json still wins).
            ast_default_t = float(getattr(cfg.pipeline, "voice_ast_temperature", 1.0))
            p = self._load_one(primary_dir, device, default_temperature=ast_default_t)
            if p is None:
                return False
            self._models.append({"name": "asv5", **p})
            # cross-check SSL wav2vec2 (optional; fail-soft). No saturation
            # pathology observed on it, so it defaults to T=1.0 (its own
            # calibration.json still overrides if a fit was run).
            cross_dir = Path(cfg.models.root) / "voice_deepfake_cross2"
            if cross_dir.exists():
                c = self._load_one(cross_dir, device, default_temperature=1.0)
                if c is not None:
                    self._models.append({"name": "w2v", **c})
            self.device = device
            for m in self._models:
                t = m.get("temperature", 1.0)
                if m["name"] == "asv5" and abs(t - 1.0) < 1e-9:
                    log.warning("voice deepfake: AST head T=1.0 — SOFTMAX IS "
                                "UNCALIBRATED (still overconfident on bonafide). "
                                "Run scripts/fit_temperature.py to de-saturate; "
                                "the v>=0.999 saturation guard is the only net "
                                "until then. Source: %s", m.get("temp_source"))
                else:
                    log.info("voice deepfake: %s softmax temperature T=%.3f (%s)",
                             m["name"], t, m.get("temp_source"))
            return True
        except Exception as e:
            log.warning("voice deepfake load failed: %s", e)
            return False

    def _load_one(self, model_dir: Path, device: str,
                  default_temperature: float = 1.0) -> dict | None:
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
        temperature, temp_source = self._resolve_temperature(model_dir, default_temperature)
        return {"processor": processor, "model": model,
                "spoof_index": self._find_spoof_index(model),
                "temperature": temperature, "temp_source": temp_source}

    @staticmethod
    def _resolve_temperature(model_dir: Path,
                             default_temperature: float) -> tuple[float, str]:
        """Softmax temperature for this checkpoint and WHERE it came from.

        Precedence: a `calibration.json` fit on the host (the real, measured value)
        wins over the config default. A fitted file is the only trustworthy source;
        the config default exists so the mechanism is wired even before a fit is
        run, and defaults to 1.0 (identity — no de-saturation, changes nothing). We
        return the source string too so `_load` can log LOUDLY when the head is
        running uncalibrated, rather than letting T=1.0 masquerade as "calibrated".
        """
        import json
        cal = model_dir / "calibration.json"
        if cal.exists():
            try:
                data = json.loads(cal.read_text())
                t = float(data.get("temperature"))
                if t > 0:
                    return t, f"calibration.json (fit: {cal})"
                log.warning("voice deepfake: %s has non-positive temperature %r — "
                            "ignoring, using default", cal, t)
            except Exception as e:
                log.warning("voice deepfake: could not read %s (%s) — using default",
                            cal, e)
        src = ("config default (UNCALIBRATED — run scripts/fit_temperature.py)"
               if abs(default_temperature - 1.0) < 1e-9
               else "config default")
        return float(default_temperature), src

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
        """Returns {spoof_prob, label, is_ai_voice, per_model, ready, backend, sources}.

        `is_ai_voice` is the explicit synthetic-voice verdict (True/False), or None
        when no backend could score the segment (unknown — never a fabricated
        False). `spoof_prob` is the accompanying 0..1 confidence.

        In "both" mode the hosted API and the local ensemble are both scored and
        combined by `_combine`. In "velma" mode the local ensemble is only run
        when Velma produced nothing, so a working key costs no extra compute.

        ``context_audio`` is the longer unpadded context for the AST head (see
        ``voice_long_window_s``): padded short windows read as spoof to AST, so
        it must see seconds of real speech. Falls back to ``audio`` when absent.
        """
        if not self.ready():
            return {"spoof_prob": None, "label": None, "is_ai_voice": None,
                    "per_model": {}, "ready": False, "backend": "none"}

        if sample_rate != 16000:
            from .audio_io import to_sample_rate
            audio, sample_rate = to_sample_rate(audio, sample_rate, 16000)
            if context_audio is not None:
                context_audio, _ = to_sample_rate(context_audio, sample_rate, 16000)

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
            return {"spoof_prob": None, "label": None, "is_ai_voice": None,
                    "per_model": per, "ready": True, "backend": self.backend,
                    "sources": 0, "agreement": None}

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
        # Explicit, distinct AI-voice verdict for the client/debug layer: a bool
        # that ONLY asserts synthetic when the merged verdict is actually "spoof".
        # "uncertain"/"bonafide" -> False (not asserting a clone); a None score
        # already returned above as is_ai_voice=None (unknown, never a fake False).
        is_ai_voice = (label == "spoof")
        return {"spoof_prob": prob, "label": label, "is_ai_voice": is_ai_voice,
                "per_model": per, "ready": True, "backend": self.backend,
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
                inputs = m["processor"](clip, sampling_rate=sample_rate,
                                        return_tensors="pt")
                if self._local_device == "cuda":
                    inputs = {k: (v.half() if v.dtype == torch.float32 else v)
                              .to("cuda") for k, v in inputs.items()}
                with torch.no_grad():
                    logits = m["model"](**inputs).logits
                # Temperature scaling (de-saturation): T>1 spreads an overconfident
                # head's mass back toward the middle so real speech stops reading as
                # a near-certain clone. T=1.0 is the identity (no change). Fit on the
                # host; see `voice_ast_temperature` / calibration.json.
                t = float(m.get("temperature", 1.0))
                if not t > 0:
                    t = 1.0
                probs = torch.softmax(logits.float() / t, dim=-1)
                idx = m["spoof_index"]
                per[m["name"]] = float(probs[0][idx].cpu().numpy()) \
                    if probs.shape[1] > idx else float(probs[0][-1])

            spoof = max(per.values()) if per else None
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