"""Configuration loader for the antAI server."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

import yaml

DEFAULT_CONFIG = Path(__file__).resolve().parents[2] / "config.yaml"  # server/config.yaml
SERVER_ROOT = Path(__file__).resolve().parents[2]  # server/


def _resolve_path(p: str | Path) -> Path:
    p = Path(p)
    if not p.is_absolute():
        p = SERVER_ROOT / p
    return p


def _load_dotenv() -> None:
    """Load ``server/.env`` (simple KEY=VALUE lines) into ``os.environ`` with
    no third-party dependency. Real environment variables always win — we only
    fill in keys that are not already set. This keeps hosted-API secrets
    (Deepgram/Velma/Groq) out of the committed config.yaml.
    """
    env_file = SERVER_ROOT / ".env"
    if not env_file.exists():
        return
    try:
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            key = key.strip()
            val = val.strip().strip('"').strip("'")
            if key:
                os.environ.setdefault(key, val)
    except Exception:
        pass


@dataclass
class ServerConfig:
    host: str = "0.0.0.0"
    port: int = 8765
    debug: bool = True

    @classmethod
    def from_dict(cls, d: dict) -> "ServerConfig":
        return cls(**d)


@dataclass
class AuthConfig:
    auto_verify_otp: bool = True
    token_ttl_hours: int = 720
    otp_len: int = 6
    # When true, /api/stream/ws rejects clients without a valid ?token=
    # (same token as the REST API). Default false keeps the LAN demo open.
    require_stream_token: bool = False

    @classmethod
    def from_dict(cls, d: dict) -> "AuthConfig":
        return cls(**d)


@dataclass
class StorageConfig:
    url: str = "sqlite:///antai.db"
    encryption_key: str = "change-me-please"
    db_path: Path = field(default_factory=lambda: SERVER_ROOT / "antai.db")

    @classmethod
    def from_dict(cls, d: dict) -> "StorageConfig":
        db_path = _resolve_path(d.get("url", "sqlite:///antai.db").replace("sqlite:///", ""))
        return cls(url=d.get("url", "sqlite:///antai.db"),
                   encryption_key=d.get("encryption_key", "change-me-please"),
                   db_path=db_path)


@dataclass
class ModelsConfig:
    root: Path = field(default_factory=lambda: SERVER_ROOT / "models")
    device: str = "auto"
    llm_device: str = "auto"
    llm_size: str = "3b"
    engines: dict = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict) -> "ModelsConfig":
        root = _resolve_path(d.get("root", "models"))
        return cls(
            root=root,
            device=d.get("device", "auto"),
            llm_device=d.get("llm_device", "auto"),
            llm_size=d.get("llm_size", "3b"),
            engines=d.get("engines", {}),
        )


@dataclass
class ProvidersConfig:
    """Hosted-API backends for the voice pipeline.

    Each voice-side engine can run its original LOCAL model or delegate to a
    hosted API. The engine NAMES and their call-site interfaces are unchanged,
    so the orchestrator/UI ("Models n/m loaded") behave exactly the same; only
    the backend that computes each number changes.

    API keys are read from the environment (``server/.env``, gitignored), never
    from this file, so secrets are never committed. Endpoints/models/backends
    are plain config so they can be changed without touching code.
    """
    # backend selection per engine
    asr: str = "faster_whisper"        # faster_whisper | deepgram
    # "both" runs the hosted Velma API AND the local SSL ensemble on every
    # segment and combines them (see VoiceDeepfakeEngine): either one can raise
    # the alarm, agreement escalates it, disagreement is held below the alert
    # line. "local" / "velma" pin a single backend.
    voice_deepfake: str = "local"      # both | local | velma
    llm: str = "local"                 # local | groq

    # Deepgram (speech-to-text)
    deepgram_api_key: str = ""
    deepgram_model: str = "nova-2"
    deepgram_endpoint: str = "https://api.deepgram.com/v1/listen"

    # Velma-2 (Modulate synthetic / AI-voice detection). This is a STREAMING
    # WebSocket API (wss://platform.modulate.ai/api/<model>-streaming), not
    # REST. ``config_json`` is the first frame sent on connect (selects the
    # behaviors/model); ``response_path`` is an optional dotted path override if
    # a deployment surfaces the synthetic-voice score at a fixed field.
    velma_api_key: str = ""
    velma_model_id: str = "velma-2-synthetic-voice-detection-streaming"
    velma_endpoint: str = ("wss://platform.modulate.ai/api/"
                           "velma-2-synthetic-voice-detection-streaming")
    velma_auth_style: str = "query"           # query (?api_key=) for Modulate
    velma_response_path: str = ""             # optional dotted JSON path override
    velma_config_json: str = ""              # first WS frame; "" -> built-in default
    # End-of-stream framing: "" auto-negotiates once per process (recommended),
    # or pin it to binary / text / json / none once you know which one the live
    # endpoint accepts. Sending the wrong one gets the stream closed with 1003.
    velma_eos: str = ""

    # Groq (LLM + LangGraph reasoning; OpenAI-compatible)
    groq_api_key: str = ""
    groq_model: str = "openai/gpt-oss-120b"
    groq_endpoint: str = "https://api.groq.com/openai/v1/chat/completions"

    # Gemini (Google) — LLM fallback #2. Different request/response shape
    # from the OpenAI-compatible providers, see gemini_chat() in providers.py.
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.0-flash"
    gemini_endpoint: str = "https://generativelanguage.googleapis.com/v1beta/models"

    # OpenRouter — LLM fallback #3 (OpenAI-compatible; routes to many providers)
    openrouter_api_key: str = ""
    openrouter_model: str = "openai/gpt-oss-120b"
    openrouter_endpoint: str = "https://openrouter.ai/api/v1/chat/completions"

    # Order in which LlmEngine tries backends on every complete() call: the
    # first entry returning non-empty text wins; entries without a key are
    # skipped. "local" needs no key so it works as the always-available final
    # entry. providers.llm is kept for backward compatibility and now only
    # decides whether the local GGUF is preloaded at startup.
    llm_fallback_chain: list[str] = field(
        default_factory=lambda: ["groq", "gemini", "openrouter", "local"])

    @classmethod
    def from_dict(cls, d: dict) -> "ProvidersConfig":
        dg = d.get("deepgram", {}) or {}
        ve = d.get("velma", {}) or {}
        gq = d.get("groq", {}) or {}
        ge = d.get("gemini", {}) or {}
        orr = d.get("openrouter", {}) or {}
        # env var wins for keys; config supplies models/endpoints (never keys)
        return cls(
            asr=d.get("asr", "faster_whisper"),
            voice_deepfake=d.get("voice_deepfake", "local"),
            llm=d.get("llm", "local"),
            deepgram_api_key=os.environ.get("DEEPGRAM_API_KEY", ""),
            deepgram_model=dg.get("model", "nova-2"),
            deepgram_endpoint=dg.get("endpoint", "https://api.deepgram.com/v1/listen"),
            velma_api_key=os.environ.get("VELMA_API_KEY", ""),
            velma_model_id=ve.get("model_id", "velma-2-synthetic-voice-detection-streaming"),
            velma_endpoint=(os.environ.get("VELMA_ENDPOINT") or ve.get("endpoint")
                            or "wss://platform.modulate.ai/api/"
                               "velma-2-synthetic-voice-detection-streaming"),
            velma_auth_style=ve.get("auth_style", "query"),
            velma_response_path=ve.get("response_path", ""),
            velma_config_json=ve.get("config_json", ""),
            velma_eos=ve.get("eos", ""),
            groq_api_key=os.environ.get("GROQ_API_KEY", ""),
            groq_model=gq.get("model", "openai/gpt-oss-120b"),
            groq_endpoint=gq.get("endpoint", "https://api.groq.com/openai/v1/chat/completions"),
            gemini_api_key=os.environ.get("GEMINI_API_KEY", ""),
            gemini_model=ge.get("model", "gemini-2.0-flash"),
            gemini_endpoint=ge.get("endpoint",
                                   "https://generativelanguage.googleapis.com/v1beta/models"),
            openrouter_api_key=os.environ.get("OPENROUTER_API_KEY", ""),
            openrouter_model=orr.get("model", "openai/gpt-oss-120b"),
            openrouter_endpoint=orr.get("endpoint",
                                        "https://openrouter.ai/api/v1/chat/completions"),
            llm_fallback_chain=d.get("llm_fallback_chain",
                                     ["groq", "gemini", "openrouter", "local"]),
        )


@dataclass
class ScenarioConfig:
    """Per-scenario risk threshold profile.

    Overrides the global ``pipeline.risk_bands`` for a single session.
    Lower thresholds mean earlier verification — appropriate for sessions
    where the consequence of a missed clone is higher.
    """
    label: str = "Routine Call"
    description: str = ""
    risk_verify_at: float = 75.0
    risk_critical_at: float = 90.0
    context_risk_boost: float = 0.0   # flat risk added at session start

    @classmethod
    def from_dict(cls, d: dict) -> "ScenarioConfig":
        return cls(
            label=d.get("label", cls.label),
            description=d.get("description", ""),
            risk_verify_at=float(d.get("risk_verify_at", 75)),
            risk_critical_at=float(d.get("risk_critical_at", 90)),
            context_risk_boost=float(d.get("context_risk_boost", 0)),
        )


@dataclass
class PipelineConfig:
    sample_rate: int = 16000
    vad_aggressiveness: int = 2
    video_fps: int = 6
    asr_beam_size: int = 1
    asr_language: str | None = None
    min_segment_s: float = 0.4
    hangover_s: float = 0.4
    max_segment_s: float = 2.0
    speaker_sim_threshold: float = 0.60
    video_vote_threshold: int = 2
    risk_bands: dict = field(default_factory=lambda: {"log": 40, "verify": 70})
    freeze_enabled: bool = True
    collective_check_enabled: bool = True
    live_guidance_interval_s: float = 3.0
    live_guidance_max_tokens: int = 180
    live_signals_interval_s: float = 0.7
    eval_interval_s: float = 2.0
    report_after: bool = True
    # deepfake pause-and-alert: freeze the call + pop a warning when the
    # synthetic-voice / deepfake-video detectors cross these margins.
    deepfake_alert_enabled: bool = True
    voice_alert_threshold: float = 0.7
    # Dual-backend synthetic-voice policy (providers.voice_deepfake = "both").
    # Agreement between the hosted API and the local SSL ensemble escalates;
    # disagreement is capped below the alert line so one model's guess cannot
    # interrupt a legitimate call.
    voice_agreement_bonus: bool = True
    voice_disagreement_cap: float = 0.84
    # ...with one exemption: a detector that is NEAR-CERTAIN is not capped. Without
    # this, a confident clone detection could never reach the alert line whenever
    # the other backend disagreed — the exact miss this feature exists to prevent.
    # Keep it high (0.95): the cap still suppresses every merely-confident flag.
    voice_solo_alert_threshold: float = 0.95
    # How much recent speech the synthetic-voice detectors get to look at.
    # VAD segments are 0.4-2.0s (tuned for responsive ASR), but the SSL models
    # behind AI-voice detection were trained on ASVspoof-style utterances of a
    # few seconds; on a single short segment their output collapses toward
    # chance, so a REAL cloned voice comes back "uncertain" (~0.5) and never
    # reaches the 0.7 alert line. The detectors therefore score a rolling window
    # of that one speaker's recent speech instead of one raw segment. Lip-sync
    # deliberately keeps using the raw segment: splicing across silences would
    # de-align the audio envelope from the video frames.
    voice_window_s: float = 4.0
    # Longer context for the AST spoof head ONLY. Measured 2026-09-06: the AST
    # checkpoint treats zero-PADDED input as spoof evidence (4s/8s padded
    # windows score 1.0 on silence, real voice AND clones alike), while a full
    # unpadded ~10s context scores sanely. So AST gets up to this many seconds
    # (processor truncates to its 1024-frame / ~10.24s native span); the
    # wav2vec2 cross-check keeps the short voice_window_s. Requires the rolling
    # voice buffer to hold at least this much (dispatcher._voice_buf_max_s).
    voice_long_window_s: float = 10.0
    video_alert_threshold: float = 0.85
    deepfake_alert_cooldown_s: float = 15.0

    @classmethod
    def from_dict(cls, d: dict) -> "PipelineConfig":
        return cls(**d)


@dataclass
class CollectiveConfig:
    min_flags_before_shortcircuit: int = 3
    max_flags_per_account_per_hour: int = 20
    reputation_min_age_days: int = 7

    @classmethod
    def from_dict(cls, d: dict) -> "CollectiveConfig":
        return cls(**d)


@dataclass
class PushConfig:
    provider: str = "ws"
    fcm_credentials: str | None = None

    @classmethod
    def from_dict(cls, d: dict) -> "PushConfig":
        return cls(**d)


@dataclass
class IceConfig:
    """ICE (STUN/TURN) servers for the WebRTC SFU peer connections.

    Purpose: let live call/video media flow over a USB-only link (no Wi-Fi).
    adb reverse only tunnels TCP, and WebRTC media is UDP, so over pure USB the
    media is relayed through a local TURN server reached over the USB tunnel
    (adb reverse tcp:3478 tcp:3478). Both the phone and this SFU connect out to
    turn:127.0.0.1:3478?transport=tcp and relay through coturn.

    Safe by default: if coturn is not running the TURN allocation just fails and
    aioice skips it, so same-Wi-Fi calls (host/STUN candidates) are unaffected.
    The server STUN url is empty on purpose — over USB there's no internet, and a
    reachable-but-slow STUN would stall ICE gathering; the phone keeps its own
    STUN for the Wi-Fi path.
    """
    enabled: bool = True
    stun_url: str = ""
    turn_url: str = "turn:127.0.0.1:3478?transport=tcp"
    turn_username: str = "antai"
    turn_password: str = "antaipass"

    @classmethod
    def from_dict(cls, d: dict) -> "IceConfig":
        return cls(**d)


@dataclass
class AppConfig:
    server: ServerConfig
    auth: AuthConfig
    storage: StorageConfig
    models: ModelsConfig
    pipeline: PipelineConfig
    collective: CollectiveConfig
    push: PushConfig
    ice: IceConfig = field(default_factory=IceConfig)
    providers: ProvidersConfig = field(default_factory=ProvidersConfig)
    log_level: str = "INFO"
    scenarios: dict = field(default_factory=dict)   # name -> ScenarioConfig

    @classmethod
    def load(cls, path: str | Path | None = None) -> "AppConfig":
        path = Path(path) if path else DEFAULT_CONFIG
        _load_dotenv()   # populate os.environ from server/.env before reading keys
        with open(path, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f) or {}
        return cls(
            server=ServerConfig.from_dict(raw.get("server", {})),
            auth=AuthConfig.from_dict(raw.get("auth", {})),
            storage=StorageConfig.from_dict(raw.get("storage", {})),
            models=ModelsConfig.from_dict(raw.get("models", {})),
            pipeline=PipelineConfig.from_dict(raw.get("pipeline", {})),
            collective=CollectiveConfig.from_dict(raw.get("collective", {})),
            push=PushConfig.from_dict(raw.get("push", {})),
            ice=IceConfig.from_dict(raw.get("ice", {})),
            providers=ProvidersConfig.from_dict(raw.get("providers", {})),
            log_level=raw.get("log_level", "INFO"),
            scenarios={k: ScenarioConfig.from_dict(v)
                       for k, v in (raw.get("scenarios") or {}).items()},
        )

    def get_scenario(self, name: str | None) -> ScenarioConfig:
        """Return the named scenario, or a default if unknown/None."""
        if name and name in self.scenarios:
            return self.scenarios[name]
        # default: use global risk_bands values
        bands = self.pipeline.risk_bands or {}
        return ScenarioConfig(
            label="Default",
            risk_verify_at=float(bands.get("log", 40)),
            risk_critical_at=float(bands.get("verify", 70)),
        )


_config: AppConfig | None = None


def get_config() -> AppConfig:
    global _config
    if _config is None:
        env_path = os.environ.get("ANTAI_CONFIG")
        _config = AppConfig.load(env_path or None)
    return _config


def set_config(cfg: AppConfig) -> None:
    global _config
    _config = cfg
