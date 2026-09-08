"""Logic mirror for the ON-DEVICE text matcher + scam scoring (Phase 1.2b).

WHAT THIS IS (and is NOT). This is a line-for-line Python mirror of the matcher
and scoring arithmetic in app/.../ai/TextEngines.kt — `tokenize`, `keywordHit`,
and the `scamHeuristic`/`intentHeuristic`/`urgencyHeuristic` formulas — so the
LOGIC (whole-word vs phrase matching, the hit-count thresholds) is verified in
the Claude sandbox, which cannot run Kotlin/Gradle. It is NOT the authoritative
test: the real, shipped Kotlin dictionaries are exercised by
app/src/test/.../TextEnginesTest.kt via `./gradlew test` (the host gate). Per the
project HARD RULE this is evidence for the ALGORITHM, not a claim that the app
was built and run.

To keep the mirror honest it re-declares ONLY the dictionary entries the asserted
strings actually exercise (a subset, clearly a subset) — enough to prove the
locked TextEnginesTest expectations still hold under the new whole-word matcher,
plus the new Phase 1.2b behavior (whole-word precision, digital-arrest, remote
access, Hinglish). If you change the Kotlin matcher, change this mirror too.

Run:  python server/tests/test_text_matcher_logic.py     (prints PASS/FAIL)
  or:  pytest server/tests/test_text_matcher_logic.py
"""
from __future__ import annotations

import re
import sys

# ------------------------------------------------------------------ matcher
_TOKEN_SPLIT = re.compile(r"[^\w]+", re.UNICODE)  # \w ~= [\p{L}\p{N}_]; good enough for the mirror


def tokenize(lower: str) -> set[str]:
    """Mirror of TextEngines.tokenize: lowercased whole words, punctuation split."""
    return {t for t in _TOKEN_SPLIT.split(lower) if t}


def keyword_hit(keyword: str, lower: str, tokens: set[str]) -> bool:
    """Mirror of TextEngines.keywordHit: phrase (has non-alnum) -> substring;
    single token -> whole-word."""
    if any(not (c.isalnum()) for c in keyword):
        return keyword in lower           # phrase / hyphenated
    return keyword in tokens              # single word


# --- dictionary SUBSET (mirrors only the Kotlin entries the asserts exercise) ---
SCAM = {
    "bank_fraud": ["account blocked", "bank", "khata band", "branch", "kyc"],
    "otp_fraud": ["otp", "otp batao", "otp share"],
    "upi_fraud": ["upi", "paisa bhejo"],
    "threat": ["police", "arrest", "cbi", "giraftar", "case ho jayega"],
    "digital_arrest": ["digital arrest", "do not disconnect", "money laundering",
                       "video call par raho", "camera on rakho"],
    "loan": ["loan", "emi", "credit"],
    "job": ["job", "part-time"],
}
REQUEST = {
    "money": ["transfer", "send money", "upi", "paisa bhejo"],
    "otp": ["otp", "otp batao"],
    "remote-access": ["anydesk", "screen share", "download the app"],
}
PRESSURE = ["urgent", "immediately", "right now", "turant", "warna"]
TIME_PRESSURE = ["today only", "time khatm"]
THREAT = ["blocked", "case", "arrest", "fine"]


def scam_prob(text: str):
    lower = text.lower()
    tokens = tokenize(lower)
    scores = {cat: sum(1 for kw in kws if keyword_hit(kw, lower, tokens))
              for cat, kws in SCAM.items()}
    total = sum(scores.values())
    if total == 0:
        return 0.05, None
    top = max(scores.items(), key=lambda kv: kv[1])[0]
    prob = 0.9 if total >= 4 else 0.75 if total == 3 else 0.55 if total == 2 else 0.33
    return prob, top


def intent(text: str):
    lower = text.lower()
    tokens = tokenize(lower)
    scores = {cat: sum(1 for kw in kws if keyword_hit(kw, lower, tokens))
              for cat, kws in REQUEST.items()}
    total = sum(scores.values())
    if total == 0:
        return False, "none"
    top = max(scores.items(), key=lambda kv: kv[1])[0]
    return True, top


def urgency(text: str) -> float:
    lower = text.lower()
    tokens = tokenize(lower)
    hits = sum(1 for k in PRESSURE if keyword_hit(k, lower, tokens))
    timeh = sum(1 for k in TIME_PRESSURE if keyword_hit(k, lower, tokens))
    threath = sum(1 for k in THREAT if keyword_hit(k, lower, tokens))
    excl = text.count("!") / (len(text) + 1.0)
    score = 10 + hits * 12 + timeh * 14 + threath * 22
    score += min(15, int(excl * 600))
    return float(min(100, score))


# ------------------------------------------------------------------ locked TextEnginesTest parity
def test_benign_low_scam():
    p, _ = scam_prob("hello, how are you doing today?")
    assert p < 0.2, p


def test_otp_scam_flagged():
    p, t = scam_prob("Your bank account blocked, share your OTP immediately on this call")
    assert p >= 0.5 and t is not None, (p, t)


def test_hindi_scam_flagged():
    p, _ = scam_prob("aapka khata band ho jayega, turant OTP batao")
    assert p >= 0.5, p


def test_lone_keyword_modest():
    p, _ = scam_prob("my bank is nearby")
    assert p <= 0.4, p


def test_intent_money():
    d, t = intent("please UPI paisa bhejo right now")
    assert d and t == "money", (d, t)


def test_urgency_calm_low():
    assert urgency("hello, take your time") < 40, urgency("hello, take your time")


def test_urgency_threat_high():
    assert urgency("URGENT! account blocked, police case ho jayega!!") > 60


# ------------------------------------------------------------------ Phase 1.2b new behavior
def test_wholeword_rejects_substring_fp():
    # OLD substring matcher: "case" in suitcase/staircase + "emi" in premier => 2 hits => 0.55.
    old_hits = sum(1 for kw in ("case", "emi")
                   if kw in "i left it in the suitcase on the staircase watching the premier league")
    assert old_hits >= 2, "sanity: substring WOULD have hit"
    p, _ = scam_prob("I left it in the suitcase on the staircase, watching the premier league")
    assert p < 0.2, f"whole-word should reject substrings, got {p}"


def test_digital_arrest_typed():
    p, t = scam_prob("This is CBI, you are under digital arrest, do not disconnect, "
                     "it is money laundering")
    assert p >= 0.75 and t == "digital_arrest", (p, t)


def test_hinglish_digital_arrest():
    p, _ = scam_prob("video call par raho, camera on rakho, warna giraftar hoga")
    assert p >= 0.5, p


def test_remote_access_intent():
    d, t = intent("download the app anydesk for screen share")
    assert d and t == "remote-access", (d, t)


def test_phrase_still_matches():
    # phrases (with spaces) must still substring-match under the new matcher
    lower = "aapka khata band ho jayega"
    assert keyword_hit("khata band", lower, tokenize(lower))
    assert not keyword_hit("otp", lower, tokenize(lower))  # whole-word absent


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
        except Exception as e:  # noqa: BLE001
            failed += 1
            print(f"ERROR {t.__name__}: {type(e).__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return failed


if __name__ == "__main__":
    sys.exit(1 if _run() else 0)
