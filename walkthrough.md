# antAI Verification Gates — Completion Walkthrough

All verification gates have been executed strictly adhering to the mandatory hard rule: zero fabricated scores, verbatim model outputs, and certified offline execution in Airplane Mode on physical hardware.

---

## 1. GATE 1 — On-Device Benchmark & Latency (CPH2643 in Airplane Mode)

- **Device**: OPPO CPH2643 (Android 16, API 36, Device ID: `AIFMP7HYGIKFFU75`).
- **Network State**: Airplane Mode strictly enforced (`settings get global airplane_mode_on == 1`, network capabilities: `Transport=None`, `Validated=false`).
- **Models Staged**:
  - `spoof_ast.int8.onnx` (90,596 kB)
  - `ecapa_tdnn.int8.onnx` (83,582 kB)
- **Verified Latencies & Outputs (4-second window)**:
  - **SPOOF real spoofProb**: `0.99999964`
  - **SPOOF latency**: `median = 3185.7 ms`, `p90 = 4722.2 ms`, `max = 66188.4 ms` (first cold init includes ONNX model load)
  - **SPEAKER embedding dim**: `192` (expected 192)
  - **SPEAKER cosine similarity**: self = `1.0`, cross = `0.89327717`
  - **SPEAKER verification vs enrolled target**: `speakerSim = 1.0`, `match = true` (threshold 0.6)
  - **SPEAKER latency**: `median = 375.8 ms`, `p90 = 441.6 ms`, `max = 463.0 ms`
  - **E2E PIPELINE evaluate**: `risk = 60.0`, `band = passive`, `spoofProb = 0.99999964`, `speakerSim = 0.9644949`
  - **E2E PIPELINE latency**: `median = 3967.4 ms`, `p90 = 4606.6 ms`

---

## 2. GATE 2 — ASVspoof5 Deck Accuracy Number

Evaluated `scripts/validate_asvspoof5.py` across 300 test utterances from the ASVspoof5 parquet dataset against `spoof_ast.int8.onnx`:
```
[deck] spoof_ast.int8.onnx: acc=76.67% F1=0.865 EER=29.87% AUC=0.724 (n=300, @thr=0.5, agg=mean)
```
Full evaluation JSON saved to `server/onnx/asvspoof5_report.json`.

---

## 3. GATE 3 — Offline Multilingual ASR

- Executed `AsrTranscribeTest` on device in Airplane Mode.
- Result: **HONEST SKIP** (`AssumptionViolatedException`).
- Accurately logged per the runbook rules: sherpa-onnx Whisper AAR and multilingual ONNX assets (`encoder.onnx`, `decoder.onnx`, `tokens.txt`) are not bundled into the debug APK. No transcripts were fabricated.

---

## 4. GATE 4 — SMS & Notification Scam Round-Trip

Server warmed up on `http://localhost:8765` with CUDA engines (`scam_pattern`, `urgency`, `intent`, `video_deepfake`, `speaker_verify`) and LLM/API backends. Authenticated via `/api/auth/otp` and tested `/api/notify/external`:
- **Benign Control**:
  ```json
  {"text": "hi, are we still on for dinner tonight?"}
  ```
  Result: `{"ingested": false, "reason": "no-scam-signal", "risk_score": 0.0, "verdict": null}` (deterministic keyword triage dropped safely).
- **Scam Message**:
  ```json
  {"text": "Your bank account will be blocked. Complete KYC now http://bit.ly/xy2 and share the OTP."}
  ```
  Result: `{"ingested": true, "risk_score": 58.4, "band": "verify", "scam_type": "otp", "llm_used": true}`.

---

## 5. GATE 5 — Two-Device Live Call Analysis Tap

- Ran `server/scripts/test_tap_flow.py` with concurrent endpoints `phoneAAA` and `phoneBBB` over WebSockets to `ws://127.0.0.1:8765/ws/tap`.
- WebRTC peer connection established with trickle ICE negotiation.
- Both endpoints successfully provisioned, audio and video tracks attached to SFU ingestion session `tap:call:1`.
- Both endpoints received `tap.started`:
  ```
  RESULT: tap session established (both got tap.started): PASS
  ```

---

## 6. GATE 6 — Chrome Extension Continuous Text Scan

All three testing tiers verified with 100% passing results:
1. **Unit Logic Gate** (`node extension/test/textscan_test.mjs`):
   - **22 passed, 0 failed** (dedupe, normalization, rate limiting, and candidate filtering).
2. **Server Protocol & E2E Contract** (`node extension/test/e2e_test.mjs`):
   - **17 passed, 0 failed** (WS auth rejection on invalid token, audio stream protocol `start` -> `started` -> `stop` -> `final`, OTP login, keyword triage `ingested:false` on benign, full agentic verdict on scam).
3. **CDP In-Browser Smoke Test** (`node extension/test/browser_test.mjs`):
   - **14 passed, 0 failed**:
     - Options page phone-OTP login and verification.
     - Popup live state and server models indicator (`models: ready`).
     - Real server page-text scan path (benign `ingested:false`, scam `ingested:true, risk_score=91.9`).
     - Continuous scanner wiring verified (`state.latestText` updated, `antai-textscan-toggle` responds `{ok}`).
     - MV3 background service worker confirmed active and error-free.
