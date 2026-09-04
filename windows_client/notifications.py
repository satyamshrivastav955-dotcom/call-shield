"""
antAI Windows Client — Desktop Notifications

Posts Windows toast notifications when risk crosses thresholds.
Uses plyer for cross-platform desktop notifications with a fallback
to a simple print for environments where plyer is not installed.
"""
from __future__ import annotations

import logging
import time
from typing import Optional

log = logging.getLogger(__name__)

_plyer_ok = False
try:
    from plyer import notification as _plyer_notif  # type: ignore
    _plyer_ok = True
except ImportError:
    log.debug("plyer not available — desktop notifications will print to console")


class DesktopNotifier:
    """Post a desktop toast notification when risk crosses a threshold.

    Notifications are rate-limited: at most one per `cooldown_s` seconds to
    avoid flooding the user during a high-risk call.
    """

    def __init__(
        self,
        verify_at: float = 50.0,
        critical_at: float = 70.0,
        cooldown_s: float = 15.0,
    ):
        self.verify_at = verify_at
        self.critical_at = critical_at
        self.cooldown_s = cooldown_s
        self._last_posted: Optional[float] = None

    def update(self, risk: float, band: str, recommendation: str = "") -> None:
        """Call on every normalized_result update from the server.

        Posts a toast notification when risk >= verify_at, subject to cooldown.
        """
        if risk < self.verify_at and band == "passive":
            return

        now = time.monotonic()
        if self._last_posted is not None and (now - self._last_posted) < self.cooldown_s:
            return
        self._last_posted = now

        if band == "critical" or risk >= self.critical_at:
            title = "antAI — HIGH RISK DETECTED"
            message = recommendation or "Do not share credentials or authorize any request."
        else:
            title = "antAI — Risk Elevated"
            message = recommendation or "Verify the caller before proceeding."

        self._post(title, message)

    def _post(self, title: str, message: str) -> None:
        if _plyer_ok:
            try:
                _plyer_notif.notify(
                    title=title,
                    message=message[:256],
                    app_name="antAI",
                    timeout=8,
                )
                return
            except Exception as e:
                log.debug("plyer notify failed: %s", e)
        # Fallback: print to console
        print(f"\n{'=' * 60}")
        print(f"🔔  {title}")
        print(f"    {message}")
        print(f"{'=' * 60}\n")
