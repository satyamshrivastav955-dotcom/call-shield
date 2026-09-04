"""LLM Reasoning node: plain-language verdict, live guidance, post-call reports.

The client NEVER sees raw scores - only the plain-language contract. If the
LLM is unavailable (weights not installed), we fall back to deterministic
template generation driven by the fusion signals, still plain-language.
"""
from __future__ import annotations

import json
import logging
from typing import Optional

from ..hub import get_hub

log = logging.getLogger(__name__)

_SYSTEM = (
    "You are the safety reasoning engine of a scam-detection app for elderly and "
    "vulnerable users. The user speaks English or Hindi. ALWAYS respond in simple, "
    "plain language - no jargon, no raw scores. Be calm, concrete, and never "
    "alarm the user unnecessarily. Keep answers SHORT (1-3 sentences each)."
)


def llm_available() -> bool:
    eng = get_hub().get("llm")
    return bool(eng and eng.ready())


def _llm() -> Optional:
    return get_hub().get("llm")


def _band(risk: float) -> str:
    if risk < 40:
        return "passive"
    if risk < 70:
        return "verify"
    return "critical"


def _active_reasons(signals: dict) -> list[str]:
    """Plain-language reasons for GENUINELY active signals only.

    The raw deepfake probabilities are miscalibrated near 0.5 on live
    (compressed/echoed) media, so a truthy check here would report "AI voice /
    deepfake" on perfectly real content. Only signals above a real confidence
    margin are surfaced. The same applies to `request_detected`: it is a boolean
    over a softmax with no reject class, so it is only quoted as a reason once
    the classifier is actually confident.
    """
    out: list[str] = []
    if signals.get("identity_mismatch"):
        out.append("the voice does not match the stored voiceprint of this contact")
    vd = signals.get("voice_deepfake")
    if vd is not None and vd > 0.7:
        out.append(f"the voice appears to be artificially generated ({int(vd * 100)}% confidence)")
    gd = signals.get("video_deepfake")
    if gd is not None and gd > 0.85:
        out.append(f"the video appears to be AI-generated ({int(gd * 100)}% confidence)")
    if signals.get("lipsync_mismatch"):
        out.append("the lip movements do not match the audio")
    if signals.get("scam_prob") and signals["scam_prob"] > 0.55:
        out.append(f"the conversation matches a known '{signals.get('scam_type', 'scam')}' pattern")
    if signals.get("urgency") and signals["urgency"] > 60:
        out.append("the caller is using high-pressure urgency language")
    if signals.get("request_detected"):
        conf = signals.get("request_confidence")
        if conf is None or float(conf) >= 0.60:
            out.append("the sender is asking for "
                       f"{_REQUEST_LABEL.get(signals.get('request_type') or '', 'something sensitive')}")
    if signals.get("collective_flagged"):
        out.append("this number has been reported as a scam by other users")
    return out


_REQUEST_LABEL = {
    "money": "money",
    "otp": "a one-time passcode (OTP)",
    "credential": "a password or bank details",
    "remote-access": "remote access to the device",
    "link": "you to open a link",
}


def _action_for_request(request_type: str | None) -> str:
    return {
        "money": "Do not send money. Hang up and confirm with your family member directly.",
        "otp": "Never share OTP codes. Banks never ask for them by phone.",
        "credential": "Do not share passwords or bank details with anyone over the phone.",
        "remote-access": "Do not install apps or share your screen with a stranger.",
        "link": "Do not click the link. It may install malware or steal your data.",
    }.get(request_type or "", "")


def _action_for_scam_type(scam_type: str | None) -> str:
    return {
        "family_emergency": "Hang up and call your family member back on their saved number "
                            "before doing anything else.",
        "otp": "Never share an OTP with anyone, including people claiming to be from your bank.",
        "bank": "Do not use any number or link from this message. Call your bank on the "
                "number printed on your card.",
        "prize": "Genuine prizes never require a fee. Ignore this and do not pay anything.",
        "job_offer": "Legitimate employers never ask for a registration fee. Do not pay or "
                     "share documents.",
        "romance": "Do not send money to someone you have not met in person. Talk to a "
                   "family member you trust first.",
        "remote_access": "Do not install any app or allow screen sharing. Uninstall anything "
                         "you already installed.",
        "gift_card": "No real company asks to be paid in gift cards. Do not buy or share any "
                     "card codes.",
    }.get(scam_type or "", "")


# ----------------------------------------------------------------- verdict
def _llm_worth_running(risk: float, signals: dict) -> bool:
    """Whether this event deserves an LLM call.

    The verdict is only ever *shown* to the user once the band reaches verify
    (see `dispatch_message`), so anything below that can be answered from a
    template in microseconds. Conversely, once we are going to interrupt the
    user we always want real reasoning — a generated reason and a concrete piece
    of advice — so the LLM must run for every surfaced verdict.
    """
    if risk >= 40:
        return True
    # a confident ask, or a number other users reported, is worth explaining
    # even when the numeric risk is still low
    if signals.get("collective_flagged"):
        return True
    conf = signals.get("request_confidence")
    return bool(signals.get("request_detected")) and conf is not None and float(conf) >= 0.75


def generate_verdict(risk: float, signals: dict, transcript: list[dict],
                     ctx: dict) -> dict:
    """Produce {verdict, why, action, scam_type, risk_score, band}.

    `why` is the REASON the event was flagged and `action` is the ADVICE on what
    to do next; they are always separate, always populated for anything the user
    will actually see, and the LLM's wording is merged OVER the deterministic
    template rather than replacing it. That last point matters: previously a
    single malformed JSON response, a revoked key, or a renamed model made
    `_parse_verdict` return None and silently threw away the whole LLM result,
    which is why no suggestions ever appeared.
    """
    base = _template_verdict(risk, signals, ctx)

    if not _llm_worth_running(risk, signals):
        base["llm_used"] = False
        base["llm_skipped"] = "low-risk fast path"
        return base

    if not llm_available():
        from ...inference.hub import get_hub
        eng = get_hub().get("llm")
        base["llm_used"] = False
        base["llm_error"] = (getattr(eng, "_unavailable_reason", None)
                             or "LLM engine is not loaded")
        log.warning("verdict fell back to template: %s", base["llm_error"])
        return base

    prompt = _verdict_prompt(risk, signals, transcript, ctx)
    out = _llm().complete([{"role": "system", "content": _SYSTEM},
                           {"role": "user", "content": prompt}], max_tokens=320)
    parsed = _parse_verdict(out)

    if not parsed:
        from ..providers import last_llm_error
        err = last_llm_error() or (
            "LLM replied but the response was not usable JSON: "
            f"{(out or '')[:200]!r}")
        base["llm_used"] = False
        base["llm_error"] = err
        log.warning("verdict fell back to template: %s", err)
        return base

    # merge: keep the template's wording for any field the LLM left blank, so a
    # partial answer still yields both a reason and a piece of advice.
    merged = dict(base)
    for key in ("verdict", "why", "action", "scam_type"):
        val = (parsed.get(key) or "").strip()
        if val and val.lower() not in ("none", "null", "n/a", "unknown"):
            merged[key] = val
    merged["risk_score"] = round(risk, 1)
    merged["band"] = _band(risk)
    merged["llm_used"] = True
    return _ensure_reason_and_advice(merged, risk, signals)


def _verdict_prompt(risk: float, signals: dict, transcript: list[dict],
                    ctx: dict) -> str:
    tr = "\n".join(f"{s.get('speaker','?')}: {s.get('text','')}" for s in transcript[-12:])
    reasons = _active_reasons(signals)
    active = "\n".join(f"- {r}" for r in reasons) or "- none (no concerning signals)"
    return (
        f"Current risk level: {int(risk)}/100.\n"
        f"Active concerns detected by our models (ONLY these are established facts):\n"
        f"{active}\n"
        f"Message / conversation:\n{tr}\n"
        f"Context: {ctx.get('context','')}\n\n"
        "Fill in these four fields and return EXACTLY one JSON object, nothing else.\n"
        "  verdict   - ONE short line naming what this appears to be, e.g. "
        '"This looks like an OTP scam pretending to be your bank."\n'
        "  why       - the REASON we flagged it. Quote the specific words or the "
        "specific request in the message that made it suspicious, and cite only "
        "the active concerns listed above. 1-2 sentences. Never leave this empty.\n"
        "  action    - the ADVICE: what the user should DO right now, as one "
        "concrete instruction they can follow immediately (e.g. do not share the "
        "code, hang up and call the bank on the number on your card). This must be "
        "different from `why` - `why` explains, `action` instructs. Never leave "
        "this empty.\n"
        "  scam_type - one of: family_emergency, otp, bank, prize, job_offer, "
        "romance, remote_access, gift_card, unknown\n\n"
        '{"verdict":"...","why":"...","action":"...","scam_type":"..."}'
    )


def _parse_verdict(text: str) -> Optional[dict]:
    """Extract the verdict JSON. Tolerates markdown fences and trailing prose."""
    if not text:
        return None
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.strip("`")
        if cleaned[:4].lower() == "json":
            cleaned = cleaned[4:]
    try:
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start < 0 or end < 0:
            return None
        data = json.loads(cleaned[start:end + 1])
        if not isinstance(data, dict):
            return None
        return {
            "verdict": str(data.get("verdict", "") or ""),
            "why": str(data.get("why", "") or ""),
            "action": str(data.get("action", "") or ""),
            "scam_type": str(data.get("scam_type", "unknown") or "unknown"),
        }
    except Exception as e:
        log.debug("verdict JSON parse failed: %s", e)
        return None


def _ensure_reason_and_advice(v: dict, risk: float, signals: dict) -> dict:
    """Guarantee that anything the user will see carries both a reason and advice."""
    if _band(risk) == "passive":
        return v
    if not (v.get("why") or "").strip():
        reasons = _active_reasons(signals)
        v["why"] = (". ".join(reasons).capitalize() if reasons else
                    "Several parts of this message match patterns we see in scams.")
    if not (v.get("action") or "").strip():
        v["action"] = (_action_for_request(signals.get("request_type"))
                       or _action_for_scam_type(v.get("scam_type"))
                       or ("Do not send money, codes or personal details. Verify with "
                           "someone you trust first."))
    return v


def _template_verdict(risk: float, signals: dict, ctx: dict) -> dict:
    reasons = _active_reasons(signals)
    scam_type = signals.get("scam_type") or "unknown"
    action = _action_for_request(signals.get("request_type"))

    if not reasons:
        verdict = ("No signs of a scam so far." if risk < 40
                   else "Please stay alert during this conversation.")
    else:
        verdict = ("This looks like a scam attempt."
                   if risk >= 70 else "Please be cautious - this may be a scam.")

    if risk >= 70:
        action = (action or _action_for_scam_type(scam_type)
                  or "Stop and do not act on this. Verify directly with the real "
                     "person or company using a number you already trust.")
    elif risk >= 40:
        action = (action or _action_for_scam_type(scam_type)
                  or "Check with a family member you trust before doing anything "
                     "this message asks for.")

    out = {"verdict": verdict, "why": ". ".join(reasons).capitalize(),
           "action": action, "scam_type": scam_type,
           "risk_score": round(risk, 1), "band": _band(risk)}
    return _ensure_reason_and_advice(out, risk, signals)


# ----------------------------------------------------------- live guidance
def generate_live_guidance(risk: float, signals: dict, transcript: list[dict],
                           last_guidance: str | None = None) -> str:
    # Continuous monitoring: always emit an assessment once there is any
    # conversation, even at low risk (the client shows it as a live stream).
    if not transcript:
        return ""
    if llm_available():
        tr = "\n".join(f"{s.get('speaker','?')}: {s.get('text','')}" for s in transcript[-8:])
        reasons = _active_reasons(signals)
        active = "; ".join(reasons) or "no concerning signals"
        prev = last_guidance or "none"
        prompt = (
            f"Risk: {int(risk)}/100. Active concerns: {active}\n"
            f"Conversation:\n{tr}\n"
            f"Previous guidance already shown: {prev}\n"
            "You are monitoring THIS live call in real time. Give ONE short, direct, "
            "personalized update (max 30 words) that references what was actually just "
            "said in the conversation and tells the user exactly what to do right now. "
            "If nothing is concerning, say so briefly in your own words."
        )
        out = _llm().complete([{"role": "system", "content": _SYSTEM},
                               {"role": "user", "content": prompt}], max_tokens=40)
        out = out.strip()
        if out.upper() == "NONE" or not out:
            return ""
        return out[:400]
    # template fallback
    if risk >= 70:
        return ("Heads up: this may be a scam. Do not share money, codes, or personal "
                "details. Hang up and call your family member directly.")
    if risk >= 40 and signals.get("request_detected"):
        return ("Be careful - they are asking for something. Do not act under pressure; "
                "verify with a family member first.")
    if risk >= 40:
        return "Please stay alert - something about this call needs a second look."
    return "Nothing suspicious so far - keep talking normally."


# ------------------------------------------------------------- post-call report
def generate_report(kind: str, transcript: list[dict], signals: dict,
                    events: list[dict], scam_type: str | None,
                    risk_peak: float) -> dict:
    title = f"Post-{'call' if kind == 'voice' or kind == 'video' else 'chat'} safety report"
    if llm_available():
        timeline = "\n".join(f"[{e.get('t','')}] {e.get('what','')}" for e in events[-30:])
        tr = "\n".join(f"{s.get('speaker','?')} ({s.get('t','')}): {s.get('text','')}"
                       for s in transcript[-40:])
        fired = _active_reasons(signals)
        sigs = "; ".join(fired) if fired else "none"
        prompt = (
            f"This is a post-{kind} incident report for a scam-detection app.\n"
            f"Peak risk: {int(risk_peak)}/100. Scam type guess: {scam_type or 'unknown'}\n"
            f"Signals that fired: {sigs}\n\nTimeline of flags:\n{timeline}\n\nTranscript:\n{tr}\n\n"
            "Write a short, warm, plain-language report (English or Hindi-friendly) with "
            "3 sections separated by blank lines: (1) What happened, (2) Why we flagged it, "
            "(3) Recommended next steps. Max 150 words."
        )
        body = _llm().complete([{"role": "system", "content": _SYSTEM},
                                {"role": "user", "content": prompt}], max_tokens=320)
        if body:
            return {"title": title, "body": body, "scam_type": scam_type or "unknown",
                    "signals_fired": signals}
    body = _template_report(kind, events, signals, scam_type, risk_peak)
    return {"title": title, "body": body, "scam_type": scam_type or "unknown",
            "signals_fired": signals}


def _template_report(kind: str, events: list[dict], signals: dict,
                     scam_type: str | None, risk_peak: float) -> str:
    lines = [f"What happened: your {kind} was monitored by antAI's real-time protection. "
             f"The highest risk level reached was {int(risk_peak)} out of 100."]
    if events:
        lines.append("\nTimeline of flags:")
        for e in events[-12:]:
            lines.append(f"- {e.get('what','')}")
    fired = _active_reasons(signals)
    if fired:
        lines.append(f"\nSignals that fired: {', '.join(fired)}.")
    if scam_type and scam_type != "unknown":
        lines.append(f"\nThis conversation matched the '{scam_type}' scam pattern.")
    lines.append("\nRecommended next steps: if you shared any codes, money, or personal "
                 "details, contact your bank immediately and report the number. Block the "
                 "contact and share this report with a family member you trust.")
    return "\n".join(lines)
