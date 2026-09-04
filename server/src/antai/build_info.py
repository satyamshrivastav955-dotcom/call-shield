"""Which code is ACTUALLY running, and whether it is still current.

Python caches imports at process start, so editing a source file changes nothing
until the server is restarted. That single fact has masked three separate rounds
of server-side fixes on this project: the file on disk was correct, the running
process was not, and every symptom looked like the fix had simply failed.

This module makes that difference observable. At import time (i.e. process
start) it records the modification time of every tracked source file. Anything
edited afterwards shows up in ``changed_since_start()``, and
``GET /api/debug/models`` reports it as ``restart_required``. So the next time a
detector "isn't working", the health endpoint answers "you are running code from
02:31; 6 files have changed since" instead of leaving it to guesswork.

Deliberately dependency-free and fail-soft: a stat that cannot be read is
skipped rather than raised, because a diagnostic aid must never be the thing
that takes the server down.
"""
from __future__ import annotations

import hashlib
import logging
import time
from pathlib import Path

log = logging.getLogger(__name__)

_PKG_ROOT = Path(__file__).resolve().parent          # src/antai
_SERVER_ROOT = _PKG_ROOT.parents[1]                  # server/

# config.yaml matters as much as the code: thresholds, provider choice and the
# engine list all live there, and a stale process is holding the OLD values.
_EXTRA_FILES = (_SERVER_ROOT / "config.yaml",)


def _tracked_files() -> list[Path]:
    files = sorted(p for p in _PKG_ROOT.rglob("*.py") if "__pycache__" not in p.parts)
    return files + [p for p in _EXTRA_FILES if p.exists()]


def _snapshot() -> dict[str, int]:
    """relative path -> mtime_ns, skipping anything unreadable."""
    out: dict[str, int] = {}
    for p in _tracked_files():
        try:
            out[str(p.relative_to(_SERVER_ROOT)).replace("\\", "/")] = p.stat().st_mtime_ns
        except OSError:
            continue
    return out


def _fingerprint(snap: dict[str, int]) -> str:
    h = hashlib.sha1()
    for path in sorted(snap):
        h.update(f"{path}:{snap[path]}\n".encode())
    return h.hexdigest()[:12]


# captured once, at process start
STARTED_AT: float = time.time()
_AT_START: dict[str, int] = _snapshot()
FINGERPRINT: str = _fingerprint(_AT_START)


def changed_since_start() -> list[str]:
    """Tracked files whose mtime differs from the value recorded at startup.

    A non-empty list means the process is running code that no longer matches the
    files on disk — restart before drawing any conclusion about behaviour.
    """
    now = _snapshot()
    changed = [p for p, m in now.items() if _AT_START.get(p) != m]
    changed += [p for p in _AT_START if p not in now]      # deleted/renamed
    return sorted(set(changed))


def runtime_info() -> dict:
    stale = changed_since_start()
    return {
        "started_at": time.strftime("%Y-%m-%d %H:%M:%S",
                                    time.localtime(STARTED_AT)),
        "uptime_s": round(time.time() - STARTED_AT, 1),
        "code_fingerprint": FINGERPRINT,
        "tracked_files": len(_AT_START),
        "restart_required": bool(stale),
        "changed_since_start": stale[:40],
        "note": ("This process is running code older than the files on disk — "
                 "restart the server before trusting any behaviour below."
                 if stale else "Running code matches the files on disk."),
    }


def log_banner(logger: logging.Logger | None = None) -> None:
    lg = logger or log
    lg.info("code fingerprint=%s (%d tracked files) started=%s",
            FINGERPRINT, len(_AT_START),
            time.strftime("%H:%M:%S", time.localtime(STARTED_AT)))
