"""Logic mirror for the Phase 3.1 FORENSIC INCIDENT TELEMETRY + cross-session reset.

WHAT THIS IS (and is NOT). Phase 3.1 adds two things to the on-device Shield:
  1. CROSS-SESSION RESET — ShieldService calls pipeline.reset() on arm (startMic)
     AND disarm (stopShield), and ShieldPipeline.reset() clears the transient
     session state (audio window, transcript buffers, decaying scam memory, the
     published verdict) while PRESERVING configuration (voiceprints, scenario,
     selected contact, transcriber). So Call A's verdict/transcript can't bleed
     into Call B.
  2. FORENSIC TELEMETRY — on a VERIFY/CRITICAL verdict, TrustedStore.appendVerdict
     persists a record with: timestamp, risk, band, summary, hard/soft signals,
     transcript excerpt, source, AND (new) spoofProb, matched contact, scamType.
     The HONESTY invariant: an absent signal is stored as JSON null (read back as
     None), NEVER a fabricated 0.0 / empty string. A contact is attributed ONLY
     when an identity was actually claimed (speakerClaimed) — an informational
     unknown-caller best-match is not a forensic attribution.

The real JSON-on-disk store (org.json in filesDir), the real Kotlin reset(), and
the real logcat are DEVICE gates — this mirror does NOT claim any record was
written on hardware. It verifies the pure DECISION + SERIALIZATION logic,
transcribed from the Kotlin, so it can't silently regress.

Run:  python server/tests/test_forensic_telemetry_logic.py
"""
from __future__ import annotations

import json
import sys

# ---- constants transcribed from the Kotlin --------------------------------
SAME_BAND_REPEAT_MS = 60_000   # ShieldService.SAME_BAND_REPEAT_MS
ELEVATED = ("verify", "critical")


# ==========================================================================
# 1. appendVerdict serialization (TrustedStore.appendVerdict):
#    absent spoofProb/contact/scamType -> JSON null (JSONObject.NULL), never 0/"".
#    A JSON null round-trips back to Python None (Kotlin: o.isNull(k) -> null).
# ==========================================================================
def build_verdict_record(ts, risk, band, explanation, hard, soft, transcript,
                         source="on-device", spoof_prob=None, contact=None,
                         scam_type=None) -> str:
    """Mirror of the JSONObject appendVerdict builds (as a JSON string)."""
    rec = {
        "ts": ts,
        "risk": float(risk),
        "band": band,
        "summary": explanation[:300],
        "hard": list(hard),
        "soft": list(soft),
        "transcript": transcript[:300],
        "source": source,
        # None -> JSON null (honest "not measured"), distinct from a fabricated 0.0.
        "spoofProb": None if spoof_prob is None else float(spoof_prob),
        "contact": contact,        # None -> null
        "scamType": scam_type,     # None -> null
    }
    return json.dumps(rec)


def read_verdict_record(js: str) -> dict:
    """Mirror of recentVerdictsDetailed's per-record parse (isNull -> None)."""
    o = json.loads(js)
    def or_none(key):
        v = o.get(key, None)
        if v is None:
            return None
        # a stored empty string reads back as None too (ifBlank { null })
        if isinstance(v, str) and v.strip() == "":
            return None
        return v
    return {
        "ts": int(o.get("ts", 0)),
        "risk": float(o.get("risk", 0.0)),
        "band": o.get("band", "passive"),
        "summary": o.get("summary", ""),
        "hard": o.get("hard", []),
        "soft": o.get("soft", []),
        "transcript": o.get("transcript", ""),
        "source": o.get("source", "on-device"),
        "spoofProb": or_none("spoofProb"),
        "contact": or_none("contact"),
        "scamType": or_none("scamType"),
    }


# ==========================================================================
# 2. Contact attribution rule (ShieldService.logIncident):
#    contact = if (speakerClaimed) speakerName else null
#    -> an unknown-caller best-match (claimed=False) is NOT attributed.
# ==========================================================================
def forensic_contact(speaker_claimed: bool, speaker_name):
    return speaker_name if speaker_claimed else None


# ==========================================================================
# 3. scamType rule (ShieldPipeline.evaluate): the LIVE window's matched category
#    (scam?.type), or None when no scam keyword fired this window. The decaying
#    sessionScamMemory can keep risk up without a fresh keyword -> then None
#    (honest: no category to name for THIS window).
# ==========================================================================
def live_scam_type(matched_type):
    return matched_type   # already None when total==0 keyword hits


# ==========================================================================
# 4. logIncident band gate + debounce (ShieldService.logIncident):
#    passive/non-elevated -> return (never logged as an incident).
#    elevated -> log on band TRANSITION, or same-band past SAME_BAND_REPEAT_MS.
# ==========================================================================
class IncidentLogMirror:
    def __init__(self):
        self.last_band = "passive"
        self.last_at = 0
        self.records = []

    def on_result(self, now_ms, band, **rec):
        if band not in ELEVATED:
            self.last_band = band          # track, but do NOT log passive
            return False
        transition = band != self.last_band
        cooldown_over = now_ms - self.last_at > SAME_BAND_REPEAT_MS
        if not transition and not cooldown_over:
            return False
        self.records.append({"band": band, **rec})
        self.last_band = band
        self.last_at = now_ms
        return True


# ==========================================================================
# 5. Cross-session reset (ShieldPipeline.reset): clears TRANSIENT state,
#    PRESERVES config. Modeled so Call A can't bleed into Call B.
# ==========================================================================
class PipelineStateMirror:
    def __init__(self):
        # transient (per-session)
        self.recent_transcript = ""
        self.full_transcript = ""
        self.session_scam_memory = 0.0
        self.published_verdict = None
        # config (must survive reset)
        self.scenario = None
        self.voiceprints = []
        self.selected_phone_hash = None

    def observe(self, transcript, scam_memory, verdict):
        self.recent_transcript = transcript
        self.full_transcript = (self.full_transcript + " " + transcript).strip()
        self.session_scam_memory = scam_memory
        self.published_verdict = verdict

    def reset(self):
        self.recent_transcript = ""
        self.full_transcript = ""
        self.session_scam_memory = 0.0
        self.published_verdict = None
        # config intentionally untouched


# ------------------------------------ tests: honest-null serialization
def test_absent_signals_serialize_as_null_not_zero():
    js = build_verdict_record(1000, 88.0, "critical", "AI voice + scam words",
                              ["ai_voice"], [], "otp batao",
                              spoof_prob=None, contact=None, scam_type=None)
    raw = json.loads(js)
    # The invariant: null, never a fabricated 0.0 / "".
    assert raw["spoofProb"] is None
    assert raw["contact"] is None
    assert raw["scamType"] is None
    back = read_verdict_record(js)
    assert back["spoofProb"] is None and back["contact"] is None and back["scamType"] is None


def test_present_signals_roundtrip():
    js = build_verdict_record(1000, 92.0, "critical", "digital arrest",
                              ["ai_voice", "identity_mismatch"], ["urgency"],
                              "do not disconnect, camera on rakho",
                              spoof_prob=0.9731, contact="Amma", scam_type="digital_arrest")
    back = read_verdict_record(js)
    assert abs(back["spoofProb"] - 0.9731) < 1e-6
    assert back["contact"] == "Amma"
    assert back["scamType"] == "digital_arrest"
    assert back["risk"] == 92.0 and back["band"] == "critical"


def test_zero_spoofprob_is_preserved_not_confused_with_null():
    # A genuine measured 0.0 (model ran, scored bonafide) is NOT the same as
    # "not measured". 0.0 must round-trip as 0.0, null as None.
    js0 = build_verdict_record(1, 10.0, "verify", "x", [], [], "", spoof_prob=0.0)
    jsN = build_verdict_record(2, 10.0, "verify", "x", [], [], "", spoof_prob=None)
    assert read_verdict_record(js0)["spoofProb"] == 0.0
    assert read_verdict_record(jsN)["spoofProb"] is None


def test_older_record_without_new_keys_reads_as_none():
    # Backward compat: a pre-Phase-3.1 record has no spoofProb/contact/scamType.
    legacy = json.dumps({
        "ts": 5, "risk": 70.0, "band": "verify", "summary": "old",
        "hard": [], "soft": [], "transcript": "", "source": "on-device",
    })
    back = read_verdict_record(legacy)
    assert back["spoofProb"] is None
    assert back["contact"] is None
    assert back["scamType"] is None


# ------------------------------------ tests: contact attribution
def test_contact_attributed_only_when_claimed():
    # Identity claimed (a specific contact selected) -> attribute the name.
    assert forensic_contact(True, "Papa") == "Papa"
    # Unknown caller best-match (informational, claimed=False) -> NOT attributed.
    assert forensic_contact(False, "Papa") is None
    # No name at all -> None.
    assert forensic_contact(True, None) is None


# ------------------------------------ tests: scamType honesty
def test_scam_type_is_none_when_no_keyword_fired():
    assert live_scam_type(None) is None
    assert live_scam_type("digital_arrest") == "digital_arrest"


# ------------------------------------ tests: incident gate + debounce
def test_passive_is_never_logged_as_incident():
    log = IncidentLogMirror()
    assert log.on_result(1000, "passive", risk=5) is False
    assert log.records == []


def test_transition_into_elevated_logs_once():
    log = IncidentLogMirror()
    assert log.on_result(1000, "verify", risk=60, scamType="otp_fraud") is True
    # same band, within cooldown -> not re-logged
    assert log.on_result(1500, "verify", risk=61) is False
    assert len(log.records) == 1
    assert log.records[0]["scamType"] == "otp_fraud"


def test_escalation_verify_to_critical_logs_again():
    log = IncidentLogMirror()
    log.on_result(1000, "verify", risk=60)
    assert log.on_result(1200, "critical", risk=90) is True   # band transition
    assert len(log.records) == 2


def test_sustained_elevated_relogs_after_cooldown():
    log = IncidentLogMirror()
    log.on_result(0, "critical", risk=90)
    assert log.on_result(30_000, "critical", risk=91) is False       # within 60s
    assert log.on_result(61_000, "critical", risk=92) is True        # past cooldown
    assert len(log.records) == 2


# ------------------------------------ tests: cross-session reset (no bleed)
def test_reset_clears_transient_but_preserves_config():
    p = PipelineStateMirror()
    p.scenario = "HIGH_VALUE_TXN"
    p.voiceprints = ["Amma", "Papa"]
    p.selected_phone_hash = "abc123"
    # Call A observes scam content + a critical verdict.
    p.observe("do not disconnect digital arrest", scam_memory=0.9,
              verdict={"risk": 95, "band": "critical"})
    assert p.published_verdict["band"] == "critical"
    # Disarm -> reset before Call B.
    p.reset()
    # Transient state gone (no Call A bleed into Call B).
    assert p.recent_transcript == ""
    assert p.full_transcript == ""
    assert p.session_scam_memory == 0.0
    assert p.published_verdict is None
    # Config preserved (not per-session).
    assert p.scenario == "HIGH_VALUE_TXN"
    assert p.voiceprints == ["Amma", "Papa"]
    assert p.selected_phone_hash == "abc123"


def test_reset_prevents_stale_critical_carryover():
    p = PipelineStateMirror()
    p.observe("otp batao", scam_memory=0.75, verdict={"risk": 88, "band": "critical"})
    p.reset()
    # A fresh benign Call B must start from a clean slate.
    p.observe("hi how are you", scam_memory=0.0, verdict={"risk": 3, "band": "passive"})
    assert p.published_verdict["band"] == "passive"
    assert "otp" not in p.full_transcript


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
