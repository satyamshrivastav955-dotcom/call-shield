"""End-to-end demonstration of notification ingestion + local LLM advice.

Part A (live server, HTTP): a scam chat message is ingested via POST /api/chat/send
Part B (in-process): a voice call is simulated through the exact SFU ingestion path
  (VAD -> ASR -> voice deepfake -> graph -> LLM live guidance -> post-call report)
Part C: the Verify-With-Trusted-Contact prompt payload (MD 4.7)

Run: conda run -n antai-server python scripts/demo_pipeline.py
(server must be running: python run_dev.py)
"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import httpx  # noqa: E402
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

BASE = "http://127.0.0.1:8765/api"


def line(t): print(f"\n{'=' * 78}\n{t}\n{'=' * 78}")


async def part_a_chat_ingestion():
    line("PART A - NOTIFICATION INGESTION (chat message -> pipeline -> LLM advice)")
    scam_body = ("Mom, I am your son. I met with an accident and I am stuck at the police "
                 "station. My phone and account are blocked. Please transfer 50000 rupees "
                 "to this account RIGHT NOW and do not tell anyone. It is very urgent!")
    async with httpx.AsyncClient(base_url=BASE, timeout=600) as c:
        for p in ("+918001234560", "+918001234561"):
            await c.post("/auth/otp", json={"phone": p})
        t1 = (await c.post("/auth/verify", json={"phone": "+918001234560",
                                                 "otp": "000000", "display_name": "Satya"})).json()["token"]
        t2 = (await c.post("/auth/verify", json={"phone": "+918001234561",
                                                 "otp": "000000", "display_name": "Mom"})).json()["token"]
        h = {"Authorization": f"Bearer {t1}"}
        await c.post("/contacts", headers=h, json={"peer_phone": "+918001234561",
                                                   "label": "Mom", "relationship_tag": "mom"})

        print("Message received by server:")
        print(f"  {scam_body}\n")
        print("-> routed to LangGraph: text tap -> scam classifier -> behavioral -> "
              "urgency -> intent -> fusion -> decision -> LLM Reasoning node\n")

        r = await c.post("/chat/send", headers=h,
                         json={"recipient_phone": "+918001234561", "body": scam_body})
        j = r.json()
        print(f"RISK SCORE : {j['risk_score']}/100   intercepted={j['intercepted']}")
        v = j.get("verdict", {})
        print(f"\nLOCAL LLM (Qwen3B) VERDICT CARD:")
        print(f"  verdict : {v.get('verdict')}")
        print(f"  why     : {v.get('why')}")
        print(f"  action  : {v.get('action')}")
        print(f"  scam_type: {v.get('scam_type')}   band={v.get('band')}")

        fz = j.get("freeze")
        if fz:
            print(f"\nFREEZE DIRECTIVE pushed to client (MD 4.6):")
            print(f"  action={fz.get('action')}  title={fz.get('title')}")
            print(f"  message={fz.get('message')}")
            print(f"  confirm_label={fz.get('confirm_label')}")
            print("  user must explicitly confirm -> POST /freeze/decide")
            dec = await c.post("/freeze/decide", json={"freeze_id": fz["freeze_id"],
                                                       "decision": "confirm"})
            print(f"  user decision -> {dec.json()}")

        # engine-level signals the LLM cited (verdicts are saved for the
        # PROTECTED user = the recipient)
        ver = await c.get("/verdicts", headers={"Authorization": f"Bearer {t2}"})
        sigs = ver.json()["verdicts"][0].get("signals", {})
        print("\nENGINE SIGNALS the fusion node saw (what the LLM reasoned over):")
        for k, val in sigs.items():
            if val is not None and val is not False:
                print(f"  {k}: {val}")


async def part_b_voice_call_ingestion():
    line("PART B - VOICE CALL INGESTION (SFU tap -> VAD -> ASR -> deepfake -> LLM)")
    wav = Path(r"C:\Users\satya\AppData\Local\Temp\opencode\caller_demo.wav")
    audio, sr = sf.read(str(wav), dtype="float32")
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    print(f"Audio ingested from the SFU tap: {len(audio) / sr:.1f}s @ {sr} Hz\n")

    from antai.orchestration.dispatcher import get_session_runner
    from antai.inference.hub import get_hub

    hub = get_hub()
    asr_ok = hub.get("asr").ready()
    vdf_ok = hub.get("voice_deepfake").ready()
    print(f"Engines: ASR ready={asr_ok}  voice_deepfake ready={vdf_ok}")

    runner = get_session_runner("demo-call-1")
    await runner.begin(kind="voice", caller_id=1, callee_id=2)
    print("VAD segments the speech, ~1-4s chunks feed ASR + deepfake-voice...\n")

    from antai.inference.voice.deepfake_voice import VoiceDeepfakeEngine
    df = hub.get("voice_deepfake")
    r = df.analyze(audio, sr)
    print(f"VOICE DEEPFAKE check: spoof_prob={r['spoof_prob']:.2f} label={r['label']} "
          f"(>0.5 = synthetic/not the real person's voice)")

    await runner.on_audio_segment(1, audio, sr, {"demo": True})
    print(f"\nASR TRANSCRIPT: {runner.transcript[-1]['text'] if runner.transcript else '(none)'}")
    print(f"Signals after graph: {json.dumps({k: v for k, v in runner.signals.items() if v}, indent=2)}")
    print(f"Live risk: {runner.risk}/100 band={runner.band}")

    from antai.inference.llm.reasoner import generate_live_guidance, generate_report
    guidance = generate_live_guidance(runner.risk, runner.signals, runner.transcript)
    print(f"\nLOCAL LLM LIVE GUIDANCE (pushed to user mid-call):")
    print(f"  {guidance}")

    report = generate_report("voice", runner.transcript, runner.signals,
                             runner.events, runner.signals.get("scam_type"), runner.risk)
    print(f"\nLOCAL LLM POST-CALL REPORT ('{report['title']}'):")
    print(f"  {report['body']}")


async def part_c_verify_prompt():
    line("PART C - VERIFY-WITH-TRUSTED-CONTACT payload (MD 4.7)")
    print("During the call, ASR heard 'this is your mother' -> identity claim matched")
    print("trusted contact 'Mom' -> this is what gets pushed to Mom's device:\n")
    payload = {
        "type": "verify.prompt",
        "verify_token": "verify:call:123:1699999999999",
        "session_key": "call:123",
        "question": "Someone on antAI is claiming to be you and is talking to "
                    "a family member right now. Are you actually on that call?",
        "options": ["Yes, it's me", "No, that's not me"],
    }
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    print("\nMom taps 'No' -> server pushes HIGH-RISK verdict:")
    print("  'We could not verify the caller's identity.'")
    print("  action: 'Hang up and call the person directly using their saved number.'")


async def main():
    await part_a_chat_ingestion()
    await part_b_voice_call_ingestion()
    await part_c_verify_prompt()
    line("DEMO COMPLETE")


if __name__ == "__main__":
    asyncio.run(main())