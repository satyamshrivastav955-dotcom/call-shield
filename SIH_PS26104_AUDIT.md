# antAI — SIH PS26104 Comprehensive Audit (Phase 0 + Phase 2 Master Plan)

> **Audit policy:** Every verdict was verified against actual source files. README claims were treated as unverified until confirmed by code inspection. File paths are relative to repository root.

---

## 1. Verified Architecture — Actual Data Path

```
WebRTC (aiortc SFU /ws/call  OR  send-only tap /ws/tap)
  → av.AudioFrame
  → AudioIngestor [server/src/antai/ingestion/audio.py]
      Silero-VAD, 16 kHz mono, 512-sample windows, 0.4–2.0 s VAD segments
  → SessionRunner.on_audio_segment [orchestration/dispatcher.py]
      per-speaker rolling ~4 s detector_window + ~20 s cross-verify buffer
      → ASR worker (Deepgram cloud OR local faster-whisper) → transcript push
      → _schedule_evaluate (coalesced, fire-and-forget, 2.0 s tick)
          → LangGraph graph.ainvoke OR _run_sequential fallback
              nodes: router → voice_detector → video_detector → text_detector
                     → identity_claim → collective_check → urgency → intent
                     → fusion_node → decision_node → llm_reasoning_node
  → live realtime hub pushes:
      signals.update / verdict.update / guidance.update / deepfake.alert
  → decision=freeze → intercept.controller [intercept/controller.py]
      per-type hold directives (otp/money/credential/link), user confirm/override
  → finish(): post-call report, clears audio buffer

EXTERNAL STREAMING PATH (exists since P0 implementation):
  /api/stream/ws (WebSocket) or /api/stream/analyze (REST POST)
  → StreamingSession [orchestration/streaming.py]
      → AudioIngestor → VAD → same SessionRunner → same LangGraph graph
      → normalized_result() → {risk, risk_level, acoustic, prosody, voiceprint, …}
  Demo script: server/scripts/stream_demo_file.py
  Dashboard: /dashboard/ served from gateway/static/{index.html, app.js}
```

The rolling streaming engine, external API, demo file streamer, and mock dashboard **all exist and are real**. The previously-identified P0 gaps have been filled.

---

## 2. The 15 SIH Requirements — Classified

| # | Requirement | Verdict | File | Lines | Actual Behavior |
|---|---|---|---|---|---|
| 1 | Acoustic/spectral synthesis-artifact detection | **EXISTS & REAL** | `inference/voice/deepfake_voice.py` | 1–404 | 2-model local SSL ensemble: AST-ASVspoof5 (`models/voice_deepfake`) + wav2vec2 cross-check (`models/voice_deepfake_cross2`). Optional Velma cloud backend. `_combine()` agreement/disagreement/near-certain logic. Wired to fusion at `nodes.py:337`. Rolling 4 s detector window used instead of raw 0.4–2.0 s segment (L83–88 dispatcher). Dead-zone: contributes 0 below 0.78 spoof_prob. |
| 2 | Prosody/behavioral analysis | **PARTIAL / MISLABELED** | `inference/urgency/scorer.py`, `inference/text/behavioral.py` | scorer.py:1–85, behavioral.py:1–end | No acoustic prosody (no pitch/energy/rate/F0 extractor). "Urgency" = heuristic keyword model (falls back from absent `models/classifiers/urgency`). "Behavioral" = real multilingual-MiniLM text-embedding drift. Both are **text-derived**, not voice prosody. Labeled honestly as `"kind":"text-derived"` in `streaming.py:110`. |
| 3 | Cross-session consistency vs historical samples | **EXISTS & REAL** | `inference/voice/speaker_verify.py` | 1–162 | ECAPA-TDNN speechbrain model. Encrypted enrolled embeddings stored in DB. Cosine similarity ≥ 0.60 threshold (configurable). Live comparison in `voice_detector_node`; manual cross-verify via `dispatcher.py` CROSS_VERIFY flow. Mismatch = +50 risk (hard signal). Enrollment quality gates: ≥3 s, RMS ≥ 0.008. |
| 4 | Continuous 0–100 risk score | **EXISTS & REAL** | `orchestration/nodes.py`, `orchestration/streaming.py` | nodes.py:304–404 | Additive dead-zone fusion model with corroboration cap. Streams live via `signals.update`/`verdict.update` WebSocket pushes every ~0.7 s or after each segment. `normalized_result()` exports `risk`, `risk_peak`, `risk_level`. |
| 5 | Scenario-configurable thresholds | **PARTIAL** | `config.py` | 189, 200–211 | `risk_bands: {log: 40, verify: 70}` exist but are global only. No per-scenario profiles. No `routine_call` / `high_value_transaction` / `privileged_access` config. No `/api/scenario` endpoint. P1.2 task remains **NOT DONE**. |
| 6 | Contextual enrichment (caller/txn/history) | **DOES NOT EXIST** | — | — | No transaction type/amount structure feeding into risk. `request_type` (money/otp/credential) contributes to risk but is extracted from speech, not injected from a caller context. No `/api/context` endpoint. No demo context data structure. P1.3 remains **NOT DONE**. |
| 7 | Multi-channel alerts | **PARTIAL** | `gateway/push.py`, `notifications/` (Android) | — | In-app Compose alerts = REAL. `push.py` exists (FCM structure). **No OS status-bar notifications** in Android: `NotificationManager` / notification channels absent despite `POST_NOTIFICATIONS` in manifest. SMS read/receive = REAL. SMS send has backend but wiring unconfirmed. Email: no implementation. P1.7 remains **NOT DONE**. |
| 8 | Pre-transaction warning + secondary verification | **EXISTS & REAL** | `intercept/controller.py`, `intercept/directives.py` | controller.py:1–end | Per-type hold directives (otp/money/credential/link). User confirm or override flow. Voiceprint cross-check as independent second opinion. Nothing auto-blocked without user decision. Fully wired Android↔server. |
| 9 | Institution-specific workflows | **DOES NOT EXIST** | — | — | No institution/scenario/bank/call-center profiles. No institution concept in codebase. P1.3/P1.9 remains **NOT DONE**. |
| 10 | Minimal retention / on-device or edge inference | **PARTIAL** | `orchestration/dispatcher.py`, `storage/db.py` | dispatcher.py:finish() | Raw audio never persisted (memory buffer cleared at call end). ASR temp file deleted. BUT: no TTL/purge on call/verdict/report rows. Default backends = cloud (Deepgram/Velma/Groq), though local equiv. (`faster-whisper`, local SSL, local Qwen/llama) exist and work. Encryption key defaults to `"change-me-please"` in config. P1.6 partially done. |
| 11 | Anonymized / feature-only logging | **PARTIAL** | `storage/db.py`, `storage/models.py` | db.py:20–22 | Phone hashed (SHA-256, unsalted). Voice embeddings Fernet-encrypted. Message bodies encrypted. BUT: `verdict_text`/`report.body` stored plaintext. Verdicts/reports keyed by re-identifiable `user_id`. Internal user IDs not pseudonymized. P1.6 remains **PARTIAL**. |
| 12 | REST/gRPC API | **EXISTS & REAL (REST only)** | `gateway/rest_api.py`, `gateway/stream_api.py` | rest_api.py:1–529 | ~23 REST endpoints + 1 WebSocket streaming endpoint + 1 REST file-analyze endpoint. No gRPC (plan confirms not required). |
| 13 | Streaming / near-real-time | **EXISTS & REAL** | `gateway/stream_api.py`, `orchestration/streaming.py` | stream_api.py:67–187 | `/api/stream/ws` WebSocket — accepts raw binary PCM or JSON PCM frames. Drives real `StreamingSession → AudioIngestor → VAD → SessionRunner → LangGraph`. Heartbeat every 1 s. Live risk updates per segment. `/api/stream/analyze` REST one-shot also exists. Demo file streamer `scripts/stream_demo_file.py` works. Dashboard at `/dashboard/`. **P0 complete.** |
| 14 | Multilingual / Indian-accent support | **PARTIAL** | `inference/voice/asr.py`, `inference/text/triage.py`, urgency scorer | triage.py, scorer.py | Hindi/Hinglish at ASR (Deepgram nova-2 / local faster-whisper). Hindi keywords in urgency, intent, scam. Multilingual-MiniLM embeddings. Acoustic SSL models (ASVspoof/VoxCeleb) are Western-data-skewed — no Indian-accent acoustic validation done. No metrics documented. |
| 15 | Live native GSM/PSTN call interception | **OUT OF SCOPE — architectural boundary** | — | — | App is app-to-app WebRTC only. No InCallService/Telecom/accessibility/root workaround. Correct architectural decision; no workaround will be attempted. |

---

## 3. Strong Differentiators — Preserve These

These are the project's real competitive capabilities. Do not rewrite them.

1. **Dual-source voice deepfake ensemble** — `deepfake_voice.py` — honest agreement/disagreement + `detection_live()` (prevents silent-fail). No fake scores.
2. **Rolling per-speaker detector window** — `dispatcher.py:_schedule_evaluate` — fixes near-chance short-segment scoring for SSL models.
3. **Deterministic fusion with dead-zones + corroboration cap** — `nodes.py:fusion_node` — LLM never makes security decisions; exactly matches brief mandate.
4. **Real intercept/freeze directives** — `intercept/controller.py` — per-type hold with user confirm; no auto-block.
5. **Voiceprint cross-check as second opinion** — `speaker_verify.py` — enrollment quality gates, encrypted storage.
6. **Anti-fakery UI contract** — null scores shown as `—`; `engines_ready` field; no fabricated values in `normalized_result()`.
7. **External streaming API fully functional** — `/api/stream/ws` + `StreamingSession` — same detection path as WebRTC, no parallel ML implementation.
8. **Streaming demo file + dashboard** — `stream_demo_file.py` + `/dashboard/` — end-to-end demonstrable without a phone.

---

## 4. Known Bugs and Dead Code

| Issue | Location | Evidence |
|---|---|---|
| `eval_video_ensemble.py:121` references undefined `combined` | `scripts/eval_video_ensemble.py` | NameError at runtime |
| Unused model dirs: `voice_deepfake_2019`, `voice_deepfake_cross1`, `classifier_base` | `server/models/` | Not referenced in `hub.py` or any loader |
| `models/classifiers/urgency` absent → urgency falls back to keyword heuristic silently | `server/models/classifiers/` | `urgency/scorer.py:33–47` |
| Android `AntaiRestClient.addContact()` never called — no trusted-contact management UI | `app/.../remote/` | Dead REST method; no matching screen |
| `ChatSocketClient.sendChat/ping` unused | Android remote layer | Never called |
| `CallComponent.kt:178` a11y label copy-paste bug | Android UI | Minor |
| `storage.encryption_key` defaults to `"change-me-please"` — weak if not overridden in `.env` | `config.py:69` | Fernet key must be set in env |
| No OS status-bar notifications — `POST_NOTIFICATIONS` permission declared but `NotificationManager` never used | Android manifest + notifications/ | Zero notification channel setup |
| Phone hash is SHA-256 without salt | `storage/db.py:20–22` | Rainbow-table attack feasible |

---

## 5. Gap Analysis — What Still Needs Implementation

### P0 — DONE ✅
- [x] External streaming WebSocket API (`/api/stream/ws`)
- [x] `StreamingSession` abstraction (real models, no parallel ML)
- [x] Prerecorded file demo streamer (`stream_demo_file.py`)
- [x] Mock dashboard (HTML/JS at `/dashboard/`)
- [x] `normalized_result()` with acoustic/prosody/voiceprint families

### P1 — PENDING (SIH compliance + product value)

| Task | Status | Priority |
|---|---|---|
| P1.1 — Verify + document dual acoustic ensemble; annotate dead model dirs | Partially done (models exist, annotation missing) | Medium |
| P1.2 — Scenario-based thresholds (`routine` / `high_value` / `privileged`) | **NOT DONE** | **HIGH** |
| P1.3 — Context structure (caller_id, txn_type, amount, historical_risk) influencing risk | **NOT DONE** | **HIGH** |
| P1.4 — Explicit multi-step recommendation (callback / MFA / escalation) | Partially done in `streaming.py:_recommendation()` | Medium |
| P1.5 — Explainable 3-signal breakdown surfaced to UI (acoustic/prosody/voiceprint) | Done in `normalized_result()` | Done |
| P1.6 — Privacy: encryption key from env (not default), TTL/purge on old rows, salt phone hash | Partial | Medium |
| P1.7 — Android OS status-bar notifications via `NotificationManager` | **NOT DONE** | **HIGH** |
| P1.8 — Multilingual validation: test Hindi + Indian English; document results honestly | **NOT DONE** | Medium |
| Eval harness — accuracy/precision/recall/F1/FPR/FNR over labeled `audio_samples/` | **NOT DONE** | **HIGH** |

### P2 — PENDING (polish, after P0+P1 stable)

| Task | Status |
|---|---|
| Android UX renovation (every screen, dead buttons, fake metrics) | **NOT DONE** |
| Score graph / incident history in Android | **NOT DONE** |
| Trusted-contact management UI (wire dead `addContact`) | **NOT DONE** |
| Android OS notifications | **NOT DONE** |
| Institution profile selector UI | **NOT DONE** |
| Windows client (audio capture → shared API → desktop risk UI) | **NOT DONE** |
| Improved LLM explanation wording | Partial |
| Additional language regression tests | **NOT DONE** |

---

## 6. Server Models — Actual Status

| Model dir | Purpose | Exists on disk | Wired in hub? | Notes |
|---|---|---|---|---|
| `models/voice_deepfake` | AST-ASVspoof5 (primary acoustic) | Yes | Yes (`VoiceDeepfakeEngine`) | Primary SSL detector |
| `models/voice_deepfake_cross2` | wav2vec2 cross-check | Yes | Yes (fail-soft) | Optional second model |
| `models/voice_deepfake_cross1` | — | Yes | **No** | Dead, not referenced |
| `models/voice_deepfake_2019` | — | Yes | **No** | Dead, not referenced |
| `models/classifier_base` | — | Yes | **No** | Dead, not referenced |
| `models/speaker_verify` | ECAPA-TDNN voiceprint | Yes | Yes | Speechbrain from HuggingFace |
| `models/asr` | faster-whisper local ASR | Yes | Yes (fallback) | Cloud Deepgram is default |
| `models/llm` | Local GGUF (Qwen/llama) | Yes | Yes | Used for explanation |
| `models/video_deepfake` | Video deepfake detection | Yes | Yes (video path) | Not relevant to voice-only demo |
| `models/classifiers/urgency` | Urgency classifier | **Missing** | Wired but falls back | Falls back to keyword heuristic |
| `models/behavioral_embeddings` | MiniLM behavioral embeddings | Yes | Yes | Multilingual MiniLM |

---

## 7. Local vs Cloud Mode (Actual Behavior)

| Component | Cloud default | Local fallback | Local works? |
|---|---|---|---|
| ASR | Deepgram nova-2 (cloud) | faster-whisper (local) | Yes |
| Voice deepfake | Velma API (optional, requires key) | SSL ensemble (local) | Yes — default is local |
| Speaker verify | — | ECAPA-TDNN (local) | Yes |
| LLM/reasoning | Groq (optional) | Local GGUF | Yes |
| Urgency | — | Keyword heuristic | Yes (heuristic only) |
| Scam pattern | — | Local multilingual classifier | Yes |
| Video deepfake | — | Local ViT models | Yes (not needed for voice demo) |

**Default config `providers.voice_deepfake = "local"`** — acoustic detection runs fully locally with no API key required. The system can run in a fully local mode today.

---

## 8. Android Application — Actual State

The Android app (`app/`) is a Kotlin/Compose WebRTC calling application with security overlays.

| Area | Status | Evidence |
|---|---|---|
| WebRTC calling (WebRTC client, signaling) | Real | `webrtc/` package |
| Live risk overlay (Compose UI during call) | Real but incomplete | `ui/` screens |
| SMS interception + analysis | Real (receive + read) | `sms/` package |
| Voiceprint enrollment UI | Exists but stub | `ui/` — no enrollment flow |
| Trusted contact management | **Dead code** — REST method exists, no UI screen | `remote/AntaiRestClient` |
| OS notifications for background alerts | **NOT IMPLEMENTED** | `notifications/` — no NotificationManager calls |
| Incident history screen | **NOT IMPLEMENTED** | No persistent incident list UI |
| Settings screen with scenario controls | **NOT IMPLEMENTED** | No scenario/threshold UI |
| Protection mode toggle | UI element exists, behavior unclear | — |

---

## 9. Design Invariants (Must Hold Across All Phases)

1. **ML models produce evidence. Deterministic policy makes decisions. LLM only explains.** `decision_node` in `nodes.py` is deterministic; `llm_reasoning_node` is explanation-only. Never invert this.
2. **No fabricated scores.** A missing/failed model surfaces as `null` / `—`, never a made-up number. `normalized_result()` enforces this explicitly.
3. **No native GSM/PSTN call interception.** #15 is an architectural boundary. No InCallService/Telecom/accessibility/root workaround.
4. **Prefer reuse over rewrites.** `StreamingSession` reuses `SessionRunner`; the external API drives the same graph as the WebRTC tap.
5. **Simulated bank/telecom only.** No real financial or telecom integration.

---

## 10. Roadmap — P1 + P2 (After Audit Approval)

### P1 (~18–24 h estimated)

#### P1.1 — Acoustic model documentation + cleanup (~1 h)
Annotate dead model dirs (`voice_deepfake_2019`, `voice_deepfake_cross1`, `classifier_base`) with README stubs explaining why they are present. Verify `voice_deepfake_cross2` checkpoint integrity. No model changes.
- Files: `server/models/*/README.md` stubs

#### P1.2 — Scenario-based thresholds (~3–4 h)
Add `scenarios:` block to `config.yaml`:
```yaml
scenarios:
  routine_call:      { risk_verify_at: 75, risk_critical_at: 90 }
  high_value_txn:    { risk_verify_at: 50, risk_critical_at: 70 }
  privileged_access: { risk_verify_at: 40, risk_critical_at: 60 }
```
Wire into `fusion_node` / `decision_node`; add `POST /api/session/scenario` endpoint.
- Files: `server/config.yaml`, `config.py:PipelineConfig`, `nodes.py:fusion_node`, `gateway/rest_api.py`

#### P1.3 — Context structure (~3–4 h)
Add `POST /api/stream/context` or inline in `start` frame:
```json
{"caller_id": "...", "transaction_type": "fund_transfer",
 "transaction_amount": 850000, "claimed_identity": "Rahul Sharma",
 "historical_risk": 0}
```
Context influences risk: large-amount transfer + elevated voice deepfake → bump risk by policy multiplier.
- Files: `orchestration/streaming.py`, `gateway/stream_api.py`, `orchestration/nodes.py:fusion_node`

#### P1.4 — Explicit multi-step recommendation (~2 h)
Extend `_recommendation()` in `streaming.py` to produce structured multi-step output:
```json
{"action": "DO_NOT_APPROVE", "steps": ["Independent callback", "MFA verification", "Security escalation"]}
```
- Files: `orchestration/streaming.py:_recommendation`, `gateway/static/index.html`

#### P1.5 — Dashboard upgrade for SIH demo (~3–4 h)
Replace current generic dashboard with a bank/call-center scenario view:
- Mock caller card (name, context, amount — labeled as demo data)
- Large risk gauge with DO NOT APPROVE / VERIFY / SAFE verdict
- Three signal family bars (acoustic / prosody / voiceprint)
- Multi-step recommendation block
- Scenario selector (routine / high-value / privileged)
- All scores from real API, mock context clearly labeled
- Files: `gateway/static/index.html`, `gateway/static/app.js`

#### P1.6 — Privacy hardening (~2–3 h)
- Salt phone hashes (migration script for existing rows)
- Load encryption key from env only; fail loudly if default `"change-me-please"` in production
- Add retention: `cleanup_old_verdicts(days=30)` and `cleanup_old_reports(days=30)` DB methods
- Add a `/api/admin/cleanup` endpoint (admin-only)
- Files: `storage/db.py`, `storage/models.py`, `config.py`, `gateway/rest_api.py`

#### P1.7 — Android OS notifications (~2–3 h)
- Create notification channel `antai_risk_alerts` on app start
- Post `NotificationManager` status-bar notification when risk crosses `verify` or `critical` while app is backgrounded
- Files: Android `notifications/` package, `MainActivity.kt`

#### P1.8 — Multilingual validation (~2 h)
- Run `stream_demo_file.py` against Hindi and Indian English samples in `audio_samples/`
- Create `docs/multilingual_results.md` with honest accuracy/FP/FN observations
- Files: new `docs/multilingual_results.md`

#### Eval harness (~3–4 h)
Create `server/scripts/eval_detector.py`:
- Reads labeled `audio_samples/` (spoof/bonafide subdirs)
- Calls `POST /api/stream/analyze` per file
- Reports accuracy, precision, recall, F1, FPR, FNR
- Files: new `server/scripts/eval_detector.py`, `server/audio_samples/README.md` (label schema)

### P2 (~40–60 h)

Order: Android UX renovation → Windows client → additional tests → polish

**Android UX renovation (Step 8–12 of master plan)**
- Home screen: protection status + recent incidents + system health
- Live protection screen: live risk gauge + 3-signal breakdown + recommendation
- Voiceprint enrollment flow: real recording → quality check → embedding stored
- Trusted contact management UI (wire dead `addContact`)
- Incident history backed by real DB
- Settings: risk sensitivity, scenario, verification policy, privacy
- Every popup/dialog rewritten — no placeholder text
- OS notifications wired (P1.7 prerequisite)
- Design system: typography, spacing, colors, risk colors, severity hierarchy

**Windows client (Step 14–15)**
- Platform-specific: Python audio capture via `sounddevice` (microphone / VB-Audio / Stereo Mix)
- Streaming client: same WebSocket contract as demo file streamer
- Risk display: Tkinter/PySide6 desktop overlay with risk gauge
- Desktop notifications via `win10toast` or `plyer`
- Shared result contract with Android (same JSON schema)
- Files: new `windows_client/` directory

---

## 11. Files Requiring Modification (P1 + P2)

### P1 (targeted changes only)
| File | Change |
|---|---|
| `server/config.yaml` | Add `scenarios:` block |
| `server/src/antai/config.py` | `PipelineConfig` + `ScenarioConfig` dataclass |
| `server/src/antai/orchestration/nodes.py` | `fusion_node` reads scenario from state |
| `server/src/antai/orchestration/streaming.py` | Accept context/scenario in `StreamingSession`; enrich `normalized_result` |
| `server/src/antai/gateway/stream_api.py` | Context fields in `start` frame; scenario endpoint |
| `server/src/antai/gateway/rest_api.py` | `/api/admin/cleanup`, `/api/session/scenario` |
| `server/src/antai/gateway/static/index.html` | SIH bank demo dashboard layout |
| `server/src/antai/gateway/static/app.js` | Scenario selector, context card, recommendation steps |
| `server/src/antai/storage/db.py` | Salt phone hashes; add cleanup methods |
| Android `notifications/` | `NotificationManager` channel + posting |
| Android `MainActivity.kt` | Create notification channel on start |

### P2 (new files + extensive Android rewrites)
- `server/scripts/eval_detector.py` — NEW
- `docs/multilingual_results.md` — NEW
- `windows_client/` — NEW directory
- All Android UI screens — REWRITE
- Android design system tokens — NEW

---

## 12. Verification Plan

| Test | Command/Method | Acceptance |
|---|---|---|
| Server unit + integration tests | `pytest tests/ -x -q` | All green |
| P0 end-to-end demo | `python scripts/stream_demo_file.py audio_samples/<spoof.wav>` | ≥3 distinct risk updates, peak ≥40 for spoof; ≤20 for bonafide |
| Eval harness | `python scripts/eval_detector.py --dir audio_samples/` | Outputs accuracy/precision/recall/F1; no crash |
| Scenario threshold | Unit test in `tests/test_e2e.py` | identical signals → different bands under different scenarios |
| Privacy: no raw audio in DB | SQLite inspection after call | No audio column in any table |
| Privacy: encryption key default rejected | `STORAGE_ENCRYPTION_KEY="" python run_dev.py` | Server refuses to start or logs CRITICAL warning |
| Android notifications | Background the app; trigger a high-risk analysis | Status-bar notification appears |
| Multilingual | `stream_demo_file.py` on Hindi samples | Documents results honestly, no crash |

---

*Audit produced: 2026-09-04. Code inspected: all files listed above. No README claims accepted without code verification.*

---

## 13. Addendum 2026-09-06 — on-device pivot (supersedes stale items above)

Architecture changed: **phone is now the product; server is a dev-only
training/export rig.** New code: `app/.../ai/` (FusionEngine Kotlin port,
OrtEngines, ProsodyEngine, TextEngines, ShieldPipeline, Explainer,
ModelManager), `app/.../shield/` (ShieldService foreground mic, ShieldScreen
toggle, ShieldViewModel, AudioFileDecoder, TrustedStore, FIR draft),
`server/scripts/export_onnx.py`, `server/audio_samples/labels.csv + README.md`,
`server/.env.example`.

Corrections to audit claims (all verified in code this session):

| Audit claim ("NOT DONE") | Reality 2026-09-06 |
|---|---|
| Scenario thresholds | DONE — `config.yaml scenarios` consumed by `nodes.py`; ported to `FusionEngine.kt` + bank/telecom presets |
| Context enrichment | DONE — context dict flows stream→dispatcher→nodes; txn≥5L boost ported |
| Android OS notifications | DONE — `RiskNotificationManager` + ShieldService background alerts |
| Salted phone hashing | DONE — server salted SHA-256; phone adds per-install salt, hash-only storage |
| `eval_detector.py` missing | EXISTS — plus fixed `eval_video_ensemble.py:121`, portable `compare_voice_models.py`, populated `spoof|bonafide/` |
| `classifiers/urgency` missing | EXISTS on disk (`models/classifiers/{scam_pattern,urgency,intent}`) |
| Family voiceprint single-user | Multi-contact `TrustedStore` (hash + embedding, claim-challenge ready) |
| Institution workflows missing | `bank`/`telecom` profile presets map to tuned scenario thresholds |
| Minimal retention | Phone: features-only, no raw audio, 30-day verdict TTL; server: document TTL still open |
| Prosody gap | Closed on-device: `ProsodyEngine` DSP (pause/speech-rate/energy) + urgency hint |

Still open (honest): on-device ASR = `Transcriber` slot (whisper.cpp/sherpa-onnx
JNI lands P2b); spoof/voiceprint ORT weights ship via `export_onnx.py`
(AASIST-L + ECAPA fine-tune need GPU rig); server DB TTL purge job.
Dead dirs `voice_deepfake_2019/cross1/classifier_base` unreferenced — left on
disk (weights), excluded from export map. `ChatSocketClient.sendChat/ping`
intentionally kept (parity comment).

