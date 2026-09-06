# antAI — FULL DETAILED PLAN.md

> Source: session research §§1–7 + on-device decision + multilingual + all feature asks. Server becomes dev-only training/export rig; phone is the product.

---

## 1. Where we are (truth)

* **Today = client-server, NOT on-device.** `app/` (Kotlin WebRTC client) streams mic/cam over `/ws/call|/ws/tap` to `server/` (Python). All AI runs there: ASR (`asr.py`: Deepgram nova-2 / faster-whisper), spoof ensemble (`deepfake_voice.py`: AST-ASVspoof5 + wav2vec2 + optional Velma cloud), speaker verify (`speaker_verify.py`: speechbrain ECAPA), text scam/urgency/intent (MiniLM + DistilBERT), fusion/decision (`orchestration/nodes.py`), LLM explain (`providers.llm: groq gpt-oss-120b | local Qwen GGUF`).
* Server reachable via LAN / USB-tether (`server/USB_SETUP.md`) / cloud. Without it app = zero detection. `server/config.yaml`: `providers.asr: deepgram, llm: groq, voice_deepfake: both`.
* `app/` gaps: no `ForegroundService`, no toggle (auto-tap only `MainViewModel:322-351`), no file picker (only voiceprint WAV upload), single-user voiceprint, dead `addContact()`, manifest lacks `FOREGROUND_SERVICE / CALL_SCREENING / DIALER / SYSTEM_ALERT_WINDOW`.
* Eval blocked: `audio_samples/spoof|bonafide/` empty (6 ad-hoc files flat), `eval_video_ensemble.py:121 NameError (combined)`, `compare_voice_models.py` hardcoded `OneDrive + cuda`.
* `SIH_PS26104_AUDIT.md` stale (scenarios, context dict, OS notifications, salted hashing ship but marked NOT DONE).

## 2. Where we go (final target)

**100% on-device, zero-network, multilingual scam + clone detector. Server becomes dev-only training/export rig.**

```
Mic/Speaker/File ─► ShieldService (ForegroundService)
  ─► Silero VAD (ONNX CPU 1-2MB)
  ─► ASR on-device (sherpa-onnx / whisper.cpp INT8, en/hi/Hinglish auto)
  ─► parallel 4s window (voice_window_s=4.0):
      a) Spoof: w2v-frozen + AASIST-L 85K (ONNX INT8 + QNN)
      b) Voiceprint: ECAPA (ONNX INT8 + QNN/Hexagon) vs Room vault
      c) Prosody DSP: F0/jitter/shimmer/pause/speech-rate (no ML)
      d) Text: MiniLM + scam/urgency/intent DistilBERT (ONNX CPU)
  ─► FusionEngine.kt (port of fusion_node weights 50/45/60/50/50/35/22/12,
      scenarios routine_call 75/90, high_value 50/70+15, privileged 40/60+20,
      voice_alert 0.85 / disagree_cap 0.84 / solo 0.95, speaker_sim 0.60)
  ─► Overlay + notifications + freeze/FIR + Room-SQLCipher (TTL purge)
```

Stock Android can't tap modem audio — honest capture: **T1 Shield speaker-mic + file import (demo guarantee) → T2 CallScreeningService pre-ring reputation + ROLE_DIALER overlay.** Never claim VOICE_CALL capture.

NPU is vendor-specific (QNN/Hexagon vs NeuroPilot vs Exynos vs Tensor; NNAPI phasing out):
- NPU-1 (low risk, first): ECAPA → ONNX INT8 + QNN/Hexagon (~4MB). Family-voiceprint offline.
- NPU-2 (medium risk): spoof → AASIST-L 85K INT8 (dodges AST attention-op-coverage risk). Re-measure thresholds after quant.
- NPU-3 (skip): ASR + LLM stay CPU/server-assisted patterns ported on-device via CPU (whisper.cpp CPU, llama.cpp CPU/Vulkan only; Hexagon forks experimental). VAD stays CPU.

## 3. Model plan (best open-source, NPU-aware)

| # | Server now | On-device ship | Export recipe | Why |
|---|---|---|---|---|
| 3.1 | Silero VAD | same ONNX CPU | direct | trivial |
| 3.2 | AST+w2v ~180M | **w2v + AASIST-L 85K INT8** | train server-side → ONNX → INT8-QDQ → QNN EP; recal thresholds | smaller AND more accurate; dodges NPU attention-op risk |
| 3.3 | ECAPA 20M | ECAPA INT8 QNN/Hexagon ~4MB | fine-tune Indian speakers → ONNX → INT8 | NPU-1, family-voiceprint offline |
| 3.4 | Deepgram / faster-whisper (CTranslate2, no mobile) | **sherpa-onnx streaming or whisper.cpp tiny/base INT8** | quant + `en/hi` auto (`asr_language null` port) | Bhashini needs net — excluded at runtime |
| 3.5 | MiniLM + 3xDistilBERT | same → ONNX CPU quant + WordPiece bundle | `train_classifiers.py` + new `export_onnx.py`; fix missing `urgency` weight | multilingual kept |
| 3.6 | Qwen/Groq LLM (explanation-only, decision deterministic — invariant, keep) | template explainer default; opt llama.cpp-Android Q4 1.5B | slot-fill (risk→sentence, signal→clause, action→imperative). Groq only as dev fallback, never runtime | NPU LLM experimental — skip |
| 3.7 | prosody = text heuristic (PS gap) | DSP F0/jitter/shimmer/pause | Kotlin/TarsosDSP | closes literal PS text |

Multilingual: ASR `en/hi/Hinglish` + multilingual MiniLM/DistilBERT tokenizers + `en/hi` keyword/scam templates; re-measure WER + FPR/language in `docs/multilingual_results.md`.

Cloud vs local LLM note: Groq 120B (~470 tok/s) default online = faster and better; local Qwen (5–25 tok/s CPU) = offline/privacy fallback ticking every ~2s. On-device target keeps template + optional small local model only.

## 4. App build (files)

* NEW `app/app/src/main/java/.../ai/`: `ModelManager.kt, VadEngine.kt, AsrEngine.kt (JNI), SpoofEngine.kt (ORT), SpeakerEngine.kt (ORT+QNN), ProsodyEngine.kt, TextEngines.kt, FusionEngine.kt, AlertEngine.kt`.
* Capture: `ShieldService.kt` + `ShieldOverlay.kt (WindowManager)` + header Toggle (replace auto-only tap) + `FileDetectActivity (OpenDocument audio/* → same pipeline)`.
* Manifest adds: `FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, RECEIVE_BOOT_COMPLETED, SYSTEM_ALERT_WINDOW, READ_CONTACTS, READ_CALL_LOG, CALL_SCREENING`; runtime `RECORD_AUDIO, POST_NOTIFICATIONS`.
* Storage: Room + SQLCipher, salted SHA-256 + encrypted voiceprints, TTL purge, features-only (no raw audio persist).
* Port `AddContactActivity` from `app_antai_client_backup/` (fixes dead `addContact()`) → multi-enroll + `claim_peer_phone` challenge; FIR draft from ported `freeze.request` → 1930/cybercrime link; institution profiles (`bank/telecom`) over scenarios; OpenAPI + thin SDK note (no full gRPC).

## 5. Execution phases + gates

* **P0 rig (2–3d):** fix `eval_video:121`, portabilize `compare_models`, fill `spoof|bonafide + labels.csv` (ASVspoof5/WaveFake/In-the-Wild + CommonVoice-hi/IndicTTS/IIT-M, speaker-disjoint), baseline `eval_detector.py`, new `server/scripts/export_onnx.py`.
* **P1 signals (1wk):** ECAPA-INT8 + AASIST-L-INT8 + VAD + FusionEngine + toggle-speaker demo. Gate: phone EER ±1pt of server.
* **P2 speech (1wk):** ASR-ONNX + text-ONNX + prosody + scenarios. Gate: WER/F1 per language.
* **P3 product (1wk):** ShieldService + overlay + file + family + FIR + encrypt + profiles. Gate: **airplane-mode Hindi-clone-on-speaker demo.**
* **P4 harden:** Snapdragon QNN bench, threshold recal, audit refresh, dead-code delete (`cross1/_2019/classifier_base`, `sendChat/ping`), real `STORAGE_ENCRYPTION_KEY/ANTAI_PHONE_SALT`.

## 6. Bugs/compliance

Fix §6 bullets in P0/P3 (`addContact` port, `change-me-please`, missing `urgency`, `combined` NameError, dead model dirs); PS26104: add TTL/purge, institution profiles, OpenAPI/SDK stub, `verdict_text` handling note; refresh audit + multilingual docs with on-device numbers before judges.

Genuinely missing PS reqs being closed: (a) true acoustic prosody, (b) institution-specific workflows.

## 7. Risks

NPU vendor split → CPU fallback always; APK 200–400MB → first-run download + Asset Delivery; Hinglish WER dip → VAD + short-window tuning; Play policy → consent + sideload demo APK.

**SIH line:** *Only offline-multilingual-NPU clone detector — airplane mode, one tap, Hindi clone on speaker, live 0–100 risk.*
