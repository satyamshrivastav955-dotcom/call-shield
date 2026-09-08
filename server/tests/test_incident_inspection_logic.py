"""Logic mirror for the Phase 3.2 ACTIONABLE INCIDENT HISTORY + bilingual complaint.

WHAT THIS IS (and is NOT). Phase 3.2 makes each Incident-History card open a full
inspection dialog and files a bilingual (English + Hindi) national cyber-crime
complaint. This mirror transcribes the pure DECISION + STRING logic from the
Kotlin so it can't silently regress:

  1. IncidentHistoryViewModel local->IncidentItem mapping now CARRIES the forensic
     telemetry (scamType/transcript/spoofProb/contact) that it used to drop to null;
     server rows still leave those null (the /api/verdicts endpoint doesn't return
     per-window telemetry). null stays null — an unmeasured signal is never faked.
  2. merge+sort: server rows then local rows, sorted non-passive-first then newest;
     "both sources empty" is an error only when the LOCAL log is empty AND the
     server call FAILED (offline-with-empty-local is just "nothing yet").
  3. IncidentInspectionDialog renders honest "Not measured / Unknown / None" for a
     null forensic field, and audio playback is ALWAYS "unavailable" — antAI never
     persists raw audio, so there is nothing to play (stating so is the honest
     answer, NOT a missing feature faked green).
  4. buildComplaintText prefills the complaint from the incident, with the same
     honest fallbacks, and always carries the real Indian channels (1930 +
     cybercrime.gov.in) and BOTH an English and a Hindi section.

The real Compose dialog, the real AlertDialog, the JSON-on-disk store and the real
Android share sheet are DEVICE gates — this mirror asserts none of them ran on
hardware. It verifies the logic transcribed from the Kotlin only.

Run:  python server/tests/test_incident_inspection_logic.py
"""
from __future__ import annotations

import sys


# ==========================================================================
# Model: IncidentItem (mirror of AntaiRestClient.IncidentItem after Phase 3.2).
# transcript/spoofProb/contact default None (server rows), populated for local.
# ==========================================================================
def incident(id, kind, risk, band, verdict, why, action, scam_type, created_at,
             transcript=None, spoof_prob=None, contact=None) -> dict:
    return {
        "id": id, "kind": kind, "riskScore": float(risk), "band": band,
        "verdict": verdict, "why": why, "action": action, "scamType": scam_type,
        "createdAt": created_at,
        "transcript": transcript, "spoofProb": spoof_prob, "contact": contact,
    }


# ---- humanizeSignal: only the mapping's use of it matters here (identity) ----
def humanize(token):
    return {
        "voice_deepfake": "Possible AI-generated voice",
        "identity_mismatch": "Voice doesn't match the claimed contact",
        "urgency": "High-pressure language",
    }.get(token, token)


# ==========================================================================
# 1. IncidentHistoryViewModel local LocalVerdict -> IncidentItem mapping.
#    Phase 3.2: now carries scamType/transcript/spoofProb/contact (was null).
# ==========================================================================
def map_local_verdict(v: dict) -> dict:
    hard = v.get("hard", [])
    soft = v.get("soft", [])
    token = (hard[0] if hard else (soft[0] if soft else None))
    verdict = humanize(token) if token is not None else "risk signal"
    transcript = v.get("transcript", "") or ""
    return incident(
        id=-v["ts"],
        kind="on-device",
        risk=v["risk"],
        band=v["band"],
        verdict=verdict,
        why=v.get("summary", ""),
        action="",
        scam_type=v.get("scamType"),                       # was hard-coded None
        created_at=v["ts"],
        transcript=(transcript if transcript.strip() != "" else None),  # ifBlank { null }
        spoof_prob=v.get("spoofProb"),                     # Float? -> Double?
        contact=v.get("contact"),
    )


# ==========================================================================
# 2. merge + sort + error rule (IncidentHistoryViewModel.load).
# ==========================================================================
def merge_incidents(server_rows, local_rows, server_failed: bool):
    merged = []
    if server_rows is not None:
        merged.extend(server_rows)
    merged.extend(local_rows)
    if not merged:
        # only an error when local is empty AND the server call actually failed
        is_error = (len(local_rows) == 0 and server_failed)
        return [], is_error
    # non-passive first (True sorts before False), then newest first
    merged.sort(key=lambda it: (it["band"] != "passive", it["createdAt"]), reverse=True)
    return merged, False


# ==========================================================================
# 3. IncidentInspectionDialog honest-null field resolution.
# ==========================================================================
def inspect_ai_voice(item):
    p = item["spoofProb"]
    return f"{int(p * 100)}%" if p is not None else "Not measured / मापा नहीं गया"


def inspect_contact(item):
    return item["contact"] if item["contact"] is not None else "Unknown caller / अज्ञात कॉलर"


def inspect_scam(item):
    return item["scamType"] if item["scamType"] is not None else "None identified / कोई नहीं"


def inspect_transcript(item):
    t = item["transcript"]
    return t if (t is not None and t.strip() != "") else "No transcript was captured for this session."


def playback_status(item):
    # INVARIANT: raw audio is never persisted -> ALWAYS unavailable, never a URI.
    return "unavailable"


# ==========================================================================
# 4. buildComplaintText (mirror of the Kotlin field selection + fallbacks).
# ==========================================================================
def build_complaint_text(item: dict) -> str:
    category = item["scamType"] or "Suspected impersonation / fraud"
    ai_voice = (f"{int(item['spoofProb'] * 100)}% (on-device AI-voice detector)"
                if item["spoofProb"] is not None else "Not measured")
    who = item["contact"] or "Unknown caller"
    reasoning = item["why"] if item["why"].strip() else "Signals crossed antAI's verification margin."
    verdict = item["verdict"] if item["verdict"].strip() else "Flagged by antAI real-time detection"
    transcript_block = ""
    t = item["transcript"]
    if t is not None and t.strip() != "":
        transcript_block = f'Transcript excerpt (auto-captured on device):\n"{t[:500]}"\n\n'
    divider = "=" * 56
    band_u = item["band"].upper()
    risk_i = int(item["riskScore"])
    return "\n".join([
        "NATIONAL CYBER CRIME COMPLAINT / राष्ट्रीय साइबर अपराध शिकायत",
        "Report online: https://cybercrime.gov.in  |  Helpline: 1930",
        "पोर्टल: https://cybercrime.gov.in  |  हेल्पलाइन: 1930",
        divider, "",
        "[ENGLISH]",
        "Nature of complaint: Suspected voice-cloning / phone scam.",
        f"Detected category: {category}",
        f"antAI risk assessment: {band_u} ({risk_i}/100)",
        f"AI-generated-voice likelihood: {ai_voice}",
        f"Caller / claimed identity: {who}",
        f"Why flagged: {reasoning}",
        f"Verdict: {verdict}",
        "",
        transcript_block + "Complainant details (please fill): name, mobile, address.",
        divider, "",
        "[हिन्दी]",
        "शिकायत का प्रकार: संदिग्ध वॉइस-क्लोनिंग / फ़ोन धोखाधड़ी।",
        f"पहचानी गई श्रेणी: {category}",
        f"antAI जोखिम आकलन: {band_u} ({risk_i}/100)",
    ])


# ------------------------------------ tests: local mapping carries telemetry
def test_local_mapping_now_carries_forensic_fields():
    v = {"ts": 1000, "risk": 88.0, "band": "critical", "summary": "AI voice + scam words",
         "hard": ["voice_deepfake"], "soft": ["urgency"],
         "transcript": "otp batao warna account block", "source": "on-device",
         "spoofProb": 0.94, "contact": "Amma", "scamType": "otp_fraud"}
    it = map_local_verdict(v)
    assert it["scamType"] == "otp_fraud"          # was dropped to None pre-3.2
    assert it["transcript"] == "otp batao warna account block"
    assert abs(it["spoofProb"] - 0.94) < 1e-9
    assert it["contact"] == "Amma"
    assert it["kind"] == "on-device"
    assert it["verdict"] == "Possible AI-generated voice"   # humanized hard[0]
    assert it["id"] == -1000                                 # negative, no id clash


def test_local_mapping_keeps_nulls_honest():
    # A benign local verdict with no scam keyword / no spoof score / unknown caller.
    v = {"ts": 5, "risk": 4.0, "band": "passive", "summary": "hi how are you",
         "hard": [], "soft": [], "transcript": "  ", "source": "on-device",
         "spoofProb": None, "contact": None, "scamType": None}
    it = map_local_verdict(v)
    assert it["scamType"] is None
    assert it["spoofProb"] is None
    assert it["contact"] is None
    assert it["transcript"] is None       # blank -> null (ifBlank { null })
    assert it["verdict"] == "risk signal"  # no hard/soft token


def test_zero_spoofprob_survives_mapping_distinct_from_null():
    v0 = {"ts": 1, "risk": 10.0, "band": "verify", "summary": "x", "hard": [], "soft": [],
          "transcript": "", "spoofProb": 0.0, "contact": None, "scamType": None}
    vN = dict(v0); vN["ts"] = 2; vN["spoofProb"] = None
    assert map_local_verdict(v0)["spoofProb"] == 0.0
    assert map_local_verdict(vN)["spoofProb"] is None


# ------------------------------------ tests: server rows stay forensic-null
def test_server_row_has_null_forensic_fields():
    # /api/verdicts doesn't return transcript/spoof/contact -> defaults None.
    row = incident(7, "voice", 72.0, "verify", "Suspicious", "urgency + otp", "Block",
                   "otp_fraud", 111)
    assert row["transcript"] is None
    assert row["spoofProb"] is None
    assert row["contact"] is None
    # but scamType from the server IS carried
    assert row["scamType"] == "otp_fraud"


# ------------------------------------ tests: merge + sort + error rule
def test_merge_orders_non_passive_first_then_newest():
    server = [incident(1, "voice", 70.0, "verify", "v", "w", "a", None, 100)]
    local = [
        map_local_verdict({"ts": 200, "risk": 3.0, "band": "passive", "summary": "ok",
                           "hard": [], "soft": [], "transcript": "", "spoofProb": None,
                           "contact": None, "scamType": None}),
        map_local_verdict({"ts": 300, "risk": 95.0, "band": "critical", "summary": "bad",
                           "hard": ["voice_deepfake"], "soft": [], "transcript": "x",
                           "spoofProb": 0.99, "contact": None, "scamType": "digital_arrest"}),
    ]
    merged, err = merge_incidents(server, local, server_failed=False)
    assert not err
    # non-passive first: critical(ts300) and verify(ts100) before passive(ts200)
    assert merged[0]["band"] == "critical" and merged[0]["createdAt"] == 300
    assert merged[1]["band"] == "verify"
    assert merged[-1]["band"] == "passive"   # passive sinks to the bottom


def test_empty_local_and_server_failure_is_error():
    merged, err = merge_incidents(None, [], server_failed=True)
    assert merged == [] and err is True


def test_empty_local_but_server_ok_is_not_error():
    # offline-safe: server returned an empty (but successful) list, local empty -> no error
    merged, err = merge_incidents([], [], server_failed=False)
    assert merged == [] and err is False


# ------------------------------------ tests: inspection dialog honest nulls
def test_inspection_fields_render_honest_unavailable():
    item = incident(1, "on-device", 50.0, "verify", "v", "w", "", None, 1,
                    transcript=None, spoof_prob=None, contact=None)
    assert "Not measured" in inspect_ai_voice(item)
    assert "Unknown caller" in inspect_contact(item)
    assert "None identified" in inspect_scam(item)
    assert "No transcript" in inspect_transcript(item)


def test_inspection_fields_render_present_values():
    item = incident(1, "on-device", 90.0, "critical", "v", "w", "", "digital_arrest", 1,
                    transcript="do not disconnect the call", spoof_prob=0.973, contact="Papa")
    assert inspect_ai_voice(item) == "97%"        # int(0.973*100)
    assert inspect_contact(item) == "Papa"
    assert inspect_scam(item) == "digital_arrest"
    assert inspect_transcript(item) == "do not disconnect the call"


def test_playback_is_always_unavailable():
    # raw audio is never persisted, for BOTH a rich and an empty incident
    rich = incident(1, "on-device", 90.0, "critical", "v", "w", "", "x", 1,
                    transcript="t", spoof_prob=0.9, contact="c")
    empty = incident(2, "on-device", 5.0, "passive", "", "", "", None, 2)
    assert playback_status(rich) == "unavailable"
    assert playback_status(empty) == "unavailable"


# ------------------------------------ tests: bilingual complaint prefill
def test_complaint_has_real_channels_and_both_languages():
    item = incident(1, "on-device", 88.0, "critical", "AI voice", "why", "", "otp_fraud", 1,
                    transcript="otp batao", spoof_prob=0.9, contact="Amma")
    txt = build_complaint_text(item)
    assert "1930" in txt
    assert "cybercrime.gov.in" in txt
    assert "[ENGLISH]" in txt
    assert "[हिन्दी]" in txt                     # Devanagari section present
    assert "राष्ट्रीय साइबर अपराध शिकायत" in txt


def test_complaint_prefills_from_incident():
    item = incident(1, "on-device", 88.0, "critical", "AI voice", "why words", "",
                    "otp_fraud", 1, transcript="otp batao warna", spoof_prob=0.9, contact="Amma")
    txt = build_complaint_text(item)
    assert "CRITICAL (88/100)" in txt
    assert "otp_fraud" in txt
    assert "90% (on-device AI-voice detector)" in txt
    assert "Amma" in txt
    assert "otp batao warna" in txt               # transcript excerpt included


def test_complaint_uses_honest_fallbacks_when_fields_null():
    item = incident(1, "on-device", 60.0, "verify", "", "", "", None, 1,
                    transcript=None, spoof_prob=None, contact=None)
    txt = build_complaint_text(item)
    assert "Suspected impersonation / fraud" in txt      # category fallback
    assert "Not measured" in txt                          # spoof fallback (not "0%")
    assert "Unknown caller" in txt                        # contact fallback
    assert "Signals crossed antAI's verification margin." in txt  # why fallback
    # a null transcript must NOT emit an empty quoted excerpt line
    assert 'Transcript excerpt (auto-captured on device):' not in txt


def test_complaint_never_fabricates_zero_for_unmeasured_spoof():
    item = incident(1, "on-device", 60.0, "verify", "v", "w", "", None, 1, spoof_prob=None)
    txt = build_complaint_text(item)
    assert "0% (on-device AI-voice detector)" not in txt
    assert "Not measured" in txt


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
