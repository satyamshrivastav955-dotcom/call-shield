# antAI — Handoff Prompt & Next Process

Paste the block below into a fresh Claude session **on the Windows host** (where the device, GPU/torch, dataset, phones, and Chrome live). Everything above the line is orientation for you; the fenced block is the paste-ready prompt.

The whole "EXECUTE full build" (Tasks 1–9) is **code-complete and statically verified in the build sandbox**. What remains is *host-gated verification* — running each gate on real hardware, capturing the logged evidence, and only then marking it truly done. That is the entire next process.

---

## Paste-ready handoff prompt

```
You are resuming the antAI project — a real-time scam/deepfake-call defense system.
Repo root: C:\Users\satya\PROJECTS\antAI
  - app/        Kotlin Android client (package com.codewithkael.simplecall)
  - server/     Python FastAPI server (agentic scam/deepfake analysis)
  - extension/  Chrome MV3 extension (audio tab-tap + continuous text scan)

STATUS: A 9-task "EXECUTE full build" is CODE-COMPLETE and was statically verified
in a sandbox (node --check, py_compile, a 22/22 pure-logic test, message-symmetry
checks). The sandbox could NOT touch a device, torch/GPU, the ASVspoof5 dataset, two
phones, or a real browser+server. Your job now is to RUN THE HOST GATES on this
Windows machine, capture the real logged evidence, and mark each gate done ONLY with
that evidence.

HARD RULE (follow exactly): "No feature is marked done without a real, repeatable,
logged test showing genuine model output on an actual device. If a score, verdict, or
popup result comes from a hardcoded floor, a fallback value, or an untested code path,
that must be explicitly disclosed, never silently shipped as 'working.' Every task ends
with a verification step — do not skip it, do not mark a task complete without producing
the actual evidence (logged numbers, screenshot, or test output) it asks for." Absent
signals must read n/a / unavailable / "—", never a fabricated 0 or fake-green.

READ FIRST (authoritative, in this order):
  1. docs/ONDEVICE_RUNBOOK.md — the consolidated host runbook. It opens with a "Gate
     index" table (all 9 tasks, in order, marked host vs static) and has sections
     §1–§4, §ASR, §SMS, §CALL, §EXTENSION — each with exact commands, an evidence
     checklist, and explicit PASS / not-a-pass semantics.
  2. Your memory: MEMORY.md, then antai_execute_build.md (full build status),
     antai_ondevice_shield.md (on-device landmines: [1,1] spoof-output contract, the
     AST-not-AASIST rename, Hilt-singleton pipeline), antai_server_api.md,
     antai_calling_arch.md + antai_verify_identity.md (P2P call + analysis-tap),
     antai_stale_server_trap.md, antai_bugs.md.

CHECK-FIRST TRAPS (before ever concluding something is "broken"):
  - STALE SERVER: the Python server can run unrestarted for hours, so a code fix looks
    like a no-op. Restart it and prove it with a fresh ~30s log (antai_stale_server_trap).
  - STALE BUILD: Kotlin was NOT compiled in the sandbox. Rebuild the APK. Use JDK 21 at
    C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot (the Android Studio jbr path is
    broken). Build with .\gradlew.
  - ENV: the server venv needs the real ML stack (see RUNBOOK §0 prereqs).

NEXT PROCESS — run in this order. For each: run it, paste the real output, then mark it
done. If it fails, diagnose the root cause (check the traps first), fix code in the repo,
re-run, and disclose any fallback/untested path.

  0. Dependency-free smoke test (no device/server):
       cd extension && node test/textscan_test.mjs        -> expect "22 passed, 0 failed"
  1. Builds & unit tests:
       - Server: activate the venv (RUNBOOK §0); run the server's test suite.
       - Android: .\gradlew :app:testDebugUnitTest  and  .\gradlew :app:assembleDebug
  2. TASK 1 (on-device models) — RUNBOOK §1–§4:
       export ECAPA + AST -> INT8 ONNX (self-verifying) -> validate on ASVspoof5 (§2, the
       DECK accuracy number) -> push to filesDir/onnx -> ShieldBenchmarkTest incl.
       airplane-mode latency. Paste shield_benchmark.txt + the ASVspoof5 [deck] line.
  3. TASK 2/3 (on-device ASR + text scoring) — RUNBOOK §ASR:
       push sherpa-onnx Whisper + Hindi/English clips -> run AsrTranscribeTest IN
       AIRPLANE MODE. Paste the actual transcripts + detected languages.
  4. TASK 6 (SMS + notification scam) — RUNBOOK §SMS:
       server up + signed in; send a known scam SMS + a benign control; run the
       notification round-trip. Paste logcat (AntaiRest:* / MsgRepo:*).
  5. TASK 8 (two-device live call) — RUNBOOK §CALL:
       two phones + antAI server (:8765) + Node signaling (:3007). Read the server
       "tap-eval" per-chunk stream. Paste an excerpt whose risk/voice_df/scam/urgency
       MOVE WITH the spoken content, plus a phone screenshot of the in-call verdict.
  6. TASK 9 (extension continuous text scan) — RUNBOOK §EXTENSION:
       load unpacked -> Options: phone-OTP sign in -> open a chat/captions page ->
       "Watch page text (live)" -> DevTools console shows [antAI text-scan] risk=… lines;
       a scam line raises a desktop notification; typing in a compose box produces NO
       scan lines (privacy). Optionally: node test/browser_test.mjs (Chrome with
       --remote-debugging-port=9222 + server) and node test/e2e_test.mjs.

DEFINITION OF DONE: a gate is done only when its RUNBOOK evidence checklist items are
pasted with real values. As gates pass, update memory antai_execute_build.md. A
keyword-gated benign message returning ingested:false / null verdict is an HONEST
no-signal result, NOT a failure and NOT a fabricated safe verdict.

OPTIONAL — Task 7: cyber-complaint PDF export. The current text draft is acceptable;
only formalize into a PDF if satya asks.

Start by reading docs/ONDEVICE_RUNBOOK.md and the memory files, confirm the server/venv
and device/adb state, then run step 0 and report back before proceeding.
```

---

## Why this is the process (short rationale)

Everything left is deliberately host-only: real ONNX model output on the phone, the ASVspoof5 dataset accuracy that goes on the deck, offline ASR transcripts, a live two-device call, and the extension running against a real browser + server. The build sandbox can't produce any of those honestly, so per the hard rule they stay open until you paste the logs. The runbook's **Gate index** is the master checklist; this prompt just sequences it and front-loads the stale-server / stale-build traps that have burned time before.

---

## Phase 0/1 demo-fix — a SEPARATE host gate (run this too)

Distinct from the EXECUTE build: this is the fix for the **demo-breaker** where a casual WhatsApp voice call produced a **CRITICAL 100/100** false positive (`identity_mismatch` + `voice_deepfake`) and the floating overlay looked **frozen**. Code + a 5-scenario regression test are complete and statically audited; **rebuild the APK** (Kotlin never compiled in the sandbox) and run the gate in **`docs/ONDEVICE_RUNBOOK.md` → §DEMO-FIX**.

Two headline gates certify the fix on hardware — nothing here is "done" without them:

  A. **5-scenario regression (`ShieldScenarioTest`)** — the false-positive gate. It asserts casual chat (S1) is **not critical** and consecutive windows (S5) **differ**:
       `.\gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.codewithkael.simplecall.ShieldScenarioTest"`
       then `adb shell run-as com.codewithkael.simplecall cat files/shield_scenarios.txt`.
     If the ECAPA speaker model isn't pushed, S2–S4 log an **honest SKIP (not a pass)**; S1/S5 still run.
  B. **Live proof on a real casual WhatsApp call** — `adb logcat -s ShieldLive`: a new ~4s line whose `risk/band/transcript` **change over time** and stay **non-critical** (the overlay-not-frozen proof).

Plus 5 UI bugs to confirm manually (§DEMO-FIX → Gate C), each with a screenshot: Bug 1 offline messages score locally and are tagged **"on-device"** (airplane-mode a scam SMS + a benign control; logcat `MsgRepo … scoring on-device`); Bug 2 model-status mirrors real files; Bug 3 live verdicts appear in Incident History; Bug 4 overlay pill sizes to content; Bug 5 arming the Shield reads **ON_DEVICE** (and stays there across a socket drop) even with the server down.

**Done (Phase 0/1)** = Gate A's S1-non-critical + Gate B's moving `ShieldLive` stream pasted; that pair is what proves the false-positive/frozen-overlay demo-breaker is actually gone on device, not just in source.
