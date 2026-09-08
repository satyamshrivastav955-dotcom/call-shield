"""Logic mirror for the Phase 2.2 alert DEBOUNCE (not the beep/vibration itself).

WHAT THIS IS (and is NOT). Phase 2.2 adds a beep + triple-vibration waveform
[0,200,100,200,100,300] in app/.../notifications/AlertFeedback.kt, fired from
ShieldService.onResult. The actual haptic/tone output is a DEVICE gate (real
Vibrator/ToneGenerator on hardware) — this mirror does NOT claim anything buzzed.

What it DOES verify in the sandbox is the fire/skip DECISION in
AlertFeedback.onVerdict, which must not regress: buzz on an ESCALATION to a more
severe band, or on a SUSTAINED elevated band at most once per REPEAT_MS; never on
passive; never on de-escalation. Transcribed from the Kotlin.

Run:  python server/tests/test_alert_debounce_logic.py
"""
from __future__ import annotations

import sys

REPEAT_MS = 20_000


def _sev(band: str) -> int:
    return {"critical": 2, "verify": 1}.get(band, 0)


class AlertFeedbackMirror:
    """Mirror of AlertFeedback.onVerdict with an injectable clock."""

    def __init__(self):
        self.last_band = "passive"
        self.last_at = 0

    def on_verdict(self, band: str, now: int) -> bool:
        """Returns True iff this verdict WOULD fire the beep+vibration."""
        if _sev(band) == 0:
            self.last_band = band
            return False
        escalated = _sev(band) > _sev(self.last_band)
        sustained_repeat = (now - self.last_at) > REPEAT_MS
        if not escalated and not sustained_repeat:
            self.last_band = band
            return False
        self.last_band = band
        self.last_at = now
        return True


def test_passive_never_fires():
    a = AlertFeedbackMirror()
    assert a.on_verdict("passive", now=0) is False
    assert a.on_verdict("safe", now=100) is False


def test_first_escalation_fires():
    a = AlertFeedbackMirror()
    assert a.on_verdict("verify", now=1000) is True


def test_sustained_within_cooldown_is_silent():
    a = AlertFeedbackMirror()
    assert a.on_verdict("verify", now=1000) is True
    # same band, 4s later (a mic window) -> no re-buzz
    assert a.on_verdict("verify", now=5000) is False
    assert a.on_verdict("verify", now=9000) is False


def test_escalation_fires_even_inside_cooldown():
    a = AlertFeedbackMirror()
    assert a.on_verdict("verify", now=1000) is True
    # verify -> critical only 4s later: escalation beats the cooldown
    assert a.on_verdict("critical", now=5000) is True


def test_deescalation_never_refires():
    a = AlertFeedbackMirror()
    assert a.on_verdict("critical", now=1000) is True
    # critical -> verify shortly after: less severe, still in cooldown -> silent
    assert a.on_verdict("verify", now=4000) is False
    # ...but a later re-escalation to critical DOES fire
    assert a.on_verdict("critical", now=7000) is True


def test_sustained_refires_after_repeat_window():
    a = AlertFeedbackMirror()
    assert a.on_verdict("critical", now=1000) is True
    assert a.on_verdict("critical", now=10000) is False       # within 20s
    assert a.on_verdict("critical", now=1000 + REPEAT_MS + 1) is True  # past 20s


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
