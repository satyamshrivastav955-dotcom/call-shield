"""Run the antAI server (dev): python run_dev.py"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))

import uvicorn  # noqa: E402

from antai.config import get_config  # noqa: E402

if __name__ == "__main__":
    cfg = get_config()
    print(f"antAI server -> http://{cfg.server.host}:{cfg.server.port}")
    print(f"  REST docs : http://{cfg.server.host}:{cfg.server.port}/docs")
    uvicorn.run("antai.gateway.main:app", host=cfg.server.host,
                port=cfg.server.port, reload=False,
                log_level="info", ws="auto")
