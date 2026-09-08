"""Logic mirror for the Phase 2.1 overlay GATE + inset math (not the rendering).

WHAT THIS IS (and is NOT). Phase 2.1 is a UI change to
app/.../shield/ShieldOverlay.kt (crimson/amber restyle, notch clearance) and the
popup gate in app/.../shield/ShieldService.kt::onResult. The VISUAL result and
real on-device notch/punch-hole clearance can only be confirmed on a device
(host/visual gate) — this mirror does NOT claim the card was rendered.

What it DOES verify, in the Claude sandbox, is the DECISION LOGIC that must not
regress, transcribed from the Kotlin:
  * should_pop(fg, band): the overlay pops ONLY for elevated bands and ONLY when
    the app is NOT foregrounded (foreground -> in-app card covers it).
  * band_accent(band): critical -> crimson, else -> amber.
  * top_inset(cutout, status, status_res, margin): max of the three insets plus
    the margin (so the pill clears the tallest of status bar / notch / cutout).

If you change those branches in Kotlin, change this mirror too.

Run:  python server/tests/test_overlay_logic.py
"""
from __future__ import annotations

import sys

# ---- mirrors of ShieldService.onResult / ShieldOverlay (Phase 2.1) ----
ELEVATED = {"verify", "critical"}
CRIMSON = 0xFFFF3B5C
AMBER = 0xFFFFB300


def should_pop(fg: bool, band: str) -> bool:
    """ShieldService.onResult: `!fg && (band==verify || band==critical)`."""
    return (not fg) and (band in ELEVATED)


def band_accent(band: str) -> int:
    """ShieldOverlay.show: `if (critical) CRIMSON else AMBER`."""
    return CRIMSON if band == "critical" else AMBER


def top_inset(cutout: int, status: int, status_res: int, margin: int) -> int:
    """ShieldOverlay.topInsetPx: `maxOf(cutoutTop, statusTop, statusBarHeightRes()) + margin`."""
    return max(cutout, status, status_res) + margin


# ------------------------------------------------------------------ gate tests
def test_passive_never_pops():
    assert should_pop(fg=False, band="passive") is False
    assert should_pop(fg=False, band="safe") is False


def test_elevated_pops_when_backgrounded():
    assert should_pop(fg=False, band="verify") is True
    assert should_pop(fg=False, band="critical") is True


def test_foreground_never_pops_overlay():
    # In-app card covers it; the floating overlay must not double up.
    assert should_pop(fg=True, band="critical") is False
    assert should_pop(fg=True, band="verify") is False


# ------------------------------------------------------------------ colour tests
def test_accent_band_coded():
    assert band_accent("critical") == CRIMSON
    assert band_accent("verify") == AMBER
    # any non-critical elevated band still uses amber
    assert band_accent("caution") == AMBER


# ------------------------------------------------------------------ inset tests
def test_inset_clears_tallest_obstacle():
    m = 36  # ~12dp @ 3x
    # punch-hole camera taller than the status bar -> cutout wins
    assert top_inset(cutout=130, status=72, status_res=72, margin=m) == 130 + m
    # tall status bar, no cutout -> status wins
    assert top_inset(cutout=0, status=96, status_res=72, margin=m) == 96 + m
    # nothing reported -> platform dimen fallback still applies
    assert top_inset(cutout=0, status=0, status_res=72, margin=m) == 72 + m


def test_inset_always_positive_margin():
    assert top_inset(0, 0, 0, 36) == 36  # never zero: margin guarantees clearance


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
