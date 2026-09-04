"""Verification harness for the false-positive fixes.

Runs WITHOUT torch/transformers: the heavy inference packages are stubbed so we
can exercise the two pieces of pure logic that decide whether a user sees an
alert — the text triage gate and the fusion scorer.

Run:  python3 server/setup/verify_false_positives.py
"""
from __future__ import annotations

import asyncio
import os
import sys
import types
from dataclasses import dataclass, field

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "src"))


# ---------------------------------------------------------------- stubbing
# nodes.py imports the model hub and the DB at module level; neither is needed
# to test fusion, and importing them would drag in torch. Stub them out.
def _stub(name: str, **attrs) -> types.ModuleType:
    mod = types.ModuleType(name)
    for k, v in attrs.items():
        setattr(mod, k, v)
    sys.modules[name] = mod
    return mod


@dataclass
class _Pipeline:
    risk_bands: dict = field(default_factory=lambda: {"log": 40, "verify": 70})
    collective_check_enabled: bool = False
    voice_alert_threshold: float = 0.85
    voice_agreement_bonus: bool = True
    voice_disagreement_cap: float = 0.84
    voice_solo_alert_threshold: float = 0.95
    speaker_sim_threshold: float = 0.60
    # section 11 asserts this still matches the real PipelineConfig default, so a
    # stub that has drifted from production cannot quietly pass the suite
    voice_window_s: float = 4.0


@dataclass
class _Providers:
    voice_deepfake: str = "both"
    velma_api_key: str = ""
    velma_endpoint: str = "wss://example.invalid/api/x"
    velma_model_id: str = "velma-2-synthetic-voice-detection-streaming"
    velma_auth_style: str = "query"
    velma_response_path: str = ""
    velma_config_json: str = ""


@dataclass
class _Collective:
    min_flags_before_shortcircuit: int = 3


@dataclass
class _Cfg:
    pipeline: _Pipeline = field(default_factory=_Pipeline)
    collective: _Collective = field(default_factory=_Collective)
    providers: _Providers = field(default_factory=_Providers)


import antai  # noqa: E402  (package __init__ is light)

_stub("antai.inference.hub", get_hub=lambda: types.SimpleNamespace(get=lambda _n: None),
      BaseEngine=object)
_stub("antai.storage", get_db=lambda: None)
_stub("antai.config", get_config=lambda: _Cfg())

from antai.inference.text.triage import (  # noqa: E402
    is_low_content, scam_relevance, should_analyse,
)
from antai.orchestration.nodes import fusion_node  # noqa: E402

GREEN, RED, DIM, OFF = "\033[32m", "\033[31m", "\033[2m", "\033[0m"
failures: list[str] = []


def check(label: str, got, want) -> None:
    ok = got == want
    mark = f"{GREEN}PASS{OFF}" if ok else f"{RED}FAIL{OFF}"
    print(f"  {mark}  {label}{DIM} -> {got}{OFF}")
    if not ok:
        failures.append(f"{label}: got {got}, expected {want}")


# ==================================================== 1. the triage gate
BENIGN_NOTIFICATIONS = [
    "hi",
    "Hii",
    "hello",
    "ok",
    "thanks",
    "Good morning",
    "haan theek hai",
    "namaste",
    "Now playing: Blinding Lights - The Weeknd",
    "Rahul: lol that was funny",
    "Priya: are we still meeting at 6",
    "Amit sent a photo",
    "Mom: dinner is ready",
    "3 new posts from people you follow",
    "Your order has been delivered",
    "Rahul: check this out https://youtu.be/dQw4w9WgXcQ",
    "Team lunch tomorrow at the usual place",
    "Sanjay: I'll pay you back tomorrow",
]

SCAM_NOTIFICATIONS = [
    "Your account will be blocked. Complete KYC immediately: http://bit.ly/kyc-verify",
    "Dear customer, share the OTP 483920 to confirm your transaction",
    "Congratulations! You have won a cash prize of Rs 25,00,000. Claim your prize now",
    "I am calling from cyber crime branch, a parcel seized in your name. Digital arrest warrant issued",
    "Install AnyDesk and share the 9 digit code so I can help with your refund",
    "URGENT: your debit card is blocked, verify card details and cvv at www.sbi-secure.top",
    "Part time job, earn daily 3000 rupees, pay registration fee of Rs 500 to start",
    "आपका खाता बंद हो जाएगा, तुरंत ओटीपी भेजो",
    "Send money urgently mom, I had an accident and I'm in the hospital, do not tell papa",
    "Buy a google play card worth $200 and share the code with the support team",
]

print("\n=== 1. notification triage gate =========================")
print(f"\n{DIM}benign — must NOT be analysed:{OFF}")
for t in BENIGN_NOTIFICATIONS:
    d = should_analyse(t)
    check(f"{t[:58]!r:62} [{d['reason']}]", d["analyse"], False)

print(f"\n{DIM}scam — MUST be analysed:{OFF}")
for t in SCAM_NOTIFICATIONS:
    d = should_analyse(t)
    check(f"{t[:58]!r:62} [{d['reason']}]", d["analyse"], True)


# ============================================ 2. low-content short-circuit
print("\n=== 2. low-content gate (protects the classifiers) ======")
for t in ["hi", "hello", "ok thanks", "hmm", "namaste ji"]:
    check(f"low-content {t!r:24}", is_low_content(t), True)
for t in ["send otp", "pay 5000 now", "click this link www.x.top"]:
    check(f"still analysed {t!r:24}", is_low_content(t), False)


# ================================================== 3. the fusion scorer
def fuse(**signals) -> tuple[float, str, dict]:
    state = dict(signals)
    fusion_node(state)
    return state["risk"], state["band"], state["risk_signals"]


print("\n=== 3. fusion: no single weak signal may alert ==========")

cases = [
    # label,                                signals,                                          expected band
    ("nothing fired",                       {},                                                "passive"),
    ("bare request_detected (no conf)",     {"request_detected": True},                        "passive"),
    ("request 'link' at 0.7 conf",          {"request_detected": True, "request_type": "link",
                                             "request_confidence": 0.7},                       "passive"),
    ("lone scam_prob 0.80",                 {"scam_prob": 0.80},                               "passive"),
    ("lone urgency 90",                     {"urgency": 90.0},                                 "passive"),
    ("lone lipsync mismatch",               {"lipsync_mismatch": True},                        "passive"),
    ("OOD drift: scam .5 + urgency 50",     {"scam_prob": 0.5, "urgency": 50.0},               "passive"),
    ("scam .85 + urgency 85 (2 soft)",      {"scam_prob": 0.85, "urgency": 85.0},              "verify"),
    ("confident OTP ask (hard)",            {"request_detected": True, "request_type": "otp",
                                             "request_confidence": 0.9},                       "verify"),
    ("identity mismatch (hard)",            {"identity_mismatch": True},                       "verify"),
    ("reported by other users (hard)",      {"collective_flagged": True},                      "verify"),
    ("AI voice 0.97 (hard)",                {"voice_deepfake": 0.97},                          "verify"),
    ("AI voice 0.60 (below dead zone)",     {"voice_deepfake": 0.60},                          "passive"),
    ("full scam: OTP ask + pattern + rush", {"request_detected": True, "request_type": "otp",
                                             "request_confidence": 0.92, "scam_prob": 0.9,
                                             "urgency": 88.0},                                 "critical"),
    ("clone + impersonation",               {"identity_mismatch": True, "voice_deepfake": 0.95,
                                             "scam_prob": 0.8},                                "critical"),
]

for label, sig, want in cases:
    risk, band, meta = fuse(**sig)
    tag = "capped" if meta["capped"] else f"hard={len(meta['hard'])} soft={len(meta['soft'])}"
    check(f"{label:38} risk={risk:5.1f} [{tag}]", band, want)


# ======================================== 4. client band mapping (0..100)
print("\n=== 4. client RiskBand mapping (mirrors ChatModels.kt) ==")
CAUTION_AT, CRITICAL_AT = 40.0, 70.0


def client_band(risk: float, verdict: str | None) -> str:
    actionable = bool(verdict and verdict.strip())
    if risk >= CRITICAL_AT:
        return "CRITICAL"
    if risk >= CAUTION_AT or actionable:
        return "CAUTION"
    return "SAFE"


# the exact regression that made everything look like a scam
check("benign 1.8/100, no verdict   ", client_band(1.8, None), "SAFE")
check("benign 0.0/100, no verdict   ", client_band(0.0, None), "SAFE")
check("borderline 39/100, no verdict", client_band(39.0, None), "SAFE")
check("flagged 55/100 + verdict     ", client_band(55.0, "Possible OTP scam"), "CAUTION")
check("severe 82/100 + verdict      ", client_band(82.0, "OTP scam"), "CRITICAL")


# ============================= 5. AI-voice: dual-backend combine policy
# Velma (hosted, streaming) and the local SSL ensemble both score every segment.
# Either one may raise the alarm — but two models are also the only defence
# against one miscalibrated model crying wolf, so agreement escalates and
# contradiction is held below the alert line.
print("\n=== 5. AI-voice dual-backend combine ====================")

from antai.inference.voice.deepfake_voice import VoiceDeepfakeEngine  # noqa: E402

ALERT_AT = _Pipeline().voice_alert_threshold


def combine(velma, local_prob):
    """Run the engine's real combine logic without loading any weights."""
    eng = object.__new__(VoiceDeepfakeEngine)
    eng.backend = "both"
    local = None
    if local_prob is not None:
        local = {"spoof_prob": local_prob,
                 "per_model": {"asv5": round(local_prob, 3)}}
    return eng._combine(velma, local)


def outcome(velma, local_prob):
    """(agreement, label, alert?, fused band) for one pair of detector scores."""
    r = combine(velma, local_prob)
    p = r["spoof_prob"]
    alert = p is not None and p > ALERT_AT
    band = fuse(voice_deepfake=p)[1] if p is not None else fuse()[1]
    return r["agreement"], r["label"], alert, band, p


voice_cases = [
    # velma, local, expected (agreement, label, alert, band)
    ("hosted key dead, local sure 0.96", None, 0.96,
     ("single-source", "spoof", True, "verify")),
    ("hosted sure 0.96, local not loaded", 0.96, None,
     ("single-source", "spoof", True, "verify")),
    ("both flag (0.93 / 0.88)", 0.93, 0.88,
     ("both-flag", "spoof", True, "verify")),
    # A contradiction is suppressed while the flagging model is merely confident...
    ("contradiction, confident (0.90 / 0.10)", 0.90, 0.10,
     ("disagree", "uncertain", False, "passive")),
    # ...but near-certainty is NOT suppressed. Capping this too would mean a real
    # clone is never reported whenever the other backend happens to disagree.
    ("contradiction, near-certain (0.97 / 0.05)", 0.97, 0.05,
     ("disagree-strong", "spoof", True, "verify")),
    ("one flags, one unsure (0.92 / 0.55)", 0.92, 0.55,
     ("one-flag-one-unsure", "spoof", True, "verify")),
    ("both clear (0.10 / 0.05)", 0.10, 0.05,
     ("both-clear", "bonafide", False, "passive")),
    ("marginal local only (0.72)", None, 0.72,
     ("single-source", "spoof", False, "passive")),
]

for label, v, l, want in voice_cases:
    agreement, lab, alert, band, p = outcome(v, l)
    got = (agreement, lab, alert, band)
    check(f"{label:36} p={p:.3f}", got, want)

# the exact regression that made every voice metric read 0: both backends silent
r = combine(None, None)
check("both backends silent -> no score  ", (r["spoof_prob"], r["sources"]), (None, 0))
check("both backends silent -> not ready-lying", r["ready"], True)

# agreement must ESCALATE, never de-escalate below the stronger detector
r = combine(0.93, 0.88)
check("agreement raises the score        ", r["spoof_prob"] > 0.93, True)
# a merely-confident contradiction must land strictly below the popup threshold
r = combine(0.92, 0.02)
check("contradiction stays below alert   ", r["spoof_prob"] <= ALERT_AT, True)
# ...and a near-certain one must NOT be silenced by the disagreement
r = combine(0.99, 0.02)
check("near-certain still alerts         ", r["spoof_prob"] > ALERT_AT, True)
check("near-certain keeps the spoof label", r["label"], "spoof")
# provenance the warning popup needs in order to explain itself
r = combine(0.93, 0.88)
check("per-model breakdown reported      ", sorted(r["per_model"]), ["asv5", "velma-2"])


# ================================ 6. "verify with a trusted contact" flow
# The button on the call screen is only as good as the events it produces: the
# in-call UI shows pending/confirmed/denied/timeout purely from `verify.result`,
# and a silent failure here means the user is told "asking your contact…" while
# nothing was ever sent. Every state is exercised against the real functions.
print("\n=== 6. verify.result state machine ======================")


class _Recorder:
    """Stands in for the realtime hub / push service and records the fan-out."""

    def __init__(self, deliver: bool = True):
        self.sent: list[tuple[int, str, dict]] = []
        self.deliver = deliver

    async def send(self, user_id, kind, payload=None):
        self.sent.append((user_id, kind, payload or {}))
        return self.deliver

    def last(self, kind, user_id=None):
        for uid, k, p in reversed(self.sent):
            if k == kind and (user_id is None or uid == user_id):
                return p
        return None

    def kinds(self, user_id=None):
        return [k for uid, k, _ in self.sent if user_id is None or uid == user_id]


_hub = _Recorder()
_push = _Recorder()
_linked = {"value": True}

_stub("antai.gateway.push", get_push=lambda: _push)
_stub("antai.realtime", get_hub=lambda: _hub, RealtimeHub=object)
_stub("antai.trust_circle.roster",
      is_linked=lambda a, b: _linked["value"],
      trusted_contacts=lambda *a, **k: [])

from antai.trust_circle import verify as vmod  # noqa: E402

PROTECTED, RESPONDER, SESSION = 11, 22, "sess-abc"

# The fields the Kotlin client reads off the wire. If the server stops sending
# one of these the UI silently degrades to a blank bar, so pin them here.
RESULT_KEYS = {"session_key", "state", "message"}


def _reset(deliver=True, linked=True):
    _hub.sent.clear()
    _push.sent.clear()
    _hub.deliver = True          # the asking side is on a call, always listening
    _push.deliver = deliver      # the contact's phone may be offline
    _linked["value"] = linked
    vmod._pending.clear()


async def _cancel_timeouts():
    """trigger_verify arms a 45s background timeout; drop it between cases."""
    for t in [t for t in asyncio.all_tasks() if t is not asyncio.current_task()]:
        t.cancel()


async def _verify_cases():
    # --- a stranger claiming to be someone you never linked -----------------
    _reset(linked=False)
    r = await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    check("not linked -> honest refusal      ",
          (r["prompt_sent"], r["reason"]), (False, "not_linked"))
    # must not tell the impersonator's target that a challenge went out, and must
    # never ping an unlinked stranger
    check("not linked -> nothing sent anywhere",
          (_push.sent, _hub.sent), ([], []))

    # --- the happy path: contact online ------------------------------------
    _reset()
    r = await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    check("linked+online -> prompt delivered ",
          (r["prompt_sent"], r["delivered"]), (True, True))
    prompt = _push.last("verify.prompt", RESPONDER)
    check("prompt carries token+options      ",
          (bool(prompt.get("verify_token")), prompt.get("session_key"),
           len(prompt.get("options", []))), (True, SESSION, 2))
    pend = _hub.last("verify.result", PROTECTED)
    check("asker told 'pending', delivered   ",
          (pend["state"], pend["delivered"]), ("pending", True))
    check("pending payload has UI fields     ",
          RESULT_KEYS <= set(pend), True)
    # a pending challenge must NOT itself scare the user with a critical verdict
    check("pending does not raise critical   ",
          "verdict.update" in _hub.kinds(PROTECTED), False)

    # --- contact's phone is offline ----------------------------------------
    _reset(deliver=False)
    r = await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    check("offline contact -> sent, undelivered",
          (r["prompt_sent"], r["delivered"]), (True, False))
    check("offline -> asker warned it may not land",
          _hub.last("verify.result", PROTECTED)["delivered"], False)
    # unreachable contact is a real risk signal, so it escalates immediately
    check("offline -> critical fallback fired",
          _hub.last("verdict.update", PROTECTED)["band"], "critical")

    # --- contact answers "yes, it's me" ------------------------------------
    _reset()
    await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    _hub.sent.clear()
    r = await vmod.resolve_verify(SESSION, RESPONDER, True)
    res = _hub.last("verify.result", PROTECTED)
    check("confirmed -> state+verified       ",
          (res["state"], res["verified"], r["verified"]), ("confirmed", True, True))
    check("confirmed -> risk stood down      ",
          _hub.last("verdict.update", PROTECTED)["band"], "passive")

    # --- contact answers "no, that's not me" -------------------------------
    _reset()
    await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    _hub.sent.clear()
    r = await vmod.resolve_verify(SESSION, RESPONDER, False)
    res = _hub.last("verify.result", PROTECTED)
    check("denied -> state+verified false    ",
          (res["state"], res["verified"], r["verified"]), ("denied", False, False))
    check("denied -> critical + hang-up advice",
          (_hub.last("verdict.update", PROTECTED)["band"],
           "Hang up" in res["message"]), ("critical", True))

    # --- silence -----------------------------------------------------------
    _reset()
    await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    token = next(iter(vmod._pending))
    _hub.sent.clear()
    await vmod._timeout(token, timeout_s=0)
    res = _hub.last("verify.result", PROTECTED)
    check("timeout -> unverified, not innocent",
          (res["state"], res["verified"]), ("timeout", False))
    check("timeout -> critical fallback fired",
          _hub.last("verdict.update", PROTECTED)["band"], "critical")

    # --- answering twice, or answering a dead session ----------------------
    _reset()
    await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    await vmod.resolve_verify(SESSION, RESPONDER, False)
    _hub.sent.clear()
    r = await vmod.resolve_verify(SESSION, RESPONDER, True)
    # the token is consumed, so a replayed "yes" cannot un-deny a call
    check("replayed answer pushes nothing    ", _hub.sent, [])
    check("replayed answer refuses honestly  ",
          (r["ok"], r["verified"], r["reason"]),
          (False, False, "no_pending_challenge"))

    # a timeout that fires after the contact already answered must stay quiet
    _reset()
    await trigger_verify_(SESSION, PROTECTED, RESPONDER)
    await _cancel_timeouts()
    tok = next(iter(vmod._pending))
    await vmod.resolve_verify(SESSION, RESPONDER, True)
    _hub.sent.clear()
    await vmod._timeout(tok, timeout_s=0)
    check("late timeout after answer is silent", _hub.sent, [])

    # an unsolicited "yes" on a guessed session must not vouch for anyone
    _reset()
    r = await vmod.resolve_verify("sess-guessed", RESPONDER, True)
    check("unchallenged 'yes' vouches for nobody",
          (r["ok"], r["verified"], _hub.sent), (False, False, []))


trigger_verify_ = vmod.trigger_verify
asyncio.run(_verify_cases())


# ============================ 7. voiceprint cross-check: honest failure copy
# The cross-check is offered right underneath an AI-voice alert, so a bare
# "couldn't check" reads as a hidden verdict to a frightened user. Every failure
# must carry actionable words, and the WS push and the REST reply must agree —
# they used to keep separate copies of this table.
print("\n=== 7. voiceprint cross-check failure copy ==============")

from antai.orchestration.dispatcher import (  # noqa: E402
    CROSS_VERIFY_HINTS, _cross_verify_failure,
)

for reason in ("no_audio_buffered", "speaker_verify_unavailable",
               "no_voiceprint", "embed_failed"):
    f = _cross_verify_failure(reason)
    check(f"{reason:28} explains itself",
          (f["ok"], f["reason"], len(f["hint"]) > 20), (False, reason, True))
    # a failed check must never look like a verdict in either direction
    check(f"{reason:28} implies no verdict",
          (f["similarity"], f["matches"]), (None, None))

# an unknown reason must degrade to empty (the client has its own fallback
# sentence) rather than raising and losing the whole result
check("unknown reason -> blank hint, no crash",
      _cross_verify_failure("wat")["hint"], "")

# the REST endpoint reads this exact table; a rename would silently blank the UI
check("hint table keys are the emitted reasons",
      sorted(CROSS_VERIFY_HINTS),
      ["embed_failed", "no_audio_buffered", "no_voiceprint",
       "speaker_verify_unavailable"])


# ================================ 8. SMS path: the chain behind the "hi" alert
# The screenshot that started this was a plain "hi" SMS wearing a Caution chip.
# An inbound SMS travels: SmsReceiver -> POST /api/notify/external -> the gate ->
# (maybe) the graph -> MessagesRepository copies risk+verdict onto the message ->
# RiskBand.of paints the chip. Only the gate and the band mapping decide whether a
# greeting can ever be flagged, and both are pure logic, so the whole chain is
# pinned here end to end.
print("\n=== 8. inbound SMS chain (gate -> reply -> chip) =========")


def sms_chip(body: str) -> tuple[str, str]:
    """(what the server did, which chip the app shows) for an inbound SMS."""
    gate = should_analyse(body)
    if not gate["analyse"]:
        # /api/notify/external returns risk_score 0.0 and verdict None here, and
        # MessagesRepository stores exactly that.
        return "skipped", client_band(0.0, None)
    return "analysed", "(model-dependent)"


BENIGN_SMS = [
    "hi",                                     # the exact message from the report
    "Hi",
    "hey",
    "ok",
    "Thanks bhai",
    "Good morning",
    "theek hai",
    "Reaching in 10 mins",
    "Your Swiggy order has been delivered. Enjoy your meal!",
    "Sanjay: call me when you're free",
    "Happy birthday!! have a great year ahead",
]

for body in BENIGN_SMS:
    check(f"SMS {body[:44]!r:48}", sms_chip(body), ("skipped", "SAFE"))

# ...and the SMS that must still get through to the models
for body in [
    "Your account will be blocked, complete KYC now http://bit.ly/kyc",
    "Share the OTP 998211 to verify your account or it will be suspended",
]:
    check(f"scam SMS {body[:39]!r:43}", sms_chip(body)[0], "analysed")

# The in-app chat path is deliberately NOT gated by should_analyse (a scam that
# dodges our lexicon must still be scored), so its protection against "hi" is the
# node-level gate. Check that too, or a greeting typed in the app could still
# reach classifiers that have no reject class.
from antai.orchestration.nodes import _analysable_text  # noqa: E402

for body in ("hi", "ok thanks", "hmm"):
    st = {"text": body, "transcript": []}
    check(f"chat {body!r:14} reaches no model",
          (_analysable_text(st), st.get("text_skipped")), ("", "low-content"))
st = {"text": "share the otp 4821 now", "transcript": []}
check("chat scam text still analysed", _analysable_text(st) != "", True)


# ============================ 9. a silent voice detector must LOOK silent
# The flagship failure mode: no synthetic-voice backend answers, the server sends
# voice_deepfake=null, and the app renders "Voice AI 0%" — which reads as "we
# listened, it's a real human". Nothing else in the system would catch that, so
# the null must survive all the way to the rendered cell, and the model-health map
# must stop claiming the engine is fine.
print("\n=== 9. silent AI-voice detection is reported, not zeroed =")


def voice_cell(spoof_prob) -> str:
    """Mirrors signalsLine() in AiInsightWindow.kt."""
    return "—" if spoof_prob is None else f"{int(spoof_prob * 100)}%"


check("both backends silent -> no score", combine(None, None)["spoof_prob"], None)
check("silent renders as '—', never 0%",
      voice_cell(combine(None, None)["spoof_prob"]), "—")
check("a real 0.0 still renders as 0%", voice_cell(0.0), "0%")
check("a real score renders normally", voice_cell(0.91), "91%")

# detection_live(): available() keeps saying True after Velma is dropped mid-run,
# which is exactly when detection dies unnoticed.
def _engine(loaded: bool, velma: bool, local: bool):
    eng = object.__new__(VoiceDeepfakeEngine)
    eng._loaded, eng._use_velma, eng._use_local = loaded, velma, local
    return eng


check("velma + local        -> live", _engine(True, True, True).detection_live(), True)
check("velma dropped, local -> live", _engine(True, False, True).detection_live(), True)
check("velma dropped, no local -> DEAD",
      _engine(True, False, False).detection_live(), False)
check("not loaded yet          -> DEAD",
      _engine(False, True, True).detection_live(), False)

# ...and the live map the app reads must carry that answer instead of the cached
# load flag, so the "⚠ n/m models" chip fires the moment scoring stops.
import antai.orchestration.dispatcher as _disp  # noqa: E402
from antai.orchestration.dispatcher import SessionRunner  # noqa: E402


def _fake_hub(voice_live: bool):
    engines = {"asr": object(), "voice_deepfake": _engine(True, voice_live, False)}
    return types.SimpleNamespace(
        summary=lambda: {n: (True, None) for n in engines},
        get=lambda n: engines.get(n),
    )


_real_get_hub = _disp.get_hub
try:
    _disp.get_hub = lambda: _fake_hub(voice_live=False)
    ready = SessionRunner._engines_ready(object())
    check("dead voice engine reported down", ready.get("voice_deepfake"), False)
    check("other engines unaffected", ready.get("asr"), True)
    _disp.get_hub = lambda: _fake_hub(voice_live=True)
    check("live voice engine reported up",
          SessionRunner._engines_ready(object()).get("voice_deepfake"), True)
finally:
    _disp.get_hub = _real_get_hub


# ================== 10. STT failure must not disable voice-clone detection
# _asr_worker() turns one audio segment into (a) a transcript line and (b) the
# state that carries that audio to the synthetic-voice detectors. Those two used
# to share a single try block, so one raised exception from asr.transcribe (dead
# STT key, socket reset, decode error) jumped clean past the
# state["pending_audio"] assignment — and with a consistently failing provider
# the voice-clone detectors received NO audio for the entire call while every
# model still reported "loaded".
#
# This is asserted structurally rather than by running the coroutine: the worker
# needs a live hub, DB and realtime hub, and the property that matters is a
# property of the code shape — "the audio handoff is not inside the block that
# transcription can throw out of". A static check states that directly and cannot
# be fooled by a mock that happens not to raise.
print("\n=== 10. transcription failure still scores the audio ====")

import ast as _ast  # noqa: E402
import inspect as _inspect  # noqa: E402

_src = _inspect.getsource(_disp)
_worker = next(n for n in _ast.walk(_ast.parse(_src))
               if isinstance(n, _ast.AsyncFunctionDef) and n.name == "_asr_worker")
_loop = next(n for n in _worker.body if isinstance(n, _ast.While))
_tries = [n for n in _loop.body if isinstance(n, _ast.Try)]


def _mentions(node, needle: str) -> bool:
    return needle in _ast.dump(node)


_transcribe = [t for t in _tries if _mentions(t, "transcribe")]
_handoff = [t for t in _tries if _mentions(t, "pending_audio")]

check("worker has a transcription try block", len(_transcribe), 1)
check("worker has an audio-handoff try block", len(_handoff), 1)
check("they are SEPARATE blocks (STT cannot skip the handoff)",
      _transcribe[0] is _handoff[0], False)
check("transcription block does not touch pending_audio",
      _mentions(_transcribe[0], "pending_audio"), False)
check("handoff block does not call transcribe",
      _mentions(_handoff[0], "transcribe"), False)
# the handoff must also run the graph, not merely build the state
check("handoff block schedules the evaluation",
      _mentions(_handoff[0], "_schedule_evaluate"), True)
# text must be initialised before the transcription block, or a failed
# transcription raises NameError in the handoff block instead of sending "".
_pre = _worker.body[_worker.body.index(_loop):]
check("text is initialised before transcription can fail",
      any(isinstance(n, _ast.Assign) and _mentions(n, "'text'")
          for n in _loop.body[:_loop.body.index(_transcribe[0])]), True)

# Listening evidence on the wire: without audio_segments the app cannot tell
# "nothing analysed yet" from "analysed and no detector answered", and would have
# to guess — which is how the warning ends up either missing or crying wolf.
_push = next(n for n in _ast.walk(_ast.parse(_src))
             if isinstance(n, _ast.AsyncFunctionDef) and n.name == "_push_signals")
for _key in ("audio_segments", "asr_failures", "engines_ready"):
    check(f"signals.update carries {_key}", _mentions(_push, _key), True)
check("_asr_failures is counted, not just logged",
      _mentions(_worker, "_asr_failures"), True)
check("_audio_segments is incremented on handoff",
      _mentions(_handoff[0], "_audio_segments"), True)


# ============= 11. the voice detectors must get seconds, not 0.4s slices
# The quiet accuracy bug behind "the clone is never flagged": VAD segments are
# 0.4-2.0s, but the SSL models behind AI-voice detection were trained on
# multi-second utterances. Fed a 0.4s slice they sit near chance, so a real clone
# scores ~0.5 "uncertain", never crosses voice_alert_threshold, and the detector
# looks healthy the whole time. detector_window() rebuilds a few seconds of that
# ONE speaker's speech from the rolling buffer we already keep.
print("\n=== 11. AI-voice detectors get a multi-second window ====")

import importlib.util as _ilu  # noqa: E402

import numpy as np  # noqa: E402

import antai.orchestration.nodes as nodes  # noqa: E402
from antai.config import get_config  # noqa: E402  (the stub above)

# antai.config is stubbed for this suite, so read the REAL default straight off
# the source module: a stub that drifts from production would otherwise let this
# section pass while the running server used a different window.
_spec = _ilu.spec_from_file_location(
    "_real_antai_config", os.path.join(ROOT, "src", "antai", "config.py"))
_real_cfg_mod = _ilu.module_from_spec(_spec)
# dataclasses resolves annotations via sys.modules[cls.__module__], so the module
# has to be registered before it executes
sys.modules[_spec.name] = _real_cfg_mod
_spec.loader.exec_module(_real_cfg_mod)
check("stub window matches the real default",
      get_config().pipeline.voice_window_s,
      _real_cfg_mod.PipelineConfig().voice_window_s)
check("a window of several seconds is configured",
      _real_cfg_mod.PipelineConfig().voice_window_s >= 2.0, True)

_SR = 16000
_WANT = int(get_config().pipeline.voice_window_s * _SR)


def _runner_with(buf: dict) -> SessionRunner:
    r = object.__new__(SessionRunner)
    r._voice_buf = buf
    r._voice_buf_sr = _SR
    return r


def _seg(value: float, secs: float = 0.4):
    return np.full(int(secs * _SR), value, dtype=np.float32)


# ten 0.4s segments = 4.0s of speech -> the models finally see a full window
_win, _sr = _runner_with({7: [_seg(0.1) for _ in range(10)]}).detector_window(7)
check("window is voice_window_s long", len(_win), _WANT)
check("window keeps the sample rate", _sr, _SR)
check("one raw segment would have been 6400 samples", len(_seg(0.1)), 6400)

# early in a call there is less than a full window: use what we have rather than
# refusing to score (refusing would show "no detector answered" on a healthy call)
_short, _ = _runner_with({7: [_seg(0.1), _seg(0.2)]}).detector_window(7)
check("short buffer -> score what we have", len(_short), int(0.8 * _SR))

# nothing buffered yet is not a fault, and must not fabricate silence
_none, _nsr = _runner_with({}).detector_window(7)
check("empty buffer -> no window", _none, None)
check("empty buffer still reports sr", _nsr, _SR)

# the window must END at "now": it may reach back into an older segment to fill
# the requested seconds, but the newest speech must always be in it — scoring
# audio the caller has stopped speaking is how a live clone gets missed.
# (values are powers of two so float32 round-tripping can't confuse the assert)
_tail, _ = _runner_with({7: [_seg(0.25, 3.0), _seg(0.5, 3.0)]}).detector_window(7)
check("window is capped at the configured length", len(_tail), _WANT)
check("window ends on the newest audio", float(_tail[-1]), 0.5)
check("all 3s of the newest segment is kept",
      int((_tail == 0.5).sum()), int(3.0 * _SR))
check("only the needed tail of the older one is kept",
      int((_tail == 0.25).sum()), _WANT - int(3.0 * _SR))

# and it must NEVER blend two speakers into one clip — that would hand the
# detector a chimera and the voiceprint check someone else's voice.
_two = _runner_with({7: [_seg(0.25, 2.0)], 8: [_seg(0.5, 2.0)]})
_a, _ = _two.detector_window(7)
_b, _ = _two.detector_window(8)
check("speaker 7's window is only speaker 7", sorted(set(_a.tolist())), [0.25])
check("speaker 8's window is only speaker 8", sorted(set(_b.tolist())), [0.5])

# wiring: the node must prefer the window and fall back to the raw segment, and
# lip-sync must keep the raw (frame-aligned) segment.
_nodes_src = _inspect.getsource(nodes)
_vnode = next(n for n in _ast.walk(_ast.parse(_nodes_src))
              if isinstance(n, _ast.AsyncFunctionDef)
              and n.name == "voice_detector_node")
_lnode = next(n for n in _ast.walk(_ast.parse(_nodes_src))
              if isinstance(n, _ast.AsyncFunctionDef)
              and n.name == "video_detector_node")
check("voice node reads detector_audio", _mentions(_vnode, "detector_audio"), True)
check("voice node falls back to the segment",
      _mentions(_vnode, "pending_audio"), True)
check("lip-sync still uses the raw segment",
      (_mentions(_lnode, "pending_audio"), _mentions(_lnode, "detector_audio")),
      (True, False))


# ==================================================================== done
print("\n=== summary ============================================")
if failures:
    print(f"{RED}{len(failures)} failure(s):{OFF}")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
print(f"{GREEN}all checks passed{OFF}\n")
