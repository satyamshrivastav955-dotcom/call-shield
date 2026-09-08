"""Logic mirror for Phase 4.1 — debug-only offline OTP fallback (login never dead-ends).

WHAT THIS IS (and is NOT). Phase 4.1 stops the messaging login from dead-ending
when the antAI server is unreachable: in a DEBUG build only, requestOtp's failure
path advances to OTP entry with a mock code (123456) and verifyOtp accepts that
mock locally, minting a clearly-labeled LOCAL debug identity. This mirror
transcribes the pure DECISION logic from MessagesViewModel so the one invariant
that actually matters can't silently regress:

  ** A RELEASE build (BuildConfig.DEBUG == false) NEVER accepts the mock code and
     NEVER advances offline — it always requires a real server-issued OTP. **

The real BuildConfig.DEBUG constant, the real AntaiSession SharedPreferences write,
the real chat-socket connect, and the Compose screen are DEVICE/BUILD gates — this
mirror asserts none of them ran. It verifies the branch logic transcribed from the
Kotlin only. In particular it does NOT prove BuildConfig is generated (that needs
`buildConfig = true` in build.gradle.kts, enabled this phase and checked by a real
compile).

Run:  python server/tests/test_login_offline_fallback_logic.py
"""
from __future__ import annotations

import sys

DEBUG_MOCK_OTP = "123456"  # mirror of MessagesViewModel.DEBUG_MOCK_OTP

# The exact user-facing strings, mirrored so their wording is regression-locked.
ERR_ENTER_PHONE = "Enter your phone number"
ERR_ENTER_CODE = "Enter the code"
ERR_SERVER_NEEDED = ("Phone verification needs the antAI server — check the IP on the Calls tab. "
                     "SMS scam scanning below still works on-device without sign-in.")
ERR_DEBUG_OFFLINE = ("Server unreachable — DEBUG build only: enter "
                     f"{DEBUG_MOCK_OTP} to continue offline (no real sign-in).")
ERR_DEBUG_WRONG = f"DEBUG offline mode: enter {DEBUG_MOCK_OTP} to continue."
ERR_VERIFY_FAILED = "Verification failed. Try again."


# ==========================================================================
# requestOtp(phone) — mirror of MessagesViewModel.requestOtp.
#   server_reachable models rest.requestOtp() success/failure.
# ==========================================================================
def request_otp(phone: str, debug: bool, server_reachable: bool, server_dev_otp=None) -> dict:
    clean = phone.strip()
    if clean == "":
        return {"phase": "ENTER_PHONE", "phone": "", "devOtp": None,
                "debugOffline": False, "error": ERR_ENTER_PHONE}
    if server_reachable:
        # onSuccess: normal (possibly server "auto_verify dev mode") path
        return {"phase": "ENTER_OTP", "phone": clean, "devOtp": server_dev_otp,
                "debugOffline": False, "error": None}
    # onFailure:
    if debug:
        return {"phase": "ENTER_OTP", "phone": clean, "devOtp": DEBUG_MOCK_OTP,
                "debugOffline": True, "error": ERR_DEBUG_OFFLINE}
    return {"phase": "ENTER_PHONE", "phone": clean, "devOtp": None,
            "debugOffline": False, "error": ERR_SERVER_NEEDED}


# ==========================================================================
# verifyOtp(code, displayName) — mirror of MessagesViewModel.verifyOtp.
#   server_accepts models rest.verifyOtp() success/failure (only consulted
#   when the debug-offline branch is NOT taken).
# ==========================================================================
def verify_otp(state: dict, code_in: str, display_name: str,
               debug: bool, server_accepts: bool) -> dict:
    phone = state["phone"]
    code = code_in.strip()
    if code == "":
        return {"loggedIn": False, "phase": state["phase"], "token": None, "error": ERR_ENTER_CODE}
    # DEBUG-only offline branch — requires BuildConfig.DEBUG AND debugOffline.
    if debug and state.get("debugOffline", False):
        if code == DEBUG_MOCK_OTP:
            return {"loggedIn": True, "phase": "DONE",
                    "token": "debug-offline-1700000000000",
                    "displayName": (display_name.strip() or "Debug user"), "error": None}
        return {"loggedIn": False, "phase": state["phase"], "token": None, "error": ERR_DEBUG_WRONG}
    # Real server path (the ONLY path in release builds).
    if server_accepts:
        return {"loggedIn": True, "phase": "DONE", "token": "server-token", "error": None}
    return {"loggedIn": False, "phase": state["phase"], "token": None, "error": ERR_VERIFY_FAILED}


# --------------------------------- requestOtp
def test_blank_phone_stays_and_errors():
    s = request_otp("   ", debug=True, server_reachable=False)
    assert s["phase"] == "ENTER_PHONE" and s["error"] == ERR_ENTER_PHONE
    assert s["debugOffline"] is False


def test_server_up_advances_normally_no_debug_flag():
    s = request_otp("+15550100", debug=True, server_reachable=True, server_dev_otp="000111")
    assert s["phase"] == "ENTER_OTP"
    assert s["devOtp"] == "000111"        # server's own dev otp, not the mock
    assert s["debugOffline"] is False     # NOT the offline fallback


def test_server_down_debug_offers_mock_and_does_not_dead_end():
    s = request_otp("+15550100", debug=True, server_reachable=False)
    assert s["phase"] == "ENTER_OTP"      # advanced, not stuck on phone entry
    assert s["devOtp"] == DEBUG_MOCK_OTP
    assert s["debugOffline"] is True
    assert "DEBUG" in s["error"] and DEBUG_MOCK_OTP in s["error"]


def test_server_down_release_stays_with_honest_message():
    s = request_otp("+15550100", debug=False, server_reachable=False)
    assert s["phase"] == "ENTER_PHONE"    # release: no offline advance
    assert s["debugOffline"] is False
    assert s["devOtp"] is None            # no mock code ever surfaced in release
    # honest, non-conflated: SMS scanning still works on-device
    assert "still works on-device" in s["error"]


# --------------------------------- verifyOtp (debug offline)
def test_blank_code_errors():
    s = {"phase": "ENTER_OTP", "phone": "+1", "debugOffline": True}
    r = verify_otp(s, "  ", "", debug=True, server_accepts=False)
    assert r["loggedIn"] is False and r["error"] == ERR_ENTER_CODE


def test_debug_offline_correct_mock_logs_in_locally():
    s = request_otp("+15550100", debug=True, server_reachable=False)
    r = verify_otp(s, DEBUG_MOCK_OTP, "", debug=True, server_accepts=False)
    assert r["loggedIn"] is True and r["phase"] == "DONE"
    assert r["token"].startswith("debug-offline-")   # local debug identity, labeled
    assert r["displayName"] == "Debug user"           # blank-name fallback


def test_debug_offline_wrong_code_rejected():
    s = request_otp("+15550100", debug=True, server_reachable=False)
    r = verify_otp(s, "000000", "", debug=True, server_accepts=False)
    assert r["loggedIn"] is False
    assert DEBUG_MOCK_OTP in r["error"]


def test_debug_offline_keeps_provided_name():
    s = request_otp("+15550100", debug=True, server_reachable=False)
    r = verify_otp(s, DEBUG_MOCK_OTP, "  Satya ", debug=True, server_accepts=False)
    assert r["displayName"] == "Satya"


# --------------------------------- THE SECURITY INVARIANT (release never bypasses)
def test_release_never_accepts_mock_even_if_flag_forced():
    # Even if a (hypothetical) debugOffline state existed in release, the mock
    # branch is gated on BuildConfig.DEBUG, so a release build falls through to
    # the server. Server unreachable -> NOT logged in (never bypassed).
    forged = {"phase": "ENTER_OTP", "phone": "+1", "debugOffline": True}
    r = verify_otp(forged, DEBUG_MOCK_OTP, "", debug=False, server_accepts=False)
    assert r["loggedIn"] is False
    assert r["error"] == ERR_VERIFY_FAILED   # went to server path, not the mock


def test_release_full_flow_server_down_cannot_log_in_with_mock():
    # End-to-end release: server down -> stuck on ENTER_PHONE, and even forcing a
    # verify with 123456 does not authenticate.
    s = request_otp("+15550100", debug=False, server_reachable=False)
    assert s["phase"] == "ENTER_PHONE"
    r = verify_otp(s, DEBUG_MOCK_OTP, "", debug=False, server_accepts=False)
    assert r["loggedIn"] is False


def test_mock_does_not_shortcut_a_reachable_server_in_debug():
    # Debug build, server UP: debugOffline is False, so entering 123456 goes to the
    # REAL server. It authenticates only if the server accepts, with a server token.
    s = request_otp("+15550100", debug=True, server_reachable=True, server_dev_otp=None)
    assert s["debugOffline"] is False
    r = verify_otp(s, DEBUG_MOCK_OTP, "", debug=True, server_accepts=True)
    assert r["loggedIn"] is True and r["token"] == "server-token"  # not a debug token


def test_debug_server_up_wrong_server_code_still_fails():
    s = request_otp("+15550100", debug=True, server_reachable=True)
    r = verify_otp(s, "999999", "", debug=True, server_accepts=False)
    assert r["loggedIn"] is False and r["error"] == ERR_VERIFY_FAILED


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
