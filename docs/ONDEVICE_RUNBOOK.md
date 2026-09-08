# antAI — On-Device Shield Runbook (host-run gates)

This is the **execution half** of the on-device build. The Claude sandbox cannot
touch a device, torch, a GPU, or the network, so every step that produces *real
model output* is run here on your Windows machine and its logged evidence pasted
back. Nothing below is marked "working" without the actual numbers it prints.

Package: `com.codewithkael.simplecall` · models land in the app's `filesDir/onnx/`
(what `ModelManager.REQUIRED` looks for).

### Gate index (the full EXECUTE build, in order)

| Task | Gate | Where | Kind |
|---|---|---|---|
| 1a/1b | Export ECAPA + AST → INT8 ONNX (self-verifying) | §1 | host: torch/onnx |
| 1c | Validate spoof model on ASVspoof5 (the deck number) | §2 | host: dataset |
| 1d | Push models + on-device benchmark | §3, §4 | host: adb/device |
| 1e | On-device latency (airplane mode) | §4 | host: device |
| 2 | Real on-device ASR transcripts, Hindi+English, offline | §ASR | host: device |
| 3 | On-device text scoring wired to the real transcript | §ASR (same run) + code | host/static |
| 4 | Per-contact in-call voiceprint verification | code-only (instrumented UI) | static |
| 5 | Corrected false ORT claim in `TextEngines.kt` | code-only | static |
| 6 | SMS + notification scam round-trip | §SMS | host: device+server |
| 7 | Cyber-complaint export (current text acceptable) | — | optional |
| 8 | Two-device live call, per-chunk `tap-eval` scores | §CALL | host: 2 phones+server |
| 9 | Continuous page-text scan (Chrome extension) | §EXTENSION | host: browser+server; logic test runs anywhere |

Static/code-only items (4, 5, and the wiring in 3) were verified by reading the
code paths and, where runnable, unit/`--check`/`py_compile` passes; everything
marked **host** produces real model output only on your machine — paste it back.

---

## 0. Prereqs (once)

```powershell
# in server/  — a venv with the real ML stack
python -m venv .venv ; .\.venv\Scripts\activate
pip install torch torchaudio transformers onnx onnxruntime speechbrain soundfile numpy pandas pyarrow
# device: USB debugging on, `adb devices` shows it, app installed as a debug build
```

---

## 1. Export the two on-device models (Task 1a/1b)

Both scripts are **self-verifying**: they compare the exported ONNX against the
original torch model and **refuse to emit** a diverged/broken file (fail loudly).

```powershell
cd server
# 1a — speaker (SpeechBrain ECAPA -> ecapa_tdnn.int8.onnx), waveform-in wrapper
python scripts/export_ecapa_onnx.py
# 1b — spoof (the REAL AST ASVspoof5 model -> spoof_ast.int8.onnx), waveform-in
python scripts/export_ast_spoof_onnx.py
```

**What good looks like** — each ends with `PASS`, e.g.:
```
[verify] wrapper vs SpeechBrain encode_batch cosine = 0.9999xx
[verify] onnx(int8) vs wrapper cosine = 0.99xx
[verify] PASS — embeddings faithful through fp32 + int8.
...
[calib] chosen front-end: sub_mean=F div=std*2  (MSE=...)
[verify] onnx(fp32) spoof=... size=1 (expected 1) ...
[verify] PASS — graph faithful; output is [1,1] spoof-prob (contract-safe).
```

Outputs: `server/onnx/ecapa_tdnn.int8.onnx`, `server/onnx/spoof_ast.int8.onnx`.

> **Why `spoof_ast`, not `spoof_aasist_l`:** the old on-device filename named
> AASIST-L, a model that was never shipped; the real detector is the AST
> ASVspoof5 model. `OrtEngines.kt` + `ModelManager.kt` were renamed to the honest
> `spoof_ast.int8.onnx`. AST attention runs on the ORT **CPU** EP on-device (fine);
> NPU/QNN acceleration is a separate, deferred optimization (see `plan.md`).

> **Contract that must hold:** the spoof ONNX outputs a **`[1,1]` spoof
> probability**. `SpoofEngine` reads `(...)[0].maxOrNull()`, so a `[1,2]` output
> would return `max(bonafide,spoof)` and flag a *genuine* caller as a clone. The
> export script asserts `size==1`; do not bypass that.

If the spoof export dies on a kaldi-fbank ONNX op: retry `--opset 18`, or as a
last resort `--mode features` (⚠ not drop-in — needs a matching Kotlin fbank and
its own re-verification; don't ship it silently).

---

## 2. Validate the spoof model on ASVspoof5 (Task 1c) — the deck number

Evaluates the **int8 file that actually ships**, through the same 4s-window path
the phone uses, so the accuracy you quote is what the phone does.

```powershell
# A) FLAC + protocol layout
python scripts/validate_asvspoof5.py `
  --model onnx/spoof_ast.int8.onnx `
  --flac-dir data/ASVspoof5/flac_D `
  --protocol data/ASVspoof5/ASVspoof5.dev.metadata.txt `
  --out onnx/asvspoof5_report.json
# B) parquet layout
python scripts/validate_asvspoof5.py --model onnx/spoof_ast.int8.onnx `
  --data data/ASVspoof5/dev.parquet --out onnx/asvspoof5_report.json
```

Prints a `[deck]` line: `acc=…%  F1=…  EER=…%  AUC=…` and writes
`asvspoof5_report.json` (confusion matrix + EER/best-accuracy thresholds). Quote
**that** number, not the server's old `eval_results.json` (0.8489 was fp32,
server-side; the phone runs int8). Compare int8 vs fp32 with `--backend torch
--model models/voice_deepfake`.

---

## 3. Push the models to the device (Task 1d)

**Method A — `run-as` (most reliable on a debug build):**
```powershell
adb push onnx/spoof_ast.int8.onnx  /data/local/tmp/
adb push onnx/ecapa_tdnn.int8.onnx /data/local/tmp/
adb shell run-as com.codewithkael.simplecall mkdir -p files/onnx
adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/spoof_ast.int8.onnx  files/onnx/
adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/ecapa_tdnn.int8.onnx files/onnx/
```

**Method B — external staging (no run-as); the benchmark test auto-copies it in:**
```powershell
adb shell mkdir -p /sdcard/Android/data/com.codewithkael.simplecall/files/onnx_staging
adb push onnx/spoof_ast.int8.onnx  /sdcard/Android/data/com.codewithkael.simplecall/files/onnx_staging/
adb push onnx/ecapa_tdnn.int8.onnx /sdcard/Android/data/com.codewithkael.simplecall/files/onnx_staging/
```

---

## 4. Run the on-device benchmark (Task 1d real output + Task 1e latency)

Put the phone in **airplane mode** first (certifies offline, Task 1e).

```powershell
.\gradlew :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.codewithkael.simplecall.ShieldBenchmarkTest"
```

Read the evidence (written to a file so logcat noise can't lose it):
```powershell
adb shell run-as com.codewithkael.simplecall cat files/shield_benchmark.txt
# or live: adb logcat -s ShieldBench
```

**Interpreting the result — no fake green checks:**
- **PASS** only when models are present *and* produce real, non-null output. You'll
  see `SPOOF real spoofProb = …`, `SPEAKER real speakerSim vs enrolled = …`, a
  self-cosine `> 0.98`, and `median/p90 ms` per 4s window.
- **SKIPPED** (assumption failed) when no models are on the device — this is *not*
  a pass. Push them (step 3) and re-run.
- **FAIL** when models are present but `ready()`/`score()`/`embed()` returns null —
  a genuinely broken model, surfaced instead of hidden.

Paste the `shield_benchmark.txt` contents back as the Task 1d/1e evidence.

---

## Evidence checklist for Task 1 (do not mark done without these)

- [ ] `export_ecapa_onnx.py` → `PASS` (cosine ≈ 1.0 fp32 & int8)
- [ ] `export_ast_spoof_onnx.py` → `PASS` (`size==1`, front-end MSE within tol)
- [ ] `asvspoof5_report.json` + the `[deck]` acc/F1/EER/AUC line
- [ ] `shield_benchmark.txt`: real `spoofProb`, real `speakerSim`, self-cosine>0.98
- [ ] `shield_benchmark.txt`: median/p90 ms per 4s window, captured in airplane mode

_(SMS round-trip/Task 6 is in §SMS below; two-device certification/Task 8 is
appended when that task lands.)_

---

## §ASR — real on-device transcription (Task 2, sherpa-onnx Whisper)

The transcriber is written and wired (`SherpaOnnxTranscriber` → Hilt
`AppModule.provideTranscriber` → `ShieldPipeline.transcriber`, set by
`ShieldService`/`ShieldViewModel`). It runs **100% offline**. Two things must be
present on your machine/device for it to activate — until then it is honest NoOp
(no fabricated transcript) and the app still builds and scores acoustic signals.

### A. Add the sherpa-onnx dependency

> ⚠️ I could not reach the network in this environment to pin the exact Maven
> coordinate/version — **confirm it on your machine.** Two ways, either works:
>
> **Maven Central (simplest if available)** — add to `app/build.gradle.kts`:
> ```kotlin
> implementation("com.k2-fsa:sherpa-onnx:1.10.+")   // confirm group/name/version on search.maven.org
> ```
> If that coordinate 404s, search Maven Central for "sherpa-onnx" and use the
> real group/artifact you find.
>
> **Prebuilt AAR (guaranteed, no coordinate guessing)** — download the official
> Android AAR from the k2-fsa/sherpa-onnx GitHub *Releases*, drop it in
> `app/app/libs/`, and:
> ```kotlin
> implementation(files("libs/sherpa-onnx.aar"))
> ```
> The AAR bundles the `com.k2fsa.sherpa.onnx.*` Kotlin API **and** the native
> `.so` (arm64-v8a etc.). Nothing else to configure.

The binding finds sherpa's classes at runtime via reflection and builds the
config with Kotlin `callBy` (so sherpa's data-class defaults fill everything we
don't set — tolerant of added fields across versions). **The one thing reflection
can't survive is a renamed class/param.** If the ASR test logs `NoOp` even with
the AAR + model present, swap to the direct API (bulletproof, compile-checked):

```kotlin
// replace SherpaOnnxTranscriber.buildRecognizer(...) body with direct calls:
import com.k2fsa.sherpa.onnx.*
val cfg = OfflineRecognizerConfig(
    featConfig  = FeatureConfig(sampleRate = 16000, featureDim = 80),
    modelConfig = OfflineModelConfig(
        whisper   = OfflineWhisperModelConfig(encoder = enc, decoder = dec),
        tokens    = tok, modelType = "whisper", numThreads = 2, provider = "cpu"),
    decodingMethod = "greedy_search")
val recognizer = OfflineRecognizer(config = cfg)
// transcribe(): val s = recognizer.createStream(); s.acceptWaveform(w, 16000)
//               recognizer.decode(s); val r = recognizer.getResult(s); r.text
```

If you enable R8/minify later, keep sherpa + reflection metadata:
```
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
```

### B. Get a multilingual Whisper model (covers Hindi + English)

sherpa-onnx ships pre-exported Whisper ONNX bundles (e.g. `whisper-tiny` or
`whisper-base`, multilingual). Download one, then **rename to the exact filenames
the loader expects** and push to `files/asr/`:

```powershell
# after unpacking sherpa's whisper bundle you'll have e.g.
#   tiny-encoder.int8.onnx  tiny-decoder.int8.onnx  tiny-tokens.txt
adb push tiny-encoder.int8.onnx /data/local/tmp/encoder.onnx
adb push tiny-decoder.int8.onnx /data/local/tmp/decoder.onnx
adb push tiny-tokens.txt        /data/local/tmp/tokens.txt
adb shell run-as com.codewithkael.simplecall mkdir -p files/asr
adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/encoder.onnx files/asr/
adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/decoder.onnx files/asr/
adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/tokens.txt   files/asr/
```

> Prefer `tiny` first — decode latency shows up in the ShieldBenchmark and in
> the per-4s-window path; `base` is more accurate but heavier. Whisper decodes
> each 4s window independently here; if latency is unacceptable, a streaming
> zipformer transducer is the lighter alternative (documented, not wired — would
> be a separate model, not a third ASR abstraction).

### C. Push Hindi + English clips and run the test (in airplane mode)

```powershell
adb shell mkdir -p /sdcard/Android/data/com.codewithkael.simplecall/files/asr_test
adb push en_sample.wav /sdcard/Android/data/com.codewithkael.simplecall/files/asr_test/
adb push hi_sample.wav /sdcard/Android/data/com.codewithkael.simplecall/files/asr_test/
# cv-hi (Common Voice Hindi) is a good Hindi source; any clear English clip works.

.\gradlew :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.codewithkael.simplecall.AsrTranscribeTest"

adb shell run-as com.codewithkael.simplecall cat files/asr_transcripts.txt
```

**Interpreting — no fake green checks:**
- **PASS** only when ASR is active *and* at least one clip yields a real
  transcript. The file logs per-clip `detected language(s)` + `transcript`, plus
  the live `network:` line (airplane mode ⇒ `activeNetwork=null` = offline proof).
- **SKIPPED** when ASR isn't active (no dep/model) or no clips were pushed — a
  skip, **not** a pass. The message says exactly what's missing.
- **FAIL** when ASR is active with real clips but produces no transcript — a
  genuinely broken model/backend, surfaced not hidden.

Paste `asr_transcripts.txt` (the Hindi + English transcripts and the `network:`
line) as the Task 2 evidence.

### ASR evidence checklist (Task 2)

- [ ] `asr_transcripts.txt`: a real **English** transcript from a real clip
- [ ] `asr_transcripts.txt`: a real **Hindi** transcript (Devanagari or romanized)
- [ ] `network:` line shows `activeNetwork=null` (device in airplane mode) = offline
- [ ] logcat `SherpaASR: sherpa-onnx ASR ACTIVE …` (confirms the real backend, not NoOp)

---

## §SMS — real device SMS + notification scam round-trip (Task 6)

Unlike the on-device Shield, **message/notification scanning is server-scored**:
the phone taps the text and posts it to the antAI server, which runs the real
agentic pipeline and returns the verdict. So this gate certifies the *round-trip*,
not a local model.

### What the static regression check already confirmed (read, not run)

The whole path is wired end-to-end and — importantly — **cannot fabricate a
"safe" verdict**:

```
incoming SMS  ─► SmsReceiver (manifest <receiver>, RECEIVE_SMS + BROADCAST_SMS guard)
other-app notif ─► AntaiNotificationListenerService (BIND_NOTIFICATION_LISTENER_SERVICE)
                        │  (notif path first runs the client NotificationTriage gate)
                        ▼
        MessagingEntryPoint.messagesRepository()
          .onSmsReceived(...) / .onNotificationPosted(...)
                        │   stores the row as analysisPending = true
                        ▼
        AntaiRestClient.notifyExternal(source, sender, text)
          → POST /api/notify/external   (Bearer token, JSON {source,sender,text})
                        ▼
        server: should_analyse(text)  ← the twin of the client-side gate
          • gated out  → {ingested:false, reason, risk_score:0.0, verdict:null}
          • relevant   → dispatch_message() runs the REAL graph
                       → {ingested:true, matched[], risk_score, verdict{...}}
                        ▼
        repo maps verdict → riskScore/verdict/why/action/scamType, analysisPending=false
          on network failure → analysisPending=false and NO score is set (not "safe")
```

Verified coherent: the client request `{source,sender,text}` matches the server's
`ExternalNotify` model exactly; the response `verdict` object fields
(`band,risk_score,verdict,why,action,scam_type`) match `VerdictPayload.from(...)`.
The client `NotificationTriage` and the server `triage.should_analyse` are kept in
sync on purpose — the client gate saves a needless network call, the server gate is
the backstop for older clients.

> **Honest-floor disclosure (per the ground rule):** a *benign* message that the
> triage gate rejects comes back `risk_score: 0.0, verdict: null, ingested:false`.
> That `0.0` is the real output of the deterministic gate ("no scam-relevant
> content"), **not** a model that ran and scored zero, and **not** a hardcoded floor
> applied to analysed content. A flagged message's score always comes from the
> live pipeline. The smoke test below exercises *both* so the distinction is visible.

### Prereqs

- antAI server running and reachable from the phone at the host set on the calls
  screen (the messaging client reads the **same** host pref).
- App signed in (the endpoint is `Bearer`-authed; an unauth'd call is a real 401,
  not a silent skip).
- Runtime grants: **SMS** (`RECEIVE_SMS`/`READ_SMS`) allowed, and the
  **Notification access** toggle enabled for "antAI Notification Guard" in Android
  Settings ▸ Notifications ▸ Notification access (a manifest entry alone does **not**
  bind the listener — the user must enable it).

### Step 0 — prove the server's text models are actually loaded (anti-floor check)

If the scam/urgency/intent engines aren't loaded server-side, the pipeline can
return `risk_score:0` that *looks* like "safe" — exactly the failure mode the
ground rule forbids shipping silently. Check first:

```powershell
curl http://<server-host>:<port>/api/debug/models
# want ready:true for: scam_pattern, urgency, intent  (text path)
```

If any read `ready:false`, fix that before trusting any verdict below.

### Step A — SMS round-trip (a known scam + a benign control)

Watch the phone while you send it two texts from another handset:

```powershell
adb logcat -c
adb logcat -s AntaiRest:* MsgRepo:*        # POST + any HTTP error surface here
```

1. **Scam SMS** (passes both gates — strong terms + link + OTP):
   `Your bank account will be blocked. Complete KYC now http://bit.ly/xy2 and share the OTP.`
2. **Benign control** (should be gated out honestly):
   `hi, are we still on for dinner tonight?`

Expected:
- Scam text → the in-app thread row flips from *analysing…* to a **flagged**
  verdict with a non-zero risk score, `why`, and a `scam_type`. Server log shows a
  `dispatch_message` run (not a `notify/external skipped` line).
- Benign text → server log shows `notify/external skipped (low-content|no-scam-signal)`;
  the row settles with **no** flag and score 0.0 (the disclosed gate path, not a
  model verdict).

### Step B — notification round-trip (other-app scam)

With Notification access enabled, send the phone a **WhatsApp/Telegram** message
with the same scam wording from Step A. It should surface in the app's
notification-guard list with a real flagged verdict. A "Now playing" / music or a
plain "hi" must **not** appear flagged (client `NotificationTriage` drops it before
it ever leaves the device — confirm with `adb logcat -s NotifTriage:* MsgRepo:*`).

### Evidence to paste back (Task 6)

- [ ] `/api/debug/models` line showing `scam_pattern/urgency/intent` `ready:true`
- [ ] logcat `AntaiRest … POST /api/notify/external` for the scam SMS (no HTTP error)
- [ ] screenshot of the **flagged** SMS row: real risk score + why + scam_type
- [ ] server log: `dispatch_message` for the scam text **and** a
      `notify/external skipped (...)` for the benign control (both behaviours shown)
- [ ] notification path: one flagged other-app message + one dropped benign one

**Interpreting — no fake green checks:** a flag is real only if `/api/debug/models`
showed the text models ready *and* the score/why came back from `dispatch_message`.
A `risk_score:0.0` is a **pass only** for the benign control (gated); a `0.0` on the
scam text means the models aren't loaded or the gate misfired — investigate, don't
record it as "safe."

---

## §CALL — two-device live-call certification, per-chunk scores (Task 8)

The call itself is **pure P2P WebRTC** (Node signaling on `:3007`); antAI is *not*
in the media path. Detection runs on a **parallel analysis tap**: while a call is
up, each phone opens a second, **send-only** PeerConnection that mirrors its own
mic/cam to the antAI server (`ws://<host>:8765/ws/tap`). The server decodes it,
runs the real pipeline (Silero VAD → faster-whisper ASR → voice/video-deepfake +
scam/urgency/intent → LangGraph → local LLM), and pushes live
`transcript.update` / `signals.update` / `verdict.update` / `guidance.update`
back to **both** phones' in-call AI window.

### What the static regression check confirmed (read, not run)

- **Client tap is wired in the pivoted P2P app:** `AiTapEngine` builds the
  send-only PC (`WebRTCFactory.createTapPeerConnection`, no extra mic/cam opened),
  `AntaiClient` drives the `/ws/tap` control socket (`tap.start {peer,kind,offer}`
  → `tap.answer`, trickle `tap.ice`), started from `MainViewModel` via
  `Constants.getAntaiTapUrl(host, USER_ID)` (`ws://<host>:8765/ws/tap?user=…`).
- **Server produces a per-segment verdict:** `TapCall` feeds `AudioIngestor` +
  `FrameSampler` into `SessionRunner._evaluate_locked`, which runs the graph and
  sets `risk`/`band`/`signals` for every segment.
- **Gap found & fixed for this task:** the client only receives `verdict.update`
  when the band changes or risk moves ≥8 (throttled/gated) — so there was **no**
  reliable per-chunk record to certify against. Added one unconditional server log
  per evaluation (see below). Every value in it is live model output; a signal no
  engine produced prints `n/a`, never a fabricated `0`.

### Step 0 — anti-floor readiness check (do this first)

```powershell
curl http://<host>:8765/api/debug/models
# want ready:true for: asr, voice_deepfake, scam_pattern, urgency, intent
# (video_deepfake/lipsync too if you certify a VIDEO call)
```

If `voice_deepfake` or `asr` read `ready:false`, the per-chunk log below will show
`voice_df=n/a` / empty transcript — that's "not loaded", **not** "call is safe".
Fix loading before trusting the run. `debug/models` also reports
`can_detect_cloned_voice` and the Velma/local backend state — confirm it's true if
you're certifying synthetic-voice detection.

### Step 1 — pre-flight without phones (simulated two-phone tap)

Proves the tap + full pipeline end-to-end from two simulated aiortc "phones" before
you involve real devices:

```powershell
cd server
conda run -n antai-server python scripts/test_tap_flow.py
# PASS = both sim phones get tap.started; "transcript.update observed" = ASR live
```

Watch the server console at the same time — you should see the new **per-chunk**
lines (this is the Task 8 evidence format):

```
tap-eval session=tap:call:<id> audio_seg=3 risk=41.0 band=verify decision=log \
         voice_df=0.72 video_df=n/a scam=0.55 urgency=48.00 transcript_len=2 asr_fail=0
```

### Step 2 — the real two-device call

Two phones (A, B) on the same Wi-Fi as the laptop; both signed into the calling
app with the server host set to the laptop's LAN IPv4 (home screen field). Start
the antAI Python server (`:8765`) and the Node signaling server (`:3007`).

```powershell
# capture only the per-chunk certification stream from the antAI server:
#   (run the server so its stdout/log is visible, then filter)
#   PowerShell:  <server-log> | Select-String "tap-eval|verdict.update|alert "
```

1. **A calls B** (voice or video). Confirm both in-call AI windows come alive
   (transcript + "models running" signals) — that means both taps attached.
2. Near one phone, play a **known scam script** ("your account is blocked, share
   the OTP, install AnyDesk…") and, if certifying AI-voice, a **synthetic/cloned
   voice** sample. Speak a benign stretch too, for contrast.
3. Read the server `tap-eval` stream: risk/band should **track the content** —
   climb on the scam/synthetic segments (`voice_df` high, `scam`/`urgency` up,
   `band=verify|critical`) and stay low on benign speech. Each line is one real
   pipeline evaluation.
4. Confirm the phones actually receive the push: the in-call AI window shows a
   live verdict/guidance and (for deepfake margins) the pause-and-warn alert.

### Evidence to paste back (Task 8)

- [ ] `/api/debug/models`: `asr` + `voice_deepfake` (+ `video_deepfake` for video)
      `ready:true`, and `can_detect_cloned_voice:true`
- [ ] `scripts/test_tap_flow.py` output: both phones `tap.started` +
      `transcript.update observed`
- [ ] a **`tap-eval` server-log excerpt** from the real call: several consecutive
      chunks whose `risk`/`voice_df`/`scam`/`urgency` **change with the content**
      (the per-chunk scores this task asks for)
- [ ] screenshot of a phone's in-call AI window showing a live verdict/guidance
      (proves the push reached the device, not just the server)

**Interpreting — no fake green checks:**
- **PASS** only when `tap-eval` shows **real, non-`n/a`** scores that move with the
  spoken content *and* at least one `verdict.update` (or `deepfake.alert`) reaches a
  phone.
- **Not a pass:** a `tap-eval` stream where `voice_df` is always `n/a` or
  `transcript_len` never grows — the tap connected but a model isn't scoring
  (revisit Step 0); or `tap.started` never arrives — the tap PC/socket isn't
  reaching `:8765` (firewall/host), so **no** analysis is happening. Neither is a
  green check; fix the cause, don't record a low risk as "safe".

---

## §EXTENSION — continuous page-text scanning (Task 9)

The Chrome extension can **continuously** scan a tab's text — chat messages, live
captions, incoming mail — and route each new snippet to the antAI server for a
real agentic verdict. The scanner (`content/textscan.js`) scores **nothing**
locally and fabricates **nothing**: the only client-side decision is *which* text
to send, made by the pure, dedupe/rate-limit gate in `content/textscan_core.js`.
Every risk number comes from the server via `POST /api/notify/external`; a scam
verdict raises a desktop notification on the shared 15 s cooldown. This keeps the
extension strictly within the **text / audio-tap-only** scope.

### What the automated tests already confirm

- **Pure-logic gate** (`extension/test/textscan_test.mjs`, no browser/server):
  loads the shipped `textscan_core.js` in a `node:vm` sandbox and asserts
  normalization, candidacy (min-length, letters-required, Hindi unicode),
  clamping, exact + whitespace-variant dedupe, the 60 s rate limit and its reset,
  and dedupe-window eviction. **Ran here: `22 passed, 0 failed`.**
- **Server contract** (`extension/test/browser_test.mjs`, live Chrome + server):
  the continuous scanner reuses the `antai-scan-text` route, which is already
  covered — benign text gated (`ingested:false`), scam text ingested with a
  numeric `risk_score`. New assertions confirm a scam scan populates
  `state.latestText` (the continuous-verdict surface) and the
  `antai-textscan-toggle` handler is registered.

### Step 0 — prereqs

```bash
# server up (text models loaded — same anti-floor check as §SMS):
curl -s http://localhost:8765/api/debug/models | python -m json.tool
#   want ready:true for: scam_pattern, urgency, intent
```

- Load unpacked: `chrome://extensions` → Developer mode → Load unpacked → `extension/`.
- **Sign in**: extension **Options → Account** → phone-OTP (dev mode returns the
  code in the response). Without a token the scanner refuses to start and the
  popup says "Sign in first" — an honest gate, not a silent no-op.

### Step 1 — run the pure-logic gate (no server needed)

```bash
cd extension
node test/textscan_test.mjs        # expect: "22 passed, 0 failed", exit 0
```

### Step 2 — the real live-browser scan (the gate that matters)

1. Open a page with **arriving** text — a web chat (WhatsApp/Telegram web), a Meet
   call with live captions on, or a webmail inbox.
2. Click the antAI icon → **Watch page text (live)**. The button flips to
   "Stop watching page text".
3. Open that page's **DevTools console**. As new text appears you should see one
   line per snippet:
   - scam-like: `[antAI text-scan] risk=<n> band=<verify|critical> type=<…> · <clip>`
   - benign, keyword-gated: `[antAI text-scan] ✓ not scam-related (gated) · <clip>`
4. Trigger a **known scam line** (paste/receive "URGENT: your account will be
   blocked, share the OTP now, install AnyDesk"). Confirm a **desktop
   notification** fires (respecting the 15 s cooldown) and the popup gauge / HUD
   reflect the elevated risk from `state.latestText`.
5. Confirm **privacy scope**: type into the page's own compose box — those
   keystrokes must **not** produce scan lines (editable fields are skipped), and
   duplicate/again-rendered messages are not re-sent (dedupe).

### Step 3 (optional) — full browser contract test

```bash
cd extension
# Chrome started with --remote-debugging-port=9222 and the extension loaded;
# server on localhost:8765. ANTAI_EXT_ID=<your unpacked id> if it differs.
node test/browser_test.mjs
```

### Evidence to paste back (Task 9)

- [ ] `node test/textscan_test.mjs` → `22 passed, 0 failed`
- [ ] a **console excerpt** from a real page: several `[antAI text-scan] risk=…`
      lines whose risk/band **track the content** (scam lines elevated, benign
      lines gated) — the continuous per-snippet scores this task asks for
- [ ] a screenshot of the **desktop notification** raised by a scam line
- [ ] confirmation that typing in a compose box produced **no** scan lines
      (privacy scope) and repeated messages were **not** re-sent (dedupe)

**Interpreting — no fake green checks:**
- **PASS** only when real server verdicts stream to the console as new page text
  arrives *and* a scam line raises a notification. The risk numbers are the
  server's; the client only decided to send the text.
- **Not a pass:** console lines that all say "sent, no verdict: unreachable" (the
  server host/token is wrong — fix Options), or **no** lines at all after new text
  arrives (scanner not injected — re-toggle; injection needs the popup gesture on
  that tab). A gated benign line (`ingested:false`) is **not** a failure and **not**
  a fabricated safe verdict — it is the server's honest "no scam signal".

**Disclosure (per the hard rule):** the scanner applies exactly one client-side
heuristic — the send/skip gate (min length ≥12 chars, letters-or-digits required,
whitespace-normalized dedupe, ≤20 snippets/min). It changes *whether* text is
scanned, never the *score*. No risk value, band, or verdict is ever computed or
defaulted on the client; an unreachable server yields "no verdict", never a 0.

---

## §DEMO-FIX — false-positive / frozen-overlay root-cause fix + UI bugs (Phase 0/1)

This is a **separate track** from the EXECUTE build above. It fixes the demo-breaker:
a casual WhatsApp voice note produced a **CRITICAL 100/100** (`identity_mismatch` +
`voice_deepfake`) false positive and the floating overlay looked **frozen**. The
code fixes are complete and statically audited in the sandbox; **nothing here is
"working" until you run the on-device gate and paste its file.** Kotlin was not
compiled in the sandbox — rebuild the APK first (JDK 21, `.\gradlew` — see the
stale-build trap).

### What was changed (so you know what to look for)

Phase 0 — kill the false positive at its root, in `ShieldPipeline`/`FusionEngine`:
- **0.A** inference runs **off the mic loop** (`ShieldService` consumeJob on
  `Dispatchers.Default`, drop-oldest backlog) and logs **one line per window** under
  tag `ShieldLive` — the honest proof the overlay is live, not frozen.
- **0.B** `identity_mismatch` fires **only when a specific contact was claimed**
  (`selectedPhoneHash != null`). An unknown caller reports a best-match cosine
  *informationally* (no risk). This alone removes the WhatsApp CRITICAL.
- **0.C** text scoring uses a **decaying ~12s window** (`RECENT_WINDOW_COUNT=3`), not
  the whole session — one scam phrase no longer alarms every later window forever.
- **0.C'** acoustic engines are gated by an **RMS silence gate** (`SILENCE_RMS`) and a
  **speech-likeness gate** (`isSpeechLike`, frame-RMS variance) so a tone/hum/codec
  washout can't score `spoof=0.9999`; a codec/replay similarity floor
  (`REPLAY_SIM_FLOOR=0.45`) stops legit compressed audio reading as a mismatch.

Phase 1 — 5 UI bugs:
- **Bug 1** offline messages score **locally** via `TextEngines` when the server is
  unreachable / not signed in, tagged **"on-device"** (this session's fix —
  `MessagesRepository.localScore`, `ChatMessage.onDevice`, thread + list labels).
- **Bug 2** model-status surface reflects **real** `ModelManager.modelStatus()`
  (present/absent per file), never a fake "ready".
- **Bug 3** live verdicts flow into **Incident History**.
- **Bug 4** overlay pill **sizes to content** (no clipped/frozen-looking pill); the
  in-app Compose preview renders it.
- **Bug 5** arming the Shield sets protection mode **ON_DEVICE** and socket
  close/error no longer stomps it back to OFFLINE (`MainViewModel`, guarded by
  `ShieldArmedState.armed.value`).

### Gate A — the 5-scenario regression (the false-positive gate), ON DEVICE

`ShieldScenarioTest` feeds the **real** `evaluate()` (same call the armed mic loop
uses) deterministic labeled inputs and **asserts each expected band**, so a
regression back to "CRITICAL on casual chat" **fails the build**, not the demo.

```powershell
# airplane mode optional here (no network needed); models improve coverage (see skips)
.\gradlew :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.codewithkael.simplecall.ShieldScenarioTest"
adb shell run-as com.codewithkael.simplecall cat files/shield_scenarios.txt
# live view: adb logcat -s ShieldScenario
# optional real clips (take precedence over synthetic stand-ins): push WAVs to
#   /sdcard/Android/data/com.codewithkael.simplecall/files/scenario_clips/
#   named casual.wav / enrolled_benign.wav / scam.wav / clone.wav
```

Scenarios & what each asserts:
- **S1 casual, no contact selected, no scam text → band ≠ critical** (the exact
  original bug: casual WhatsApp read CRITICAL). This is the headline assertion.
- **S2 enrolled contact, matching voice, benign → `identity_mismatch` absent.**
- **S3 same voice, scam transcript → risk rises above passive from content alone.**
- **S4 clone contract (`voiceDeepfake=0.999`+`identityMismatch=true`) → band ≠ passive.**
- **S5 two consecutive windows → different verdicts** (the frozen-overlay proof).

**Interpreting — no fake green checks:**
- **PASS** only when the test process reports success **and** `shield_scenarios.txt`
  shows S1 non-critical and S5's two windows differing, with real `risk/band/spoof`
  values printed.
- **Honest SKIP, not a pass:** if the **ECAPA speaker model is absent**, the file
  logs `SPEAKER model absent — scenarios 2/3/4 … SKIP (honest skip, not pass)` and
  S1/S5 still run. Push `ecapa_tdnn.int8.onnx` (RUNBOOK §3) to cover S2–S4.
- **FAIL** = a real regression (e.g. S1 critical again). Do not edit the test to pass;
  fix the pipeline.

### Gate B — live proof during a real WhatsApp call (overlay not frozen)

The static test can't prove the *live* loop. On a real call, capture the per-window
stream the service logs:

```powershell
adb logcat -c
adb logcat -s ShieldLive ShieldService
# arm the Shield, take/receive a casual WhatsApp voice call, talk ~20s
```
Expected: a **new `ShieldLive` line every ~4s** whose `risk`/`band`/`transcript`
**change over time** (not one stuck value), and for a casual call the band stays
**passive/verify**, never CRITICAL. A `SLOW … ms` line only if a window took >4s.

### Gate C — the 5 UI bugs (manual, on device; screenshot each)

- **Bug 1 (offline scoring):** turn on **airplane mode** (or sign out), then send the
  phone the scam SMS from §SMS (`… account will be blocked … share the OTP …`) and a
  benign control (`are we still on for dinner?`). Expected: scam row shows a
  **CAUTION/CRITICAL badge with an "on-device" label** (thread bubble + list row
  suffix); benign row shows **no** flag. logcat proof:
  `adb logcat -s MsgRepo` → `notify/external (sms) failed — scoring on-device`. The
  score is the **real** `TextEngines` heuristic output — if a genuine scam reads
  below the CAUTION line (40/100) the heuristic needs tuning; don't fake it.
- **Bug 2 (honest model status):** open the protection/status surface with **no**
  models pushed → each model reads **absent**; push them (RUNBOOK §3) → reads
  **present**. It must mirror the files, never claim ready when absent.
- **Bug 3 (incident history):** after Gate A/B produced verdicts, open Incident
  History → the live events are listed.
- **Bug 4 (overlay pill):** armed overlay pill hugs its text at several risk levels
  (no clipped/ellipsized/frozen-looking pill); the in-app preview renders it.
- **Bug 5 (ON_DEVICE when armed):** with the server **unreachable**, arm the Shield →
  the protection chip reads **ON_DEVICE** (not OFFLINE), and stays ON_DEVICE across a
  socket drop.

### Evidence to paste back (Phase 0/1)

- [ ] `shield_scenarios.txt`: S1 `band` non-critical, S5 two differing windows, real
      `risk/spoof` values (and the honest SKIP line if the speaker model was absent)
- [ ] the `connectedDebugAndroidTest` result line for `ShieldScenarioTest` (pass)
- [ ] a `ShieldLive` logcat excerpt from a **real casual WhatsApp call**: consecutive
      ~4s windows whose values change and whose band stays non-critical
- [ ] Bug 1: screenshot of an **on-device**-tagged scam SMS + a benign one unflagged,
      plus the `MsgRepo … scoring on-device` logcat line
- [ ] Bugs 2–5: one screenshot each (honest model status, incident history entry,
      overlay pill, ON_DEVICE chip while armed with server down)

**Definition of done (Phase 0/1):** the headline is Gate A's **S1 non-critical** plus
Gate B's **moving `ShieldLive` stream on a real casual call** — together they certify
the false-positive + frozen-overlay demo-breaker is actually gone on hardware, not
just in the source. Everything above stays open until those two are pasted.

