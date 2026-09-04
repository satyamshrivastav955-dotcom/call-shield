# Real-Time Scam & Deepfake Defense — Native Calling/Messaging App + Server Architecture (v2)

**Problem ID-IHNG6 · Track 002 - NextGenAI · "Real-Time Scam and Impersonation Defense"**

## 0. What Changed From v1, and Why

The original design assumed the app could tap into calls/video calls/messages happening in *other* apps (Phone app, WhatsApp, etc.) using Android system hooks (`CallScreeningService`, `MediaProjection`, `NotificationListenerService`). In practice this doesn't hold up: third-party calls are increasingly E2E-encrypted and off-limits to any app that isn't the OS dialer itself, and OEM/Play Store restrictions on those APIs make reliable, real-time raw audio/video access to someone else's call effectively impossible without root.

**The fix: stop trying to tap other apps' calls — become the calling app.** This version turns the product into its own first-party VoIP calling + video calling + messaging app (the "normal app" layer — contacts, profiles, numbers, chat, call log), so every voice call, video call, and message the protected user has *through this app* is natively available to the pipeline as it happens — no interception hack required. All detection logic, models, the orchestration graph, and the local LLM from v1 are preserved exactly; they simply move from on-device to a backend server, which also removes v1's mobile-inference/battery ceiling and its LangGraph-on-Android workaround.

The trade-off this introduces is real and is addressed head-on in Section 8: the server needs plaintext audio/video/text to run inference, so this app cannot offer true end-to-end encryption on scanned calls, and it now requires two people (the vulnerable user *and* whoever they're talking to) to be on the platform for a given call to be protected — same shape as v1's "trusted circle," just extended to the whole app rather than only the verification step.

Every feature from the v1 document is preserved below: VAD, SSL deepfake voice detection, speaker-embedding voiceprint match, streaming ASR, face detection, video deepfake classifier, lip-sync check, scam-pattern text classifier, behavioral-deviation model, urgency/pressure scorer, request/intent classifier, the LangGraph orchestration topology, the Freeze/Intercept mechanism, Verify-With-Trusted-Contact, the Collective Flagging Database, and the plain-language output contract. Nothing was dropped — only *where it runs* and *how it gets its input* changed.

---

## 1. Design Constraints (v2)

| Constraint | Implication |
|---|---|
| Protection = calls/messages made **through this app** | The app must be a real, usable VoIP + video-call + messaging client (contacts, numbers, profiles, chat, call log) — not just a scanner. Adoption requires the vulnerable user *and* their trusted circle to install it, same as v1's trusted-circle idea, now app-wide. |
| Detection moves server-side | No more TFLite/GGUF/mobile-quantization ceiling. Models can run at full or lightly-quantized precision on GPU. This also removes v1's LangGraph-on-Android problem — the orchestration graph now runs as native Python LangGraph on the server, no JVM port or Chaquopy bridge needed. |
| Real-time budget = network + inference, not just on-device compute | The <300–500ms streaming window from v1 still applies, but now spans client → media relay → inference → fusion → client. This pushes toward a media relay and inference stack that are co-located (same region/DC), not a generic multi-hop cloud path. |
| Media must be readable by the server to be scored | The server cannot run deepfake/voice/text detection on ciphertext it can't decrypt. This means calls scanned by this app are **not** end-to-end encrypted in the Signal/WhatsApp sense — this must be disclosed plainly during onboarding (Section 8). |
| Elderly / vulnerable end users | Unchanged from v1: output is always a single plain-language verdict + action, never a raw score. |
| "Local LLM" now means self-hosted, not on-device | The reasoning LLM runs on infrastructure the team controls (not a third-party cloud API), for the same privacy reason v1 avoided sending data off-device — it's just now "off-phone, on our server" rather than "on-phone." |

---

## 2. High-Level Architecture — Client / Server Split

```
┌───────────────────────────────────────┐            ┌─────────────────────────────────────────────┐
│      KOTLIN CLIENT (Android app)        │            │                BACKEND SERVER                  │
├───────────────────────────────────────┤            ├─────────────────────────────────────────────┤
│ L6 — UI / UX                            │            │ L4 — SIGNALING + MEDIA RELAY (SFU/TURN)         │
│   Call & video-call screens · Chat UI   │            │   WebRTC signaling over WebSocket · SFU relays   │
│   Contacts & profiles · Verdict card    │◄──────────►│   audio/video between callers · forks a copy      │
│   Freeze/confirm prompts · Post-call    │  WebRTC    │   of the stream into the Ingestion Tap             │
│   report screen                         │  media +   ├─────────────────────────────────────────────┤
├───────────────────────────────────────┤  WebSocket │ L3 — AGENTIC ORCHESTRATION (native LangGraph)    │
│ L5 — NATIVE COMMS ENGINE (NEW)          │  control   │   Router → parallel detector nodes → Fusion →    │
│   VoIP calling engine · Video calling   │  channel   │   conditional branch (log / verify / freeze) →   │
│   engine · In-app messaging · Contact / │            │   LLM Reasoning → END                            │
│   number / profile directory            │            ├─────────────────────────────────────────────┤
├───────────────────────────────────────┤            │ L2 — INFERENCE ENGINES (server, GPU)             │
│ L1(client) — LOCAL CACHE                │            │   Voice: VAD / SSL-deepfake / speaker-embedding /│
│   Recent verdicts · trusted-circle list │            │   streaming ASR · Video: face / deepfake /       │
│   · offline send queue · minimal        │            │   lip-sync · Text: scam-pattern / behavioral ·   │
│   contact cache for fast identity checks│            │   Urgency/pressure scorer · Request/intent ·     │
└───────────────────────────────────────┘            │   Local self-hosted LLM (reasoning + reports)    │
                                                          ├─────────────────────────────────────────────┤
                                                          │ L1(server) — STORAGE                            │
                                                          │   Voiceprints · behavioral profiles · risk logs │
                                                          │   · flag cache (encrypted at rest)              │
                                                          ├─────────────────────────────────────────────┤
                                                          │ L0 — COLLECTIVE + PUSH SERVICES                 │
                                                          │   Collective flag aggregation · FCM push for    │
                                                          │   trusted-contact verify pings                  │
                                                          └─────────────────────────────────────────────┘
```

---

## 3. Client App — the "Normal App" Layer (Kotlin / Android)

This is the part that makes the security features possible at all: a real calling, video-calling, and messaging app that people actually use, so their communication naturally flows through a pipeline the team controls.

### 3.1 VoIP Calling & Video Calling Engine
- Built on WebRTC (Android WebRTC SDK, or a self-hosted SFU client such as LiveKit/mediasoup) — handles mic/camera capture, codec negotiation, and rendering.
- Signaling (offer/answer/ICE candidates) goes over a WebSocket to the server's signaling service (Section 4.1).
- Every call and video call is relayed through the server's SFU rather than pure device-to-device, which is what makes server-side scanning possible without a second upload from the client (Section 4.1).

### 3.2 In-App Messaging
- Standard client-server chat: message composed → sent via WebSocket/REST → delivered to recipient, with the same request also feeding the text ingestion path (Section 4.2) at send-time.
- Message history cached locally (Room) for offline reading; new sends queue and flush when connectivity returns.

### 3.3 Contacts, Numbers & Profiles
- Phone-number-based signup (SMS OTP), matching the familiar WhatsApp/Signal pattern.
- Profile: display name, avatar, and a **relationship tag** (e.g. "Mom," "Bank," "Son") — this tag is what feeds the Identity-Claim node (Section 4.5) and the behavioral-deviation model (Section 4.3.3).
- Optional import/match against device `ContactsContract` to find which existing contacts are already on the platform.
- Trusted-circle linking: mutual-consent flow where family members explicitly link accounts (unchanged in spirit from v1 Section 6, now just part of normal onboarding rather than a bolt-on).
- Voiceprint enrollment: a short guided recording during onboarding for the protected user and each trusted contact, which seeds the speaker-embedding store used in Section 4.3.1.

### 3.4 Local Cache & Offline Behavior
- Recent verdict history, trusted-circle roster, and a minimal contact cache are kept locally so identity checks and the verdict UI still work through brief connectivity gaps.
- Nothing model-heavy runs on-device in this version — the client's job is capture, render, cache, and display, not inference.

### 3.5 Real-Time Overlay UI
- Verdict card, "Verify with [contact]" button, freeze/confirm prompts, and the post-call report screen — same UI contract as v1 Section 9, now driven by messages arriving on the WebSocket control channel from the server instead of a local model.

---

## 4. Server Architecture

### 4.1 Signaling + Media Relay (SFU)
- A WebSocket signaling service negotiates each call/video-call session (offer/answer/ICE) between two clients.
- A self-hosted SFU (e.g. LiveKit or mediasoup) relays the actual audio/video between participants — because the media already passes through a server component for NAT traversal and relaying, no extra upload is needed for detection: the SFU forks a read-only copy of each track into the Ingestion Tap below.
- Alternative for teams that want to keep calls peer-to-peer for latency/cost reasons: each client independently streams a secondary "detection feed" to the server alongside the direct P2P call. This preserves call quality but roughly doubles each client's upload for the duration of the call and still ends the true end-to-end-encryption claim, so Section 4.1's default (server-relayed) is the simpler and more honest option to build first.

### 4.2 Ingestion Layer (replaces v1's Android system hooks)

| Source | Server mechanism | What's captured |
|---|---|---|
| Voice calls | Audio track forked from the SFU | Raw audio stream, both participants' numbers/profiles from the call session |
| Video calls | Video track forked from the SFU, sampled at 5–8 fps | Frame stream (reduced fps, matching v1's CV pipeline) |
| Messages | Message payload tapped at send-time from the chat service | Text content, sender, timestamp |
| Contact/behavior history | Server-side interaction log, keyed by linked account | Baseline "how this person normally talks/texts," replacing v1's local-only history |

Because all of this now originates from the app's own call/video-call/chat infrastructure rather than OS-level snooping, the consent story is simpler: users explicitly agree, at signup, that calls and messages made *through this app* are scanned for scam protection. This still needs a clear onboarding consent screen — arguably more important now, since both parties on a call are affected, not just the device owner (see Section 8).

### 4.3 Inference Engines (server-side, GPU)

These are the same six engines from v1, unchanged in what they do — only where they run and their size constraints change.

**4.3.1 Voice Pipeline**
```
Raw call audio (streaming, 16kHz, from Ingestion Tap)
        │
        ▼
 VAD (Voice Activity Detection)
        │
        ├─► SSL Deepfake/Synthetic-Voice Detector
        │     (wav2vec2/WavLM-style backbone; can now run at
        │      higher precision than v1's mobile-quantized version)
        │
        ├─► Speaker Embedding Extractor (ECAPA-TDNN / titanet)
        │     → compares live embedding against the stored voiceprint
        │       of the contact the caller's profile claims to be
        │     → cosine similarity = "is this really Mom's voice?"
        │
        └─► Streaming ASR (Whisper, server-sized model)
              → live transcript feeds the NLP/LLM node
```

**4.3.2 Video-Call Pipeline**
```
Video frames (5–8 fps, from Ingestion Tap)
        │
        ▼
 Face Detector (MediaPipe Face Mesh / BlazeFace)
        │
        ▼
 Deepfake Classifier (frame-level + temporal-consistency check
 across the frame buffer, to catch flicker/artifact patterns)
        │
        ▼
 Lip-sync consistency check (audio-visual sync score — mismatched
 lip movement vs. audio is a strong deepfake signal)
```

**4.3.3 Text / Message Pipeline**
```
Message text (from Ingestion Tap)
        │
        ▼
 Scam-pattern classifier (transformer fine-tuned on scam-script
 corpora: "your son is in an accident," OTP-sharing, urgent-
 payment language, etc.)
        │
        ▼
 Behavioral-deviation model (compares against the sender's own
 historical writing style/topics from server-side history — flags
 if the contact tagged "Dad" suddenly writes in a different
 register asking for money)
```

**4.3.4 Urgency / Emotional-Pressure Scorer**
```
Live transcript / message text
        │
        ▼
 Pattern + classifier model (pressure phrases: "act now,"
 "don't tell anyone," time-constraint and threat language,
 secrecy requests)
        │
        ▼
 urgency_score: 0–100 (kept as its own signal, not folded into
 the scam-pattern score, so Fusion can cite it by name)
```

**4.3.5 Request / Intent Classifier**
```
Live transcript / message text
        │
        ▼
 Intent classifier: does this contain an actionable ASK?
 (money transfer, OTP/credential share, gift-card purchase,
 remote-access install, urgent link click)
        │
        ▼
 request_detected: bool, request_type: enum
 (money | otp | credential | remote-access | link | none)
```

### 4.4 Local (Self-Hosted) LLM — Reasoning + "Afterward" Report Node
- A self-hosted LLM (e.g. Llama-3-8B/70B or Qwen2.5, served via vLLM/TGI/Ollama on the team's own infrastructure) — "local" in the sense of *not* calling an external cloud LLM API, preserving the privacy intent of v1's on-device LLM.
- Real-time role, unchanged from v1: takes the outputs of 4.3.1–4.3.5 (scores, transcript, flags) and produces a plain-language verdict, classifies scam type, and generates the actionable next step — never raw scores to the user.
- **New — post-call/post-chat report ("give information afterwards"):** once a call or conversation ends, the LLM generates a fuller incident report: a timeline of what was flagged and when, which signals fired and why, the scam-type classification, and recommended follow-up (e.g. "report this number to your bank," "block this contact"). This report is saved server-side, pushed to the client's history, and can optionally be shared with the trusted circle.

### 4.5 Agentic Orchestration Graph (native LangGraph)

Same topology as v1 — reproduced here unchanged, now runnable as real Python LangGraph since it lives on the server:

```
                        ┌─────────────┐
                        │   START     │
                        │ (call/video/│
                        │  msg event) │
                        └──────┬──────┘
                               ▼
                     ┌───────────────────┐
                     │  Router Node       │
                     │ (classify event    │
                     │  type: voice/video/│
                     │  text)             │
                     └─────┬─────┬────────┘
             ┌─────────────┘     └─────────────┐
             ▼                                  ▼
     ┌───────────────┐                 ┌────────────────┐
     │ Voice/Video    │                 │ Text/Message    │
     │ Detector Nodes │                 │ Detector Node   │
     │ (4.3.1 / 4.3.2)│                 │ (4.3.3)         │
     └───────┬────────┘                 └────────┬────────┘
             │                                    │
             ▼                                    ▼
     ┌──────────────────────┐          ┌──────────────────────┐
     │ Identity-Claim Node    │          │ Collective-DB Check    │
     │ "is caller's profile   │          │ Node (Section 4.8,     │
     │ tag a known contact?"  │          │ hash-based lookup)     │
     └──────────┬──────────────┘          └──────────┬─────────────┘
                │                                     │
                ▼                                     ▼
     ┌──────────────────────┐          ┌──────────────────────┐
     │ Urgency/Pressure        │          │ Request/Intent          │
     │ Scorer Node (4.3.4)      │          │ Classifier Node (4.3.5) │
     └──────────┬────────────────┘          └──────────┬──────────────┘
                │                                       │
                └─────────────┬─────────────────────────┘
                               ▼
                     ┌───────────────────┐
                     │  Fusion Node        │
                     │ (combines identity-  │
                     │  mismatch + scam-    │
                     │  pattern + urgency + │
                     │  request_type →      │
                     │  continuous risk     │
                     │  score 0–100)         │
                     └─────────┬───────────┘
                               ▼
                 conditional edge: risk_score AND request_detected
        ┌──────────────────────┼──────────────────────────────────┐
        ▼                      ▼                                  ▼
┌───────────────┐   ┌────────────────────┐          ┌──────────────────────┐
│ 0–40 → Passive │   │ 41–70 → Trigger      │          │ 71–100 → branch on     │
│ log only        │   │ "Verify with trusted │          │ request_detected:       │
│                 │   │ contact" prompt       │          │  • true → Freeze/       │
│                 │   │ (Section 4.7)         │          │    Intercept (4.6)      │
│                 │   │                       │          │  • false → escalate to  │
│                 │   │                       │          │    trusted contact (4.7)│
└───────┬────────┘   └───────────┬───────────┘          └────────────┬─────────┘
        │                        │                                   │
        └────────────────────────┴───────────────────┬───────────────┘
                                                       ▼
                                        ┌────────────────────────────────────┐
                                        │  LLM Reasoning Node (4.4)            │
                                        │  Plain-language explanation, citing  │
                                        │  each signal by name, scam-type      │
                                        │  classification, recommended action  │
                                        └──────────────────┬───────────────────┘
                                                            ▼
                                                  ┌───────────────────┐
                                                  │  END → push verdict │
                                                  │  to client over WS   │
                                                  │  + server log write  │
                                                  │  + optional collective│
                                                  │    flag submission    │
                                                  └───────────────────┘
```

Each node remains a pure function `(state) -> updated_state`, matching LangGraph's `StateGraph` model exactly. Why a graph and not a linear pipeline, unchanged from v1: a text-only scam never needs the video-deepfake node to fire, a medium-confidence voice case triggers the human-in-the-loop verify step rather than an automatic verdict, and only a high-risk-*plus*-actionable-request combination triggers Freeze/Intercept.

### 4.6 Freeze / Intercept Mechanism

The gap this closes, unchanged from v1: don't just warn after the fact — break the reflexive/panic path the moment a harmful action is about to happen. In v2 this is *more* powerful than v1, because the actionable requests it intercepts (sharing an OTP, tapping a link, installing something) are now happening **inside this app's own UI**, so the server can push a hold directly onto the exact button the client is about to render — no OS-level guessing required.

```
Fusion Node outputs: risk_score >= 71 AND request_detected = true
        │
        ▼
 Request/Intent Classifier's request_type determines
 the intercept action, pushed to the client over the
 WebSocket control channel mid-call/mid-chat:
        │
   ┌────┼─────────────────┬─────────────────────┐
   ▼    ▼                 ▼                      ▼
 OTP    Money transfer    Credential/remote-      Link/gift-card
 share  request            access install request  purchase request
   │       │                  │                        │
   ▼       ▼                  ▼                        ▼
 Delay OTP  Hold the         Block the install/        Show blocking
 visibility  transfer intent  permission grant           confirm dialog
 for 60s     for a confirm    prompt, require            before opening
             window ("This    explicit re-confirm         link/app
             looks like a     after warning
             high-pressure
             money request —
             confirm you want
             to proceed?")
        │
        └──────────────┬──────────────────────────┘
                        ▼
              User must actively confirm
              "I understand, proceed anyway"
              to bypass the hold — no auto-block,
              since the user must retain control,
              but the reflexive/panic path is broken
```

Design principle, unchanged: **never silently block** — always show the reasoning and require a conscious choice.

### 4.7 "Verify With Trusted Contact"

Simplified relative to v1, because everyone involved is already on the same platform (contacts/profiles from Section 3.3), rather than needing a separate registration flow:

```
Caller's profile is tagged "Mom" during an active call
        │
        ▼
 App/server detects an identity claim (ASR transcript keyword/NER:
 "this is your mother," relationship terms) OR user taps
 "Verify Caller" in the overlay
        │
        ▼
 Server looks up "Mom" in the trusted-circle roster
        │
   ┌────┴─────┐
   │ Linked    │ Not linked
   ▼           ▼
 Push a        Fallback: show "cannot verify —
 silent         treat with caution" + generic
 verification   scam-safety guidance
 ping (FCM) to
 Mom's device
        │
        ▼
 Mom's app shows a push notification:
 "[Name] is claiming you're calling them right now.
  Are you currently calling them? [Yes] [No]"
        │
   ┌────┴─────┐
   ▼           ▼
  Yes         No / no response in N seconds
  → mark      → mark call HIGH-RISK, surface
  verified,    immediate warning + suggested
  clear        action ("hang up, do not send
  warning      money, this is likely a scam
               impersonating a family member")
```

This still needs a minimal always-on push channel (FCM) for the one case where true real-time delivery outside the active call matters.

### 4.8 Collective Flagging Database

Unchanged in mechanism from v1, just naturally centralized now that there's already a server:

```
User marks a number/caller as "scam" or "AI-generated"
        │
        ▼
 Local flag cached immediately (client keeps working offline)
        │
        ▼
 Synced to server: HASHED identifier (phone-number hash +
 optional voice-embedding hash, never raw PII)
        │
        ▼
 Server aggregates flag counts + confidence (rate-limited per
 account, weighted by account age/reputation to resist mass
 false-flagging/abuse)
        │
        ▼
 On any future incoming call/message, the graph does a fast
 hash lookup against this table first → if flagged, this
 short-circuits straight to HIGH-RISK before running full
 inference (faster + cheaper)
```

### 4.9 Storage

| Data | Where | Notes |
|---|---|---|
| Voiceprints of trusted contacts | Server DB, encrypted at rest, embeddings only, never raw audio retained | Used by 4.3.1 speaker verification |
| Behavioral text profiles | Server DB, rolling window per contact, summarized rather than stored verbatim long-term | Used by 4.3.3 deviation detection |
| Collective flag table | Server DB | Synced down to client cache periodically for offline lookup |
| Call/message risk logs + post-call reports | Server DB, user-viewable "history" screen in the client | Especially useful for family members reviewing an elderly user's activity, with consent |
| Recent verdicts, trusted-circle roster, offline queue | Client local cache (Room) | For offline resilience only — not the system of record |

---

## 5. End-to-End Walkthrough: a Live Call

1. User A calls User B through the app. The Kotlin client opens a signaling session (Section 4.1); the server's SFU sets up the relay and forks a copy of each media track into the Ingestion Tap (4.2).
2. Audio (16kHz chunks) and video (5–8 fps) stream into the Orchestration Graph in the same <300–500ms windows as v1. Any in-call chat messages tap in the same way.
3. The graph fans out: Voice/Video Detector nodes, Identity-Claim node, Urgency scorer, Intent classifier, and Collective-DB check all run in parallel (4.5).
4. Fusion Node computes a continuous risk score.
5. Conditional branch fires:
   - **0–40:** logged only, call continues normally.
   - **41–70:** server pushes a "Verify with trusted contact" prompt to the client over the WebSocket control channel (4.7).
   - **71–100 + request_detected:** server pushes a Freeze/Intercept directive that holds the specific in-app action being attempted — an OTP about to be shared in chat, a "send money" flow, a link tap (4.6).
6. The LLM Reasoning Node turns all of the above into the plain-language verdict card the client renders live (Section 6).
7. When the call ends, the LLM generates the post-call report and stores it; the client's history screen updates, and the user can optionally share the report with their trusted circle.

---

## 6. Real-Time Output Contract

Unchanged from v1 — the client never shows raw model scores. The LLM Reasoning Node's output always contains:

1. **Verdict** — one line: "This is likely a scam call impersonating your relative."
2. **Why** — plain-language reasons citing each signal by name: "The voice doesn't match your son's stored voice pattern, the caller is using high-pressure urgency language, and they're requesting a money transfer."
3. **What to do now** — concrete next step: "Hang up. Do not send money or share the code. Call your son directly using the number saved in your contacts."
4. **Optional escalation** — a one-tap "Notify my family member" button, using the trusted-circle push channel from Section 4.7.
5. **If a request was frozen** — a clear statement of what was held and why, plus the explicit "proceed anyway" override from Section 4.6.
6. **Post-call report (new)** — after the call/chat ends, a fuller written summary from Section 4.4: timeline, signals fired, scam-type classification, and recommended follow-up.

---

## 7. Suggested Module Breakdown

```
app/  (Kotlin, Android)
 ├── comms/
 │    ├── voip/            (WebRTC call engine — capture, codecs, rendering)
 │    ├── video/            (video-call UI, camera pipeline)
 │    ├── messaging/        (chat UI, send/receive, local message cache)
 │    └── contacts/         (contact/number/profile directory, trusted-circle linking)
 ├── realtime/               (WebSocket client — receives verdicts, freeze directives,
 │                            verify prompts from the server in real time)
 ├── ui/                      (verdict overlay, freeze/confirm dialogs, post-call report
 │                            screen, onboarding/consent flows)
 ├── storage/                  (Room DAOs — local verdict/roster/offline-queue cache)
 └── auth/                      (phone-number signup/login, session management)

server/
 ├── signaling/         (WebRTC signaling over WebSocket)
 ├── media-sfu/          (self-hosted SFU — LiveKit/mediasoup — relay + stream fork)
 ├── ingestion/           (audio chunker, frame sampler, text tap consumers)
 ├── inference/
 │    ├── voice/          (VAD, SSL deepfake, speaker-embedding, ASR)
 │    ├── video/           (face detector, deepfake classifier, lip-sync)
 │    ├── text/             (scam-pattern classifier, behavioral model)
 │    ├── urgency/           (pressure/urgency scorer)
 │    ├── request/            (request/intent classifier)
 │    └── llm/                 (self-hosted LLM serving — reasoning + post-call reports)
 ├── orchestration/            (native LangGraph app — the graph from Section 4.5)
 ├── intercept/                 (freeze/hold session state, WS directive dispatch)
 ├── trust-circle/                (contact linking, verify-ping push handling, FCM)
 ├── collective-db/                (hash store, rate-limiter, sync API)
 ├── storage/                       (encrypted DB — voiceprints, profiles, logs, flags)
 └── gateway/                        (REST/WS API gateway, auth, push dispatch)
```

---

## 8. Key Risks to Design Around Early

- **Adoption / cold start**: protection only covers calls and messages made *through this app*, so it only works once both the vulnerable user and their trusted circle install it — the same adoption problem v1 had for its trusted-circle feature, now central to the whole product, not just one feature.
- **No true end-to-end encryption on scanned calls**: the server must see plaintext audio/video/text to run inference. This has to be disclosed clearly at onboarding — for *both* participants on a call, not just the account holder, since the other party's voice/video is processed too.
- **Real-time latency budget**: the full round trip (client → SFU → inference → fusion → LLM → back to client) has to land inside the same <300–500ms window v1 set for on-device inference — this means co-locating the media relay and the GPU inference stack, not spreading them across generic multi-region cloud services.
- **Media/GPU infrastructure cost and scaling**: unlike v1's "everything stays on-device, network is minimal" model, this version needs to provision and scale an SFU plus GPU inference per concurrent call — a real, ongoing infra cost that v1 didn't have.
- **Call-recording/consent law varies by jurisdiction**: some regions require all-party consent to record or analyze a call, not just one-party consent. Since this app processes both callers' audio/video, the consent flow needs explicit acknowledgment from whoever the protected user calls, not just from the protected user.
- **Data retention discipline**: raw audio/video now transits a server, even briefly — retention policy should default to processing in memory and discarding raw media immediately after inference, keeping only derived signals/embeddings, to preserve the privacy posture v1 had by being on-device.
- **False positives on elderly/vulnerable users**: unchanged from v1 — a wrong "this is a scam" verdict eroding trust can be worse than a missed detection; tune thresholds conservatively and always give a human-verifiable reason, never a black-box score.
- **Collective DB abuse**: unchanged from v1 — mass false-flagging as a griefing vector needs reputation weighting from day one.
- **Freeze mechanism over-blocking**: unchanged from v1 — freezing a legitimate request (a real family member genuinely asking for money) will frustrate users if triggered too aggressively; always allow a fast, explicit override rather than a hard block.
