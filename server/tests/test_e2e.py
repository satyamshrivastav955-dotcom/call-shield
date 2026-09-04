"""End-to-end tests against the antAI server (FastAPI TestClient).

Run: conda run -n antai-server python -m pytest tests/ -x -q
"""
from __future__ import annotations

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from fastapi.testclient import TestClient  # noqa: E402

from antai.gateway.main import app  # noqa: E402
from antai.storage import get_db  # noqa: E402
from antai.config import get_config  # noqa: E402

client = TestClient(app)


@pytest.fixture(autouse=True)
def clean_db():
    db = get_db()
    with db.session() as s:
        from antai.storage.models import (Call, Contact, Flag, FlagSubmission,
                                          FreezeSession, Message, Report,
                                          SessionToken, User, Verdict, Voiceprint)
        for m in (SessionToken, Contact, Message, Verdict, Report, Call,
                  Flag, FlagSubmission, FreezeSession, Voiceprint, User):
            s.query(m).delete()
        s.commit()
    yield


def register(phone: str, name: str = "User") -> str:
    client.post("/api/auth/otp", json={"phone": phone})
    r = client.post("/api/auth/verify",
                    json={"phone": phone, "otp": "123456", "display_name": name})
    assert r.status_code == 200, r.text
    return r.json()["token"]


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


# ---------------------------------------------------------------- auth
def test_auth_flow():
    t = register("+911111111111", "Satya")
    assert len(t) > 20
    me = client.get("/api/auth/me", headers=auth(t))
    assert me.status_code == 200
    assert me.json()["display_name"] == "Satya"
    bad = client.get("/api/auth/me", headers=auth("bogus"))
    assert bad.status_code == 401


# ------------------------------------------------------------- contacts
def test_contacts_and_trust_circle():
    t1 = register("+912222222222", "Satya")
    t2 = register("+913333333333", "Mom")
    r = client.post("/api/contacts", headers=auth(t1),
                    json={"peer_phone": "+913333333333", "label": "Mom",
                          "relationship_tag": "mom", "is_trusted": True})
    assert r.status_code == 200, r.text
    lst = client.get("/api/contacts", headers=auth(t1)).json()["contacts"]
    assert len(lst) == 1 and lst[0]["relationship_tag"] == "mom"
    r = client.post("/api/trust-circle/link", headers=auth(t1),
                    json={"peer_phone": "+913333333333", "label": "Mom"})
    assert r.status_code == 200
    lst = client.get("/api/contacts", headers=auth(t1)).json()["contacts"]
    assert lst[0]["linked"] is True


def test_auto_provision_unregistered_peer():
    # adding a contact whose number is not registered must NOT 404
    t = register("+919111222333")
    r = client.post("/api/contacts", headers=auth(t),
                    json={"peer_phone": "+919444555666", "label": "Dad"})
    assert r.status_code == 200, r.text
    r2 = client.post("/api/chat/send", headers=auth(t),
                     json={"recipient_phone": "+919444555666",
                           "body": "Hello Dad"})
    assert r2.status_code == 200, r2.text


# ---------------------------------------------------------------- chat
def test_chat_scam_detection_and_freeze():
    t1 = register("+914444444444", "Satya")
    register("+915555555555", "Mom")
    client.post("/api/contacts", headers=auth(t1),
                json={"peer_phone": "+915555555555", "label": "Mom"})
    r = client.post("/api/chat/send", headers=auth(t1),
                    json={"recipient_phone": "+915555555555",
                          "body": "Mom please send your OTP code right now, urgent, tell no one!"})
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["analysis_pending"] is True

    # Synchronous triage / scan verification via external notification / sms endpoint
    r_ext = client.post("/api/notify/external", headers=auth(t1),
                        json={"sender": "Unknown", "app_package": "com.whatsapp",
                              "text": "Mom please send your OTP code right now, urgent, tell no one!"})
    assert r_ext.status_code == 200, r_ext.text
    j_ext = r_ext.json()
    assert j_ext["risk_score"] >= 30, j_ext
    assert j_ext["verdict"] is not None, j_ext
    assert j_ext["verdict"]["verdict"], j_ext


def test_chat_benign_no_alarm():
    t1 = register("+916666666666", "Satya")
    register("+917777777777", "Mom")
    client.post("/api/contacts", headers=auth(t1),
                json={"peer_phone": "+917777777777", "label": "Mom"})
    r = client.post("/api/chat/send", headers=auth(t1),
                    json={"recipient_phone": "+917777777777",
                          "body": "Let's meet at the park tomorrow morning"})
    assert r.status_code == 200
    j = r.json()
    assert j["analysis_pending"] is True

    r_ext = client.post("/api/notify/external", headers=auth(t1),
                        json={"sender": "Friend", "app_package": "com.whatsapp",
                              "text": "Let's meet at the park tomorrow morning"})
    assert r_ext.status_code == 200
    j_ext = r_ext.json()
    assert j_ext["risk_score"] < 40, j_ext


# ---------------------------------------------------------- collective db
def test_collective_flag_rate_limit():
    t = register("+918888888888")
    cfg = get_config()
    limit = cfg.collective.max_flags_per_account_per_hour
    ok = 0
    for i in range(limit + 3):
        r = client.post("/api/flags", headers=auth(t),
                        json={"phone": f"+9190{i:07d}", "kind": "scam"})
        if r.json().get("ok"):
            ok += 1
    assert ok == limit  # submissions beyond the hourly limit are rejected
    db = get_db()
    from antai.storage.models import Flag
    with db.session() as s:
        f = s.query(Flag).first()
        assert f is not None and f.count >= 1


# -------------------------------------------------------------- devices
def test_device_token_registration():
    t = register("+918000000000")
    r = client.post("/api/devices", headers=auth(t),
                    json={"platform": "android", "token": "fcm-token-abc123"})
    assert r.status_code == 200
    db = get_db()
    assert db.device_tokens_for(db.get_user_by_phone("+918000000000").id) == ["fcm-token-abc123"]


# ------------------------------------------------------------- voiceprint
def test_voiceprint_enroll_bad_audio():
    t = register("+918111111111")
    r = client.post("/api/voiceprints/enroll", headers=auth(t),
                    files={"file": ("bad.wav", b"not audio", "audio/wav")})
    # A rejected recording is a result, not a transport error, so the app can
    # show the reason in the recorder instead of a generic network failure.
    assert r.status_code == 200
    body = r.json()
    assert body["enrolled"] is False
    assert body["reason"] in ("engine_unavailable", "undecodable_audio")
    assert body["hint"]
    assert body["count"] == 0


def test_voiceprint_status_before_enrolment():
    t = register("+918111111112")
    r = client.get("/api/voiceprints/me", headers=auth(t))
    assert r.status_code == 200
    body = r.json()
    assert body["enrolled"] is False and body["count"] == 0
    assert body["min_seconds"] > 0


# ------------------------------------------------------------------ freeze
def test_freeze_create_and_decide():
    from antai.intercept.controller import create_intercept, decide_freeze, has_pending_freeze
    state = {"request_type": "otp", "risk": 85}
    frz = create_intercept("test-session", 1, state)
    assert frz is not None
    d = frz["directive"]
    assert d["action"] == "delay"
    assert d["hold_seconds"] == 60
    # duplicate freeze while one is pending
    assert create_intercept("test-session", 1, {"request_type": "money", "risk": 90}) is None
    r = decide_freeze(frz["freeze_id"], "confirm")
    assert r["ok"] and r["decision"] == "confirm"
    # pending hold released -> a new freeze may now be created
    assert not has_pending_freeze("test-session")


# ------------------------------------------------------- realtime WS push
def test_ws_chat_realtime_delivery():
    t1 = register("+918222222222", "Satya")
    t2 = register("+918333333333", "Mom")
    client.post("/api/contacts", headers=auth(t1),
                json={"peer_phone": "+918333333333", "label": "Mom"})
    with client.websocket_connect(f"/ws/call?token={t2}") as ws:
        import time
        time.sleep(0.3)
        r = client.post("/api/chat/send", headers=auth(t1),
                        json={"recipient_phone": "+918333333333",
                              "body": "Hello Mom, are you free this evening?"})
        assert r.status_code == 200
        # recipient should receive chat.recv over the live socket
        msg = ws.receive_json()
        assert msg["type"] == "chat.recv"
        assert msg["sender_phone"] == "+918222222222"
        assert msg["body"] == "Hello Mom, are you free this evening?"
        # and a verdict.update (payload includes peer_phone for thread matching)
        v = ws.receive_json()
        assert v["type"] == "verdict.update"
        assert v["peer_phone"] == "+918222222222"