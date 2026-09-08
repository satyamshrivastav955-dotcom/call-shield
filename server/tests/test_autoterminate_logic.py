"""Logic mirror for the Phase 2.3 AUTO-TERMINATION decisions (not the UI/telecom).

WHAT THIS IS (and is NOT). Phase 2.3 adds:
  - an in-app 5s auto-hangup COUNTDOWN in DeepfakeAlertDialog.kt (Compose
    LaunchedEffect) that ends the WebRTC call we own, engaged only for a
    critical/high-risk verdict (MainViewModel.pauseForDeepfakeAlert), and
  - a one-tap CELLULAR hang-up via TelecomManager.endCall in
    CellularCallController.kt, capability-gated to the default-dialer / call-
    screening role, with an HONEST no-op (manual hint) otherwise — and NO way at
    all to end a third-party VoIP call (WhatsApp etc.).

The real countdown tick (Compose), the real TelecomManager.endCall on hardware,
and the real overlay button are DEVICE gates — this mirror does NOT claim any call
was ended. What it verifies in the sandbox is the pure DECISION logic, transcribed
from the Kotlin, so it can't silently regress:
  1. should_countdown(band, risk)        — when the auto-hangup engages at all
  2. CountdownMirror                      — fires onEndCall at 0, never if cancelled
  3. cellular_capability(...) / end_...   — the honest capability gate + no-fake rule

Run:  python server/tests/test_autoterminate_logic.py
"""
from __future__ import annotations

import sys

# ---- constants transcribed from the Kotlin -------------------------------
AUTO_HANGUP_RISK = 80.0     # MainViewModel.AUTO_HANGUP_RISK (risk is 0..100)
AUTO_HANGUP_SECONDS = 5     # MainViewModel.AUTO_HANGUP_SECONDS


# ==========================================================================
# 1. In-app auto-hangup ENGAGE gate (MainViewModel.pauseForDeepfakeAlert)
#    val critical = insight.band == "critical" || insight.riskScore >= AUTO_HANGUP_RISK
#    deepfakeAutoHangupState = if (critical) AUTO_HANGUP_SECONDS else null
# ==========================================================================
def should_countdown(band: str, risk: float) -> bool:
    return band == "critical" or risk >= AUTO_HANGUP_RISK


def auto_hangup_seconds(band: str, risk: float):
    """Mirror of the state the VM publishes: the seconds, or None (no countdown)."""
    return AUTO_HANGUP_SECONDS if should_countdown(band, risk) else None


# ==========================================================================
# 2. The countdown itself (DeepfakeAlertDialog LaunchedEffect):
#    remaining = seconds; while (remaining > 0) { delay(1s); remaining-- }; onEndCall()
#    A deliberate engagement (cross-check) sets cancelled=true -> effect returns
#    early and never calls onEndCall. "resume anyway" dismisses the dialog (also
#    tears down the effect) -> modeled here as cancel().
# ==========================================================================
class CountdownMirror:
    def __init__(self, seconds):
        self.active = seconds is not None and seconds > 0
        self.remaining = seconds if self.active else 0
        self.cancelled = False
        self.fired = False              # True once onEndCall() would be invoked

    def tick(self):
        """Advance one real second."""
        if not self.active or self.cancelled or self.fired:
            return
        if self.remaining > 0:
            self.remaining -= 1
            if self.remaining == 0:
                self.fired = True       # loop exits -> onEndCall()

    def cancel(self):
        """Cross-check tapped, or dialog dismissed: stop the timer, never fire."""
        self.cancelled = True


# ==========================================================================
# 3. Cellular hang-up capability gate (CellularCallController):
#    < API 28            -> UNSUPPORTED_OS
#    no ANSWER_PHONE_CALLS-> NO_PERMISSION
#    not default dialer / not call-screening role -> NOT_DEFAULT_DIALER
#    else                -> SUPPORTED
# ==========================================================================
def cellular_capability(sdk: int, has_permission: bool,
                        is_default_dialer: bool, is_screener: bool) -> str:
    if sdk < 28:
        return "UNSUPPORTED_OS"
    if not has_permission:
        return "NO_PERMISSION"
    if not (is_default_dialer or is_screener):
        return "NOT_DEFAULT_DIALER"
    return "SUPPORTED"


def end_cellular_call(sdk: int, has_permission: bool, is_default_dialer: bool,
                      is_screener: bool, platform_reports_ended: bool) -> bool:
    """HONEST contract: only attempt when SUPPORTED, and return the platform's OWN
    result. Never fake success; when not SUPPORTED, return False WITHOUT regard to
    what the platform would say (we don't even call it)."""
    if sdk < 28:
        return False
    if cellular_capability(sdk, has_permission, is_default_dialer, is_screener) != "SUPPORTED":
        return False
    return bool(platform_reports_ended)


# ------------------------------------ tests: engage gate
def test_countdown_engages_for_critical():
    assert should_countdown("critical", 0.0) is True
    assert auto_hangup_seconds("critical", 0.0) == 5


def test_countdown_engages_at_high_risk_regardless_of_band():
    assert should_countdown("verify", 80.0) is True     # risk >= 80 alone engages
    assert should_countdown("verify", 92.5) is True


def test_countdown_does_not_engage_for_suspicious_or_calm():
    assert should_countdown("verify", 79.9) is False
    assert should_countdown("passive", 10.0) is False
    assert auto_hangup_seconds("verify", 50.0) is None  # -> alert WITHOUT countdown


# ------------------------------------ tests: the countdown
def test_countdown_fires_after_exactly_five_ticks():
    c = CountdownMirror(AUTO_HANGUP_SECONDS)
    for _ in range(4):
        c.tick()
        assert c.fired is False
    c.tick()                    # 5th second
    assert c.fired is True
    assert c.remaining == 0


def test_countdown_cancel_before_zero_never_fires():
    c = CountdownMirror(AUTO_HANGUP_SECONDS)
    c.tick(); c.tick()          # 2 seconds elapsed
    c.cancel()                  # user taps cross-check / resume
    for _ in range(10):
        c.tick()
    assert c.fired is False     # auto-hangup was cancelled -> call stays up


def test_no_countdown_when_seconds_none():
    c = CountdownMirror(None)   # verify-band alert: no countdown
    for _ in range(10):
        c.tick()
    assert c.fired is False


# ------------------------------------ tests: cellular capability (honest gate)
def test_capability_unsupported_below_api_28():
    assert cellular_capability(24, True, True, True) == "UNSUPPORTED_OS"


def test_capability_needs_permission():
    assert cellular_capability(30, False, True, False) == "NO_PERMISSION"


def test_capability_needs_default_dialer_or_screener():
    assert cellular_capability(30, True, False, False) == "NOT_DEFAULT_DIALER"
    assert cellular_capability(30, True, True, False) == "SUPPORTED"   # default dialer
    assert cellular_capability(30, True, False, True) == "SUPPORTED"   # call-screening role


def test_end_call_returns_platform_result_only_when_supported():
    # SUPPORTED + platform ended a call -> True
    assert end_cellular_call(28, True, True, False, platform_reports_ended=True) is True
    # SUPPORTED but no active call (platform said false) -> False, not faked
    assert end_cellular_call(28, True, True, False, platform_reports_ended=False) is False


def test_end_call_never_fakes_success_when_not_supported():
    # The honesty invariant: even if the platform WOULD report ended, a non-SUPPORTED
    # capability must return False (we never call endCall()).
    assert end_cellular_call(24, True, True, True, platform_reports_ended=True) is False   # old OS
    assert end_cellular_call(30, False, True, True, platform_reports_ended=True) is False  # no perm
    assert end_cellular_call(30, True, False, False, platform_reports_ended=True) is False # not dialer


def test_whatsapp_style_voip_is_never_auto_ended():
    # A third-party VoIP call: antAI is not the phone app, so there is no path.
    # (Even holding the role wouldn't end WhatsApp — TelecomManager only governs the
    #  cellular call — but the gate already returns False here, forcing the manual hint.)
    cap = cellular_capability(33, has_permission=True, is_default_dialer=False, is_screener=False)
    assert cap == "NOT_DEFAULT_DIALER"
    assert end_cellular_call(33, True, False, False, platform_reports_ended=True) is False


def _run():
    tests = [v for k, v in sorted(globals().items())
             if k.startswith("test_") and callable(v)]
    failed = 0
    for t in tests:
        try:
            t()
            print(f"PASS {t.__name__}")
        except AssertionError as e:
            failed += 1
            print(f"FAIL {t.__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return failed


if __name__ == "__main__":
    sys.exit(1 if _run() else 0)
