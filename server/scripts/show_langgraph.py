"""Show the LangGraph architecture + trace one scam message through every node."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))

import asyncio  # noqa: E402


def show_graph():
    print("""
LANGGRAPH ARCHITECTURE (server/src/antai/orchestration)
========================================================

                       START (call/video/msg event)
                              |
                              v
                   +----------------------+
                   |  1. ROUTER NODE      |  classifies event: message -> text
                   |                     |  voice/video -> media
                   +----------+-----------+
                              |
            +-----------------+------------------+
            v                                    v
   +----------------------+            +----------------------+
   | 2. MEDIA DETECTORS   |            | 2. TEXT DETECTORS    |
   |   voice_deepfake     |            |   scam_pattern  (CNN  |
   |   (AST ASVspoof5)    |            |     classifier)       |
   |   speaker_verify     |            |   behavioral    (text |
   |   (ECAPA voiceprint) |            |     embeddings)       |
   |   video ensemble     |            +----------+-----------+
   |   (CF-ViT+DiCoME+    |                       |
   |    temporal 2-of-3)  |                       |
   |   lipsync            |                       |
   +----------+-----------+                       |
              |                                   |
              v                                   v
   +----------------------+            +----------------------+
   | 3. IDENTITY-CLAIM    |            | 3. COLLECTIVE-DB     |
   |   "is caller really  |            |    hash lookup ->     |
   |    who they say?"    |            |    short-circuit HIGH |
   +----------+-----------+            +----------+-----------+
              |                                   |
              +----------------+------------------+
                               v
                   +----------------------+
                   | 4. URGENCY/PRESSURE  |  0-100 pressure score
                   +----------+-----------+
                               v
                   +----------------------+
                   | 5. REQUEST/INTENT    |  money|otp|credential|
                   |                      |  remote-access|link|none
                   +----------+-----------+
                               v
                   +----------------------+
                   | 6. FUSION NODE       |  weighted signals ->
                   |                      |  continuous risk 0-100
                   +----------+-----------+
                               v
                   +----------------------+
                   | 7. DECISION NODE     |  conditional branch
                   +----------+-----------+
            +----------------+------------------+
            v                v                  v
   0-40: LOG        41-70: VERIFY      71-100: FREEZE/ESCALATE
   (passive)        (trusted contact    (if request_detected:
                     ping Yes/No)        freeze the action;
                                        else escalate)
                               |
                               v
                   +----------------------+
                   | 8. LLM REASONING     |  Qwen-3B plain-language
                   |    NODE              |  verdict + why + action
                   +----------+-----------+
                               v
                          END -> verdict card -> WS push + DB + report
""")
    print("Real graph edges (graph.py):",
          "router -> {text,media} -> identity/collective -> urgency -> intent",
          "-> fusion -> decision -> llm -> END")


async def trace():
    print("\nLIVE TRACE - scam message through every node (real engines):\n")
    from antai.orchestration import nodes
    from antai.orchestration.state import AnalysisState

    state: AnalysisState = {
        "kind": "message", "session_key": "trace:1",
        "caller_id": 1, "callee_id": 2,
        "text": "Mom, I met with an accident and am stuck at the police station. "
                "Send 50000 to this account RIGHT NOW, do not tell anyone!",
        "transcript": [], "events": [],
        "request_detected": False, "identity_mismatch": False,
        "lipsync_mismatch": False, "collective_flagged": False,
        "intercept_handled": False, "risk": 0.0,
    }

    steps = [
        ("1. ROUTER", nodes.router_node, ["kind"]),
        ("2. TEXT DETECTORS (scam+behavioral)", nodes.text_detector_node,
         ["scam_prob", "scam_type", "deviation"]),
        ("3. COLLECTIVE-DB", nodes.collective_check_node,
         ["collective_flagged"]),
        ("4. URGENCY SCORER", nodes.urgency_node, ["urgency"]),
        ("5. INTENT CLASSIFIER", nodes.intent_node,
         ["request_detected", "request_type", "request_confidence"]),
        ("6. FUSION", None, ["risk", "band"]),
        ("7. DECISION", nodes.decision_node, ["decision"]),
    ]
    for name, fn, keys in steps:
        if fn:
            await fn(state)
        else:
            nodes.fusion_node(state)
        print(f"  [{name}]")
        for k in keys:
            if state.get(k) is not None and state.get(k) is not False:
                print(f"      {k} = {state.get(k)}")
    print("  [8. LLM REASONING]")
    await nodes.llm_reasoning_node(state)
    v = state.get("verdict", {})
    print(f"      verdict = {v.get('verdict')}")
    print(f"      why     = {v.get('why')}")
    print(f"      action  = {v.get('action')}")
    print(f"      scam_type = {v.get('scam_type')}  band = {v.get('band')}")


async def main():
    show_graph()
    await trace()


if __name__ == "__main__":
    asyncio.run(main())