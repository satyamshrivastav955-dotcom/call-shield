"""FastAPI application entrypoint: mounts REST + WS routes."""
from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..config import get_config
from ..gateway.rest_api import router as rest_router
from ..gateway.signaling import router as ws_router
from ..gateway.stream_api import router as stream_router
from ..gateway.tap import router as tap_router
from ..storage import get_db


def create_app() -> FastAPI:
    cfg = get_config()
    logging.basicConfig(level=getattr(logging, cfg.log_level.upper(), logging.INFO))

    # Print which code this process is actually running. Editing a file changes
    # nothing until restart, and that has repeatedly made a working fix look
    # broken — the fingerprint in the log is what lets you tell the two apart.
    from ..build_info import log_banner
    log_banner(logging.getLogger("antai.build"))

    # initialize storage (creates tables)
    get_db()

    # warm model engines in the background so the first request doesn't pay
    # the load cost (LLM GGUF load can take tens of seconds)
    _warm_engines()

    app = FastAPI(title="antAI server", version="0.1.0",
                  description="Real-time scam & deepfake defense backend. "
                              "Client contract: REST for auth/data, WebSocket for "
                              "call signaling + realtime verdict/guidance/freeze.")
    app.add_middleware(CORSMiddleware, allow_origins=["*"],
                       allow_methods=["*"], allow_headers=["*"])

    app.include_router(rest_router, prefix="/api")
    app.include_router(stream_router, prefix="/api")
    app.include_router(ws_router)
    app.include_router(tap_router)

    # Mock bank/call-center dashboard + demo assets (served from gateway/static).
    # It subscribes to /api/stream/ws and renders the live risk + signal families;
    # all numbers come from the real API, nothing is hardcoded in the page.
    from pathlib import Path
    from fastapi.staticfiles import StaticFiles
    static_dir = Path(__file__).resolve().parent / "static"
    if static_dir.is_dir():
        app.mount("/dashboard", StaticFiles(directory=str(static_dir), html=True),
                  name="dashboard")

    @app.get("/")
    def root():
        return {"service": "antAI", "status": "ok",
                "docs": "/docs", "ws": ["/ws/call", "/ws/chat", "/ws/tap"],
                "stream": {"ws": "/api/stream/ws", "rest": "/api/stream/analyze"},
                "dashboard": "/dashboard/"}

    return app


def _warm_engines():
    import threading
    import logging
    from ..inference.hub import get_hub

    log = logging.getLogger("antai.warmup")

    def warm():
        try:
            hub = get_hub()
            names = list(hub.summary().keys())
            log.info("warming %d inference engines: %s", len(names), ", ".join(names))
            ready, failed = [], []
            for name in names:
                eng = hub.get(name)
                try:
                    ok = bool(eng and eng.ready())
                except Exception as e:  # pragma: no cover
                    ok = False
                    eng and setattr(eng, "_unavailable_reason", f"{name}: {e}")
                if ok:
                    ready.append(name)
                    log.info("  [ready] %-16s device=%s", name,
                             getattr(eng, "device", "?"))
                else:
                    reason = (eng.unavailable_reason() if eng else "not registered") \
                        or "weights missing / load failed"
                    failed.append(name)
                    log.warning("  [FAILED] %-16s -> %s", name, reason)
            log.info("engine warmup complete: %d ready, %d unavailable (%s)",
                     len(ready), len(failed),
                     ("all ready" if not failed else "unavailable: " + ", ".join(failed)))
            if failed:
                log.warning("Unavailable engines report 0/None signals -> the in-call "
                            "window will show 0%% for them. Inspect the [FAILED] reasons "
                            "above or GET /api/debug/models. Usually a missing python dep "
                            "(torch / transformers / llama-cpp-python) or absent weights.")
            _log_voice_status(log, hub)
        except Exception:
            log.exception("engine warmup crashed")

    threading.Thread(target=warm, daemon=True, name="engine-warmup").start()


def _log_voice_status(log, hub) -> None:
    """One unmissable line about the flagship detector.

    AI-voice-clone detection is the headline capability, and its two backends can
    each fail independently and silently — a rejected hosted key, or absent local
    weights. Whether a cloned voice could be caught *right now* should never
    require reading forty lines of warmup output to work out.
    """
    from ..config import get_config

    eng = hub.get("voice_deepfake")
    if eng is None or not getattr(eng, "ready", lambda: False)():
        log.error("AI-VOICE DETECTION IS OFF — no synthetic-voice backend loaded. "
                  "A cloned voice will NOT be flagged. See the [FAILED] reason above.")
        return
    velma = bool(getattr(eng, "_use_velma", False))
    local = bool(getattr(eng, "_local_ready", False))
    thr = get_config().pipeline.voice_alert_threshold
    log.info("AI-VOICE DETECTION: backend=%s  hosted(Velma)=%s  local SSL ensemble=%s"
             "  alert above %.2f", getattr(eng, "backend", "?"),
             "live" if velma else "NOT in use", "loaded" if local else "NOT loaded", thr)
    if not velma and local:
        log.warning("AI-VOICE: running on the local SSL ensemble alone — detection IS "
                    "active, just single-source. Check velma_* fields in "
                    "GET /api/debug/models for why the hosted API is not in use.")
    if velma and not local:
        log.warning("AI-VOICE: running on the hosted API alone — if it rejects the "
                    "audio there is no second opinion. Put the SSL weights in "
                    "models/voice_deepfake to get a local fallback.")


app = create_app()
