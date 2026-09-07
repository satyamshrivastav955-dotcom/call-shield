## User

https://github.com/satyamshrivastav955-dotcom/antAI-Guardian.git pull these

## Assistant

<thinking>
I need to clone this GitHub repo, so let me first check the current project structure to figure out where it should go.
</thinking>

**Tool Call: List the `SIH26P1` directory's contents**
Status: Completed

SIH26P1 is empty.


## Assistant

<thinking>
The project directory is empty, so I'll clone the repo into it.
</thinking>

**Tool Call: git clone https://github.com/satyamshrivastav955-dotcom/antAI-Guardian.git .**
Status: Completed

```
Cloning into '.'...
remote: Enumerating objects: 647, done.
remote: Counting objects: 100% (647/647), done.
remote: Compressing objects: 100% (452/452), done.
remote: Total 647 (delta 125), reused 642 (delta 123), pack-reused 0 (from 0)
Receiving objects: 100% (647/647), 10.05 MiB | 7.25 MiB/s, done.
Resolving deltas: 100% (125/125), done.
```

## Assistant

Cloned successfully into `SIH26P1`.

**Tool Call: List the `SIH26P1` directory's contents**
Status: Completed

# Folders:
SIH26P1\SimpleVideoCallBackend
SIH26P1\SimpleVideoCallReacJs
SIH26P1\app
SIH26P1\app_antai_client_backup
SIH26P1\docs
SIH26P1\server
SIH26P1\windows_client

# Files:
SIH26P1\.gitignore
SIH26P1\ANTAI_V3_DEVICE_LEVEL_PLAN.md
SIH26P1\CALL_APP_SETUP.md
SIH26P1\LANGGRAPH_ARCHITECTURE.md
SIH26P1\MANUAL_TEST_PLAN.md
SIH26P1\README.md
SIH26P1\SIH2026-IDEA-Presentation-Epoch12.pptx
SIH26P1\SIH_PS26104_AUDIT.md
SIH26P1\antai_e2e_workflow.jpg
SIH26P1\antai_langgraph_architecture.jpg
SIH26P1\antai_screen.png
SIH26P1\antai_screen2.png
SIH26P1\antai_screen3.png
SIH26P1\ppt.readme
SIH26P1\scam-detection-app-architecture-v2.md


## Assistant

The `antAI-Guardian` repo is now pulled into `SIH26P1`. It contains several components: `SimpleVideoCallBackend`, `SimpleVideoCallReacJs`, `app`, `app_antai_client_backup`, `docs`, `server`, `windows_client`, plus architecture/planning docs.

Let me know what you'd like to do next — e.g., set up dependencies, run a specific service, or review the architecture docs.

## User

research two things for me that are 
- i think first is can i can it total on device phone or exterxnil in chrome
- and what improve i can do in it 
- last check all minimum requirements are covered for sih ps26104

## Assistant

<thinking>
I need to figure out whether this can run fully on-device or requires browser/external dependencies, identify possible improvements, and verify coverage against the SIH PS26104 requirements. Let me start by exploring the repo structure—README, audit docs, architecture, app and windows_client folders—to get the full picture.
</thinking>

**Tool Call: Read file `README.md`**
Status: Completed

```SIH26P1/README.md
SUCCESS: File outline retrieved. This file is too large to read all at once, so the outline below shows the file's structure with line numbers.

IMPORTANT: Do NOT retry this call without line numbers - you will get the same outline.
Instead, use the line numbers below to read specific sections by calling this tool again with start_line and end_line parameters.

# File outline for C:\Users\sidde\OneDrive\Desktop\FSD\Hackthonss\SIH26P1\README.md

div [L1]
# 🛡️ antAI [L3-270]
 ### Real-Time Scam, Deepfake & Impersonation Defense System [L4-39]
  a [L7-9]
  p [L11-16]
  p [L18-25]
   a [L19]
   a [L20]
   a [L21]
   a [L22]
   a [L23]
   a [L24]
  p [L27-35]
 ### 🚨 *Modern scams no longer look like spam — they look like credible conversations.* [L39-46]
 ## 💡 Why antAI? [L46-62]
  ### ⚖️ Traditional Spam Filters vs. antAI Active Defense [L50-62]
 ## ⚡ End-to-End Workflow [L62-86]
  div [L66-68]
  ### 🔄 How the End-to-End Chain Operates: [L72-86]
 ## 🧠 LangGraph Orchestration Architecture [L86-116]
  div [L90-92]
  ### 🧩 14-Node Agentic State Machine Breakdown [L96-116]
   details [L98-99]
    summary [L99]
     b [L99]
 ## 🔮 Multi-Signal AI Brain [L116-153]
  ### 📋 Model Specifications & Detection Table [L135-153]
 ## 💬 Explainable AI: Plain-Language Reasoning [L153-167]
  verdict [L159]
  why [L160]
  action [L161]
 ## 📂 Repository Layout [L167-198]
 ## 🚀 Quickstart Guide [L198-247]
  ### 📋 Prerequisites [L202-209]
  ### Step 1 ➔ Start the Signaling Server [L209-219]
  ### Step 2 ➔ Start the antAI AI Backend [L219-235]
  ### Step 3 ➔ Launch the Android App [L235-247]
 ## 🔒 Privacy & Security by Design [L247-256]
 ## 👥 Hackathon Context & Metadata [L256-270]
  div [L263]

Showing symbols 1-40 (total symbols: 40)


NEXT STEPS: To read a specific symbol's implementation, call read_file with the same path plus start_line and end_line from the outline above.
For example, to read a function shown as [L100-150], use start_line: 100 and end_line: 150.
```


**Tool Call: Read file `SIH_PS26104_AUDIT.md`**
Status: Completed

```SIH26P1/SIH_PS26104_AUDIT.md
SUCCESS: File outline retrieved. This file is too large to read all at once, so the outline below shows the file's structure with line numbers.

IMPORTANT: Do NOT retry this call without line numbers - you will get the same outline.
Instead, use the line numbers below to read specific sections by calling this tool again with start_line and end_line parameters.

# File outline for C:\Users\sidde\OneDrive\Desktop\FSD\Hackthonss\SIH26P1\SIH_PS26104_AUDIT.md

# antAI — SIH PS26104 Comprehensive Audit (Phase 0 + Phase 2 Master Plan) [L1-331]
 ## 1. Verified Architecture — Actual Data Path [L7-41]
 ## 2. The 15 SIH Requirements — Classified [L41-63]
 ## 3. Strong Differentiators — Preserve These [L63-78]
 ## 4. Known Bugs and Dead Code [L78-94]
 ## 5. Gap Analysis — What Still Needs Implementation [L94-132]
  ### P0 — DONE ✅ [L96-103]
  ### P1 — PENDING (SIH compliance + product value) [L103-117]
  ### P2 — PENDING (polish, after P0+P1 stable) [L117-132]
 ## 6. Server Models — Actual Status [L132-150]
 ## 7. Local vs Cloud Mode (Actual Behavior) [L150-166]
 ## 8. Android Application — Actual State [L166-184]
 ## 9. Design Invariants (Must Hold Across All Phases) [L184-194]
 ## 10. Roadmap — P1 + P2 (After Audit Approval) [L194-289]
  ### P1 (~18–24 h estimated) [L196-264]
   #### P1.1 — Acoustic model documentation + cleanup (~1 h) [L198-202]
   #### P1.2 — Scenario-based thresholds (~3–4 h) [L202-213]
    scenarios [L205-209]
     routine_call [L206]
     high_value_txn [L207]
     privileged_access [L208]
   #### P1.3 — Context structure (~3–4 h) [L213-223]
    caller_id [L216]
    transaction_type [L216]
    transaction_amount [L217]
    claimed_identity [L217]
    historical_risk [L218]
   #### P1.4 — Explicit multi-step recommendation (~2 h) [L223-230]
    action [L226]
    steps [L226]
   #### P1.5 — Dashboard upgrade for SIH demo (~3–4 h) [L230-240]
   #### P1.6 — Privacy hardening (~2–3 h) [L240-247]
   #### P1.7 — Android OS notifications (~2–3 h) [L247-252]
   #### P1.8 — Multilingual validation (~2 h) [L252-257]
   #### Eval harness (~3–4 h) [L257-264]
  ### P2 (~40–60 h) [L264-289]
 ## 11. Files Requiring Modification (P1 + P2) [L289-315]
  ### P1 (targeted changes only) [L291-306]
  ### P2 (new files + extensive Android rewrites) [L306-315]
 ## 12. Verification Plan [L315-331]

Showing symbols 1-40 (total symbols: 40)


NEXT STEPS: To read a specific symbol's implementation, call read_file with the same path plus start_line and end_line from the outline above.
For example, to read a function shown as [L100-150], use start_line: 100 and end_line: 150.
```


**Tool Call: Read file `ANTAI_V3_DEVICE_LEVEL_PLAN.md`**
Status: Completed

```SIH26P1/ANTAI_V3_DEVICE_LEVEL_PLAN.md
     1	# antAI v3 — Device-Level Scam Defense Plan
     2	
     3	**Status:** planning only. No code changes proposed here.
     4	**Date:** 2026-08-23
     5	**Purpose:** fix the architectural flaw the judges identified, and define what we build if time allows.
     6	
     7	---
     8	
     9	## 1. The flaw the judges found
    10	
    11	Our current calling design requires **both** people to install antAI and to type each other's in-app
    12	user ID. Analysis then runs on that app-to-app WebRTC call.
    13	
    14	The objection is correct and fatal to the pitch: a scammer will never install our app, never register,
    15	and never hand us their antAI ID. So the calling half of the product can only ever analyze calls
    16	between two cooperating users — which are, by definition, not scam calls. We were demonstrating the
    17	pipeline on a channel where the threat cannot appear.
    18	
    19	Note carefully that the objection applies **only to the calling half**. The SMS and notification
    20	scanning we finished on 2026-08-23 already has the correct property: a scammer texts the victim from
    21	any ordinary phone, installs nothing, cooperates in no way, and we still analyze the content. That
    22	half of the product already answers the judges. The plan below extends the same property to calls.
    23	
    24	---
    25	
    26	## 2. The corrected thesis: victim-side install
    27	
    28	antAI is installed by the **person being protected**, and by nobody else. It sits on the receiving end
    29	of the phone and inspects what arrives — calls, texts, and notifications from any sender on the public
    30	network.
    31	
    32	This is not a workaround or a compromise. It is the same model Truecaller, Hiya, and Google's own
    33	Pixel call screening use: the protected user installs, the caller does not participate. Our
    34	differentiator is that we go past caller-ID reputation and analyze the **live content** of the
    35	interaction — the language of the scam and the authenticity of the voice — rather than only judging
    36	the number.
    37	
    38	Reframed one-liner for the pitch: *antAI is a guardian that lives on the victim's phone and inspects
    39	every call, text, and notification that reaches them, no matter who sent it.*
    40	
    41	---
    42	
    43	## 3. What we keep, and what changes
    44	
    45	We keep essentially all of the engineering already done. The analysis brain is unchanged: the server's
    46	LangGraph pipeline, Deepgram ASR, the Groq LLM scam-intent reasoning, the voice-deepfake model, the
    47	verdict/band schema, the risk UI, the SMS and notification capture, and the whole messaging stack. The
    48	`/ws/tap` ingestion endpoint already accepts `kind: "voice"`, i.e. audio-only sessions, so it can take
    49	a phone-call audio stream without redesign.
    50	
    51	What changes is only the **source of the call**. Today the source is an app-to-app WebRTC session
    52	negotiated through our Node signaling server. In v3 the primary source becomes the **real cellular
    53	call** arriving from any stranger, captured because our app holds the operating system's default
    54	phone-app role.
    55	
    56	The existing app-to-app P2P calling does not get deleted. It gets demoted from "the product" to two
    57	useful supporting roles: a clean-audio channel for demonstrating the detection models without
    58	degradation, and a future "verified antAI-to-antAI call" feature. It also remains our safest
    59	regression baseline, since we know it works.
    60	
    61	---
    62	
    63	## 4. The OS roles we acquire, and what each one actually gives us
    64	
    65	Android gates this kind of capability behind `RoleManager` roles that the user grants once, with a
    66	system consent dialog. There are three worth holding, plus one special access grant.
    67	
    68	**`ROLE_CALL_SCREENING`** makes us the default caller-ID and spam app. This is the highest-value,
    69	lowest-risk role. Through `CallScreeningService` we are handed every incoming call **before the phone
    70	rings**, with the caller's number, and we may allow it, silence it, or reject it outright. This alone
    71	delivers pre-ring scam interception on real calls from real strangers.
    72	
    73	**`ROLE_DIALER`** makes us the default phone app, so our `InCallService` becomes the actual in-call UI.
    74	This gives full call control — answer, mute, hold, end — reliable real-time call state, and, critically
    75	for us, control of the **audio route**, meaning we can put a call on speaker programmatically. It does
    76	**not**, on its own, hand us the call's audio stream; see section 5.
    77	
    78	**`ROLE_SMS`** makes us the default messaging app. We already read incoming SMS correctly today via the
    79	`SMS_RECEIVED` broadcast with `RECEIVE_SMS`, so this role is *not* required to see texts — a common
    80	misconception. What it adds is the ability to write to the SMS provider, handle MMS properly, and
    81	legitimately present our unified thread list as the user's real inbox instead of a parallel view.
    82	Worth taking for product completeness, not for capability.
    83	
    84	**Notification listener access** (already implemented) is a separate special grant, not a role, and
    85	covers scam content arriving through WhatsApp, Telegram, bank apps, and everything else.
    86	
    87	---
    88	
    89	## 5. The call-audio question, answered honestly
    90	
    91	This is the single most important technical point in the plan, and the place where a well-informed
    92	judge will probe hardest. We must get it right.
    93	
    94	**On stock, unrooted Android, no third-party app can read the raw audio of a cellular call.** The audio
    95	sources that would provide it — `VOICE_CALL`, `VOICE_DOWNLINK`, `VOICE_UPLINK` — are guarded by
    96	`CAPTURE_AUDIO_OUTPUT`, which is a `signature|privileged` permission available only to the OEM or a
    97	preinstalled system app. Google closed this path to third parties in Android 10. This is why Truecaller
    98	dropped call recording on Android 10 and above, and why Google's Pixel scam detection can do what we
    99	cannot: Google is the OEM.
   100	
   101	Holding `ROLE_DIALER` does **not** lift this restriction. Being the default dialer gives us control
   102	*over* the call; it does not give us the modem's audio.
   103	
   104	Given that, we pursue live audio in tiers, and we are explicit about which tier any given demo is using.
   105	
   106	**Tier A — number, metadata, and behavior. No audio at all.** Runs on any stock phone, needs no
   107	recording, and is the backbone of the product. On the pre-ring `CallScreeningService` callback we
   108	evaluate the caller against our server: prior reports for that number, crowd-sourced flags, whether
   109	the caller is in contacts, first-contact status, neighbour-spoofing patterns (caller shares the
   110	victim's prefix), call velocity, and time-of-day anomalies. We render a full-screen Truecaller-style
   111	verdict before the phone rings, and auto-reject anything critical. This is genuinely shippable and
   112	needs nothing from the scammer.
   113	
   114	**Tier B — consented speakerphone acoustic capture. Degraded audio, stock phone.** When a risky call
   115	connects, we offer the user a "Scam Shield" action. Because we hold `ROLE_DIALER` we set the audio
   116	route to speaker, then open `AudioRecord` and capture what the microphone hears — which now includes
   117	the remote party's voice acoustically, via the speaker. We stream that to `/ws/tap` as `kind: "voice"`
   118	and run the existing pipeline, surfacing a live verdict overlay.
   119	
   120	Two implementation details matter here. First, the capture source must be `MIC` (or `UNPROCESSED` where
   121	available), **not** `VOICE_COMMUNICATION` — that source deliberately applies acoustic echo cancellation
   122	and noise suppression, whose entire job is to remove the speaker's output from the mic signal, which is
   123	precisely the signal we want. Choosing it would silently defeat the feature. Second, quality is
   124	device-dependent, because some OEMs apply hardware echo cancellation we cannot disable. Tier B is real,
   125	but it is degraded and must be described as such.
   126	
   127	**Tier C — clean audio through privilege. Roadmap, not the shipping app.** A preinstalled or
   128	OEM-partnered build, a carrier/IMS-side deployment, or an enterprise MDM install can hold
   129	`CAPTURE_AUDIO_OUTPUT` and read the call stream directly at full fidelity. A rooted lab device can do
   130	the same for internal model evaluation. This is the honest answer to "how does this become a real
   131	product at scale," and it should appear as a partnership roadmap item rather than a current capability.
   132	
   133	**Tier D — adjacent channels that are easy wins.** Voicemail audio, any call recordings the user's own
   134	OEM dialer already produced and exposed, and our existing app-to-app P2P channel, which gives
   135	undegraded audio for demonstrating the detection models at their true accuracy.
   136	
   137	---
   138	
   139	## 6. AI voice-clone detection under degraded audio
   140	
   141	The flagship problem statement is cloned-voice fraud, and Tier B's degradation hurts precisely the
   142	acoustic artefacts a deepfake detector relies on. We therefore do not stake the verdict on the acoustic
   143	model alone. We fuse four signal families, weighting each by how well it survives bad audio.
   144	
   145	The acoustic deepfake score comes from the voice model and is the most degradation-sensitive, so it
   146	carries reduced weight under Tier B and full weight under Tier C or the P2P channel. The linguistic
   147	scam-intent signal, derived from the ASR transcript through the LLM, is highly robust to audio quality
   148	— urgency, authority impersonation, payment instructions, and secrecy demands survive a noisy speaker
   149	path intact, and in practice this will carry most of the verdict in Tier B. Conversational and prosodic
   150	anomalies such as unnatural turn-taking latency or flat affect provide a third, partially robust
   151	signal. Finally, caller metadata and reputation from Tier A anchor the whole judgement.
   152	
   153	The strongest differentiating feature sits on top of this. The canonical clone attack impersonates a
   154	family member — the "your son has been in an accident" call. Our server already exposes voiceprint
   155	enrolment (`POST /api/voiceprints/enroll`) and trust-circle linking, which means a user can enrol the
   156	voices of the handful of people an attacker would plausibly imitate. When an unknown number claims to be
   157	an enrolled contact, we compare the live voice against that person's real voiceprint. That is a
   158	concrete, demonstrable answer to cloned-voice fraud that pure liveness detection cannot match.
   159	
   160	**Known gap to close:** `speaker_verify` is currently `enabled: false` in `server/config.yaml`, because
   161	the local voiceprint model was switched off during the voice-to-API pivot and no hosted replacement was
   162	chosen. The enrolment endpoint and the trust-circle plumbing exist, but the matching model is inactive.
   163	Re-enabling this — either by restoring a local ECAPA-style embedder or selecting a hosted speaker-
   164	verification API — is a prerequisite for the family-voiceprint feature and should be treated as a
   165	first-class task, not an afterthought.
   166	
   167	---
   168	
   169	## 7. End-to-end flow we are aiming for
   170	
   171	A stranger dials the user's ordinary phone number from an ordinary phone. Before the handset rings,
   172	Android hands the call to our `CallScreeningService`, and we query the server for a reputation and
   173	context verdict on that number. If the verdict is critical we can reject or silence it; otherwise the
   174	call proceeds and our in-call UI presents the risk band up front, so the user answers already informed.
   175	
   176	If the user answers a call we have flagged as uncertain or risky, we offer Scam Shield. On acceptance we
   177	route audio to the speaker and begin streaming captured audio to the server, which transcribes it, runs
   178	scam-intent reasoning over the transcript, scores the voice for synthesis artefacts, and — where the
   179	caller claims to be an enrolled contact — compares against that contact's voiceprint. Verdicts stream
   180	back live and we escalate the on-screen band as evidence accumulates, up to an interrupting warning
   181	that advises ending the call and never sending money or codes.
   182	
   183	After the call the user can confirm or reject our judgement. That feedback writes back to the number's
   184	reputation, which improves Tier A for every other user — a network effect that makes the product
   185	stronger the more people run it, and a good note to end the pitch on.
   186	
   187	Texts and third-party app notifications continue to flow through the pipeline already built, so the
   188	same verdict language covers every channel the victim can be reached on.
   189	
   190	---
   191	
   192	## 8. Phasing, if time allows
   193	
   194	The order is chosen so that each phase is independently demonstrable and nothing later is a
   195	prerequisite for something earlier.
   196	
   197	Phase one is Tier A: request `ROLE_CALL_SCREENING`, implement `CallScreeningService`, add a
   198	number-reputation lookup and a report-as-scam write on the server, and build the pre-ring verdict
   199	overlay. This is the direct answer to the judges and it is the highest value per unit of risk.
   200	
   201	Phase two adds `ROLE_DIALER` with a minimal in-call UI, plus the Scam Shield audio path of Tier B
   202	streaming into the existing `/ws/tap` voice session, with the live verdict overlay.
   203	
   204	Phase three re-enables speaker verification and ships family voiceprint enrolment and trust-circle
   205	matching, which is what makes the clone story land.
   206	
   207	Phase four is polish and hardening: `ROLE_SMS` for inbox completeness, post-call feedback into
   208	reputation, and a clean fallback everywhere the roles have not been granted.
   209	
   210	Throughout, the existing WebRTC calling, signaling, and analysis-tap code stays untouched, exactly as
   211	it has been through the restyle and the messaging build.
   212	
   213	---
   214	
   215	## 9. What we may claim, and what we must never claim
   216	
   217	| Safe to claim | Never claim |
   218	| --- | --- |
   219	| The protected user installs; the caller needs nothing | That we read raw cellular call audio on a stock phone |
   220	| We intercept real calls pre-ring as the default screening app | That `ROLE_DIALER` unlocks the modem audio stream |
   221	| We control the audio route and capture consented speaker audio | That `MediaProjection` can capture voice-call audio — telephony is excluded from playback capture |
   222	| Live transcript-based scam-intent detection on real calls | That we can take over another app's `ConnectionService`; WhatsApp/Signal calls are *self-managed* and are deliberately withheld from the default dialer |
   223	| Tier B audio is degraded, and we fuse robust signals to compensate | That third-party `ROLE_DIALER` apps inherit OEM call-recording privileges — that is unreliable and ROM-specific |
   224	| Clean audio at scale needs OEM/carrier partnership | That the family voiceprint feature works today (`speaker_verify` is disabled) |
   225	
   226	Two of these deserve emphasis because they appeared in circulated notes and are wrong.
   227	`MediaProjection` with `AudioPlaybackCaptureConfiguration` captures *app playback* audio and explicitly
   228	excludes telephony and anything marked as voice communication, so it cannot be our call-audio path. And
   229	a default dialer cannot register itself as the managing `ConnectionService` for another app's VoIP
   230	calls — self-managed connections exist precisely so that the app keeps its own UI and audio, and they
   231	are withheld from the default dialer by design.
   232	
   233	Volunteering the Tier A/B/C distinction before a judge digs for it reads as engineering maturity. Being
   234	caught overclaiming does the opposite.
   235	
   236	---
   237	
   238	## 10. Open risks
   239	
   240	Call-audio analysis touches recording-consent law, which varies by jurisdiction, so Scam Shield must
   241	stay explicitly per-call and user-initiated, and we should analyze without retaining raw audio. This is
   242	worth stating in the pitch; it is not legal advice and should be checked before any real deployment.
   243	
   244	Play Store policy is strict about the dialer and SMS roles and about anything resembling call recording,
   245	so a store listing needs its permission use documented and justified. A hackathon or sideloaded demo is
   246	unaffected.
   247	
   248	Tier B quality varies by OEM, so we should test on every handset we intend to demo on and have the P2P
   249	channel ready as a clean-audio fallback if the venue phone behaves badly. Latency also matters: a
   250	verdict that arrives after the victim has already paid is worthless, so the streaming path should target
   251	a few seconds end to end.
   252	
   253	Finally, false positives carry real cost. Wrongly flagging a legitimate hospital or bank call trains the
   254	user to ignore us, so the bands should stay conservative and the critical, interrupting state should be
   255	reserved for strong, multi-signal evidence.

```


**Tool Call: Read file `scam-detection-app-architecture-v2.md`**
Status: Completed

```SIH26P1/scam-detection-app-architecture-v2.md
SUCCESS: File outline retrieved. This file is too large to read all at once, so the outline below shows the file's structure with line numbers.

IMPORTANT: Do NOT retry this call without line numbers - you will get the same outline.
Instead, use the line numbers below to read specific sections by calling this tool again with start_line and end_line parameters.

# File outline for C:\Users\sidde\OneDrive\Desktop\FSD\Hackthonss\SIH26P1\scam-detection-app-architecture-v2.md

# Real-Time Scam & Deepfake Defense — Native Calling/Messaging App + Server Architecture (v2) [L1-477]
 ## 0. What Changed From v1, and Why [L5-17]
 ## 1. Design Constraints (v2) [L17-30]
 ## 2. High-Level Architecture — Client / Server Split [L30-65]
 ## 3. Client App — the "Normal App" Layer (Kotlin / Android) [L65-94]
  ### 3.1 VoIP Calling & Video Calling Engine [L69-74]
  ### 3.2 In-App Messaging [L74-78]
  ### 3.3 Contacts, Numbers & Profiles [L78-85]
  ### 3.4 Local Cache & Offline Behavior [L85-89]
  ### 3.5 Real-Time Overlay UI [L89-94]
 ## 4. Server Architecture [L94-401]
  ### 4.1 Signaling + Media Relay (SFU) [L96-101]
  ### 4.2 Ingestion Layer (replaces v1's Android system hooks) [L101-112]
  ### 4.3 Inference Engines (server-side, GPU) [L112-196]
  ### 4.4 Local (Self-Hosted) LLM — Reasoning + "Afterward" Report Node [L196-201]
  ### 4.5 Agentic Orchestration Graph (native LangGraph) [L201-283]
  ### 4.6 Freeze / Intercept Mechanism [L283-322]
  ### 4.7 "Verify With Trusted Contact" [L322-363]
  ### 4.8 Collective Flagging Database [L363-389]
  ### 4.9 Storage [L389-401]
 ## 5. End-to-End Walkthrough: a Live Call [L401-416]
 ## 6. Real-Time Output Contract [L416-429]
 ## 7. Suggested Module Breakdown [L429-466]
 ## 8. Key Risks to Design Around Early [L466-477]

Showing symbols 1-24 (total symbols: 24)


NEXT STEPS: To read a specific symbol's implementation, call read_file with the same path plus start_line and end_line from the outline above.
For example, to read a function shown as [L100-150], use start_line: 100 and end_line: 150.
```


## Assistant

<thinking>
This device-level plan confirms it's a server-based architecture with Deepgram ASR, Groq LLM, and server-side voice models rather than fully on-device processing. I should dig into the SIH_PS26104_AUDIT.md details and search for the actual PS26104 problem statement requirements to verify compliance.

I also want to check if there's a chrome/web extension component by looking at the windows_client and app folder structures, then read through the full audit sections for a complete picture.
</thinking>

**Tool Call: Read file `SIH_PS26104_AUDIT.md` (lines 1-194)**
Status: Completed

````SIH26P1/SIH_PS26104_AUDIT.md
     1	# antAI — SIH PS26104 Comprehensive Audit (Phase 0 + Phase 2 Master Plan)
     2	
     3	> **Audit policy:** Every verdict was verified against actual source files. README claims were treated as unverified until confirmed by code inspection. File paths are relative to repository root.
     4	
     5	---
     6	
     7	## 1. Verified Architecture — Actual Data Path
     8	
     9	```
    10	WebRTC (aiortc SFU /ws/call  OR  send-only tap /ws/tap)
    11	  → av.AudioFrame
    12	  → AudioIngestor [server/src/antai/ingestion/audio.py]
    13	      Silero-VAD, 16 kHz mono, 512-sample windows, 0.4–2.0 s VAD segments
    14	  → SessionRunner.on_audio_segment [orchestration/dispatcher.py]
    15	      per-speaker rolling ~4 s detector_window + ~20 s cross-verify buffer
    16	      → ASR worker (Deepgram cloud OR local faster-whisper) → transcript push
    17	      → _schedule_evaluate (coalesced, fire-and-forget, 2.0 s tick)
    18	          → LangGraph graph.ainvoke OR _run_sequential fallback
    19	              nodes: router → voice_detector → video_detector → text_detector
    20	                     → identity_claim → collective_check → urgency → intent
    21	                     → fusion_node → decision_node → llm_reasoning_node
    22	  → live realtime hub pushes:
    23	      signals.update / verdict.update / guidance.update / deepfake.alert
    24	  → decision=freeze → intercept.controller [intercept/controller.py]
    25	      per-type hold directives (otp/money/credential/link), user confirm/override
    26	  → finish(): post-call report, clears audio buffer
    27	
    28	EXTERNAL STREAMING PATH (exists since P0 implementation):
    29	  /api/stream/ws (WebSocket) or /api/stream/analyze (REST POST)
    30	  → StreamingSession [orchestration/streaming.py]
    31	      → AudioIngestor → VAD → same SessionRunner → same LangGraph graph
    32	      → normalized_result() → {risk, risk_level, acoustic, prosody, voiceprint, …}
    33	  Demo script: server/scripts/stream_demo_file.py
    34	  Dashboard: /dashboard/ served from gateway/static/{index.html, app.js}
    35	```
    36	
    37	The rolling streaming engine, external API, demo file streamer, and mock dashboard **all exist and are real**. The previously-identified P0 gaps have been filled.
    38	
    39	---
    40	
    41	## 2. The 15 SIH Requirements — Classified
    42	
    43	| # | Requirement | Verdict | File | Lines | Actual Behavior |
    44	|---|---|---|---|---|---|
    45	| 1 | Acoustic/spectral synthesis-artifact detection | **EXISTS & REAL** | `inference/voice/deepfake_voice.py` | 1–404 | 2-model local SSL ensemble: AST-ASVspoof5 (`models/voice_deepfake`) + wav2vec2 cross-check (`models/voice_deepfake_cross2`). Optional Velma cloud backend. `_combine()` agreement/disagreement/near-certain logic. Wired to fusion at `nodes.py:337`. Rolling 4 s detector window used instead of raw 0.4–2.0 s segment (L83–88 dispatcher). Dead-zone: contributes 0 below 0.78 spoof_prob. |
    46	| 2 | Prosody/behavioral analysis | **PARTIAL / MISLABELED** | `inference/urgency/scorer.py`, `inference/text/behavioral.py` | scorer.py:1–85, behavioral.py:1–end | No acoustic prosody (no pitch/energy/rate/F0 extractor). "Urgency" = heuristic keyword model (falls back from absent `models/classifiers/urgency`). "Behavioral" = real multilingual-MiniLM text-embedding drift. Both are **text-derived**, not voice prosody. Labeled honestly as `"kind":"text-derived"` in `streaming.py:110`. |
    47	| 3 | Cross-session consistency vs historical samples | **EXISTS & REAL** | `inference/voice/speaker_verify.py` | 1–162 | ECAPA-TDNN speechbrain model. Encrypted enrolled embeddings stored in DB. Cosine similarity ≥ 0.60 threshold (configurable). Live comparison in `voice_detector_node`; manual cross-verify via `dispatcher.py` CROSS_VERIFY flow. Mismatch = +50 risk (hard signal). Enrollment quality gates: ≥3 s, RMS ≥ 0.008. |
    48	| 4 | Continuous 0–100 risk score | **EXISTS & REAL** | `orchestration/nodes.py`, `orchestration/streaming.py` | nodes.py:304–404 | Additive dead-zone fusion model with corroboration cap. Streams live via `signals.update`/`verdict.update` WebSocket pushes every ~0.7 s or after each segment. `normalized_result()` exports `risk`, `risk_peak`, `risk_level`. |
    49	| 5 | Scenario-configurable thresholds | **PARTIAL** | `config.py` | 189, 200–211 | `risk_bands: {log: 40, verify: 70}` exist but are global only. No per-scenario profiles. No `routine_call` / `high_value_transaction` / `privileged_access` config. No `/api/scenario` endpoint. P1.2 task remains **NOT DONE**. |
    50	| 6 | Contextual enrichment (caller/txn/history) | **DOES NOT EXIST** | — | — | No transaction type/amount structure feeding into risk. `request_type` (money/otp/credential) contributes to risk but is extracted from speech, not injected from a caller context. No `/api/context` endpoint. No demo context data structure. P1.3 remains **NOT DONE**. |
    51	| 7 | Multi-channel alerts | **PARTIAL** | `gateway/push.py`, `notifications/` (Android) | — | In-app Compose alerts = REAL. `push.py` exists (FCM structure). **No OS status-bar notifications** in Android: `NotificationManager` / notification channels absent despite `POST_NOTIFICATIONS` in manifest. SMS read/receive = REAL. SMS send has backend but wiring unconfirmed. Email: no implementation. P1.7 remains **NOT DONE**. |
    52	| 8 | Pre-transaction warning + secondary verification | **EXISTS & REAL** | `intercept/controller.py`, `intercept/directives.py` | controller.py:1–end | Per-type hold directives (otp/money/credential/link). User confirm or override flow. Voiceprint cross-check as independent second opinion. Nothing auto-blocked without user decision. Fully wired Android↔server. |
    53	| 9 | Institution-specific workflows | **DOES NOT EXIST** | — | — | No institution/scenario/bank/call-center profiles. No institution concept in codebase. P1.3/P1.9 remains **NOT DONE**. |
    54	| 10 | Minimal retention / on-device or edge inference | **PARTIAL** | `orchestration/dispatcher.py`, `storage/db.py` | dispatcher.py:finish() | Raw audio never persisted (memory buffer cleared at call end). ASR temp file deleted. BUT: no TTL/purge on call/verdict/report rows. Default backends = cloud (Deepgram/Velma/Groq), though local equiv. (`faster-whisper`, local SSL, local Qwen/llama) exist and work. Encryption key defaults to `"change-me-please"` in config. P1.6 partially done. |
    55	| 11 | Anonymized / feature-only logging | **PARTIAL** | `storage/db.py`, `storage/models.py` | db.py:20–22 | Phone hashed (SHA-256, unsalted). Voice embeddings Fernet-encrypted. Message bodies encrypted. BUT: `verdict_text`/`report.body` stored plaintext. Verdicts/reports keyed by re-identifiable `user_id`. Internal user IDs not pseudonymized. P1.6 remains **PARTIAL**. |
    56	| 12 | REST/gRPC API | **EXISTS & REAL (REST only)** | `gateway/rest_api.py`, `gateway/stream_api.py` | rest_api.py:1–529 | ~23 REST endpoints + 1 WebSocket streaming endpoint + 1 REST file-analyze endpoint. No gRPC (plan confirms not required). |
    57	| 13 | Streaming / near-real-time | **EXISTS & REAL** | `gateway/stream_api.py`, `orchestration/streaming.py` | stream_api.py:67–187 | `/api/stream/ws` WebSocket — accepts raw binary PCM or JSON PCM frames. Drives real `StreamingSession → AudioIngestor → VAD → SessionRunner → LangGraph`. Heartbeat every 1 s. Live risk updates per segment. `/api/stream/analyze` REST one-shot also exists. Demo file streamer `scripts/stream_demo_file.py` works. Dashboard at `/dashboard/`. **P0 complete.** |
    58	| 14 | Multilingual / Indian-accent support | **PARTIAL** | `inference/voice/asr.py`, `inference/text/triage.py`, urgency scorer | triage.py, scorer.py | Hindi/Hinglish at ASR (Deepgram nova-2 / local faster-whisper). Hindi keywords in urgency, intent, scam. Multilingual-MiniLM embeddings. Acoustic SSL models (ASVspoof/VoxCeleb) are Western-data-skewed — no Indian-accent acoustic validation done. No metrics documented. |
    59	| 15 | Live native GSM/PSTN call interception | **OUT OF SCOPE — architectural boundary** | — | — | App is app-to-app WebRTC only. No InCallService/Telecom/accessibility/root workaround. Correct architectural decision; no workaround will be attempted. |
    60	
    61	---
    62	
    63	## 3. Strong Differentiators — Preserve These
    64	
    65	These are the project's real competitive capabilities. Do not rewrite them.
    66	
    67	1. **Dual-source voice deepfake ensemble** — `deepfake_voice.py` — honest agreement/disagreement + `detection_live()` (prevents silent-fail). No fake scores.
    68	2. **Rolling per-speaker detector window** — `dispatcher.py:_schedule_evaluate` — fixes near-chance short-segment scoring for SSL models.
    69	3. **Deterministic fusion with dead-zones + corroboration cap** — `nodes.py:fusion_node` — LLM never makes security decisions; exactly matches brief mandate.
    70	4. **Real intercept/freeze directives** — `intercept/controller.py` — per-type hold with user confirm; no auto-block.
    71	5. **Voiceprint cross-check as second opinion** — `speaker_verify.py` — enrollment quality gates, encrypted storage.
    72	6. **Anti-fakery UI contract** — null scores shown as `—`; `engines_ready` field; no fabricated values in `normalized_result()`.
    73	7. **External streaming API fully functional** — `/api/stream/ws` + `StreamingSession` — same detection path as WebRTC, no parallel ML implementation.
    74	8. **Streaming demo file + dashboard** — `stream_demo_file.py` + `/dashboard/` — end-to-end demonstrable without a phone.
    75	
    76	---
    77	
    78	## 4. Known Bugs and Dead Code
    79	
    80	| Issue | Location | Evidence |
    81	|---|---|---|
    82	| `eval_video_ensemble.py:121` references undefined `combined` | `scripts/eval_video_ensemble.py` | NameError at runtime |
    83	| Unused model dirs: `voice_deepfake_2019`, `voice_deepfake_cross1`, `classifier_base` | `server/models/` | Not referenced in `hub.py` or any loader |
    84	| `models/classifiers/urgency` absent → urgency falls back to keyword heuristic silently | `server/models/classifiers/` | `urgency/scorer.py:33–47` |
    85	| Android `AntaiRestClient.addContact()` never called — no trusted-contact management UI | `app/.../remote/` | Dead REST method; no matching screen |
    86	| `ChatSocketClient.sendChat/ping` unused | Android remote layer | Never called |
    87	| `CallComponent.kt:178` a11y label copy-paste bug | Android UI | Minor |
    88	| `storage.encryption_key` defaults to `"change-me-please"` — weak if not overridden in `.env` | `config.py:69` | Fernet key must be set in env |
    89	| No OS status-bar notifications — `POST_NOTIFICATIONS` permission declared but `NotificationManager` never used | Android manifest + notifications/ | Zero notification channel setup |
    90	| Phone hash is SHA-256 without salt | `storage/db.py:20–22` | Rainbow-table attack feasible |
    91	
    92	---
    93	
    94	## 5. Gap Analysis — What Still Needs Implementation
    95	
    96	### P0 — DONE ✅
    97	- [x] External streaming WebSocket API (`/api/stream/ws`)
    98	- [x] `StreamingSession` abstraction (real models, no parallel ML)
    99	- [x] Prerecorded file demo streamer (`stream_demo_file.py`)
   100	- [x] Mock dashboard (HTML/JS at `/dashboard/`)
   101	- [x] `normalized_result()` with acoustic/prosody/voiceprint families
   102	
   103	### P1 — PENDING (SIH compliance + product value)
   104	
   105	| Task | Status | Priority |
   106	|---|---|---|
   107	| P1.1 — Verify + document dual acoustic ensemble; annotate dead model dirs | Partially done (models exist, annotation missing) | Medium |
   108	| P1.2 — Scenario-based thresholds (`routine` / `high_value` / `privileged`) | **NOT DONE** | **HIGH** |
   109	| P1.3 — Context structure (caller_id, txn_type, amount, historical_risk) influencing risk | **NOT DONE** | **HIGH** |
   110	| P1.4 — Explicit multi-step recommendation (callback / MFA / escalation) | Partially done in `streaming.py:_recommendation()` | Medium |
   111	| P1.5 — Explainable 3-signal breakdown surfaced to UI (acoustic/prosody/voiceprint) | Done in `normalized_result()` | Done |
   112	| P1.6 — Privacy: encryption key from env (not default), TTL/purge on old rows, salt phone hash | Partial | Medium |
   113	| P1.7 — Android OS status-bar notifications via `NotificationManager` | **NOT DONE** | **HIGH** |
   114	| P1.8 — Multilingual validation: test Hindi + Indian English; document results honestly | **NOT DONE** | Medium |
   115	| Eval harness — accuracy/precision/recall/F1/FPR/FNR over labeled `audio_samples/` | **NOT DONE** | **HIGH** |
   116	
   117	### P2 — PENDING (polish, after P0+P1 stable)
   118	
   119	| Task | Status |
   120	|---|---|
   121	| Android UX renovation (every screen, dead buttons, fake metrics) | **NOT DONE** |
   122	| Score graph / incident history in Android | **NOT DONE** |
   123	| Trusted-contact management UI (wire dead `addContact`) | **NOT DONE** |
   124	| Android OS notifications | **NOT DONE** |
   125	| Institution profile selector UI | **NOT DONE** |
   126	| Windows client (audio capture → shared API → desktop risk UI) | **NOT DONE** |
   127	| Improved LLM explanation wording | Partial |
   128	| Additional language regression tests | **NOT DONE** |
   129	
   130	---
   131	
   132	## 6. Server Models — Actual Status
   133	
   134	| Model dir | Purpose | Exists on disk | Wired in hub? | Notes |
   135	|---|---|---|---|---|
   136	| `models/voice_deepfake` | AST-ASVspoof5 (primary acoustic) | Yes | Yes (`VoiceDeepfakeEngine`) | Primary SSL detector |
   137	| `models/voice_deepfake_cross2` | wav2vec2 cross-check | Yes | Yes (fail-soft) | Optional second model |
   138	| `models/voice_deepfake_cross1` | — | Yes | **No** | Dead, not referenced |
   139	| `models/voice_deepfake_2019` | — | Yes | **No** | Dead, not referenced |
   140	| `models/classifier_base` | — | Yes | **No** | Dead, not referenced |
   141	| `models/speaker_verify` | ECAPA-TDNN voiceprint | Yes | Yes | Speechbrain from HuggingFace |
   142	| `models/asr` | faster-whisper local ASR | Yes | Yes (fallback) | Cloud Deepgram is default |
   143	| `models/llm` | Local GGUF (Qwen/llama) | Yes | Yes | Used for explanation |
   144	| `models/video_deepfake` | Video deepfake detection | Yes | Yes (video path) | Not relevant to voice-only demo |
   145	| `models/classifiers/urgency` | Urgency classifier | **Missing** | Wired but falls back | Falls back to keyword heuristic |
   146	| `models/behavioral_embeddings` | MiniLM behavioral embeddings | Yes | Yes | Multilingual MiniLM |
   147	
   148	---
   149	
   150	## 7. Local vs Cloud Mode (Actual Behavior)
   151	
   152	| Component | Cloud default | Local fallback | Local works? |
   153	|---|---|---|---|
   154	| ASR | Deepgram nova-2 (cloud) | faster-whisper (local) | Yes |
   155	| Voice deepfake | Velma API (optional, requires key) | SSL ensemble (local) | Yes — default is local |
   156	| Speaker verify | — | ECAPA-TDNN (local) | Yes |
   157	| LLM/reasoning | Groq (optional) | Local GGUF | Yes |
   158	| Urgency | — | Keyword heuristic | Yes (heuristic only) |
   159	| Scam pattern | — | Local multilingual classifier | Yes |
   160	| Video deepfake | — | Local ViT models | Yes (not needed for voice demo) |
   161	
   162	**Default config `providers.voice_deepfake = "local"`** — acoustic detection runs fully locally with no API key required. The system can run in a fully local mode today.
   163	
   164	---
   165	
   166	## 8. Android Application — Actual State
   167	
   168	The Android app (`app/`) is a Kotlin/Compose WebRTC calling application with security overlays.
   169	
   170	| Area | Status | Evidence |
   171	|---|---|---|
   172	| WebRTC calling (WebRTC client, signaling) | Real | `webrtc/` package |
   173	| Live risk overlay (Compose UI during call) | Real but incomplete | `ui/` screens |
   174	| SMS interception + analysis | Real (receive + read) | `sms/` package |
   175	| Voiceprint enrollment UI | Exists but stub | `ui/` — no enrollment flow |
   176	| Trusted contact management | **Dead code** — REST method exists, no UI screen | `remote/AntaiRestClient` |
   177	| OS notifications for background alerts | **NOT IMPLEMENTED** | `notifications/` — no NotificationManager calls |
   178	| Incident history screen | **NOT IMPLEMENTED** | No persistent incident list UI |
   179	| Settings screen with scenario controls | **NOT IMPLEMENTED** | No scenario/threshold UI |
   180	| Protection mode toggle | UI element exists, behavior unclear | — |
   181	
   182	---
   183	
   184	## 9. Design Invariants (Must Hold Across All Phases)
   185	
   186	1. **ML models produce evidence. Deterministic policy makes decisions. LLM only explains.** `decision_node` in `nodes.py` is deterministic; `llm_reasoning_node` is explanation-only. Never invert this.
   187	2. **No fabricated scores.** A missing/failed model surfaces as `null` / `—`, never a made-up number. `normalized_result()` enforces this explicitly.
   188	3. **No native GSM/PSTN call interception.** #15 is an architectural boundary. No InCallService/Telecom/accessibility/root workaround.
   189	4. **Prefer reuse over rewrites.** `StreamingSession` reuses `SessionRunner`; the external API drives the same graph as the WebRTC tap.
   190	5. **Simulated bank/telecom only.** No real financial or telecom integration.
   191	
   192	---
   193	
   194	## 10. Roadmap — P1 + P2 (After Audit Approval)

````


**Tool Call: List the `SIH26P1/windows_client` directory's contents**
Status: Completed


# Files:
SIH26P1\windows_client\README.md
SIH26P1\windows_client\audio_capture.py
SIH26P1\windows_client\main.py
SIH26P1\windows_client\notifications.py
SIH26P1\windows_client\requirements.txt
SIH26P1\windows_client\streaming_client.py
SIH26P1\windows_client\ui_overlay.py


**Tool Call: List the `SIH26P1/app` directory's contents**
Status: Completed

# Folders:
SIH26P1\app\app
SIH26P1\app\gradle

# Files:
SIH26P1\app\.gitignore
SIH26P1\app\README.md
SIH26P1\app\build.gradle.kts
SIH26P1\app\gradle.properties
SIH26P1\app\gradlew
SIH26P1\app\gradlew.bat
SIH26P1\app\settings.gradle.kts


**Tool Call: List the `SIH26P1/server` directory's contents**
Status: Completed

# Folders:
SIH26P1\server\audio_samples
SIH26P1\server\scripts
SIH26P1\server\setup
SIH26P1\server\src
SIH26P1\server\tests
SIH26P1\server\video_samples

# Files:
SIH26P1\server\USB_SETUP.md
SIH26P1\server\config.yaml
SIH26P1\server\requirements.txt
SIH26P1\server\run_dev.py
SIH26P1\server\turnserver.conf
SIH26P1\server\usb_setup.bat
SIH26P1\server\usb_setup.sh


## Assistant

<thinking>
The audit document seems to already address all three questions, but I want to cross-check its 15-requirement list against the actual official PS26104 problem statement text, and also check config.yaml for local versus cloud defaults. I should also look at whether windows_client is really a Chrome-related component or just a Python client, and search for the real SIH PS26104 statement online to validate the audit's claims.
</thinking>

**Tool Call: Searched the web: 5 results**
Status: Completed

https://www.codehuntersacademy.com/sih-2026-ps

https://sih2026.vuce.in/ps/SIH26106

https://www.artificialintelligence-news.com/news/scam-ai-announces-qualcomm-partnership-launches-halo-deepfake-detection-model-at-computex-2026/

https://www.alphaxiv.org/abs/2604.23742

https://sih2026.vuce.in/ps/SIH26145

**Tool Call: Read file `server\config.yaml`**
Status: Completed

```SIH26P1/server/config.yaml
     1	# antAI server configuration
     2	server:
     3	  host: "0.0.0.0"
     4	  port: 8765
     5	  debug: true
     6	
     7	auth:
     8	  auto_verify_otp: true        # prototype: skip real SMS, accept any OTP
     9	  token_ttl_hours: 720
    10	  otp_len: 6
    11	
    12	storage:
    13	  url: "sqlite:///antai.db"
    14	  encryption_key: "change-me-please"   # used to derive Fernet key (REPLACE in prod)
    15	
    16	models:
    17	  root: "models"
    18	  device: "auto"               # auto | cuda | cpu
    19	  llm_device: "auto"           # where Qwen runs
    20	  llm_size: "3b"               # 3b | 1.5b
    21	
    22	  # Per-engine enable/disable + weights paths (drop files here manually, or run setup/download_models.py)
    23	  engines:
    24	    asr:            { enabled: true }    # backend chosen in `providers.asr` (deepgram)
    25	    voice_deepfake: { enabled: true }    # backend chosen in `providers.voice_deepfake`
    26	    speaker_verify: { enabled: true }    # local ECAPA voiceprint (models/speaker_verify)
    27	    face:           { enabled: true }
    28	    video_deepfake: { enabled: true }
    29	    community_vit:  { enabled: true }
    30	    dicome:         { enabled: true }
    31	    decof:          { enabled: true }
    32	    lipsync:        { enabled: true }
    33	    scam_pattern:   { enabled: true }
    34	    behavioral:     { enabled: true }
    35	    urgency:        { enabled: true }
    36	    intent:         { enabled: true }
    37	    llm:            { enabled: true }    # backend chosen in `providers.llm` (groq)
    38	
    39	# Hosted-API backends for the VOICE pipeline. The local voice models are turned
    40	# off here and delegated to these APIs; the engine names stay the same so the
    41	# in-call "Models n/m loaded" indicator still shows them running. Video/text/
    42	# other detectors are unchanged (still local). API KEYS live in server/.env
    43	# (gitignored) — never in this file.
    44	providers:
    45	  asr: deepgram              # deepgram | faster_whisper
    46	  voice_deepfake: both       # both | velma | local
    47	  llm: groq                  # groq | local
    48	  deepgram:
    49	    model: nova-2            # nova-2 supports multilingual + language detection
    50	    endpoint: "https://api.deepgram.com/v1/listen"
    51	  velma:
    52	    model_id: "velma-2-synthetic-voice-detection-streaming"
    53	    # Modulate VELMA-2 streaming WebSocket. Auth is via ?api_key= (added from
    54	    # VELMA_API_KEY in .env). Leave endpoint here to override the built-in
    55	    # default, or set VELMA_ENDPOINT in .env.
    56	    endpoint: "wss://platform.modulate.ai/api/velma-2-synthetic-voice-detection-streaming"
    57	    auth_style: query          # query (?api_key=) for Modulate
    58	    config_json: ""            # first WS frame (JSON); "" -> {"behaviors":["preset:synthetic_voice"]}
    59	    response_path: ""          # optional dotted JSON path override to the synthetic-voice score
    60	    # End-of-stream framing. "" = negotiate once on the first segment and reuse
    61	    # the winner (recommended). Pin to binary | text | json | none only if you
    62	    # already know which one this endpoint accepts — the wrong one is closed
    63	    # with 1003 (unsupported data) and every segment silently scores nothing.
    64	    eos: ""
    65	  groq:
    66	    model: "openai/gpt-oss-120b"
    67	    endpoint: "https://api.groq.com/openai/v1/chat/completions"
    68	
    69	pipeline:
    70	  sample_rate: 16000
    71	  vad_aggressiveness: 2       # 0-3 (2 = moderate: keeps quieter speech, drops noise)
    72	  video_fps: 6                 # frame sampling rate
    73	  asr_beam_size: 1
    74	  asr_language: null           # null = auto-detect (en/hi both supported)
    75	  min_segment_s: 0.4           # VAD: emit nothing shorter than this (improves Whisper)
    76	  hangover_s: 0.4              # VAD: silence tolerated before ending a segment
    77	  max_segment_s: 2.0           # VAD: force-flush a long continuous segment (lower = snappier transcript)
    78	  speaker_sim_threshold: 0.60  # voiceprint match threshold (ECAPA cosine sim)
    79	  video_vote_threshold: 2     # ensemble: flag fake when >=N signals agree
    80	  risk_bands:
    81	    log: 40
    82	    verify: 70
    83	  freeze_enabled: true
    84	  collective_check_enabled: true
    85	  live_guidance_interval_s: 2.0
    86	  live_guidance_max_tokens: 180
    87	  live_signals_interval_s: 0.7      # stream per-engine model signals at most this often
    88	  eval_interval_s: 2.0              # periodic pipeline tick (SSL + fusion + LLM run a chunk)
    89	  report_after: true
    90	  deepfake_alert_enabled: true
    91	  # Synthetic-voice prob above this -> pause + popup. Reached when BOTH detectors
    92	  # agree, or when a single detector is very confident. When the two detectors
    93	  # disagree the score is capped below this line on purpose: the risk still rises
    94	  # and the signal is shown, but the user is not interrupted by one model's guess.
    95	  voice_alert_threshold: 0.85
    96	  voice_agreement_bonus: true        # both backends flag -> escalate toward critical
    97	  voice_disagreement_cap: 0.84       # one flags, one clears -> cap below the alert line
    98	  # ...except when the flagging backend is near-certain. Without this exemption a
    99	  # confident clone detection could NEVER alert while the other backend disagreed.
   100	  voice_solo_alert_threshold: 0.95
   101	  # Seconds of that speaker's recent speech handed to the synthetic-voice models.
   102	  # VAD segments are 0.4-2.0s (kept short so the transcript stays live), but these
   103	  # SSL detectors were trained on multi-second utterances: fed one short segment
   104	  # they sit near chance, so a REAL cloned voice scores ~0.5 "uncertain" and never
   105	  # reaches voice_alert_threshold. Raise for accuracy, lower for latency; 0
   106	  # disables the window and scores the raw segment.
   107	  voice_window_s: 4.0
   108	  video_alert_threshold: 0.95       # deepfake-video prob above this -> pause + popup
   109	  deepfake_alert_cooldown_s: 15.0   # min seconds between alerts (per source)
   110	
   111	# ── Scenario-based risk thresholds ─────────────────────────────────────────
   112	# Each scenario overrides the global risk_bands for that session only.
   113	# The dashboard scenario selector and the stream API 'start' frame set this.
   114	# Scenarios with lower thresholds demand earlier verification — appropriate
   115	# for high-value or privileged interactions where the cost of a miss is high.
   116	scenarios:
   117	  routine_call:
   118	    label: "Routine Call"
   119	    description: "Standard personal call — alert only on strong signal."
   120	    risk_verify_at: 75
   121	    risk_critical_at: 90
   122	    context_risk_boost: 0
   123	
   124	  high_value_txn:
   125	    label: "High-Value Transaction"
   126	    description: "Financial transfer or large-value authorization."
   127	    risk_verify_at: 50
   128	    risk_critical_at: 70
   129	    context_risk_boost: 15     # base boost because stakes are high
   130	
   131	  privileged_access:
   132	    label: "Privileged Access"
   133	    description: "IT admin, banking credentials, or sensitive data access."
   134	    risk_verify_at: 40
   135	    risk_critical_at: 60
   136	    context_risk_boost: 20
   137	
   138	collective:
   139	  min_flags_before_shortcircuit: 3
   140	  max_flags_per_account_per_hour: 20
   141	  reputation_min_age_days: 7
   142	
   143	push:
   144	  provider: "ws"               # ws | fcm (fcm = stub adapter)
   145	  fcm_credentials: null
   146	
   147	# ICE (STUN/TURN) for WebRTC call/video media.
   148	# For USB-only media, run coturn with server/turnserver.conf on this machine and
   149	# forward the TURN port to each phone: adb reverse tcp:3478 tcp:3478
   150	# (usb_setup.bat does this). Media then relays over the USB tunnel. If coturn is
   151	# not running these are harmlessly ignored and same-Wi-Fi calls still work.
   152	ice:
   153	  enabled: true
   154	  stun_url: ""                                     # server side: no STUN (offline over USB)
   155	  turn_url: "turn:127.0.0.1:3478?transport=tcp"    # local TURN, reached over adb-reversed 3478
   156	  turn_username: "antai"
   157	  turn_password: "antaipass"
   158	
   159	log_level: "INFO"

```


**Tool Call: Search files for regex `chrome|extension|browser`**
Status: Completed

Found 6 matches:

## Matches in SIH26P1\CALL_APP_SETUP.md

### # antAI Call — Setup & Run Book › ## Step 2 — Find your laptop's Wi-Fi IP › ### No Wi-Fi router? Using a hotspot › L88-92
```
**Alternative — one phone hosts the hotspot.** That host phone can still be one of the two
callers; the laptop and the other phone join its hotspot. This _can_ work, but many phone
hotspots block device-to-device traffic. Test it: from the **other** phone's browser, open
`http://<laptop-IP>:3007` — if it shows "WebSocket Server is running", you're fine; if it times
out, the hotspot is isolating clients, so switch to the laptop-hotspot option above.
```

### # antAI Call — Setup & Run Book › ## Optional — test with the web client (no second phone needed) › L151-152
```
The React client runs in a browser **on the laptop** and calls a phone.

```

### # antAI Call — Setup & Run Book › ## Optional — test with the web client (no second phone needed) › L159-162
```
Open the URL Vite prints (usually `http://localhost:5173`). The web client connects to
`ws://localhost:3007` automatically (same laptop as the backend — no config needed). It
shows its own ID; call between the browser and a phone (the phone still uses the laptop's
LAN IP from Step 5).
```

### # antAI Call — Setup & Run Book › ## Troubleshooting › L168-170
```
- **"Can't reach server at …:3007" toast** → the backend isn't running, the IP is wrong,
  or the firewall is blocking 3007. Recheck Steps 1–3. Confirm from a phone browser that
  `http://<laptop-IP>:3007` shows "WebSocket Server is running".
```

## Matches in SIH26P1\SimpleVideoCallBackend\README.md

### # Simple Video Call Application Using WebRTC (Backend-NodeJs) – Complete Guide › L7-8
```
3. **Web Client** – A browser-based version built with **React.js (Vite)**.  
4. **iOS Client** – (Placeholder for upcoming iOS development).  
```

## Matches in SIH26P1\SimpleVideoCallReacJs\README.md

### # Simple Video Call Application Using WebRTC (Website-ReactJs) – Complete Guide › L7-8
```
3. **Web Client** – A browser-based version built with **React.js (Vite)**.  
4. **iOS Client** – (Placeholder for upcoming iOS development).  
```


## Assistant

<thinking>
Now I have a solid picture: the "SimpleVideoCallReacJs" browser client is just for testing, while the real AI processing runs server-side on Python/GPU or CPU rather than on-device. The Android app simply streams audio to that server via WebRTC for analysis, meaning it's not truly on-device and depends on an external backend.

Let me also try fetching the actual SIH26104 problem statement page from sih2026.vuce.in since the search didn't surface the text directly.
</thinking>

**Tool Call: Fetch https://sih2026.vuce.in/ps/SIH26104**
Status: Completed

Smart India Hackathon/Problem Statementsv2026.1Search problem statements…⌘KShortlistSIH26104

# AI-Powered Real-Time Detection and Prevention of Voice Cloning Impersonation Attacks

SoftwareBlockchain & CybersecurityAdd to shortlistCopyShare on WhatsAppOpen in chatShareSave note

## Metadata & Specs

OrganizationAll India Council for Technical Education (Cyber Security Cell)

Department

Cyber Security Cell

Category

Software

Theme

Blockchain & Cybersecurity

Deadline

30 September 2026

Submitted ideas

0/500Open on sih.gov.in

## Quick Navigation

 PrevSIH26103Next SIH26105

## Problem Description & Statement Details

- Background Recent advancements in generative AI and neural speech synthesis have made high-fidelity voice cloning possible from only a few seconds of recorded audio. Threat actors are exploiting these capabilities to impersonate CXOs,government officials, and trusted individuals in order to initiate fraudulent financial transactions, manipulate employees,or bypass verification procedures in high-risk workflows. Conventional call verification methods-such as caller ID, manual call-back, and basic voice familiarity-are no longer sufficient to distinguish genuine callers from AI-generated or manipulated voices, especially in high-pressure social engineering scenarios.

These attacks are increasingly orchestrated over VoIP, mobile networks, and enterprise collaboration platforms,sometimes combined with leaked personal information to create highly convincing narratives. The absence of automated detection of synthetic or cloned voices in real time significantly increases the likelihood of large-scale financial fraud and reputational damage to institutions that rely heavily on telephonic instructions and approvals.
- Problem Statement Current telephony and communication ecosystems lack a robust, AI-driven mechanism to detect and flag voice cloning or synthetic speech impersonation during live calls. Existing solutions rarely perform granular analysis of acoustic artifacts, prosody, and speech generation patterns and typically cannot provide an actionable risk score while the conversation is ongoing.There is a need for an end-to-end security framework that can analyze incoming voice streams in near real time,determine the likelihood that the caller is using a cloned or AI-generated voice, and provide timely alerts and recommendations before sensitive actions-such as approval of fund transfers or disclosure of confidential information-are taken. The solution must be privacy-preserving, scalable across telecom and enterprise environments, and support multilingual contexts with diverse Indian accents and dialects.
- Proposed Solution Develop an AI-powered, real-time voice integrity verification framework that integrates advanced deep learning,digital signal processing, and contextual analysis to detect AI-generated or manipulated voices. The system should continuously process live or near live audio streams from telephony, VoIP, and collaboration platforms,extract discriminative features, and compute a dynamic impersonation risk score.The framework should expose APIs and SDKs for seamless integration with banking applications, enterprise communication systems, and telecom operator infrastructures, enabling proactive fraud prevention and enhanced cyber resilience in voice channels.
- Key Components
- Multi-Layer Voice Authenticity Analysis o Acoustic and spectral analysis using deep learning models to detect synthesis artifacts, phase inconsistencies, and spectral signatures indicative of cloned or AI-generated audio.

o Prosody and behavioral analysis to model speech rhythm, pitch contours, pauses, and microvariations, differentiating natural human speech from neural TTS outputs.

o Cross-session consistency checks comparing ongoing call features against historical genuine samples (where available) to detect anomalies in speaker identity.
- Real-Time Risk Scoring Engine o Continuous computation of a confidence/risk score indicating the probability of impersonation or synthetic speech.

o Threshold-based alerting logic configurable for different risk scenarios (for example, high-value transaction calls, privileged access approvals).

o Contextual enrichment using metadata such as call origin, known contact information, transaction context, and historical fraud indicators.
- Alerting and User Interaction Layer o Multi-channel alert mechanisms (UI prompts, SMS/email, in-app notifications) for frontline staff and end users.

o Pre-transaction warning prompts recommending secondary verification such as call-back, multifactor authentication, or escalation to supervisors.

o Configurable workflows for banks,enterprises, and government agencies to define automated responses when impersonation risk crosses thresholds.
- Privacy and Compliance Module o Minimal retention of voice recordings with options for on-device or edge inference to reduce central storage of sensitive audio data.

o Support for anonymization or feature-only logging to comply with data protection and privacy requirements.
- Platform and Integration APIs o REST/gRPC APIs and SDKs for integration with core banking systems, contact center platforms, enterprise communication tools, and telecom networks.

o Support for multiple Indian languages and regional accents through language-agnostic feature extraction and language-specific acoustic models.
- Expected Outcomes
- Significant reduction in financial fraud and social engineering incidents driven by voice cloning and AIenabled impersonation.
- Improved trust and assurance in voice-based communication channels for individuals, financial institutions, enterprises, and government organizations.
- Early detection of AI-driven social engineering attacks, enabling proactive containment and incident response.
- A reusable security layer for telecom operators and enterprises that strengthens overall cyber resilience and aligns with national cybersecurity objectives.

## Similar Problem StatementsSame Theme or Organization

SIH26026

### Development of Mobile (Quadruped)/Handheld Device/System for Real-Time Detection of Narcotics and Explosives across Indian Railways.

HardwareBlockchain & CybersecurityMinistry of Railways30 September 2026SIH26105

### AI-Powered Continuous Cyber Risk Quantification and Investment Optimization Platform

SoftwareBlockchain & CybersecurityAll India Council for Technical Education (Cyber Security Cell)30 September 2026SIH26106

### AI-Powered Email Threat Detection, GeoLocation and Forensic Intelligence Platform

SoftwareBlockchain & CybersecurityAll India Council for Technical Education (Cyber Security Cell)30 September 2026SIH26125

### Blockchain-Based Secure Platform for Identity,Access Control, and Digital Asset Management

SoftwareBlockchain & CybersecurityBharat Electronics Limited30 September 2026SIH26141

### Quantum-Inspired Cyber Threat Detection for Digital Signature Security

SoftwareBlockchain & CybersecurityDatasetEgreen Quanta30 September 2026SIH26145

### AI-Based Detection of Cyber Threats in Unidirectional IP Traffic

SoftwareBlockchain & CybersecurityDatasetNational Technical Research Organisation (NTRO)30 September 2026

All India Council for Technical Education (Cyber Security Cell) · Software · Deadline 30 September 2026 Smart India Hackathon/Problem Statements

Community-maintained archive of all 233 <link>problem statements</link> published by Smart India Hackathon. Content: CC-BY-4.0 · Code: MIT. SIH 2026 Problem Statements 233 total 176 software 57 hardware

Unofficial community archive - not affiliated with Smart India Hackathon or the Government of India.

Made by <author>vedantchalke36</author> with love Vedant Chalke · CC-BY-4.0 · MIT

## Command Palette

Search for a command to run...

## Assistant

<thinking>
I've confirmed the official PS26104 requirements align closely with the audit document's expanded 15-point list, validating that SIH_PS26104_AUDIT.md is accurate. Now I have enough to structure the answer, starting with clarifying that the core AI pipeline runs server-side rather than on-device.

The Android app is just a WebRTC client streaming audio to the server. By default it depends on cloud services (Deepgram for ASR, Groq for LLM, optionally Velma for voice deepfake) per config.yaml, though local fallbacks exist too—faster-whisper, a local SSL ensemble, local GGUF LLMs, and ECAPA speaker verification—so it can run fully self-hosted without external APIs, but it still needs an external server machine and isn't truly on-device within the phone itself.

There's also a browser-based React web client (SimpleVideoCallReacJs) for testing calls without a second phone, connecting to the same signaling server and Python backend, plus a Windows desktop client that streams mic audio from a PC. All of this confirms the architecture is client-server rather than on-device, which is what the device-level plan aims to address.

The device-level plan targets call interception specifically, keeping call capture/screening on-device via CallScreeningService while analysis still hits the server — so full on-device inference (ASR, deepfake detection, LLM all running locally via TensorFlow Lite or similar) isn't implemented or planned; that would require significant porting work not present in the current Kotlin codebase.

Now checking whether P1.2's scenario-based thresholds were actually implemented — I see config.yaml now defines scenarios like routine_call, high_value_txn, and privileged_access with risk thresholds, which contradicts the older audit doc saying this wasn't done. The codebase has clearly evolved past what the audit captured, so I should flag this discrepancy and verify current state against nodes.py/streaming.py before finalizing my improvement suggestions.

I'll also check addContact wiring, and run these greps in parallel to speed things up.
</thinking>

**Tool Call: Search files for regex `context_risk_boost|risk_verify_at|risk_critical_at|scenario`**
Status: Completed

Found 17 matches:

## Matches in SIH26P1\server\src\antai\config.py

### L176-181
```

@dataclass
class ScenarioConfig:
    """Per-scenario risk threshold profile.

    Overrides the global ``pipeline.risk_bands`` for a single session.
```

### L185-200
```
    label: str = "Routine Call"
    description: str = ""
    risk_verify_at: float = 75.0
    risk_critical_at: float = 90.0
    context_risk_boost: float = 0.0   # flat risk added at session start

    @classmethod
    def from_dict(cls, d: dict) -> "ScenarioConfig":
        return cls(
            label=d.get("label", cls.label),
            description=d.get("description", ""),
            risk_verify_at=float(d.get("risk_verify_at", 75)),
            risk_critical_at=float(d.get("risk_critical_at", 90)),
            context_risk_boost=float(d.get("context_risk_boost", 0)),
        )

```

### L313-317
```
    providers: ProvidersConfig = field(default_factory=ProvidersConfig)
    log_level: str = "INFO"
    scenarios: dict = field(default_factory=dict)   # name -> ScenarioConfig

    @classmethod
```

### L332-349
```
            providers=ProvidersConfig.from_dict(raw.get("providers", {})),
            log_level=raw.get("log_level", "INFO"),
            scenarios={k: ScenarioConfig.from_dict(v)
                       for k, v in (raw.get("scenarios") or {}).items()},
        )

    def get_scenario(self, name: str | None) -> ScenarioConfig:
        """Return the named scenario, or a default if unknown/None."""
        if name and name in self.scenarios:
            return self.scenarios[name]
        # default: use global risk_bands values
        bands = self.pipeline.risk_bands or {}
        return ScenarioConfig(
            label="Default",
            risk_verify_at=float(bands.get("log", 40)),
            risk_critical_at=float(bands.get("verify", 70)),
        )

```

## Matches in SIH26P1\server\src\antai\gateway\rest_api.py

### L529-538
```


# ---------------- scenarios (P1.2) ----------------
@router.get("/scenarios")
def list_scenarios():
    """Return available scenario names, labels, and threshold values.

    The dashboard scenario selector and the stream API 'start' frame use these
    names. Thresholds are read from ``config.yaml``; no fabricated values here.
    """
```

### L542-549
```
            "label": s.label,
            "description": s.description,
            "risk_verify_at": s.risk_verify_at,
            "risk_critical_at": s.risk_critical_at,
        }
        for name, s in cfg.scenarios.items()
    }

```

## Matches in SIH26P1\server\src\antai\gateway\stream_api.py

### L135-141
```
                session_key = f"stream:{uuid.uuid4().hex[:12]}"

                # P1.2 / P1.3: scenario + caller context forwarded from the
                # dashboard / call-center backend into the detection pipeline.
                scenario = ctrl.get("scenario") or None
                context_raw = ctrl.get("context") or {}
                if isinstance(context_raw, str):
```

### L158-162
```
                                           claimed_identity_id=claimed,
                                           on_update=_sync_update,
                                           scenario=scenario,
                                           context=context_raw)
                await session.begin()
```

### L204-208
```
                         sample_rate: int = Form(0),
                         claimed_identity_id: int = Form(0),
                         scenario: str = Form(""),
                         context_json: str = Form("{}")):
    """One-shot: upload an audio file, stream it through the real pipeline in
```

### L222-227
```

    claimed = claimed_identity_id or None
    # P1.2 / P1.3: optional scenario + caller context from the form upload
    scenario_name = scenario.strip() or None
    try:
        context_dict = json.loads(context_json) if context_json.strip() else {}
```

### L230-234
```
    session = StreamingSession(f"stream:file:{uuid.uuid4().hex[:8]}",
                               speaker_id=1, claimed_identity_id=claimed,
                               scenario=scenario_name, context=context_dict)
    await session.begin()

```

## Matches in SIH26P1\server\src\antai\orchestration\dispatcher.py

### L546-552
```
            "collective_flagged": False, "risk": 0.0,
            "intercept_handled": False,
            # P1.2 / P1.3: scenario + caller context (set by StreamingSession or
            # the stream API; fall back to None / {} for calls without them).
            "scenario": getattr(self, "active_scenario", None),
            "context": getattr(self, "active_context", {}),
        }
```

## Matches in SIH26P1\server\src\antai\orchestration\nodes.py

### L384-399
```

    # --- corroboration requirement ----------------------------------------
    # Use per-scenario thresholds if a scenario is active; fall back to global bands.
    cfg = get_config()
    scenario_name = state.get("scenario")
    scenario = cfg.get_scenario(scenario_name)
    verify_at = scenario.risk_verify_at
    critical_at = scenario.risk_critical_at

    # Context-based risk boost: high-stakes scenarios start with a base penalty
    # so the same voice signals trigger earlier action. This is DECLARED as
    # demo context where used — it never fabricates a detection signal.
    ctx = state.get("context") or {}
    context_boost = float(scenario.context_risk_boost)
    # Additional context signals: large transaction amount bumps risk
    txn_amount = ctx.get("transaction_amount")
```

## Matches in SIH26P1\server\src\antai\orchestration\state.py

### L12-17
```
    callee_id: int
    text: str                    # for message events
    # scenario / caller context (P1.2 / P1.3)
    scenario: str | None         # active scenario name (e.g. "high_value_txn")
    context: dict                # caller context: caller_id, transaction_type,
                                 #   transaction_amount, claimed_identity,
```

## Matches in SIH26P1\server\src\antai\orchestration\streaming.py

### L131-136
```
        "verdict": runner.last_verdict,
        "t": time.strftime("%H:%M:%S"),
        # --- scenario / context (P1.2 / P1.3) --------------------------------
        "scenario": getattr(runner, "active_scenario", None),
        "context": getattr(runner, "active_context", {}),
    }
```

### L162-166
```
                 claimed_identity_id: Optional[int] = None,
                 on_update: Optional[Callable[[dict], None]] = None,
                 scenario: Optional[str] = None,
                 context: Optional[dict] = None):
        self.session_key = session_key
```

### L171-176
```
        self._on_update = on_update
        self._began = False
        # P1.2 / P1.3: scenario + caller context propagated into every graph state
        self.runner.active_scenario: str | None = scenario
        self.runner.active_context: dict = context or {}

```


**Tool Call: Search files for regex `NotificationManager|NotificationChannel|notify\(`**
Status: Completed

Found 15 matches:

## Matches in SIH26P1\app\app\src\main\java\com\codewithkael\simplecall\notifications\RiskNotificationManager.kt

### L1-6
```
package com.codewithkael.simplecall.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
```

### L8-12
```
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.MainActivity
```

### L29-33
```
 */
@Singleton
class RiskNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
```

### L49-57
```
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
```

### L58-62
```
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }
```

### L120-124
```

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_RISK, notif)
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS permission not granted (Android 13+); ignore
```

### L128-132
```
    /** Dismiss any active risk notification (call when the call ends). */
    fun dismissRiskNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RISK)
    }
}
```

## Matches in SIH26P1\app\app\src\main\java\com\codewithkael\simplecall\ui\MainActivity.kt

### L10-14
```
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.codewithkael.simplecall.notifications.RiskNotificationManager
import com.codewithkael.simplecall.ui.screens.HomeScreen
import com.codewithkael.simplecall.ui.theme.SimpleCallTheme
```

### L21-25
```

    @Inject
    lateinit var riskNotificationManager: RiskNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
```

### L28-32
```
        // P1.7: Create the antai_risk_alerts notification channel (idempotent on re-creates).
        // Channel must exist before the app can post any notification on Android 8+.
        riskNotificationManager.createChannels()

        enableEdgeToEdge()
```

### L47-51
```
        // App came to foreground — dismiss any lingering risk notification (user is
        // already looking at the live call screen / home screen).
        riskNotificationManager.dismissRiskNotification()
        SimpleCallApplication.isForegrounded = true
    }
```

## Matches in SIH26P1\app\app\src\main\java\com\codewithkael\simplecall\ui\viewmodel\MainViewModel.kt

### L53-57
```
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import com.codewithkael.simplecall.notifications.RiskNotificationManager
import javax.inject.Inject

```

### L68-72
```
    // button-initiated verify.result to. Nothing in the call path uses it.
    private val messagesRepo: MessagesRepository,
    private val riskNotificationManager: RiskNotificationManager,
    application: Application
) : ViewModel() {
```

### L405-409
```
                // P1.7: Post a status-bar notification if risk crosses the verify
                // threshold while the user is backgrounded.
                riskNotificationManager.onRiskUpdate(
                    risk = signals.risk,
                    band = signals.band,
```

### L695-699
```
        stopAiTap()
        // P1.7: dismiss any lingering risk alert when the call ends
        riskNotificationManager.dismissRiskNotification()
        deepfakeAlertState.value = null
        remoteMediaStream = null
```


**Tool Call: Search files for regex `encryption_key|change-me-please|salt`**
Status: Completed

Found 8 matches:

## Matches in SIH26P1\server\src\antai\config.py

### class StorageConfig › L68-77
```
    url: str = "sqlite:///antai.db"
    encryption_key: str = "change-me-please"
    db_path: Path = field(default_factory=lambda: SERVER_ROOT / "antai.db")

    @classmethod
    def from_dict(cls, d: dict) -> "StorageConfig":
        db_path = _resolve_path(d.get("url", "sqlite:///antai.db").replace("sqlite:///", ""))
        return cls(url=d.get("url", "sqlite:///antai.db"),
                   encryption_key=d.get("encryption_key", "change-me-please"),
                   db_path=db_path)
```

## Matches in SIH26P1\server\src\antai\gateway\auth.py

### def _new_token › L28-30
```
    cfg = get_config()
    s = URLSafeTimedSerializer(cfg.storage.encryption_key, salt="antai-session")
    return s.dumps({"uid": user_id})
```

### def _read_token › L34-40
```
    cfg = get_config()
    s = URLSafeTimedSerializer(cfg.storage.encryption_key, salt="antai-session")
    try:
        data = s.loads(token, max_age=cfg.auth.token_ttl_hours * 3600)
        return int(data["uid"])
    except (BadSignature, SignatureExpired, KeyError, ValueError):
        return None
```

## Matches in SIH26P1\server\src\antai\storage\db.py

### L19-28
```


# MIGRATION NOTE (P1.6): A salt prefix was added to _phone_hash.
# Existing rows hashed without a salt are still found when ANTAI_PHONE_SALT=""
# (the default), which preserves backward compatibility.  Once all clients are
# on a salted deployment, re-hash and rotate: set the env var, run a migration
# script that re-hashes phone_hash for every User row, then clear the old rows.
def _phone_hash(phone: str, salt: str = "") -> str:
    """SHA-256 hash of (salt + phone).  salt comes from ANTAI_PHONE_SALT."""
    return hashlib.sha256((salt + phone).encode("utf-8")).hexdigest()
```

### class Database › def __init__ › L35-49
```
        self.cfg = cfg
        self.crypto = Crypto(cfg.storage.encryption_key)
        # Warn loudly when the placeholder key is still in use (P1.6 privacy hardening).
        if cfg.storage.encryption_key == "change-me-please" and not os.environ.get("ANTAI_ALLOW_DEFAULT_KEY"):
            import logging as _logging
            _logging.getLogger(__name__).critical(
                "SECURITY: storage.encryption_key is the default value 'change-me-please'. "
                "Set a real key in server/.env as STORAGE_ENCRYPTION_KEY or change it in config.yaml. "
                "Set ANTAI_ALLOW_DEFAULT_KEY=1 to suppress this warning in dev/test environments."
            )
        # Salt used for phone-number hashing (P1.6).  Defaults to "" so that
        # existing deployments without the env var keep working unchanged.
        self._phone_salt = os.environ.get("ANTAI_PHONE_SALT", "")
        url = cfg.storage.url
        if url.startswith("sqlite:///"):
```

6 lines remaining in ancestor node. Read the file to see all.

### class Database › def create_user › L63-70
```
        with self._lock, self.session() as s:
            user = User(phone_hash=_phone_hash(phone, self._phone_salt),
                        phone_enc=self.crypto.encrypt(phone),
                        display_name=display_name or "User")
            s.add(user)
            s.commit()
            s.refresh(user)
            return user
```

### class Database › def get_user_by_phone › L76-88
```
        with self.session() as s:
            u = s.scalar(select(User).where(User.phone_hash == _phone_hash(clean, self._phone_salt)))
            if u is not None:
                return u
            # Try with +91 if 10 digits
            if len(clean) == 10 and clean.isdigit():
                u = s.scalar(select(User).where(User.phone_hash == _phone_hash(f"+91{clean}", self._phone_salt)))
                if u is not None:
                    return u
            elif clean.startswith("+91") and len(clean) == 13:
                u = s.scalar(select(User).where(User.phone_hash == _phone_hash(clean[3:], self._phone_salt)))
                if u is not None:
                    return u
```

11 lines remaining in ancestor node. Read the file to see all.

### class Database › def get_user_by_phone › L89-92
```
            elif clean.startswith("+"):
                u = s.scalar(select(User).where(User.phone_hash == _phone_hash(clean[1:], self._phone_salt)))
                if u is not None:
                    return u
```


**Tool Call: Search files for regex `addContact`**
Status: Completed

Found 1 matches:

## Matches in SIH26P1\app\app\src\main\java\com\codewithkael\simplecall\remote\antai\AntaiRestClient.kt

### @Singleton AntaiRestClient › fun addContact › L136-140
```

    /** POST /api/contacts {peer_phone, label} (auto-provisions). Best-effort. */
    suspend fun addContact(peerPhone: String, label: String): Result<Unit> = post(
        "/api/contacts",
        JSONObject().put("peer_phone", peerPhone).put("label", label),
```


**Tool Call: Search files for regex `/api/context|caller_id|transaction_amount|transaction_type`**
Status: Completed

Showing matches 1-20 (there were more matches found; use offset: 20 to see next page):

## Matches in SIH26P1\server\src\antai\gateway\rest_api.py

### def calls › L241-244
```
        from sqlalchemy import or_
        from ..storage.models import Call
        rows = list(s.query(Call).filter(or_(Call.caller_id == user.id, Call.callee_id == user.id))
                    .order_by(Call.started_at.desc()).limit(50))
```

## Matches in SIH26P1\server\src\antai\gateway\signaling.py

### async def _handle_start › L132-142
```
async def _handle_start(msg: dict, caller_id: int, ws: WebSocket, handlers: dict):
    cfg = get_config()
    db = get_db()
    callee_phone = msg.get("callee_phone")
    kind = msg.get("kind", "voice")
    offer = msg.get("offer", {})
    if not callee_phone or not offer.get("sdp"):
        await _send_ws(ws, {"type": "error", "message": "invalid call.start"})
        return
    callee = db.get_user_by_phone(callee_phone)
    if callee is None:
```

37 lines remaining in ancestor node. Read the file to see all.

### async def _handle_start › L143-151
```
        callee = db.create_user(callee_phone, "Contact")  # auto-provision

    call = db.create_call(caller_id, callee.id, kind)
    cm = get_call_manager()

    # create session; tracks are fanned into ingestion when they arrive
    session = await cm.create(call.id, caller_id, callee.id, kind,
                              send=None, on_ingest=None)
    handler = SignalingHandler(session.session_key)
```

### async def _handle_start › L170-175
```
    await get_hub().send(callee.id, "call.incoming", {
        "session_key": session.session_key,
        "kind": kind,
        "caller_phone_hash": db.get_user(caller_id).phone_hash,
        "offer": {"sdp": callee_offer["sdp"], "type": "offer"},
    })
```

### async def _handle_start › L177-181
```
    _ws_handlers[session.session_key] = handler
    handlers[session.session_key] = handler  # this connection (the caller) owns it too
    await handler.runner.begin(kind=kind, caller_id=caller_id, callee_id=callee.id)


```

### async def _handle_ice › L193-199
```
    try:
        if user_id == session.caller_id:
            await session.add_ice("caller", cand)
        else:
            await session.add_ice("callee", cand)
    except Exception as e:
        log.warning("ICE add failed (skipping candidate): %s", e)
```

### async def _handle_accept › L212-216
```
    await session.set_callee_answer(answer["sdp"])
    from ..realtime import get_hub
    await get_hub().send(session.caller_id, "call.state",
                         {"session_key": key, "state": "connected"})
    await get_hub().send(user_id, "call.state",
```

### async def _handle_decline › L221-231
```
    key = msg.get("session_key")
    cm = get_call_manager()
    session = cm.get(key)
    if session is None:
        return
    from ..realtime import get_hub
    await get_hub().send(session.caller_id, "call.state",
                         {"session_key": key, "state": "declined"})
    handler = _ws_handlers.pop(key, None)
    if handler:
        await handler.runner.finish()
```

1 lines remaining in ancestor node. Read the file to see all.

### async def _handle_end › L251-255
```
    await cm.remove(key)
    from ..realtime import get_hub
    await get_hub().send(session.caller_id, "call.state",
                         {"session_key": key, "state": "ended"})
    await get_hub().send(session.callee_id, "call.state",
```

## Matches in SIH26P1\server\src\antai\gateway\tap.py

### class TapCall › def __init__ › L79-89
```
        self.call_id = call_id
        self.user_ids = sorted(user_ids)          # stable roles
        self.caller_id, self.callee_id = self.user_ids[0], self.user_ids[1]
        self.names = dict(names or {})
        self.kind = kind
        self.taps: dict[int, TapSession] = {}
        self._lock = asyncio.Lock()
        self._begun = False
        # unique per-call runner key (a repeat call between the same two
        # users must NOT reuse the previous call's transcript)
        self.session_key = f"tap:call:{call_id}"
```

3 lines remaining in ancestor node. Read the file to see all.

### class TapCall › async def attach › L116-120
```
            self._begun = True
            await self.runner.begin(kind=self.kind, caller_id=self.caller_id,
                                    callee_id=self.callee_id,
                                    participants=list(self.user_ids),
                                    participant_names=self.names)
```

### async def _finish_tap_call › L160-170
```
    """Both taps gone: end the DB call, run the LLM post-call report."""
    key = _pair_key(call.caller_id, call.callee_id)
    _tap_calls.pop(key, None)
    try:
        await call.runner.finish()
    except Exception:
        log.exception("tap: runner.finish failed for %s", call.session_key)
    try:
        get_db().end_call(call.call_id, risk_peak=call.runner.risk_peak)
    except Exception:
        log.exception("tap: end_call failed for %s", call.call_id)
```

## Matches in SIH26P1\server\src\antai\media_sfu\session.py

### class CallSession › def __init__ › L75-89
```
class CallSession:
    def __init__(self, call_id: int, session_key: str, caller_id: int, callee_id: int,
                 kind: str = "voice", send: SendFn | None = None,
                 on_ingest: Callable[[str, object], Awaitable[None]] | None = None):
        self.call_id = call_id
        self.session_key = session_key
        self.caller_id = caller_id
        self.callee_id = callee_id
        self.kind = kind
        self._send = send
        self._on_ingest = on_ingest

        # PC peers with the CALLER client
        # ICE config adds a local TURN relay so media works over USB-only
        # (no Wi-Fi). Passing configuration=None keeps aiortc's default.
```

126 lines remaining in ancestor node. Read the file to see all.

### class CallSession › def _on_remote_track › async def handler › L132-134
```
            if self._on_ingest:
                participant_id = self.caller_id if role == "caller" else self.callee_id
                fanout.add_tap(lambda frame, pid=participant_id: self._on_ingest(pid, frame))
```

### class CallManager › async def create › L219-234
```
    """Registry of live call sessions."""

    def __init__(self):
        self._sessions: dict[str, CallSession] = {}
        self._by_call_id: dict[int, CallSession] = {}
        self._lock = asyncio.Lock()

    async def create(self, call_id: int, caller_id: int, callee_id: int, kind: str,
                     send: SendFn, on_ingest) -> CallSession:
        key = f"call:{call_id}"
        s = CallSession(call_id, key, caller_id, callee_id, kind, send=send,
                        on_ingest=on_ingest)
        async with self._lock:
            self._sessions[key] = s
            self._by_call_id[call_id] = s
        return s
```

14 lines remaining in ancestor node. Read the file to see all.

## Matches in SIH26P1\server\src\antai\orchestration\dispatcher.py

### async def dispatch_message › L64-71
```
    state: AnalysisState = {
        "kind": "message", "session_key": f"msg:{sender_id}:{recipient_id}",
        "caller_id": sender_id, "callee_id": recipient_id,
        "text": body, "transcript": [],
        "events": [], "request_detected": False, "identity_mismatch": False,
        "lipsync_mismatch": False, "collective_flagged": False,
        "intercept_handled": False, "risk": 0.0,
    }
```

### class SessionRunner › def __init__ › L192-202
```
        self.session_key = session_key
        self.kind = "voice"
        self.caller_id = None
        self.callee_id = None
        # every user that should receive realtime pushes for this session.
        # Defaults to [callee_id] (classic SFU behavior: the protected user);
        # the analysis-tap path passes BOTH participants.
        self.participants: list[int] = []
        # optional display names per user id (the analysis-tap path passes the
        # two app usernames so transcript pushes are self-describing)
        self.participant_names: dict[int, str] = {}
```

60 lines remaining in ancestor node. Read the file to see all.

### class SessionRunner › async def begin › L262-266
```
        self._periodic_task: asyncio.Task | None = None

    async def begin(self, kind: str, caller_id: int, callee_id: int,
                    participants: list[int] | None = None,
                    participant_names: dict[int, str] | None = None):
```

### class SessionRunner › async def begin › L267-277
```
        self.kind = kind
        self.caller_id = caller_id
        self.callee_id = callee_id
        self.participants = list(participants) if participants else [callee_id]
        self.participant_names = dict(participant_names or {})
        self._begun = True
        self.events.append({"t": time.strftime("%H:%M:%S"), "what": "Call started"})
        # periodic pipeline tick: even during silence, run a chunk so the SSL
        # models + fusion + LLM keep producing fresh output every few seconds
        if self._periodic_task is None:
            self._periodic_task = asyncio.create_task(self._periodic_loop())
```

### class SessionRunner › def buffered_voice › L364-366
```
        if speaker_id is None:
            # default to whoever is NOT the protected user, i.e. the caller
            speaker_id = self.caller_id
```


## Assistant

<thinking>
It turns out the codebase has moved well past the audit doc—P1.2, P1.3, P1.6, and P1.7 are all actually implemented now, which is worth flagging since the audit is outdated in several places. I still need to verify whether addContact, which exists in AntaiRestClient, is actually wired up to any UI screen.

I'll do one more grep pass to check where addContact is called from and to check on the eval harness, then wrap up given the effort budget—the evidence gathered is solid enough to answer all three parts of the question without over-investigating.
</thinking>

**Tool Call: Search files for regex `\.addContact\(|eval_harness|precision.*recall.*f1|classification_report`**
Status: Completed

Found 2 matches:

## Matches in SIH26P1\app_antai_client_backup\app\src\main\java\com\antai\app\comms\AppRepository.kt

### object AppRepository › fun addContact › val res › L43-51
```
            runCatching {
                val res = AppContainer.api.addContact(phone, label, tag, trusted)
                if (res.optJSONObject("contact_id") != null || res.length() > 0) {
                    db.contacts().upsert(ContactEntity(
                        phone = phone, displayName = label, label = label,
                        relationshipTag = tag, isTrusted = trusted, synced = true))
                }
                true
            }.getOrDefault(false)
```

## Matches in SIH26P1\app_antai_client_backup\app\src\main\java\com\antai\app\ui\AddContactActivity.kt

### AddContactActivity › fun onCreate › val ok › L37-47
```
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = AppRepository.addContact(phone, label, tag, trusted)
                if (ok && trusted) AppRepository.linkTrusted(phone, label, tag)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddContactActivity,
                        if (ok) "Contact saved on device + server" else "Peer must be on antAI to link",
                        Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }
```


**Tool Call: Find paths matching “`SIH26P1/server/scripts/*eval*`”**
Status: Completed

```json
{
  "offset": 0,
  "current_matches_page": [
    "C:\\Users\\sidde\\OneDrive\\Desktop\\FSD\\Hackthonss\\SIH26P1\\server\\scripts\\eval_detector.py",
    "C:\\Users\\sidde\\OneDrive\\Desktop\\FSD\\Hackthonss\\SIH26P1\\server\\scripts\\eval_video_ensemble.py"
  ],
  "all_matches_len": 2
}
```

## Assistant

<thinking>
addContact is only referenced in the legacy backup folder, not the active Kotlin app, confirming it's dead code in the current codebase as the audit claimed. I also want to check eval_detector.py to see if it computes precision/recall/F1 metrics.
</thinking>

**Tool Call: Read file `server\scripts\eval_detector.py` (lines 1-40)**
Status: Completed

```SIH26P1/server/scripts/eval_detector.py
     1	"""Detection evaluation harness (SIH Phase-2 Step 3).
     2	
     3	Reads a labeled audio sample directory and scores each file through the real
     4	AI pipeline, then reports accuracy, precision, recall, F1, FPR, and FNR.
     5	
     6	Directory layout expected:
     7	    audio_samples/
     8	        spoof/   <- files known to be synthetic/AI-generated
     9	        bonafide/ <- files known to be real human speech
    10	
    11	Usage:
    12	    # start the server first: python run_dev.py
    13	    python scripts/eval_detector.py
    14	    python scripts/eval_detector.py --dir audio_samples --server http://localhost:8765
    15	    python scripts/eval_detector.py --threshold 40   # change decision boundary
    16	"""
    17	from __future__ import annotations
    18	
    19	import argparse
    20	import asyncio
    21	import json
    22	import sys
    23	from pathlib import Path
    24	
    25	ROOT = Path(__file__).resolve().parent.parent
    26	sys.path.insert(0, str(ROOT / "src"))
    27	
    28	AUDIO_EXTS = {".wav", ".flac", ".ogg", ".mp3", ".m4a", ".opus"}
    29	
    30	
    31	async def score_file(path: Path, server: str) -> dict | None:
    32	    """POST one file to /api/stream/analyze and return the normalized result."""
    33	    import aiohttp
    34	    url = f"{server.rstrip('/')}/api/stream/analyze"
    35	    try:
    36	        async with aiohttp.ClientSession() as session:
    37	            data = aiohttp.FormData()
    38	            data.add_field("file", open(path, "rb"),
    39	                           filename=path.name,
    40	                           content_type="application/octet-stream")

```


## Assistant

<thinking>
The eval harness confirms the audit doc is now significantly outdated—most P1 items marked as not done are actually complete. The real remaining gaps are the addContact/trusted-contact UI missing from the active app (only present in the backup), plus a few minor items. I have enough evidence now to compile a comprehensive answer covering the on-device vs external/Chrome question and the suggested improvements.
</thinking>

## Research Summary — antAI (SIH PS26104)

### 1. Is it fully on-device, or does it need something external (like Chrome)?

**It is not on-device today — it's a client-server system.** Concretely:

- The Android app (`app/`) is a WebRTC **client**. It captures mic/video and streams it over `/ws/call` or `/ws/tap` to a separate **Python server** (`server/`) that does all the actual AI work (ASR, voice-deepfake ensemble, speaker verification, LLM reasoning, fusion/decision).
- That server must run somewhere reachable by the phone — same Wi-Fi, a USB-tethered laptop (`server/USB_SETUP.md`, `usb_setup.bat`), or a cloud VM. Without it, the app has no detection capability.
- **Cloud dependencies by default** (`server/config.yaml`): `providers.asr: deepgram`, `providers.llm: groq`, `providers.voice_deepfake: both` (local + Velma cloud). These call external APIs unless you flip them.
- **Local fallbacks exist and genuinely work** (per `SIH_PS26104_AUDIT.md` §7): `faster-whisper` (local ASR), the local SSL ensemble (`models/voice_deepfake` + `cross2`), local ECAPA speaker verification, and a local GGUF LLM. So you *can* run the whole brain **self-hosted with zero external API calls** — but it still runs on a server process, not inside the phone's own runtime (no TFLite/ONNX-mobile/NPU inference in the Kotlin code).
- **Chrome/browser path**: `SimpleVideoCallReacJs` is a React/Vite web client explicitly built so you can test a call "from a browser" without a second phone (`CALL_APP_SETUP.md`). It's a QA/demo convenience, not a redesign — it talks to the same Node signaling server + Python backend.
- There's also a **Windows desktop client** (`windows_client/`) that captures mic audio and streams it to the server's `/api/stream` endpoint with a desktop overlay — another external client, same story.
- `ANTAI_V3_DEVICE_LEVEL_PLAN.md` (their own architecture doc) is explicit and honest about this: even the planned upgrade to intercept real cellular calls (`CallScreeningService`/`ROLE_DIALER`) only moves *call capture* on-device — the actual ML inference still round-trips to the server. Full on-device inference is not implemented and isn't currently planned.

**Bottom line:** today it needs an external backend (local server, LAN/USB laptop, or cloud). It can be run *offline/self-hosted* (no internet cloud calls) if you switch the three cloud providers to their local equivalents, but "total on-device" (all inference inside the phone app itself) does not exist in this codebase.

---

### 2. What you can improve

Cross-checking `SIH_PS26104_AUDIT.md` against the **actual current code**, several items the audit flagged as "NOT DONE" have since been implemented (scenario thresholds, context enrichment, Android notifications, salted phone hashing, eval harness — all found wired in during this research). Real remaining gaps:

| Priority | Gap | Evidence |
|---|---|---|
| **High** | Trusted-contact management UI is dead in the *active* app | `app/…/remote/AntaiRestClient.kt` defines `addContact()` but nothing in `app/` calls it. It **is** wired in `app_antai_client_backup/` (a legacy/parallel app) — worth porting that screen into the current app rather than rebuilding it. |
| **High** | `storage.encryption_key` still defaults to `"change-me-please"` in `config.yaml` | The code now warns loudly at startup if unset (good), but the shipped default is still the placeholder — set `STORAGE_ENCRYPTION_KEY`/`ANTAI_PHONE_SALT` in `server/.env` before any demo/deploy. |
| **Medium** | Acoustic prosody signal is mislabeled | "Prosody/behavioral" (`urgency/scorer.py`, `text/behavioral.py`) is actually **text-derived** (keyword heuristics + MiniLM embeddings), not real pitch/F0/energy/rate extraction from audio. PS26104 explicitly asks for "pitch contours, pauses, microvariations" — a real acoustic prosody extractor (e.g., praat-parselmouth or librosa F0/jitter/shimmer) would close a genuine gap vs. the problem statement, not just the internal audit. |
| **Medium** | `models/classifiers/urgency` is missing on disk | Falls back silently to a keyword heuristic. Either ship the model or document the heuristic as the intended primary path so it isn't read as a broken feature. |
| **Medium** | Indian-accent/multilingual validation undocumented | Hindi/Hinglish is *supported* (Deepgram nova-2, local Whisper, Hindi keyword lists) but there's no accuracy report proving it — and the acoustic SSL deepfake models (ASVspoof/VoxCeleb-trained) are Western-data-skewed. Run `eval_detector.py` against Indian-accent + Hindi/Hinglish spoof/bonafide samples and publish the numbers; this is a direct PS26104 ask ("diverse Indian accents and dialects"). |
| **Low** | Dead model directories (`voice_deepfake_cross1`, `voice_deepfake_2019`, `classifier_base`) and a couple of unused REST/WS methods (`ChatSocketClient.sendChat/ping`) | Cosmetic, but a judge grepping the repo will notice. Either wire them or delete/annotate. |
| **Low** | `eval_video_ensemble.py:121` references an undefined `combined` | Will raise `NameError` if that script is ever run live during a demo — fix before any video-path demo. |
| **Product** | No gRPC, only REST | PS26104 says "REST/**gRPC**" — REST alone satisfies "REST/gRPC" as an either/or read, but if a judge is strict, having zero gRPC surface is worth a one-line justification in the pitch (already anticipated in the audit as "plan confirms not required"). |

**Beyond compliance**, the two highest-leverage improvements for demo impact:
1. **Voiceprint/trust-circle family-impersonation flow** — this is your strongest differentiator per the audit, but `speaker_verify` needs a live end-to-end enrol→match demo path with a real UI (currently "exists but stub").
2. **Tier A (pre-ring, no-audio) call screening**, per `ANTAI_V3_DEVICE_LEVEL_PLAN.md` — this directly answers the "will a scammer ever install your app?" objection that judges are likely to raise, and it's flagged as the highest-value/lowest-effort phase not yet built.

---

### 3. SIH PS26104 minimum-requirement coverage

I pulled the **official problem statement** (sih2026.vuce.in/ps/SIH26104 — "AI-Powered Real-Time Detection and Prevention of Voice Cloning Impersonation Attacks") and checked it against the audit + live code. It has 6 "Key Components" groups; here's the honest status per group:

| PS26104 requirement group | Status | Notes |
|---|---|---|
| Acoustic/spectral synthesis-artifact detection | ✅ Real | Dual-model local SSL ensemble (AST-ASVspoof5 + wav2vec2), + optional cloud Velma. |
| Prosody & behavioral analysis | ⚠️ Partial/mislabeled | Currently text-derived (keywords + MiniLM), **not** true acoustic prosody (pitch/pause/microvariation). This is the single clearest gap vs. the literal PS text. |
| Cross-session consistency vs. historical samples | ✅ Real | ECAPA-TDNN voiceprint, encrypted storage, cosine-sim threshold, live compare. |
| Continuous 0–100 risk score, streamed | ✅ Real | Live WebSocket push, dead-zone fusion, streaming API + dashboard. |
| Scenario-configurable thresholds (routine/high-value/privileged) | ✅ **Now implemented** (audit doc is stale here) | `config.yaml` scenarios + `nodes.py` fusion actually consumes `risk_verify_at`/`risk_critical_at`/`context_risk_boost`. |
| Contextual enrichment (caller/txn/history) | ✅ **Now implemented** (audit doc is stale here) | `context` dict (caller_id, transaction_type, transaction_amount, claimed_identity) flows from `stream_api.py` → `dispatcher.py` → `nodes.py`. |
| Multi-channel alerts (UI/SMS/email/notifications) | ✅ Mostly real | In-app Compose alerts + **Android OS status-bar notifications now implemented** (`RiskNotificationManager`, contradicts the audit's "NOT DONE"). SMS receive/read real; email still absent. |
| Pre-transaction warning + secondary verification | ✅ Real | `intercept/controller.py` per-type hold (otp/money/credential/link), user confirm/override. |
| Institution-specific configurable workflows | ❌ Not implemented | No institution/bank/call-center profile concept anywhere in the code. |
| Minimal retention / on-device or edge inference option | ⚠️ Partial | No raw-audio persistence, local-model fallback exists for full self-hosting — but "edge/on-device" in the strict sense (inference *inside* the phone) doesn't exist (see §1). No TTL/purge job on old DB rows yet. |
| Anonymization / feature-only logging | ⚠️ Partial | Phone numbers now **salted** SHA-256 (fixed since audit) + Fernet-encrypted voiceprints/messages, but `verdict_text`/report bodies are still stored in plaintext, keyed by re-identifiable `user_id`. |
| REST/gRPC APIs + SDKs | ✅ Partial | ~23 REST endpoints + WebSocket streaming; no gRPC, no packaged SDK (just raw HTTP/WS contracts). |
| Multilingual / Indian-accent support | ⚠️ Partial, unvalidated | Hindi/Hinglish supported at ASR + keyword layers; no documented accuracy numbers on Indian-accented speech, and the acoustic deepfake models are trained on Western datasets. |

**Verdict:** the two functionally load-bearing requirements groups that are **still genuinely missing or weak** against the literal PS text are **(a) true acoustic prosody analysis** (currently substituted by text-based heuristics) and **(b) institution-specific configurable workflows** (not started at all). Everything else has at least a partial-to-real implementation, and several items the project's own audit doc lists as "NOT DONE" (scenario thresholds, context enrichment, Android notifications, phone-hash salting) have since been completed in the code — the `SIH_PS26104_AUDIT.md` file itself is now out of date and should be refreshed before it's shown to judges, since it currently understates the project's actual completeness.

## User

can run complete models on device using npu in phone like that.

## Assistant

<thinking>
I'm thinking through whether the antAI models could run entirely on-device using a phone's NPU, similar to how Scam.ai's Halo leverages Qualcomm hardware on desktop. I need to work through each model in the pipeline—starting with ASR, where Deepgram runs in the cloud but a local alternative like whisper.cpp or a TFLite version of Whisper could potentially handle on-device transcription for Android.

For deepfake detection, the AST-ASVspoof5 and wav2vec2 cross-check models are transformer-based and moderately large, so they'd need ONNX conversion and quantization before running on an NPU delegate, which is feasible but not trivial. Speaker verification via ECAPA-TDNN is much smaller and already has known mobile deployments, making it well-suited for edge use, while the LLM reasoning component is the biggest challenge since most mobile LLM runtimes still rely primarily on CPU/GPU rather than true NPU acceleration.

Since the design treats the LLM as explanation-only rather than decision-critical, I'm realizing it doesn't need to be safety-critical on-device and could be scaled down or dropped if resources are tight. VAD via Silero is already lightweight enough for real-time mobile use, and video deepfake detection models are heavier but may not need on-device support since this project's focus is voice. I'm now cataloging the available frameworks for accessing phone NPUs, weighing NNAPI's deprecation against TensorFlow Lite's various hardware-specific delegates.

MLC-LLM also supports Vulkan on Android GPU and can target QNN NPU on newer Snapdragon chips. Given this landscape, I should look into the current state of the art for running voice-deepfake detection models (wav2vec2/AST) and ECAPA speaker verification on Android NPUs specifically.

I want to search for concrete guidance on quantized wav2vec2 with NNAPI on Android, mobile ECAPA-TDNN deployment, ONNX Runtime's QNN execution provider for Snapdragon, and on-device NPU approaches used by faster-whisper and llama.cpp, so I can ground a phased engineering plan tailored to their actual model stack and weigh quantization accuracy loss, latency, battery, and chip fragmentation tradeoffs.
</thinking>

**Tool Call: Searched the web: 5 results**
Status: Completed

https://github.com/onnxruntime/onnxruntime-qnn

https://docs.qualcomm.com/doc/80-80022-15B/topic/run-an-onnx-model-using-ort.html

https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html

https://docs.qualcomm.com/doc/80-70029-15B/topic/run-an-onnx-model-using-ort.html

https://github.com/onnxruntime/onnxruntime-qnn/releases

**Tool Call: Searched the web: 5 results**
Status: Completed

https://huggingface.co/KIRILLEVS125/ECAPA-QAT

https://doi.org/10.1109/apsipaasc58517.2023.10317337

https://gitcode.com/xujiashuai/speech_ecapa-tdnn_sv_en_voxceleb_16k

https://doi.org/10.48550/arxiv.2110.04438

https://sites.duke.edu/dkusmiip/files/2022/11/qingjian.pdf

**Tool Call: Searched the web: 5 results**
Status: Completed

https://github.com/chraac/llama-cpp-qnn-builder

https://arxiv.org/html/2509.23324v1

https://github.com/haozixu/llama.cpp-npu

https://github.com/tetsuo974/ollama_npu

https://github.com/haozixu/llama.cpp-npu/blob/refs/heads/htp-backend/README.md

**Tool Call: Search files for regex `class.*Engine|torch\.load|from_pretrained|AutoModel|wav2vec2|ast-|Wav2Vec2|num_params|model_size`**
Status: Completed

Found 7 matches:

## Matches in SIH26P1\server\src\antai\inference\voice\asr.py

### class AsrEngine › L26-36
```
class AsrEngine(BaseEngine):
    name = "asr"

    def _load(self) -> bool:
        if get_config().providers.asr == "deepgram":
            return self._load_deepgram()
        return self._load_faster_whisper()

    # --------------------------------------------------------- Deepgram (API)
    def _load_deepgram(self) -> bool:
        prov = get_config().providers
```

80 lines remaining in ancestor node. Read the file to see all.

## Matches in SIH26P1\server\src\antai\inference\voice\deepfake_voice.py

### L1-11
```
"""Synthetic/AI-voice (deepfake) detection - multi-model SSL ensemble.

Two independent SSL models run per clip; no single model decides:
  A. AST-ASVspoof5 (Audio Spectrogram Transformer, ASVspoof5)
       models/voice_deepfake          (primary, strong on EN TTS)
  B. Wav2Vec2 deepfake-voice detector (SSL wav2vec2)
       models/voice_deepfake_cross2   (cross-check, catches non-EN TTS)

Verdict: flag as spoof when max(A, B) > 0.7 (a single model may miss its
training distribution; agreement of two independent SSL models is the signal).
Margins: <0.4 bonafide, >0.7 spoof, in between "uncertain".
```

1 lines remaining in ancestor node. Read the file to see all.

### class VoiceDeepfakeEngine › L33-43
```
class VoiceDeepfakeEngine(BaseEngine):
    name = "voice_deepfake"

    # which backend is actually producing numbers right now: "velma" | "local".
    # Exposed (not underscore-prefixed) so GET /api/debug/models reports it and a
    # silent fallback is visible instead of looking like a healthy Velma.
    backend: str | None = None
    _local_ready: bool = False
    _local_device: str | None = None
    _velma_failures: int = 0
    # live per-backend capability; class-level defaults so detection_live() is safe
```

361 lines remaining in ancestor node. Read the file to see all.

### class VoiceDeepfakeEngine › def _load_local › L158-168
```
            self._models: list[dict] = []
            # primary (AST ASVspoof5)
            p = self._load_one(primary_dir, device)
            if p is None:
                return False
            self._models.append({"name": "asv5", **p})
            # cross-check SSL wav2vec2 (optional; fail-soft)
            cross_dir = Path(cfg.models.root) / "voice_deepfake_cross2"
            if cross_dir.exists():
                c = self._load_one(cross_dir, device)
                if c is not None:
```

3 lines remaining in ancestor node. Read the file to see all.

### class VoiceDeepfakeEngine › def _load_one › L178-181
```
            from transformers import (AutoFeatureExtractor,
                                      AutoModelForAudioClassification)
            processor = AutoFeatureExtractor.from_pretrained(str(model_dir))
            model = AutoModelForAudioClassification.from_pretrained(str(model_dir))
```

### class VoiceDeepfakeEngine › def _load_one › L184-187
```
                from transformers import (AutoFeatureExtractor,
                                          Wav2Vec2ForSequenceClassification)
                processor = AutoFeatureExtractor.from_pretrained(str(model_dir))
                model = Wav2Vec2ForSequenceClassification.from_pretrained(str(model_dir))
```

## Matches in SIH26P1\server\src\antai\inference\voice\speaker_verify.py

### class SpeakerVerifyEngine › L23-33
```
class SpeakerVerifyEngine(BaseEngine):
    name = "speaker_verify"

    def _load(self) -> bool:
        cfg = get_config()
        savedir = Path(cfg.models.root) / "speaker_verify"
        if not savedir.exists():
            log.warning("speaker_verify weights not downloaded (%s)", savedir)
            return False
        try:
            from speechbrain.inference.speaker import EncoderClassifier
```

45 lines remaining in ancestor node. Read the file to see all.


## Assistant

<thinking>
I now have a clear picture of the model sizes involved: transformer-based audio classifiers like AST-ASVspoof5 (~85-90M params) and Wav2Vec2ForSequenceClassification (~95M params), a much smaller speechbrain ECAPA model (~20M params), plus Qwen LLMs at 1.5b/3b in GGUF format. With these figures in hand, I can put together a grounded, concrete answer about the feasibility of running these specific models on-device.

Going model by model, VAD via Silero is trivial and already runs fine on CPU without needing NPU at all. Speaker verification with ECAPA-TDNN looks very feasible on NPU too — with INT8/INT4 quantization it shrinks to around 4MB with ~66ms CPU latency, and can be converted through ONNX to NNAPI or QNN execution providers or TFLite. For ASR, options like whisper-tiny/base or Silero could run through whisper.cpp or whisper.tflite, though I need to flag that faster-whisper's CTranslate2 backend won't run directly on Android NPU — it would need re-exporting to ONNX/TFLite first for QNN/NNAPI support.

The voice deepfake ensemble models (AST-ASVspoof5, wav2vec2) are the riskiest to port cleanly — their transformer/attention ops have much weaker Hexagon NPU support than CNN ops do, often forcing partial CPU fallback that erodes latency gains, though some quantized deployments do exist at reduced accuracy. The LLM piece (Qwen 1.5B/3B) is genuinely bleeding-edge for on-device NPU since standard llama.cpp only supports CPU/GPU, though experimental Hexagon NPU backends are emerging in research.

Given the design invariant that the LLM should only explain and never decide, I'm leaning toward keeping it on CPU with int4 quantization via llama.cpp's Vulkan/OpenCL delegate, or dropping it in favor of a deterministic template explanation since it isn't safety critical. I'm now considering the broader problem of cross-vendor NPU fragmentation — Qualcomm needs QNN SDK/Hexagon NPU access through ONNX Runtime's QNN execution provider or TFLite Hexagon delegate, since NNAPI is being phased out in favor of vendor-specific drivers, and I still need to work out how MediaTek's approach differs.

AI Hub) and accept other chips fall back to CPU/GPU delegate, which still works but is slower and drains more battery. Given the hackathon's time constraints and the project's existing "ML evidence / deterministic decision / LLM explains" invariant, a pragmatic phased approach makes sense: start by porting VAD and ECAPA speaker verification fully on-device via TFLite/ONNX with NNAPI/QNN, since those models are small and low-risk.

As phase two, I'd port the acoustic deepfake ensemble (AST + wav2vec2) to ONNX INT8 and test on QNN, benchmarking accuracy and latency on a real Snapdragon device — the audit's dead-zone/agreement-bonus logic should tolerate some quantization loss since it's just one signal among several, though attention-op support on Hexagon is the main risk to budget testing time for.

For phase three, I'd defer ASR and LLM server-side: faster-whisper's backend lacks a direct NPU path, and re-exporting Whisper on-device would likely regress Indian-language accuracy versus Deepgram nova-2, while the LLM explanation step is explicitly non-safety-critical by their own design, making server-side the cheapest, most defensible choice.

This lands on a genuine hybrid architecture — the safety-critical voiceprint and deepfake ensemble run fully on-device on the NPU with zero network dependency, satisfying the "edge inference" requirement literally, while ASR/LLM degrade gracefully offline or stay server-assisted. That's a stronger pitch than an all-or-nothing story, since I can honestly claim edge inference where it matters most while being transparent about what still needs connectivity.

Now I'm mapping out the concrete Android-side engineering path, starting with exporting the PyTorch checkpoints.

Writing quantization and deployment steps...

I want to be upfront that this is inherently per-chipset engineering, not portable — I'd realistically scope Snapdragon as the primary target given its tooling maturity and Android market share, and flag that NPU acceleration can silently fall back to slower CPU paths when ops aren't supported, so gains aren't guaranteed. Importantly, the actual decision logic in the scoring pipeline stays untouched — I'm just swapping the network call for a local ONNX Runtime session.

I also want to flag that quantization's accuracy hit needs real measurement against their eval_detector.py harness rather than assumption, since it could force adjustments to their threshold constants, and that running a 90M-parameter transformer every few seconds during a call has real battery/thermal implications that need on-device testing before calling it viable. Now I'll write up the answer concisely, grounded in specific repo files and models, with a phased plan and a clear feasibility verdict per model — no more tool calls needed since this is a consulting-style question I already have enough grounding for.


</thinking>

## Can antAI run fully on-device using the phone's NPU?

**Short answer: partially, and only for some models — not the whole pipeline, and not portably across chip vendors.** Here's the model-by-model reality, based on what's actually in `server/src/antai/inference/`:

| Model | What it is now | On-device NPU feasibility | Verdict |
|---|---|---|---|
| VAD (Silero) | Already tiny (~1–2 MB) | Trivial — runs fine on CPU alone | ✅ Don't even bother with NPU |
| Speaker verify (`speaker_verify.py` — ECAPA-TDNN via speechbrain) | ~20M params | **High.** ECAPA is the most mobile-proven model here — quantized (INT4/INT8) versions exist at ~4 MB / 66 ms CPU latency, and NPU ports (ONNX→NNAPI/QNN, or TFLite Hexagon delegate) are well-documented | ✅ Real, ship it first |
| Voice deepfake ensemble (`deepfake_voice.py` — AST-ASVspoof5 + wav2vec2, ~90–95M params each, transformer/SSL) | Two `transformers` models | **Medium risk.** ONNX export + INT8 QDQ quantization works, and Qualcomm's QNN execution provider for ONNX Runtime targets exactly this (Snapdragon Hexagon NPU). But attention/softmax/layernorm op coverage on NPU delegates is narrower than CNN coverage — some ops may silently fall back to CPU, eating the latency win. Needs real device benchmarking, not just "should work" | ⚠️ Doable, but budget real device-testing time |
| ASR (`asr.py` — Deepgram cloud / faster-whisper local) | faster-whisper uses **CTranslate2**, which has no Android/NPU path at all | Would require re-exporting to a plain Whisper checkpoint → ONNX/whisper.cpp/whisper.tflite — a separate porting effort, and a tiny/base on-device Whisper will likely lose accuracy vs. Deepgram nova-2, especially for Hindi/Hinglish | ⚠️ Possible but a real accuracy trade-off |
| LLM reasoning (Qwen/llama GGUF via `llama.cpp`) | 1.5B–3B params | Standard llama.cpp on Android uses **CPU or GPU (Vulkan/OpenCL)**, not NPU. NPU backends for llama.cpp exist (`ggml-hexagon`, Qualcomm QNN fork, the `llama.cpp-npu` research prototype) but are explicitly experimental/research-grade, require Snapdragon 8 Gen 2+, and are not production-ready | ❌ Not realistically NPU-portable today |

### The bigger catch: NPU access isn't one API

There's no single "run on phone NPU" switch — it's vendor-specific:
- **Qualcomm (Snapdragon)** → QNN SDK / Hexagon NPU, exposed via ONNX Runtime's QNN execution provider or TFLite's Hexagon delegate. This has by far the most mature tooling (Qualcomm AI Hub ships pre-validated quantized models).
- **MediaTek** → NeuroPilot/APU SDK.
- **Samsung Exynos** → Samsung Neural SDK.
- **Google Tensor (Pixel)** → different delegate again.
- Android's old vendor-agnostic layer (**NNAPI**) is being phased out by Google, not the long-term answer.

So "runs on the NPU" in practice means: **pick Snapdragon as the primary target, ship the same ONNX file everywhere, and let it gracefully fall back to CPU (XNNPACK) or GPU on every other chipset.** That's a legitimate, honest claim — "full universal NPU support" is not.

### Recommended phased plan (matches your project's existing honesty style — Tier A/B/C for calls, same idea for compute placement)

1. **Phase 1 — ship now, low risk:** Port `speaker_verify.py` (ECAPA) fully on-device (ONNX → QNN EP / NNAPI, INT8). This alone lets your strongest differentiator — family-voiceprint matching — run with **zero network round-trip**, which is a genuine, demoable "edge inference" claim satisfying PS26104's privacy ask literally.
2. **Phase 2 — medium risk, real payoff:** Port the voice-deepfake ensemble (`deepfake_voice.py`) to ONNX INT8, benchmark on an actual Snapdragon device against your existing `eval_detector.py` harness (`server/scripts/eval_detector.py`) — quantization can shift `voice_alert_threshold` / `voice_disagreement_cap` in `config.yaml`, so re-measure, don't assume.
3. **Phase 3 — keep server-assisted:** ASR and LLM stay where they are (cloud/server), because (a) CTranslate2→mobile is a real porting project with an accuracy cost, and (b) your own design invariant already states "LLM only explains, never decides" — so it's the lowest-value, highest-effort thing to force onto an NPU. If you want an offline story for the LLM step, swap in a deterministic template explanation when there's no network, rather than chasing experimental NPU LLM backends.

**Net result:** a genuine **hybrid edge/server architecture** — the two safety-critical signals (voiceprint identity check + voice-deepfake scoring) run on-device on the NPU with no network dependency, while ASR/LLM remain server-assisted with graceful degradation offline. That's a stronger, more defensible pitch than claiming either "fully on-device" (not true today) or "fully server-side" (undersells what's actually portable), and it reuses your existing fusion/decision code unchanged — you'd only relocate where `voice_deepfake` and `speaker_verify` scores come from (local ONNX Runtime session instead of an HTTP round-trip into `orchestration/nodes.py`), not the deterministic policy itself.

## User

Model | What it is now | On-device NPU feasibility | Verdict |
|---|---|---|---|
| VAD (Silero) | Already tiny (~1–2 MB) | Trivial — runs fine on CPU alone | ✅ Don't even bother with NPU |
| Speaker verify (`speaker_verify.py` — ECAPA-TDNN via speechbrain) | ~20M params | **High.** ECAPA is the most mobile-proven model here — quantized (INT4/INT8) versions exist at ~4 MB / 66 ms CPU latency, and NPU ports (ONNX→NNAPI/QNN, or TFLite Hexagon delegate) are well-documented | ✅ Real, ship it first |
| Voice deepfake ensemble (`deepfake_voice.py` — AST-ASVspoof5 + wav2vec2, ~90–95M params each, transformer/SSL) | Two `transformers` models | **Medium risk.** ONNX export + INT8 QDQ quantization works, and Qualcomm's QNN execution provider for ONNX Runtime targets exactly this (Snapdragon Hexagon NPU). But attention/softmax/layernorm op coverage on NPU delegates is narrower than CNN coverage — some ops may silently fall back to CPU, eating the latency win. Needs real device benchmarking, not just "should work" | ⚠️ Doable, but budget real device-testing time |
| ASR (`asr.py` — Deepgram cloud / faster-whisper local) | faster-whisper uses **CTranslate2**, which has no Android/NPU path at all | Would require re-exporting to a plain Whisper checkpoint → ONNX/whisper.cpp/whisper.tflite — a separate porting effort, and a tiny/base on-device Whisper will likely lose accuracy vs. Deepgram nova-2, especially for Hindi/Hinglish | ⚠️ Possible but a real accuracy trade-off |
| LLM reasoning (Qwen/llama GGUF via `llama.cpp`) | 1.5B–3B params | Standard llama.cpp on Android uses **CPU or GPU (Vulkan/OpenCL)**, not NPU. NPU backends for llama.cpp exist (`ggml-hexagon`, Qualcomm QNN fork, the `llama.cpp-npu` research prototype) but are explicitly experimental/research-grade, require Snapdragon 8 Gen 2+, and are not production-ready | ❌ Not realistically NPU-portable today




tell me one question why only this models are this other approch which will good model or we can fine tuning models

## Assistant

<thinking>
The user wants to know why I settled on this specific model stack—Silero VAD, ECAPA-TDNN, AST-ASVspoof5 with wav2vec2, faster-whisper/Deepgram, and Qwen/llama—and whether alternative architectures or fine-tuning could work better.

I'll break down my answer by explaining the rationale behind each model choice (lightweight, pretrained, task-appropriate, well-established), then suggest alternatives per category with tradeoffs, and finally discuss fine-tuning as a complementary strategy—especially valuable for adapting to Indian-accent/Hindi speech and scam-call-specific audio data. Starting with VAD: Silero is already near state-of-art for this lightweight task, so alternatives like WebRTC VAD or Pyannote don't offer much improvement, and fine-tuning wouldn't add much value since it already generalizes well across languages.

For ECAPA-TDNN speaker verification, I note it's the standard well-documented baseline with small quantized variants available, though alternatives like Titanet, ResNetSE34, or WavLM-based embeddings could be competitive or more edge-friendly. The highest-value fine-tuning opportunity here is adapting ECAPA to Indian-accented voices for the family voiceprint feature, since it's trained on Western VoxCeleb data by default, and distillation could also help shrink it for mobile use. Now I'm considering the rationale for the deepfake detection stack built on AST-ASVspoof5 and wav2vec2, since ASVspoof5 is the standard benchmark for synthetic speech detection.

I'm also weighing lighter-weight alternatives like RawNet3, RawNet2, or AASIST, which are raw-waveform models that perform competitively or better on ASVspoof benchmarks while being smaller and faster than full transformer-based AST. Fine-tuning would be especially valuable here too, since these deepfake detectors are trained mostly on Western/English TTS-generated fakes and likely generalize poorly to Indian speech patterns and accents.

2 offers native multilingual and Hindi support with low-latency streaming, while faster-whisper gives efficient offline CPU inference via CTranslate2. But AI4Bharat's IndicWhisper/IndicConformer models are specifically fine-tuned for Indian languages and accents, which is probably a much stronger fit here than generic Whisper or Deepgram for non-English content.

Given this is a government-sponsored SIH hackathon, using Bhashini's ASR APIs could be a compelling pitch alignment since it ties directly into the government's own tech stack. Both Whisper and IndicWhisper could also be fine-tuned further on collected regional scam-call audio to adapt to scam script vocabulary and telecom noise conditions.

For the LLM reasoning component, Qwen/Llama seems chosen for its local-hostability and decent instruction-following in GGUF format for llama.cpp, though smaller specialized language models could be worth considering as alternatives.

Since this is explanation-only rather than decision-making, fine-tuning is lower priority since a good prompt template captures most of the value, though LoRA-finetuning on verdict-to-explanation pairs could reduce hallucination and improve trust. I want to organize my answer around explaining the current model choices' rationale, then listing better alternatives per model with specific benchmarks and licensing considerations.

I should note where fine-tuning would help most — the deepfake ensemble and speaker verification, due to the Indian-accent domain gap — versus lower priority areas like VAD and the LLM. My concrete recommendation is fine-tuning over a full architecture swap for near-term gains, since existing infrastructure (loaders, thresholds, fusion logic) is already tuned to current models, and fine-tuning preserves that interface while improving accuracy on the actual target distribution.

That said, I want to flag that switching antispoofing to something like AASIST could offer a bigger structural win than fine-tuning the current AST/wav2vec2 setup, especially given NPU-latency constraints, since AASIST/RawNet-type raw waveform models tend to be smaller and faster. I think I have enough domain knowledge, but I want to do a couple of quick web searches to confirm current state-of-the-art antispoofing benchmarks (AASIST/RawNet3) and check IndicWhisper/Bhashini's accuracy claims for Indian languages, so I can ground the answer in real citations rather than pure recall.
</thinking>

**Tool Call: Searched the web: 5 results**
Status: Completed

https://arxiv.org/html/2606.11674

https://github.com/Security-FIT/SpAArSIST

https://arxiv.org/pdf/2402.17127

https://arxiv.org/pdf/2110.01200

https://arxiv.org/html/2408.17352

**Tool Call: Searched the web: 5 results**
Status: Completed

https://ai4bharat.iitm.ac.in/areas/model/ASR/IndicWhisper/

https://arxiv.org/pdf/2512.10967

https://projecttech4dev.org/building-the-missing-evaluation-layer-for-voice-ai-in-indian-languages/

https://voireport46466.ai.joshtalks.com/

https://doi.org/10.48550/arxiv.2305.15386

## Assistant

<thinking>
This confirms AASIST's lightweight variant beats current systems by 20% with far fewer parameters than AST/wav2vec2, and IndicWhisper's fine-tuning shows a meaningful WER improvement across most benchmarks for Indian languages — solid evidence for both better architecture and fine-tuning value. I'm ready to draft the final answer.
</thinking>

## Why these specific models — and is there a better path?

**Why these were chosen (my read of the code):** they're all the standard, off-the-shelf pretrained checkpoint for each subtask — easy to `pip install`/`from_pretrained()`, no training required, "good enough" to demo. That's a rational hackathon choice, but it means the project picked **convenience over fit**, and it shows up as exactly the two known weaknesses your own audit and I already flagged: Western-data-skewed acoustic models and no Indian-accent validation. So — yes, both a better architecture exists, and fine-tuning would help, and they solve *different* problems. Here's the breakdown:

### 1. Voice deepfake detector — there IS a structurally better model: **AASIST**

Your current setup (AST-ASVspoof5 + wav2vec2, ~90–95M params each) is a reasonable ensemble, but the published anti-spoofing literature has moved past plain wav2vec2/AST classifier heads:

- **AASIST** (graph attention network over spectro-temporal features) beats prior SOTA by **20% relative** on ASVspoof, and its lightweight variant **AASIST-L has only 85K parameters** — literally ~1000x smaller than your current wav2vec2 model, while scoring *better*.
- Recent variants (**SpAArSIST**, **AASIST3**) push this further — SpAArSIST cuts backend compute another 20% and improves out-of-domain robustness to 2.82% EER.
- The common modern recipe is **XLS-R/wav2vec2 frontend (frozen, for feature extraction) + AASIST backend (small, trainable)** — you'd keep wav2vec2 as a feature extractor (which you already have) but replace its classifier head with the AASIST graph-attention pooling, which is dramatically smaller and more accurate.

**Why this matters for your NPU question too:** an 85K-parameter graph-attention model is trivially cheap to run on-device — CPU is probably enough, no NPU heroics needed. So switching architecture here solves *two* problems at once (accuracy **and** the NPU-portability problem I raised earlier).

### 2. ASR — a better-fit model exists for your actual requirement: **IndicWhisper / Bhashini**

Deepgram nova-2 and generic Whisper are trained mostly on Western-language data. AI4Bharat's **IndicWhisper** is literally Whisper fine-tuned on **10.7K hours across 12 Indian languages**, and it has the lowest WER in **39 of 59** Indian-language benchmarks (avg. 4.1-point WER reduction vs. baseline Whisper). Given PS26104 explicitly demands "multilingual contexts with diverse Indian accents and dialects," this is a much closer fit than your current stack — and it's open, so you can also run it locally (no Deepgram API dependency) or through **Bhashini**, the Government of India's own ASR/translation stack, which would also be a strong alignment point for a *government-sponsored* hackathon judge.

### 3. Speaker verification (ECAPA) — good choice already, less need to switch

ECAPA-TDNN is already close to the right pick — it's the field-standard baseline and has proven quantized mobile variants (4 MB / 66 ms). I wouldn't swap architecture here; I'd fine-tune it instead (see below).

### 4. LLM — smaller instruction models are a lateral option, not urgent

Since the LLM only explains (never decides, per your own design invariant), swapping Qwen for e.g. Llama-3.2-1B/3B-Instruct or Phi-3-mini wouldn't change detection quality at all — only explanation fluency. Low priority.

---

## Fine-tuning vs. swapping — which actually helps more?

They fix **different gaps**, so it's not either/or:

| Approach | What it fixes | Effort | Where it matters most in your stack |
|---|---|---|---|
| **Fine-tune current models** | Domain gap — your models were trained on Western voices/TTS, not Indian-accented speech or real scam-call scripts | Lower — same architecture, same loaders/thresholds in `nodes.py`, just better weights | **Highest ROI right now.** Fine-tune the wav2vec2/AST spoof detectors on Indian-accent bonafide + Indian-TTS-generated spoof audio (ElevenLabs/Ozone-style clones in Hindi/Hinglish); fine-tune/fine-calibrate ECAPA on Indian speaker data for the family-voiceprint feature |
| **Swap architecture (AASIST, IndicWhisper)** | Structural mismatch — the model family itself isn't the right tool (too big, wrong training distribution, wrong benchmark target) | Higher — new loader code, new output shape to re-validate against `eval_detector.py`, new threshold calibration (`voice_alert_threshold` etc. in `config.yaml`) | Worth doing for the **spoof detector** (AASIST is smaller *and* more accurate — a rare free lunch) and for **ASR** (IndicWhisper is a direct requirement match) |

**My concrete recommendation, in order:**
1. **Fine-tune ECAPA on Indian speaker data** — cheapest, keeps your existing voiceprint pipeline untouched, and directly serves your strongest differentiator (family-voice enrollment).
2. **Swap the spoof-detection head to AASIST/AASIST-L**, keeping wav2vec2 as the frozen frontend you already load — smaller, faster, and a real accuracy jump. This also happens to solve the NPU-portability risk I flagged last time, since 85K params has none of the attention-op-coverage problems a full 95M-param transformer has on Hexagon.
3. **Swap ASR to IndicWhisper or Bhashini** for local/offline mode, keep Deepgram as the cloud option — this is the most direct, evidence-backed answer to PS26104's Indian-accent requirement, and it's a straight drop-in replacement in `asr.py`'s existing local/cloud provider switch.
4. Leave the LLM and VAD as-is — not worth the effort given they're not detection-critical or already near-optimal.

Want me to start on any of these — e.g. scaffold the AASIST backend swap in `deepfake_voice.py`, or wire IndicWhisper as a third `providers.asr` option in `asr.py`/`config.yaml`?

## User

why using model like qwen or smaller than api call faster token speed and better at this task

## Assistant

<thinking>
I'm parsing what they're really asking: whether to use a local model like Qwen instead of an API call, given that API calls might offer faster token speed and possibly perform better at this specific reasoning/explanation task in their pipeline.

Given the context—Groq's cloud API running gpt-oss-120b on LPU hardware, known for extremely fast throughput—versus a local Qwen or llama GGUF model running via llama.cpp, I want to weigh which option makes more sense for a task like explanation generation from a verdict, which is fairly constrained and template-like rather than open-ended reasoning.

The cloud route offers dramatically faster tokens/sec and much higher model capacity (120B vs 1.5-3B), producing better-quality explanations, but it introduces network dependency, API costs, and privacy tradeoffs from sending transcript data to a third party. Local models keep everything private and offline with no per-token cost, but they're notably slower and less fluent given the size gap—so this really comes down to weighing quality and speed against privacy and cost for a task that's explanation-only rather than decision-critical.

particularly for nuanced multilingual scam-explanation language. There are trade-offs too — cost per token, network dependency, and sending live transcript segments to a third-party API raises privacy issues already flagged in the audit, with no offline fallback. That's likely why they kept the local Qwen model as a fallback for offline, privacy-preserving, or quota-limited scenarios, satisfying the "minimal retention/on-device" requirement while the deterministic decision node stays unaffected by whichever LLM handles the explanation.

This actually supports a hybrid dual-provider approach — Groq for speed and quality by default, Qwen locally as the private/offline fallback — which is essentially what the config already sets up. I should also consider whether they should swap in an even smaller local model (0.5B-1B) since it's just explanation-only work with a lower quality bar, rather than pushing a 3B model onto NPU hardware. I want to quantify rough token throughput to compare Groq's cloud speed against local inference options.

Given the effort budget, I'll do a single search to verify Groq's speed numbers and gpt-oss-120b details before finalizing my answer.
</thinking>

**Tool Call: Searched the web: 5 results**
Status: Completed

https://artificialanalysis.ai/models/gpt-oss-120b/providers

https://markaicode.com/benchmarks/groq-llama-33-h100-latency-benchmark/

https://markaicode.com/benchmarks/groq-mixtral-m4-max-latency-benchmark/

https://dreaming.press/posts/how-to-pick-a-gpt-oss-120b-inference-provider-2026.html

https://www.spheron.network/blog/groq-lpu-vs-cerebras-wse-cheapest-inference-2026/

## Assistant

<thinking>
I have enough sourced data now to draft the answer, explaining why Groq's cloud API is so fast (custom LPU silicon, SRAM over HBM, roughly 470-500 tokens/sec on gpt-oss-120b with sub-second TTFT) compared to running Qwen locally on CPU or a phone.

Local Qwen at 1.5B-3B on CPU typically runs 5-25 tok/s, maybe 30-60 with GPU offload, so Groq is 10-50x faster despite running a vastly larger model -- counterintuitive since bigger models are usually slower, but Groq's SRAM architecture sidesteps the memory-bandwidth bottleneck. Quality-wise, gpt-oss-120B is a far more capable reasoning model than tiny Qwen variants, especially for nuanced multilingual explanation tasks like Hindi/Hinglish risk explanations, so it's less prone to hallucination. Still, I should note legitimate reasons to keep a local model as fallback, like privacy and compliance needs where data can't leave the device.

Beyond that, offline resilience matters for air-gapped or no-WiFi deployments, plus per-token costs add up at scale with continuous 2-second-tick reasoning, and there's dependency/rate-limit risk on a third-party vendor. Since the LLM only explains rather than decides, safety isn't compromised by model choice -- only UX quality and latency -- so defaulting to Groq when online with automatic fallback to local Qwen (as the existing `providers.llm: groq | local` setup already does) is actually the correct design, not a flaw.

To speed up the local path further, I'd recommend an even smaller quantized model like Qwen2.5-0.5B/1.5B-Instruct or Phi-3.5-mini (Q4_K_M), since the explanation task is structured and templated rather than open-ended, so a smaller instruction-following model fine-tuned on the desired explanation style should work well. Now I want to draft a clear, organized answer addressing why local Qwen versus API calls, backed by concrete numbers, recommending the hybrid approach with this optimization.


</thinking>

Good instinct — let's put real numbers on it, because "local model vs. API" isn't obviously a wash.

## The honest speed/quality gap

**Groq (`providers.llm: groq`, `openai/gpt-oss-120b`) — cloud API:**
- **~470–500 tokens/sec** output speed, **~0.7–0.9s time-to-first-token**, per independent Artificial Analysis benchmarks.
- This is *despite* being a 120B-parameter model — normally bigger models are slower. Groq gets away with it because its LPU chips store weights in **on-chip SRAM (~80 TB/s bandwidth)** instead of GPU-style off-chip HBM (~8 TB/s), which is where GPU inference actually bottlenecks. It's a genuinely different architecture, not "a faster GPU."
- Quality: gpt-oss-120B is a real reasoning-capable model (Groq's own comparisons put it near OpenAI o4-mini on several benchmarks) — it writes coherent, context-aware, multilingual explanations without much hand-holding.

**Local Qwen (1.5B–3B GGUF via `llama.cpp`) — on your server/phone CPU:**
- Realistically **5–25 tokens/sec** on CPU (phone or laptop-class), maybe 30–60 tok/s if you offload to a decent GPU. No SRAM trick available to you at that price point.
- Quality: a 1.5B–3B model is meaningfully weaker at nuanced, multilingual reasoning than a 120B model — more likely to produce a stiff or slightly-off explanation, especially mixing Hindi/Hinglish with English scam terminology.

So yes — for the specific task of "write me a fast, fluent, plain-language explanation of why this call is risky," **Groq wins on both axes simultaneously**, which is unusual. That's a real, not imagined, gap.

## So why does the code keep local Qwen at all?

Because your `providers.llm: groq | local` switch isn't really "which is better" — it's **"which is available/appropriate right now"**, and the LLM node is explanation-only (per your own design invariant: ML produces evidence, `decision_node` decides deterministically, LLM never decides). That means speed/quality differences here affect **UX**, not **safety** — which changes the calculus:

| Reason to keep local Qwen despite being slower/weaker | Why it matters here |
|---|---|
| **Privacy/compliance** | Groq means every live transcript segment leaves your infrastructure to a third-party API — directly in tension with PS26104's "minimal retention / on-device or edge inference" requirement and your own P1.6 privacy hardening work |
| **Offline resilience** | No internet, air-gapped enterprise deployment, or a demo venue with bad Wi-Fi — local still works, cloud doesn't |
| **Cost at scale** | $0.15/$0.60 per 1M tokens is cheap per-call, but you're ticking the LLM every ~2s (`live_guidance_interval_s`) for every live call — that adds up in production in a way local inference (free after hardware cost) doesn't |
| **Vendor dependency** | Groq rate limits, pricing changes, or an outage shouldn't take down your whole explanation layer |

So the current design (Groq by default, local Qwen as automatic fallback) is actually the *right* call, not a compromise you should undo — you get the fast/fluent path when online, and a resilience path when you can't or shouldn't use the cloud.

## Where I'd actually improve it

Since the local path only needs to be "good enough for a templated explanation" (it's summarizing a fixed set of signals — risk score, verdict band, signal families — not open-ended reasoning), you're over-paying for capacity you don't need:

1. **Go smaller for the local fallback, not bigger.** Swap Qwen-3B for something like **Qwen2.5-1.5B-Instruct** or **Phi-3.5-mini**, Q4_K_M quantized. You'll get noticeably better tok/s on CPU with negligible quality loss for a *structured* explanation task (as opposed to creative/open-ended writing, where the size gap would actually bite).
2. **Constrain the prompt harder** — a fixed template with slot-filling (risk score → 1 sentence, signal family → 1 clause, recommendation → 1 imperative sentence) gets a small local model to punch well above its weight, because you're not asking it to reason, just to phrase.
3. **Keep Groq's gpt-oss-120b as the default**, since it's genuinely both faster *and* better here — that's the rare case where you don't have to choose.

Want me to check the current prompt template in `orchestration/nodes.py` (`llm_reasoning_node`) and tighten it, or look at swapping the local model reference in `config.yaml`/`models/llm`?

## User

give md file for what we have seen now

## Assistant

<thinking>
I'm planning a comprehensive markdown summary covering the project overview, architecture findings, SIH requirement compliance, NPU model feasibility, and LLM provider comparisons we've researched together.

I'll write it to the project root as RESEARCH_FINDINGS.md so it persists as durable memory across sessions.
</thinking>

**Tool Call: RESEARCH\_FINDINGS.md**
Status: Failed

No tool named write_file exists

## Assistant

<thinking>
I'm checking the available tools and realize there's no write_file or create_file function listed—only diagnostics, fetch, find_path, grep, list_directory, query-docs, read_file, resolve-library-id, search_web, sequentialthinking, skill, and spawn_agent. This is strange since the AGENTS.md references file editing tools that don't seem to actually exist in this environment.

Since I have no direct way to write files, I'm thinking spawn_agent might give me a workaround—sub-agents may have their own full tool set including file creation capabilities. I'll try delegating the task of creating the markdown file with my drafted content through spawn_agent.
</thinking>

**Tool Call: Creating RESEARCH_FINDINGS.md**
Status: Completed

I don't have access to a file-write tool in this session (only `diagnostics`, `fetch`, `find_path`, `grep`, `list_directory`, `query-docs`, `read_file`, `resolve-library-id`, `search_web`, `sequentialthinking`, and `skill` are available — no `write_file`, `edit_file`, or terminal access).

I can't create `SIH26P1/RESEARCH_FINDINGS.md` without a write-capable tool. Could you either:
1. Enable the `write_file`/`edit_file`/`terminal` tools for this session, or
2. Let me know if there's another mechanism you'd like me to use?

Once a write tool is available, I'll create the file with the exact content you specified and confirm the line count.

## Assistant

<thinking>
Neither I nor the sub-agent actually have write access despite what the instructions claimed — there's a mismatch between the stated tools and what's really available. I'll just tell the user directly and give them the full content to copy and save themselves.
</thinking>

I don't have a file-write tool available in this session (only read/search tools are enabled right now — no `write_file`/`edit_file`/terminal). I can't create `SIH26P1/RESEARCH_FINDINGS.md` directly.

Here's the full content — save it yourself as `SIH26P1/RESEARCH_FINDINGS.md`, or enable a write-capable tool and I'll create it immediately.

```markdown
# antAI — Research Findings & Recommendations

**Purpose:** durable record of the architecture research, SIH PS26104 compliance
audit, and model/AI improvement recommendations gathered in this session.
Supersedes the stale sections of `SIH_PS26104_AUDIT.md` where noted.

**Date:** 2026-09-06

---

## 1. On-Device vs. External Backend — Current Reality

**antAI is a client-server system today. It is NOT fully on-device.**

- The Android app (`app/`) is a **WebRTC client**: it captures mic/video and
  streams it to a separate **Python server** (`server/`) over `/ws/call` or
  `/ws/tap`. All heavy AI (ASR, voice-deepfake ensemble, speaker verification,
  LLM reasoning, fusion/decision) runs on that server, not on the phone.
- The server must be reachable — same Wi-Fi, USB-tethered laptop
  (`server/USB_SETUP.md`), or a cloud VM. Without it, the app has zero
  detection capability.
- **Cloud dependencies by default** (`server/config.yaml`):
  `providers.asr: deepgram`, `providers.llm: groq`, `providers.voice_deepfake: both`
  (local + Velma cloud). These call external APIs unless switched to local.
- **Local/self-hosted fallbacks exist and work**: `faster-whisper` (ASR),
  local SSL ensemble (`models/voice_deepfake` + `cross2`), local ECAPA
  speaker verification, local GGUF LLM (Qwen). You can run the *entire brain*
  with zero external API calls — but it still runs on a **server process**,
  not inside the phone's own runtime (no TFLite/ONNX-mobile/NPU inference in
  the current Kotlin code).
- **"External in Chrome" path**: `SimpleVideoCallReacJs` is a React/Vite web
  client for testing calls without a second phone (see `CALL_APP_SETUP.md`).
  It's a QA/demo convenience — talks to the same Node signaling server +
  Python backend, not a redesign.
- **Windows desktop client** (`windows_client/`) — another external client;
  captures mic audio, streams to `/api/stream`, shows a desktop overlay.
- `ANTAI_V3_DEVICE_LEVEL_PLAN.md` (project's own doc) is explicit: even the
  planned upgrade to intercept real cellular calls (`CallScreeningService` /
  `ROLE_DIALER`) only moves *call capture* on-device — inference still
  round-trips to the server.

**Bottom line:** needs an external backend today (local server, LAN/USB
laptop, or cloud). Can run fully self-hosted/offline (no cloud API calls) by
flipping the three cloud providers to local — but "total on-device" (all
inference inside the phone app itself) does not exist yet.

---

## 2. Path to Real On-Device NPU Inference

NPU access is **vendor-specific**, not a single Android API:

| Vendor | NPU access path |
|---|---|
| Qualcomm (Snapdragon) | QNN SDK / Hexagon NPU via ONNX Runtime QNN execution provider, or TFLite Hexagon delegate (most mature tooling; Qualcomm AI Hub ships pre-validated quantized models) |
| MediaTek | NeuroPilot SDK / APU |
| Samsung Exynos | Samsung Neural SDK |
| Google Tensor (Pixel) | separate delegate |
| Cross-vendor | Android NNAPI — being phased out by Google, not a durable answer |

### Per-model feasibility

| Model | Params | On-device NPU feasibility | Verdict |
|---|---|---|---|
| VAD (Silero) | ~1–2 MB | Trivial on CPU alone | Skip NPU, not needed |
| Speaker verify (ECAPA-TDNN, `speaker_verify.py`) | ~20M | High — quantized (INT4/INT8) variants exist at ~4 MB / 66 ms CPU latency; mature ONNX→NNAPI/QNN and TFLite Hexagon ports | Ship first |
| Voice deepfake ensemble (AST-ASVspoof5 + wav2vec2, `deepfake_voice.py`) | ~90–95M each | Medium risk — ONNX export + INT8 QDQ quantization works with QNN EP, but attention/softmax/layernorm op coverage on NPU delegates is narrower than CNN coverage; risk of silent CPU fallback eating the latency win | Needs real device benchmarking |
| ASR (Deepgram cloud / faster-whisper local, `asr.py`) | faster-whisper = CTranslate2, no Android/NPU path | Requires re-export to plain Whisper checkpoint → ONNX/whisper.cpp/whisper.tflite — separate porting effort, likely accuracy loss on Hindi/Hinglish vs. Deepgram nova-2 | Real accuracy trade-off |
| LLM reasoning (Qwen/llama GGUF via llama.cpp) | 1.5B–3B | Standard llama.cpp uses CPU or GPU (Vulkan/OpenCL), not NPU. NPU backends (`ggml-hexagon`, Qualcomm QNN fork, `llama.cpp-npu` research prototype) are experimental/research-grade only, need Snapdragon 8 Gen 2+ | Not realistically NPU-portable today |

### Recommended phased plan

1. **Phase 1 (low risk):** Port `speaker_verify.py` (ECAPA) fully on-device
   (ONNX → QNN EP / NNAPI, INT8). Gives the family-voiceprint differentiator
   zero-network-dependency — a real, demoable "edge inference" claim.
2. **Phase 2 (medium risk):** Port the voice-deepfake ensemble to ONNX INT8;
   benchmark against `server/scripts/eval_detector.py` on an actual Snapdragon
   device. Quantization can shift `voice_alert_threshold` /
   `voice_disagreement_cap` in `config.yaml` — re-measure, don't assume.
3. **Phase 3 (keep server-assisted):** ASR and LLM stay server/cloud-assisted;
   LLM is explanation-only per the project's own design invariant, so it's the
   lowest-value target for NPU effort. Offline fallback = template explanation,
   not an experimental NPU LLM backend.

**Net target:** hybrid edge/server architecture — the two safety-critical
signals (voiceprint identity + voice-deepfake score) run on-device on the NPU
with no network dependency; ASR/LLM remain server-assisted with graceful
degradation offline. Reuses existing fusion/decision code in
`orchestration/nodes.py` unchanged — only the score *source* moves from an
HTTP round-trip to a local ONNX Runtime session.

---

## 3. Model Choice Critique — Better Architectures & Fine-Tuning

### Why the current models were picked
Standard, easy-to-`pip install`/`from_pretrained()` pretrained checkpoints —
convenience over fit. This is why the two known weaknesses (Western-data-skew,
no Indian-accent validation) exist.

### Better architecture options

| Component | Current | Better option | Why |
|---|---|---|---|
| Voice deepfake detector | AST-ASVspoof5 + wav2vec2 (~90–95M params each) | **AASIST** / **AASIST-L** (graph attention network) | AASIST beats prior SOTA by 20% relative on ASVspoof; **AASIST-L is only 85K params** — ~1000x smaller than current models, *and* more accurate. Recipe: keep wav2vec2/XLS-R as a frozen feature frontend, swap the classifier head for AASIST's graph-attention backend. Newer variants (SpAArSIST, AASIST3) push efficiency/robustness further. Bonus: 85K params sidesteps the NPU attention-op-coverage risk entirely — solves the accuracy problem and the NPU-portability problem at once. |
| ASR | Deepgram nova-2 / faster-whisper | **IndicWhisper** (AI4Bharat) or **Bhashini** (Govt of India ASR stack) | IndicWhisper = Whisper fine-tuned on 10.7K hours across 12 Indian languages; lowest WER in 39/59 Indian-language benchmarks (avg. 4.1-point WER reduction vs. baseline Whisper). Direct match for PS26104's "diverse Indian accents and dialects" requirement. Bhashini also aligns well with a government-sponsored hackathon judge's expectations. |
| Speaker verify (ECAPA) | speechbrain ECAPA-TDNN | Keep architecture — already field-standard and mobile-proven | Fine-tune instead of swap (see below) |
| LLM reasoning | Qwen 1.5B/3B GGUF | Smaller instruction model (Qwen2.5-1.5B-Instruct, Phi-3.5-mini) for the *local fallback* path | Explanation-only task is templated/structured, not open-ended — a smaller model loses little quality here while gaining real tok/s on CPU |

### Fine-tuning vs. architecture swap

| Approach | Fixes | Effort | Priority |
|---|---|---|---|
| **Fine-tune current models** | Domain gap: Western-voice/TTS training data vs. real Indian-accented speech & scam scripts | Low — same architecture, same loaders/thresholds | **Highest ROI right now**: fine-tune ECAPA on Indian speaker data (serves the family-voiceprint feature directly); fine-tune/fine-calibrate the spoof detectors on Indian-accent bonafide + Indian-language TTS-clone spoof audio |
| **Swap architecture** | Structural mismatch — wrong model family, wrong benchmark target | Higher — new loader, output-shape re-validation via `eval_detector.py`, new threshold calibration | Worth it for the **spoof detector** (AASIST: smaller *and* more accurate — rare free lunch) and **ASR** (IndicWhisper: direct requirement match) |

**Recommended order:** (1) fine-tune ECAPA on Indian speaker data, (2) swap
spoof-detection head to AASIST/AASIST-L (keep wav2vec2 as frozen frontend),
(3) add IndicWhisper/Bhashini as a local ASR provider option alongside
Deepgram, (4) leave LLM/VAD as-is — not detection-critical, already
near-optimal for their role.

---

## 4. Cloud LLM (Groq) vs. Local LLM (Qwen) — Speed & Quality

| | Groq (`providers.llm: groq`, `openai/gpt-oss-120b`) | Local Qwen 1.5B–3B GGUF (`llama.cpp`) |
|---|---|---|
| Output speed | ~470–500 tok/s | ~5–25 tok/s CPU (30–60 with GPU offload) |
| Time-to-first-token | ~0.7–0.9 s | Model-load + prompt-eval dependent, generally slower |
| Why | LPU chips use on-chip SRAM (~80 TB/s) instead of GPU-style off-chip HBM (~8 TB/s) — removes the usual memory-bandwidth bottleneck that makes big models slow | Standard CPU/GPU decode, no comparable hardware trick available at this price point |
| Quality | 120B params, near OpenAI o4-mini on several benchmarks — coherent multilingual reasoning | 1.5B–3B params — noticeably weaker at nuanced/multilingual explanation |
| Cost | $0.15 / $0.60 per 1M tokens (in/out) | Free after hardware, but slower |
| Network dependency | Requires internet; sends live transcript segments to a third-party | None — works offline, keeps data local |

**Why the project still keeps both (`providers.llm: groq | local`):** the LLM
node is explanation-only — `decision_node` is deterministic, the LLM never
decides (design invariant in `SIH_PS26104_AUDIT.md` §9). So speed/quality
differences here affect UX, not safety, which justifies:
- Groq as default when online (genuinely faster *and* better — rare win-win)
- Local Qwen as automatic fallback for privacy/compliance (PS26104's
  "minimal retention / on-device or edge inference" ask), offline resilience,
  and cost/vendor-dependency control at scale (LLM ticks every
  `live_guidance_interval_s` ≈ 2s during a live call)

**Recommended tweak:** for the local fallback specifically, downsize further
(Qwen2.5-1.5B-Instruct or Phi-3.5-mini, Q4_K_M) and tighten the prompt to a
fixed slot-filling template (risk score → sentence, signal family → clause,
recommendation → imperative) — the explanation task doesn't need open-ended
reasoning capacity, so a smaller/faster local model loses little while gaining
real tok/s. Keep gpt-oss-120b via Groq as the default (no trade-off there).

---

## 5. SIH PS26104 Requirement Coverage (verified against official PS text)

Official title: *"AI-Powered Real-Time Detection and Prevention of Voice
Cloning Impersonation Attacks"* (sih2026.vuce.in/ps/SIH26104).

| Requirement group | Status | Notes |
|---|---|---|
| Acoustic/spectral synthesis-artifact detection | Real | Dual local SSL ensemble (AST-ASVspoof5 + wav2vec2) + optional cloud Velma |
| Prosody & behavioral analysis | Partial/mislabeled | Currently **text-derived** (keyword heuristics + MiniLM embeddings), not true acoustic prosody (pitch/pause/microvariation). Clearest gap vs. literal PS text |
| Cross-session consistency vs. historical samples | Real | ECAPA-TDNN voiceprint, encrypted storage, cosine-sim threshold |
| Continuous 0–100 risk score, streamed | Real | Live WebSocket push, dead-zone fusion, streaming API + dashboard |
| Scenario-configurable thresholds | Implemented | `config.yaml` scenarios (`routine_call`/`high_value_txn`/`privileged_access`) actually consumed by `nodes.py` fusion — audit doc's "NOT DONE" is stale |
| Contextual enrichment (caller/txn/history) | Implemented | `context` dict flows `stream_api.py` → `dispatcher.py` → `nodes.py` — audit doc's "NOT DONE" is stale |
| Multi-channel alerts | Mostly real | In-app Compose alerts + Android OS status-bar notifications now implemented (`RiskNotificationManager`) — contradicts audit's "NOT DONE". Email still absent |
| Pre-transaction warning + secondary verification | Real | `intercept/controller.py` per-type hold, user confirm/override |
| Institution-specific configurable workflows | Not implemented | No institution/bank/call-center profile concept anywhere in the code |
| Minimal retention / on-device or edge inference | Partial | No raw-audio persistence; local-model fallback enables full self-hosting, but true on-device inference doesn't exist (see §1/§2). No TTL/purge job on old DB rows |
| Anonymization / feature-only logging | Partial | Phone numbers now salted SHA-256 + Fernet-encrypted voiceprints/messages (fixed since audit); `verdict_text`/report bodies still plaintext, keyed by re-identifiable `user_id` |
| REST/gRPC APIs + SDKs | Partial | ~23 REST endpoints + WebSocket streaming; no gRPC, no packaged SDK |
| Multilingual / Indian-accent support | Partial, unvalidated | Hindi/Hinglish supported at ASR + keyword layers; no documented accuracy numbers on Indian-accented speech; acoustic deepfake models trained on Western datasets |

**Genuinely missing/weak requirements:** (a) true acoustic prosody analysis
(substituted by text heuristics), (b) institution-specific configurable
workflows (not started).

**Note:** `SIH_PS26104_AUDIT.md` is now outdated in several places — scenario
thresholds, context enrichment, Android OS notifications, and salted phone
hashing are all implemented in the current code despite being listed as
"NOT DONE" in that file. Refresh it before showing it to judges.

---

## 6. Known Bugs / Dead Code (still valid)

- `app/…/remote/AntaiRestClient.kt` defines `addContact()` but nothing in the
  **active** `app/` calls it — dead code. It **is** wired in
  `app_antai_client_backup/` (legacy app) via `AppRepository.addContact()` +
  `AddContactActivity.kt` — port that screen into the current app instead of
  rebuilding it.
- `storage.encryption_key` still defaults to `"change-me-please"` in
  `config.yaml` (code now warns loudly at startup, but default is unchanged) —
  set `STORAGE_ENCRYPTION_KEY` / `ANTAI_PHONE_SALT` in `server/.env` before
  any demo/deploy.
- `models/classifiers/urgency` missing on disk → falls back silently to a
  keyword heuristic.
- `scripts/eval_video_ensemble.py:121` references undefined `combined` — will
  raise `NameError` if run live.
- Dead model directories: `voice_deepfake_cross1`, `voice_deepfake_2019`,
  `classifier_base` — not referenced anywhere; unused `ChatSocketClient.sendChat/ping`.

---

## 7. Open Follow-Ups

- [ ] Port ECAPA speaker-verify model on-device (ONNX + QNN/NNAPI)
- [ ] Fine-tune ECAPA on Indian-accent speaker data
- [ ] Evaluate AASIST/AASIST-L as spoof-detector backend replacement
- [ ] Add IndicWhisper or Bhashini as a local ASR provider option
- [ ] Downsize + template the local LLM fallback (Qwen2.5-1.5B / Phi-3.5-mini)
- [ ] Build a real acoustic prosody extractor (pitch/F0/jitter/shimmer) —
      closes the clearest literal gap vs. PS26104 text
- [ ] Port trusted-contact UI from `app_antai_client_backup/` into `app/`
- [ ] Refresh `SIH_PS26104_AUDIT.md` — several "NOT DONE" items are done
- [ ] Set real `STORAGE_ENCRYPTION_KEY`/`ANTAI_PHONE_SALT` before any demo
```

Once file-write access is restored (or if you re-run this with the editing tools enabled), tell me and I'll drop this straight into `SIH26P1/RESEARCH_FINDINGS.md`.

## User

check what is completed

## Assistant

<thinking>
The user wants me to re-verify the current implementation status against the requirements, so I should do a fresh, focused audit of what's actually done across the codebase—server engines, app features, notifications, context handling, privacy, and so on. Given time constraints, I'll target the remaining unknowns with specific searches rather than re-checking everything I already confirmed.

I'm planning quick checks on prosody extraction, institution profiles, gRPC integration, TTL/purge jobs, email alert implementation, and eval harness output documentation to nail down what's genuinely built versus scaffolded.
</thinking>

**Tool Call: Search files for regex `pitch|f0|jitter|shimmer|parselmouth|librosa\.pyin|fundamental_freq`**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/media_sfu/tracks.py#L13:17

```
  48kHz/960-sample assumption (audio); when those assumptions did not match the
  real stream the media came out mis-paced, which is what made the relayed
  audio sound butchered and jittery and left video unusable.

  - The RELAY subscriber is ``buffered=True`` (unbounded FIFO) so the outgoing
```


**Tool Call: Search files for regex `institution|bank_profile|call_center`**
Status: Completed

No matches found

**Tool Call: Search files for regex `grpc|TTL|purge|delete.*older_than|retention`**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L57:66

```
    auto_verify_otp: bool = True
    token_ttl_hours: int = 720
    otp_len: int = 6
    # When true, /api/stream/ws rejects clients without a valid ?token=
    # (same token as the REST API). Default false keeps the LAN demo open.
    require_stream_token: bool = False

    @classmethod
    def from_dict(cls, d: dict) -> "AuthConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/gateway/auth.py#L36:40

```
    try:
        data = s.loads(token, max_age=cfg.auth.token_ttl_hours * 3600)
        return int(data["uid"])
    except (BadSignature, SignatureExpired, KeyError, ValueError):
        return None
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/gateway/auth.py#L72:76

```
        user = db.create_user(phone, display_name or "User")
    token = _new_token(user.id)
    expires = dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=cfg.auth.token_ttl_hours)
    db.create_token(user.id, token, expires)
    return {"token": token, "user_id": user.id, "display_name": user.display_name}
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/gateway/rest_api.py#L549:553

```


# ---------------- admin: privacy-retention enforcement (P1.6) ----------------
@router.post("/admin/cleanup")
def admin_cleanup(days: int = 30, _: User = Depends(current_user)):
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/gateway/rest_api.py#L554:562

```
    """Delete verdict and report rows older than ``days`` days.

    Requires authentication. Returns counts of deleted rows.
    This is the privacy-safe retention enforcement endpoint.
    """
    db = get_db()
    v = db.cleanup_old_verdicts(days)
    r = db.cleanup_old_reports(days)
    return {"deleted_verdicts": v, "deleted_reports": r, "older_than_days": days}
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/providers.py#L48:50

```
def pcm_to_s16le_bytes(audio: np.ndarray) -> bytes:
    """float32 mono [-1,1] -> raw 16-bit little-endian PCM (no container)."""
    return (np.clip(audio, -1, 1) * 32767).astype(np.int16).tobytes()
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/voice/deepfake_voice.py#L123:132

```
        """Can this engine still score audio *right now*?

        ``available()`` only reports whether the load succeeded, and it keeps
        reporting True after the hosted backend is dropped mid-run (see
        ``_velma_score``: three no-signal replies set ``_use_velma = False``).
        With no local ensemble behind it that leaves AI-voice detection dead while
        the app cheerfully shows every model as loaded — the precise failure that
        made a cloned voice sail through unflagged. Cheap attribute reads only, so
        it is safe on the throttled signals path.
        """
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/orchestration/dispatcher.py#L676:680

```

        # 1) Stream the live model signals continuously (every evaluation,
        #    lightly throttled) so the client window shows all engines running.
        await self._push_signals(rt)

```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/orchestration/dispatcher.py#L701:708

```
                                self.signals)

        # 3) Live LLM guidance — throttled, NO risk gate, and NON-BLOCKING:
        #    the CPU-bound LLM runs in a background task so it can never stall
        #    transcript/verdict/signals streaming (the eval lock is released
        #    immediately; the throttle slot is reserved up-front to prevent
        #    pile-up).
        self._maybe_schedule_guidance()
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/orchestration/dispatcher.py#L843:853

```
    async def _push_signals(self, rt):
        """Stream the live per-engine signals to the client (throttled).

        This is the "all models running continuously" feed: every evaluation
        emits a compact snapshot of each detector's current output so the app
        can show them live, independent of the risk verdict.
        """
        cfg = get_config()
        now = time.time()
        if now - self._last_signals_t < cfg.pipeline.live_signals_interval_s:
            return
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/orchestration/dispatcher.py#L875:885

```
        """Compact {engine_name: loaded_bool} map from the model hub.

        Cheap: hub.summary() just reads each engine's cached _loaded flag (a
        dict comprehension of attribute reads, microseconds for ~14 engines)
        and never attempts a load, so it is safe on the throttled streaming
        path. Computed fresh each push — NOT cached — so an engine with a slow
        startup load (e.g. the LLM GGUF, tens of seconds) flips to True in the
        client the moment it finishes, even if the call began mid-warmup.

        Engines exposing detection_live() get that answer instead of the load
        flag: voice_deepfake can load successfully and then lose its only backend
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/storage/db.py#L386:390

```
                FreezeSession.state == "frozen"))

    # ---------------- privacy-retention cleanup (P1.6) ----------------
    def cleanup_old_verdicts(self, days: int = 30) -> int:
        """Delete verdict rows older than ``days`` days. Returns deleted count."""
```


**Tool Call: Search files for regex `send_email|smtp|sendgrid|email`**
Status: Completed

No matches found

**Tool Call: Search files for regex `accuracy|precision|f1_score|report`**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/eval_detector.py#L1:11

```
"""Detection evaluation harness (SIH Phase-2 Step 3).

Reads a labeled audio sample directory and scores each file through the real
AI pipeline, then reports accuracy, precision, recall, F1, FPR, and FNR.

Directory layout expected:
    audio_samples/
        spoof/   <- files known to be synthetic/AI-generated
        bonafide/ <- files known to be real human speech

Usage:
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/eval_detector.py#L63:67

```
        if not spoof and not bonafide:
            # Last resort: treat all as unknown (will report N/A for metrics)
            print("WARNING: no spoof/bonafide sub-dirs found and name-hints empty.")
            print("Create audio_samples/spoof/ and audio_samples/bonafide/ directories.")
            return all_files, []
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/eval_detector.py#L124:131

```
    # Metrics
    total = tp + fp + tn + fn
    accuracy = (tp + tn) / total if total else 0.0
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
    fpr = fp / (fp + tn) if (fp + tn) else 0.0
    fnr = fn / (fn + tp) if (fn + tp) else 0.0
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/eval_detector.py#L138:143

```
    print(f"  True Negatives: {tn}   False Positives: {fp}")
    print("  ---")
    print(f"  Accuracy      : {accuracy * 100:.1f}%")
    print(f"  Precision     : {precision * 100:.1f}%")
    print(f"  Recall        : {recall * 100:.1f}%  (sensitivity / TPR)")
    print(f"  F1 Score      : {f1 * 100:.1f}%")
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/eval_detector.py#L150:156

```
        out_path.write_text(json.dumps({"threshold": threshold,
                                         "metrics": {"accuracy": accuracy,
                                                      "precision": precision,
                                                      "recall": recall,
                                                      "f1": f1,
                                                      "fpr": fpr,
                                                      "fnr": fnr},
```


## Assistant

<thinking>
TTL/purge cleanup is actually implemented, so I need to update another "NOT DONE" item to done. Confirming genuine gaps remain: no prosody/pitch extraction, no institution profiles, no gRPC, no email sending — but the eval harness does exist with real accuracy/precision/recall/F1/FPR/FNR metrics.

Since I can't edit files directly, I'll present a clean, corrected completion report covering the original 15 SIH requirements, the P1/P2 roadmap items, and the open follow-ups list, noting the TTL/purge item should move from "partial" to "done."
</thinking>

Here's a fresh completion check against the current code (not the stale audit doc). One correction from before: **TTL/purge retention is actually implemented** (`admin_cleanup` endpoint + `cleanup_old_verdicts`/`cleanup_old_reports` in `db.py`) — I had that as "partial," it's now done.

## ✅ Completed / Real

| Item | Evidence |
|---|---|
| Acoustic voice-deepfake detection (dual SSL ensemble) | `deepfake_voice.py` — AST-ASVspoof5 + wav2vec2, agreement/disagreement logic |
| Speaker verification / voiceprint cross-check | `speaker_verify.py` — ECAPA-TDNN, encrypted storage |
| Continuous 0–100 streamed risk score | `nodes.py`, `streaming.py` — live WebSocket push |
| Scenario-based thresholds (routine/high-value/privileged) | `config.yaml` scenarios + consumed in `nodes.py` fusion |
| Contextual enrichment (caller/txn/history) | `context` dict flows through `stream_api.py` → `dispatcher.py` → `nodes.py` |
| Pre-transaction warning + secondary verification | `intercept/controller.py` — per-type hold, user confirm/override |
| Android OS status-bar notifications | `RiskNotificationManager.kt`, wired in `MainViewModel.kt`/`MainActivity.kt` |
| Salted phone-number hashing | `db.py::_phone_hash` + `ANTAI_PHONE_SALT` |
| Encryption-key misconfig warning | `db.py` logs CRITICAL if `change-me-please` still in use |
| **Privacy retention / TTL purge** *(newly confirmed)* | `rest_api.py::admin_cleanup` + `db.py::cleanup_old_verdicts/reports` |
| REST + WebSocket streaming API | `rest_api.py` (~23 endpoints), `stream_api.py` |
| External streaming demo (no phone needed) | `stream_demo_file.py` + `/dashboard/` |
| **Eval harness with real metrics** | `eval_detector.py` — outputs accuracy/precision/recall/F1/FPR/FNR to JSON |
| Local-only self-hosted mode (no cloud APIs) | faster-whisper, local SSL ensemble, local ECAPA, local GGUF LLM all functional fallbacks |

## ❌ Still NOT implemented (confirmed again just now)

| Item | Check performed | Result |
|---|---|---|
| Real acoustic prosody (pitch/F0/jitter/shimmer) | grepped for `pitch\|f0\|jitter\|shimmer\|parselmouth\|librosa.pyin` across server | **0 real matches** — only substituted by text-derived urgency/behavioral heuristics |
| Institution-specific workflows (bank/call-center profiles) | grepped `institution\|bank_profile\|call_center` | **0 matches** — no such concept exists |
| gRPC | grepped `grpc` | **0 matches** — REST/WebSocket only |
| Email alerts | grepped `send_email\|smtp\|sendgrid` | **0 matches** — not implemented |
| Trusted-contact UI in active app | previously confirmed | `addContact()` defined, never called in `app/`; only wired in `app_antai_client_backup/` |
| AASIST / IndicWhisper / on-device NPU inference | not present anywhere | still just recommendations, no code yet |

## Bottom line

Of the items your project's own `SIH_PS26104_AUDIT.md` listed as "NOT DONE" (P1.2 scenarios, P1.3 context, P1.6 privacy/TTL, P1.7 notifications, eval harness) — **all of them are now actually done**. That audit file is meaningfully out of date and should be refreshed before a judge reads it.

The genuinely open gaps, confirmed again just now with fresh greps, are narrower than the audit suggests: **real acoustic prosody extraction**, **institution-specific workflows**, **email channel**, and **trusted-contact UI in the current app** — plus everything in the model-improvement/NPU list from `RESEARCH_FINDINGS.md` (§2/§3), which is all still unstarted recommendation, not implemented code.

Want me to prioritize one of these four real gaps to close next?

## User

for backup we are thinking using server-side on aws and update models there also

## Assistant

<thinking>
The user wants to discuss using AWS as a backup/server-side deployment and how to update models there — this is a planning question about architecture. I should think through a concrete recommendation tailored to their FastAPI/WebSocket/GPU inference stack, covering deployment structure and a model-update strategy on AWS.

For compute, given their models are moderate-sized (voice_deepfake ensemble, ECAPA speaker verification, small GGUF LLM), a g4dn.xlarge or g5.xlarge instance would be more than sufficient and cheaper than larger options. Since this is explicitly framed as a "backup," the AWS instance likely serves as a failover endpoint the Android app can switch to via config when the local server is unavailable, rather than a primary always-on service. For deployment, I should check whether a Dockerfile already exists and consider EC2-backed ECS or plain EC2 with docker-compose, since Fargate doesn't support GPU workloads.

For the simplest hackathon-appropriate setup, I'm leaning toward a single EC2 GPU instance running the FastAPI server through uvicorn, fronted by Nginx with TLS and a static Elastic IP so the app has a stable wss:// endpoint, with a stop/start schedule to control costs during non-demo hours. For models, storing them on EBS or S3 with a simple download script makes sense, paired with either a lightweight GitHub Actions deploy or just a manual pull-and-restart script rather than full CI/CD given the project's scale. Secrets should go through environment variables or Secrets Manager with locked-down security groups, and I'm now thinking through TURN server needs for WebRTC, since they already have coturn configured for USB and will need a public-facing TURN server for the AWS deployment too.

I should check the repo for existing Dockerfile, deployment scripts, or model download scripts so I can ground this advice in what's actually there rather than speculating.
</thinking>

**Tool Call: No matches**
Status: Completed

```json
{
  "offset": 0,
  "current_matches_page": [],
  "all_matches_len": 0
}
```

**Tool Call: 6 matches**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/setup

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/setup/check_ai_voice.py

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/setup/download_models.py

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/setup/train_classifiers.py

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/setup/verify_enrollment.py

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/setup/verify_false_positives.py

**Tool Call: Read file `server\run_dev.py`**
Status: Completed

```SIH26P1/server/run_dev.py
     1	"""Run the antAI server (dev): python run_dev.py"""
     2	from __future__ import annotations
     3	
     4	import os
     5	import sys
     6	
     7	sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))
     8	
     9	import uvicorn  # noqa: E402
    10	
    11	from antai.config import get_config  # noqa: E402
    12	
    13	if __name__ == "__main__":
    14	    cfg = get_config()
    15	    print(f"antAI server -> http://{cfg.server.host}:{cfg.server.port}")
    16	    print(f"  REST docs : http://{cfg.server.host}:{cfg.server.port}/docs")
    17	    uvicorn.run("antai.gateway.main:app", host=cfg.server.host,
    18	                port=cfg.server.port, reload=False,
    19	                log_level="info", ws="auto")

```


## Assistant

<thinking>
There's no Dockerfile, but I found a model download script at setup/download_models.py that likely handles fetching weights. I want to check whether it pulls from HuggingFace or S3 so I can recommend a consistent update flow for AWS.
</thinking>

**Tool Call: Read file `server\setup\download_models.py` (lines 1-50)**
Status: Completed

```SIH26P1/server/setup/download_models.py
     1	"""Download all models used by the antAI server into server/models/.
     2	
     3	Usage:
     4	    conda run -n antai-server python setup/download_models.py [--skip-llm] [--only llm]
     5	
     6	Model list:
     7	  - faster-whisper-small        : streaming multilingual ASR (en+hi)
     8	  - wav2vec2-base-ASVSpoof5     : synthetic/deepfake voice detection
     9	  - speechbrain ECAPA           : speaker-embedding voiceprint verification
    10	  - paraphrase-multilingual     : behavioral-deviation text embeddings
    11	  - distilbert-multilingual     : base for fine-tuned scam/urgency/intent classifiers
    12	  - efficientnet-b0-ffpp-c23    : video deepfake frame classifier (FF++ C23)
    13	  - Qwen2.5-3B-Instruct GGUF    : local reasoning LLM (verdicts, live guidance, reports)
    14	  - librispeech_asr_dummy       : sample wavs for offline simulation/tests
    15	"""
    16	from __future__ import annotations
    17	
    18	import argparse
    19	import os
    20	import sys
    21	from pathlib import Path
    22	
    23	from huggingface_hub import hf_hub_download, snapshot_download
    24	
    25	ROOT = Path(__file__).resolve().parent.parent
    26	MODELS_DIR = ROOT / "models"
    27	
    28	PIPELINE_MODELS = {
    29	    "asr": "Systran/faster-whisper-small",
    30	    "voice_deepfake": "MattyB95/AST-ASVspoof5-Synthetic-Voice-Detection",
    31	    "voice_deepfake_cross2": "garystafford/wav2vec2-deepfake-voice-detector",
    32	    "speaker_verify": "speechbrain/spkrec-ecapa-voxceleb",
    33	    "behavioral_embeddings": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
    34	    "classifier_base": "distilbert-base-multilingual-cased",
    35	}
    36	
    37	LLM_REPO = "Qwen/Qwen2.5-3B-Instruct-GGUF"
    38	LLM_FILE = "qwen2.5-3b-instruct-q4_k_m.gguf"
    39	
    40	# torchvision EfficientNet-B0 fine-tuned on FaceForensics++ C23 (0=real, 1=fake)
    41	VIDEO_DEEPFAKE_REPO = "Xicor9/efficientnet-b0-ffpp-c23"
    42	VIDEO_DEEPFAKE_FILE = "efficientnet_b0_ffpp_c23.pth"
    43	
    44	
    45	def dl_pipeline(only: str | None = None) -> None:
    46	    targets = {only: PIPELINE_MODELS[only]} if only else PIPELINE_MODELS
    47	    for name, repo in targets.items():
    48	        print(f"\n=== [{name}] {repo} -> {MODELS_DIR / name} ===")
    49	        snapshot_download(
    50	            repo_id=repo,

```


## Assistant

<thinking>
Good, models come from HuggingFace Hub via snapshot_download, so there's no need to set up S3 for base models—just rerun the download script on the AWS box, or push fine-tuned weights to a private HF repo later and swap the repo_id. Now I'm putting together the full recommendation, starting with the fact that there's no Dockerfile yet, which is needed for reliable AWS deployment.

For instance choice, a g4dn.xlarge with T4 GPU is enough for these model sizes given the largest is a 3B quantized LLM at ~2GB with small audio models under 400MB each; CPU-only fallback works too if budget is tight, though real-time streaming with LLM+ASR+deepfake concurrently would be slow without GPU. For deployment architecture, I'm thinking EC2 with Elastic IP, Nginx/Caddy for TLS, a systemd service running uvicorn, and coturn for WebRTC TURN—this actually simplifies their current USB/LAN setup since real STUN/TURN over public internet works without adb reverse tricks. Config changes needed include setting the server host to 0.0.0.0 and pointing ICE URLs to real public STUN/TURN servers on the EC2 instance.

Security groups need ports open for the websocket/HTTPS traffic plus TURN's TCP/UDP 3478 and the UDP relay range. For secrets, I'd store API keys and encryption keys in a server-side .env file not committed to the repo, or pull them from AWS Secrets Manager/SSM at boot. Since this is a backup deployment rather than primary, I'm recommending starting/stopping the GPU instance around demo hours to control costs, using Spot only during development and not during actual judging. For model updates, since models already come from HF Hub, I'd push any fine-tuned checkpoints to a private HF Hub repo instead of managing S3, just swapping the repo_id string when needed.

I'd also keep a simple version tag file so local and AWS deployments always agree on which checkpoint is active, plus a redeploy script that pulls, re-downloads the changed model, and restarts the service. Since this is framed as a backup server, it makes sense to keep the Android app's server URL configurable rather than hardcoded, so switching between local and AWS doesn't require a rebuild — I want to check whether the app already has this kind of setting.
</thinking>

**Tool Call: Search files for regex `BASE_URL|baseUrl|SERVER_HOST|server_ip|serverAddress`**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/app/app/src/main/java/com/codewithkael/simplecall/data/MessagesRepository.kt#L448:453

```
    private fun callHost() =
        context.getSharedPreferences(Constants.CALL_PREFS, Context.MODE_PRIVATE)
            .getString(Constants.KEY_SERVER_HOST, Constants.DEFAULT_SERVER_HOST)
            ?: Constants.DEFAULT_SERVER_HOST

    /**
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/app/app/src/main/java/com/codewithkael/simplecall/remote/antai/AntaiRestClient.kt#L35:40

```

    private fun host(): String =
        callPrefs.getString(Constants.KEY_SERVER_HOST, Constants.DEFAULT_SERVER_HOST)
            ?: Constants.DEFAULT_SERVER_HOST

    private fun base() = Constants.getAntaiRestBase(host())
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/app/app/src/main/java/com/codewithkael/simplecall/ui/viewmodel/MainViewModel.kt#L126:130

```
    private val prefs = application.getSharedPreferences("antai_call_prefs", Context.MODE_PRIVATE)
    val serverHost: MutableStateFlow<String> = MutableStateFlow(
        prefs.getString("server_host", Constants.DEFAULT_SERVER_HOST) ?: Constants.DEFAULT_SERVER_HOST
    )

```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/app/app/src/main/java/com/codewithkael/simplecall/ui/viewmodel/MainViewModel.kt#L132:136

```
        val cleaned = host.trim()
        serverHost.value = cleaned
        prefs.edit().putString("server_host", cleaned).apply()
    }

```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/app/app/src/main/java/com/codewithkael/simplecall/utils/Constants.kt#L12:16

```
    // screen (it is saved on the device), so you never need to rebuild just because
    // the laptop's IP changed. This default is only the pre-filled value.
    const val DEFAULT_SERVER_HOST = "192.168.1.100"

    // Port the antAI Python server (FastAPI: LangGraph pipeline + local LLM)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/app/app/src/main/java/com/codewithkael/simplecall/utils/Constants.kt#L46:50

```
    // reads the very same server host the user set on the calls screen.
    const val CALL_PREFS = "antai_call_prefs"
    const val KEY_SERVER_HOST = "server_host"

    // Separate store for the messaging identity (phone + bearer token).
```


## Assistant

Good news — the app **already** supports a runtime-configurable server host (`server_host` in SharedPreferences, editable in the calls screen, no rebuild needed). That means switching between your local/LAN server and an AWS backup is already just typing a new IP/hostname in the app — no extra work needed on the client side.

Here's a concrete plan for the AWS backup, grounded in what's actually in `server/`:

## What you have vs. what's missing for AWS

- **No `Dockerfile` exists yet** — `run_dev.py` just runs `uvicorn` directly. You'll want a container or at minimum a systemd unit for a reliable AWS deploy.
- **Model weights already come from Hugging Face Hub** (`setup/download_models.py` → `snapshot_download`), not from a bundled repo — this is actually great news for "backup + update models" since both your local machine and AWS can pull from the same source of truth with one command.
- **Coturn config already exists** (`server/turnserver.conf`) but is wired for the USB/LAN workaround (`adb reverse tcp:3478`). On AWS with a real public IP, this gets *simpler*, not harder — standard TURN over the internet, no adb tricks needed.

## Recommended AWS architecture

1. **Instance type:** `g4dn.xlarge` (NVIDIA T4, 16GB) is comfortably enough for your model sizes — AST/wav2vec2 (~90–95M params each, ~350MB), ECAPA (~20M), Qwen2.5-3B-Instruct Q4_K_M GGUF (~2GB). Cheaper than a bigger GPU box, and your local-fallback code already proves the pipeline runs fine at this scale. If budget is the priority over live-demo latency, a CPU-only instance (`c6i.2xlarge`) also works since every engine has a working local/CPU fallback per your own audit — just slower per-segment.
2. **Networking:** Elastic IP (stable address for the app to point at) + security group opening: `443`/`8765` (API/WebSocket, ideally behind Nginx+TLS), `3478` tcp/udp (coturn), and a UDP relay range (e.g. `49152–65535`) for TURN media relay.
3. **Process management:** containerize with a `Dockerfile` (doesn't exist yet — I can draft one) or run via `systemd` calling `run_dev.py`/`uvicorn` with `gunicorn -k uvicorn.workers.UvicornWorker` for restart-on-crash. Put `coturn` as a second systemd service on the same box.
4. **Secrets:** `DEEPGRAM_API_KEY`, `GROQ_API_KEY`, `VELMA_API_KEY`, `STORAGE_ENCRYPTION_KEY`, `ANTAI_PHONE_SALT` go in `server/.env` on the instance (gitignored) — or pull from AWS Secrets Manager/SSM Parameter Store at boot if you want them out of the filesystem entirely. **Do not** ship the default `"change-me-please"` key to AWS — that warning in `db.py` will fire loudly if you forget.
5. **Config diff for AWS vs. local:** in `config.yaml`, `ice.turn_url` needs to point at the EC2 public IP instead of `127.0.0.1`, and `ice.stun_url` can now be a real STUN server (`stun.l.google.com:19302`) instead of empty — the USB-only workarounds in the current config are specifically there *because* there's no public IP locally; AWS removes that constraint.
6. **Cost control (since this is explicitly a backup, not primary):** GPU instances bill hourly whether used or not. Either (a) manually start/stop around demo/judging windows, or (b) a small EventBridge+Lambda schedule to stop it overnight, or (c) use Spot for development/testing only — not during an actual judged demo, since Spot can be reclaimed mid-call.

## "Update models there also" — the actual workflow

Since `download_models.py` already pulls from HF Hub by `repo_id`, the update loop for any future fine-tuning (ECAPA on Indian speakers, AASIST swap, etc. from `RESEARCH_FINDINGS.md`) is the same on both machines:

1. Fine-tune locally, push the resulting checkpoint to a **private HF Hub repo** (free, versioned, one `huggingface-cli upload`) instead of hand-copying files.
2. Change the corresponding `repo_id` string in `PIPELINE_MODELS` (`setup/download_models.py`).
3. Run `python setup/download_models.py --only <engine_name>` on **both** local and AWS — same command, same source of truth, no drift between environments.
4. Restart the service (`systemctl restart antai`) to pick up the new weights.

This avoids ever having two different "flavors" of a model quietly diverging between your laptop and your AWS backup — a real risk if you update one and forget the other before a demo.

**Want me to draft the actual `Dockerfile` + a systemd unit file + a `deploy.sh` for this?** I can also write the updated `config.yaml` `ice`/`server` block for the AWS case specifically. (Note: I still don't have file-write tools in this session, so I'd hand you the content to paste in — same as `RESEARCH_FINDINGS.md` above — unless write access gets enabled.)

## User (checkpoint)

for llm using groq and gemini,openrouter as backup

## Assistant

<thinking>
I need to look at the current LLM provider setup to figure out how to add Gemini and OpenRouter as fallbacks alongside Groq—checking the config schema and inference engine so I can design a fallback chain (Groq primary, then Gemini, then OpenRouter, then local Qwen) for redundancy.
</thinking>

**Tool Call: Search files for regex `class.*Llm|class.*LLM|_load_groq|_load_local|providers\.llm|def generate|def explain`**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/llm.py#L1:11

```
"""LLM serving — Groq (hosted) or llama-cpp-python (local Qwen2.5 GGUF).

Backend is selected by ``providers.llm`` in config:

  * ``groq``   -> Groq chat completions (OpenAI-compatible); no GGUF loaded
  * ``local``  -> self-hosted Qwen2.5 GGUF via llama-cpp-python

``complete()`` / ``complete_stream()`` keep the same signatures and return
types, so the reasoner (verdict / live guidance / post-call report) is
unchanged.
"""
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/llm.py#L24:35

```
class LlmEngine(BaseEngine):
    name = "llm"

    def _load(self) -> bool:
        if get_config().providers.llm == "groq":
            return self._load_groq()
        return self._load_local()

    # -------------------------------------------------------------- Groq (API)
    def _load_groq(self) -> bool:
        prov = get_config().providers
        if not prov.groq_api_key:
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/llm.py#L45:49

```

    # ------------------------------------------------------ llama-cpp (local)
    def _load_local(self) -> bool:
        cfg = get_config()
        size = cfg.models.llm_size  # 3b | 1.5b
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/reasoner.py#L135:145

```
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
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/reasoner.py#L291:301

```
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
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/reasoner.py#L330:340

```
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
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/voice/deepfake_voice.py#L78:82

```

        # local first, so we always know whether a fallback exists
        self._local_ready = self._load_local()
        if self._local_ready:
            self._local_device = self.device
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/voice/deepfake_voice.py#L149:153

```

    # ------------------------------------------------- SSL ensemble (local)
    def _load_local(self) -> bool:
        cfg = get_config()
        device = self._device_for()
```


**Tool Call: 3 matches**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/__init__.py

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/llm.py

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/inference/llm/reasoner.py

**Tool Call: Read file `server\src\antai\inference\llm\llm.py`**
Status: Completed

```SIH26P1/server/src/antai/inference/llm/llm.py
     1	"""LLM serving — Groq (hosted) or llama-cpp-python (local Qwen2.5 GGUF).
     2	
     3	Backend is selected by ``providers.llm`` in config:
     4	
     5	  * ``groq``   -> Groq chat completions (OpenAI-compatible); no GGUF loaded
     6	  * ``local``  -> self-hosted Qwen2.5 GGUF via llama-cpp-python
     7	
     8	``complete()`` / ``complete_stream()`` keep the same signatures and return
     9	types, so the reasoner (verdict / live guidance / post-call report) is
    10	unchanged.
    11	"""
    12	from __future__ import annotations
    13	
    14	import logging
    15	import threading
    16	from pathlib import Path
    17	
    18	from ..hub import BaseEngine
    19	from ...config import get_config
    20	
    21	log = logging.getLogger(__name__)
    22	
    23	
    24	class LlmEngine(BaseEngine):
    25	    name = "llm"
    26	
    27	    def _load(self) -> bool:
    28	        if get_config().providers.llm == "groq":
    29	            return self._load_groq()
    30	        return self._load_local()
    31	
    32	    # -------------------------------------------------------------- Groq (API)
    33	    def _load_groq(self) -> bool:
    34	        prov = get_config().providers
    35	        if not prov.groq_api_key:
    36	            self._unavailable_reason = "llm: GROQ_API_KEY not set"
    37	            return False
    38	        self._backend = "groq"
    39	        # kept for interface parity with the local path (HTTP is thread-safe,
    40	        # but callers never inspect this)
    41	        self._lock = threading.Lock()
    42	        self.device = "groq-api"
    43	        log.info("LLM backend: Groq (model=%s)", prov.groq_model)
    44	        return True
    45	
    46	    # ------------------------------------------------------ llama-cpp (local)
    47	    def _load_local(self) -> bool:
    48	        cfg = get_config()
    49	        size = cfg.models.llm_size  # 3b | 1.5b
    50	        llm_dir = Path(cfg.models.root) / "llm"
    51	        if not llm_dir.exists():
    52	            return False
    53	        candidates = sorted(llm_dir.glob(f"*{size}*.gguf")) + sorted(llm_dir.glob("*.gguf"))
    54	        if not candidates:
    55	            return False
    56	        path = str(candidates[0])
    57	        try:
    58	            from llama_cpp import Llama
    59	            n_gpu = self._gpu_layers()
    60	            kwargs = dict(model_path=path, n_ctx=2048, n_gpu_layers=n_gpu,
    61	                          verbose=False)
    62	            self.model = Llama(**kwargs)
    63	            # llama-cpp's chat completion is NOT thread-safe: serialize every
    64	            # call so the verdict + live-guidance + report paths can never run
    65	            # concurrently and corrupt/hang the model.
    66	            self._lock = threading.Lock()
    67	            self._backend = "local"
    68	            self.path = path
    69	            self.device = "cuda" if n_gpu > 0 else "cpu"
    70	            log.info("LLM loaded: %s on %s", path, self.device)
    71	            return True
    72	        except Exception as e:
    73	            log.warning("LLM load failed: %s", e)
    74	            return False
    75	
    76	    def _gpu_layers(self) -> int:
    77	        try:
    78	            import torch
    79	            from llama_cpp import llama_supports_gpu_offload
    80	            if torch.cuda.is_available() and self._device_for() == "cuda" \
    81	                    and llama_supports_gpu_offload():
    82	                return -1  # offload all layers
    83	        except Exception:
    84	            pass
    85	        return 0
    86	
    87	    def complete(self, messages: list[dict], max_tokens: int = 256,
    88	                 temperature: float = 0.3) -> str:
    89	        if not self.ready():
    90	            return ""
    91	        if getattr(self, "_backend", None) == "groq":
    92	            from ..providers import groq_chat
    93	            prov = get_config().providers
    94	            return groq_chat(messages, api_key=prov.groq_api_key,
    95	                             model=prov.groq_model, endpoint=prov.groq_endpoint,
    96	                             max_tokens=max_tokens, temperature=temperature)
    97	        try:
    98	            with self._lock:
    99	                resp = self.model.create_chat_completion(
   100	                    messages=messages, max_tokens=max_tokens, temperature=temperature)
   101	            return resp["choices"][0]["message"]["content"].strip()
   102	        except Exception as e:
   103	            log.warning("LLM complete failed: %s", e)
   104	            return ""
   105	
   106	    def complete_stream(self, messages: list[dict], max_tokens: int = 256,
   107	                        temperature: float = 0.3):
   108	        if not self.ready():
   109	            return
   110	        if getattr(self, "_backend", None) == "groq":
   111	            from ..providers import groq_chat_stream
   112	            prov = get_config().providers
   113	            yield from groq_chat_stream(
   114	                messages, api_key=prov.groq_api_key, model=prov.groq_model,
   115	                endpoint=prov.groq_endpoint, max_tokens=max_tokens,
   116	                temperature=temperature)
   117	            return
   118	        try:
   119	            with self._lock:
   120	                stream = self.model.create_chat_completion(
   121	                    messages=messages, max_tokens=max_tokens, temperature=temperature,
   122	                    stream=True)
   123	                for chunk in stream:
   124	                    delta = chunk["choices"][0]["delta"].get("content")
   125	                    if delta:
   126	                        yield delta
   127	        except Exception as e:
   128	            log.warning("LLM stream failed: %s", e)

```


## Assistant

<thinking>
I see the current structure uses a single backend chosen at load time, with complete() and complete_stream() branching on that choice, but there's no runtime fallback chain if Groq fails—I need to check whether groq_chat fails silently by returning an empty string.

Now I'm designing a cascading fallback chain: Groq as primary, then Gemini, then OpenRouter, then local Qwen as the final offline option, which is more robust than the current static groq/local choice. I'll check providers.py for the existing groq_chat and groq_chat_stream implementations to model the new Gemini and OpenRouter functions similarly, and review config.py's ProvidersConfig structure to extend it.
</thinking>

**Tool Call: Read file `server\src\antai\inference\providers.py`**
Status: Completed

```
SUCCESS: File outline retrieved. This file is too large to read all at once, so the outline below shows the file's structure with line numbers.

IMPORTANT: Do NOT retry this call without line numbers - you will get the same outline.
Instead, use the line numbers below to read specific sections by calling this tool again with start_line and end_line parameters.

# File outline for C:\Users\sidde\OneDrive\Desktop\FSD\Hackthonss\SIH26P1\server\src\antai\inference\providers.py

def pcm_to_wav_bytes [L36-45]
def pcm_to_s16le_bytes [L48-50]
def _http_post [L54-74]
def _http_post_stream [L77-94]
def _dig [L97-116]
def deepgram_transcribe [L120-154]
def last_llm_error [L166-168]
def _explain_groq_status [L171-184]
def groq_chat [L187-220]
def groq_chat_stream [L223-245]
def last_velma_error [L280-282]
def velma_eos_in_use [L285-287]
def _redact [L290-297]
def _norm_prob [L300-308]
def _first_num [L311-315]
def _extract_score [L318-364]
async def _velma_attempt [L367-450]
 async def _run [L391-430]
def velma_planned_attempts [L459-461]
async def _velma_ws_detect [L464-510]
def velma_detect [L513-586]
 def _run_coro [L561-567]

Showing symbols 1-22 (total symbols: 22)


NEXT STEPS: To read a specific symbol's implementation, call read_file with the same path plus start_line and end_line from the outline above.
For example, to read a function shown as [L100-150], use start_line: 100 and end_line: 150.
```


**Tool Call: Search files for regex `class ProvidersConfig|groq_api_key|groq_model|groq_endpoint|velma_|from_dict`**
Status: Completed

file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L50:52

```
    @classmethod
    def from_dict(cls, d: dict) -> "ServerConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L64:66

```
    @classmethod
    def from_dict(cls, d: dict) -> "AuthConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L75:80

```
    @classmethod
    def from_dict(cls, d: dict) -> "StorageConfig":
        db_path = _resolve_path(d.get("url", "sqlite:///antai.db").replace("sqlite:///", ""))
        return cls(url=d.get("url", "sqlite:///antai.db"),
                   encryption_key=d.get("encryption_key", "change-me-please"),
                   db_path=db_path)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L91:100

```
    @classmethod
    def from_dict(cls, d: dict) -> "ModelsConfig":
        root = _resolve_path(d.get("root", "models"))
        return cls(
            root=root,
            device=d.get("device", "auto"),
            llm_device=d.get("llm_device", "auto"),
            llm_size=d.get("llm_size", "3b"),
            engines=d.get("engines", {}),
        )
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L104:114

```
class ProvidersConfig:
    """Hosted-API backends for the voice pipeline.

    Each voice-side engine can run its original LOCAL model or delegate to a
    hosted API. The engine NAMES and their call-site interfaces are unchanged,
    so the orchestrator/UI ("Models n/m loaded") behave exactly the same; only
    the backend that computes each number changes.

    API keys are read from the environment (``server/.env``, gitignored), never
    from this file, so secrets are never committed. Endpoints/models/backends
    are plain config so they can be changed without touching code.
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L133:178

```
    # behaviors/model); ``response_path`` is an optional dotted path override if
    # a deployment surfaces the synthetic-voice score at a fixed field.
    velma_api_key: str = ""
    velma_model_id: str = "velma-2-synthetic-voice-detection-streaming"
    velma_endpoint: str = ("wss://platform.modulate.ai/api/"
                           "velma-2-synthetic-voice-detection-streaming")
    velma_auth_style: str = "query"           # query (?api_key=) for Modulate
    velma_response_path: str = ""             # optional dotted JSON path override
    velma_config_json: str = ""              # first WS frame; "" -> built-in default
    # End-of-stream framing: "" auto-negotiates once per process (recommended),
    # or pin it to binary / text / json / none once you know which one the live
    # endpoint accepts. Sending the wrong one gets the stream closed with 1003.
    velma_eos: str = ""

    # Groq (LLM + LangGraph reasoning; OpenAI-compatible)
    groq_api_key: str = ""
    groq_model: str = "openai/gpt-oss-120b"
    groq_endpoint: str = "https://api.groq.com/openai/v1/chat/completions"

    @classmethod
    def from_dict(cls, d: dict) -> "ProvidersConfig":
        dg = d.get("deepgram", {}) or {}
        ve = d.get("velma", {}) or {}
        gq = d.get("groq", {}) or {}
        # env var wins for keys; config supplies models/endpoints (never keys)
        return cls(
            asr=d.get("asr", "faster_whisper"),
            voice_deepfake=d.get("voice_deepfake", "local"),
            llm=d.get("llm", "local"),
            deepgram_api_key=os.environ.get("DEEPGRAM_API_KEY", ""),
            deepgram_model=dg.get("model", "nova-2"),
            deepgram_endpoint=dg.get("endpoint", "https://api.deepgram.com/v1/listen"),
            velma_api_key=os.environ.get("VELMA_API_KEY", ""),
            velma_model_id=ve.get("model_id", "velma-2-synthetic-voice-detection-streaming"),
            velma_endpoint=(os.environ.get("VELMA_ENDPOINT") or ve.get("endpoint")
                            or "wss://platform.modulate.ai/api/"
                               "velma-2-synthetic-voice-detection-streaming"),
            velma_auth_style=ve.get("auth_style", "query"),
            velma_response_path=ve.get("response_path", ""),
            velma_config_json=ve.get("config_json", ""),
            velma_eos=ve.get("eos", ""),
            groq_api_key=os.environ.get("GROQ_API_KEY", ""),
            groq_model=gq.get("model", "openai/gpt-oss-120b"),
            groq_endpoint=gq.get("endpoint", "https://api.groq.com/openai/v1/chat/completions"),
        )

```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L194:202

```
    @classmethod
    def from_dict(cls, d: dict) -> "ScenarioConfig":
        return cls(
            label=d.get("label", cls.label),
            description=d.get("description", ""),
            risk_verify_at=float(d.get("risk_verify_at", 75)),
            risk_critical_at=float(d.get("risk_critical_at", 90)),
            context_risk_boost=float(d.get("context_risk_boost", 0)),
        )
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L261:263

```
    @classmethod
    def from_dict(cls, d: dict) -> "PipelineConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L272:274

```
    @classmethod
    def from_dict(cls, d: dict) -> "CollectiveConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L282:284

```
    @classmethod
    def from_dict(cls, d: dict) -> "PushConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L309:311

```
    @classmethod
    def from_dict(cls, d: dict) -> "IceConfig":
        return cls(**d)
```


file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/src/antai/config.py#L334:347

```
        return cls(
            server=ServerConfig.from_dict(raw.get("server", {})),
            auth=AuthConfig.from_dict(raw.get("auth", {})),
            storage=StorageConfig.from_dict(raw.get("storage", {})),
            models=ModelsConfig.from_dict(raw.get("models", {})),
            pipeline=PipelineConfig.from_dict(raw.get("pipeline", {})),
            collective=CollectiveConfig.from_dict(raw.get("collective", {})),
            push=PushConfig.from_dict(raw.get("push", {})),
            ice=IceConfig.from_dict(raw.get("ice", {})),
            providers=ProvidersConfig.from_dict(raw.get("providers", {})),
            log_level=raw.get("log_level", "INFO"),
            scenarios={k: ScenarioConfig.from_dict(v)
                       for k, v in (raw.get("scenarios") or {}).items()},
        )
```


## Assistant

<thinking>
Now that I have the exact structure, I'm going to model gemini_chat and openrouter_chat after the groq_chat function.
</thinking>

**Tool Call: Read file `server\src\antai\inference\providers.py` (lines 160-246)**
Status: Completed

```SIH26P1/server/src/antai/inference/providers.py
   160	# had nothing to say" and the reasoner quietly served template text forever.
   161	# We now log at ERROR with the actual cause and remember it so
   162	# GET /api/debug/models can show why the reason/advice is missing.
   163	_LAST_LLM_ERROR: Optional[str] = None
   164	
   165	
   166	def last_llm_error() -> Optional[str]:
   167	    """Most recent Groq failure, or None if the last call succeeded."""
   168	    return _LAST_LLM_ERROR
   169	
   170	
   171	def _explain_groq_status(status: int, body: bytes, model: str) -> str:
   172	    snippet = body[:300].decode("utf-8", "ignore")
   173	    if status in (401, 403):
   174	        hint = "GROQ_API_KEY is missing, wrong or revoked (check server/.env)"
   175	    elif status == 404 or "decommissioned" in snippet or "does not exist" in snippet:
   176	        hint = (f"model '{model}' was not accepted by Groq — it may have been "
   177	                f"renamed or decommissioned; update providers.groq.model in config.yaml")
   178	    elif status == 429:
   179	        hint = "Groq rate limit / quota exhausted"
   180	    elif status >= 500:
   181	        hint = "Groq server-side error; retry later"
   182	    else:
   183	        hint = "unexpected Groq response"
   184	    return f"HTTP {status}: {hint} :: {snippet}"
   185	
   186	
   187	def groq_chat(messages: list[dict], *, api_key: str, model: str, endpoint: str,
   188	              max_tokens: int = 256, temperature: float = 0.3) -> str:
   189	    """Groq chat completion (OpenAI-compatible). Returns the message content.
   190	
   191	    Returns "" on failure (the reasoner then falls back to template text), but
   192	    the reason is logged loudly and retained in ``last_llm_error()``.
   193	    """
   194	    global _LAST_LLM_ERROR
   195	    if not api_key:
   196	        _LAST_LLM_ERROR = "GROQ_API_KEY is not set (see server/.env)"
   197	        log.error("Groq chat skipped: %s", _LAST_LLM_ERROR)
   198	        return ""
   199	    headers = {"Authorization": f"Bearer {api_key}"}
   200	    payload = {"model": model, "messages": messages,
   201	               "max_tokens": max_tokens, "temperature": temperature}
   202	    try:
   203	        status, body = _http_post(endpoint, headers, json_body=payload, timeout=30.0)
   204	        if status != 200:
   205	            _LAST_LLM_ERROR = _explain_groq_status(status, body, model)
   206	            log.error("Groq chat failed — %s", _LAST_LLM_ERROR)
   207	            return ""
   208	        data = json.loads(body)
   209	        content = (_dig(data, "choices.0.message.content") or "").strip()
   210	        if not content:
   211	            _LAST_LLM_ERROR = (f"Groq returned 200 but no message content "
   212	                               f"(finish_reason={_dig(data, 'choices.0.finish_reason')})")
   213	            log.error("Groq chat empty — %s", _LAST_LLM_ERROR)
   214	            return ""
   215	        _LAST_LLM_ERROR = None
   216	        return content
   217	    except Exception as e:
   218	        _LAST_LLM_ERROR = f"{type(e).__name__}: {e}"
   219	        log.error("Groq chat raised — %s", _LAST_LLM_ERROR, exc_info=True)
   220	        return ""
   221	
   222	
   223	def groq_chat_stream(messages: list[dict], *, api_key: str, model: str,
   224	                     endpoint: str, max_tokens: int = 256,
   225	                     temperature: float = 0.3):
   226	    """Groq streaming chat completion; yields content deltas (str)."""
   227	    headers = {"Authorization": f"Bearer {api_key}"}
   228	    payload = {"model": model, "messages": messages, "max_tokens": max_tokens,
   229	               "temperature": temperature, "stream": True}
   230	    try:
   231	        for raw in _http_post_stream(endpoint, headers, payload, timeout=30.0):
   232	            line = raw.decode("utf-8", "ignore").strip()
   233	            if not line.startswith("data:"):
   234	                continue
   235	            chunk = line[len("data:"):].strip()
   236	            if chunk == "[DONE]":
   237	                break
   238	            try:
   239	                delta = _dig(json.loads(chunk), "choices.0.delta.content")
   240	            except Exception:
   241	                continue
   242	            if delta:
   243	                yield delta
   244	    except Exception as e:
   245	        log.warning("Groq stream failed: %s", e)
   246	

```


## Assistant

<thinking>
I now realize OpenRouter follows the same OpenAI-compatible shape as Groq with just a different endpoint and optional headers, but Gemini uses a distinct Google API format needing its own adapter function. I'm starting to sketch the plan, beginning with config changes to add Gemini API key and model settings.

Adding OpenRouter config...

Then in `providers.py`, I need a `gemini_chat()` adapter that converts OpenAI-style messages into Gemini's `contents` format with roles mapped to user/model and system prompts handled separately, extracting the response text from the nested candidates structure. The `openrouter_chat()` function should be nearly identical to the Groq one since OpenRouter mirrors that format.

For `llm.py`, I'm rethinking `LlmEngine` to support a fallback chain rather than locking into a single backend — the engine stays ready as long as any configured provider has a key, and `complete()` tries each backend in order until one succeeds, falling back to the local GGUF model as the last resort, with the local model ideally being pre-loaded so it's warm when needed.

For streaming, mid-stream fallback is too messy, so I'll keep it simple: pick the first available backend and only fall back if it errors before yielding any tokens, since correctness matters more for the non-streaming `complete()` path used in verdicts than for the live-guidance streaming UX. Then I need to update config.yaml with gemini/openrouter provider blocks and the fallback chain list.

Since I can't write files directly, I'll present this as a clear plan with paste-ready code snippets: config schema additions, provider block additions, new `gemini_chat()` and `openrouter_chat()` functions, a refactored chain-based `llm.py`, and .env key additions — plus a note that OpenRouter itself supports a `models` array with built-in automatic fallback across underlying providers, which is worth mentioning as an alternative approach.
</thinking>

Good — I checked `inference/llm/llm.py` and `inference/providers.py`. Right now the LLM backend is a **single static choice** (`groq` or `local`, picked once at startup) with no runtime fallback — if Groq fails mid-call (rate limit, revoked key, outage), `complete()` just returns `""` and the reasoner silently serves template text. Adding Gemini + OpenRouter as backups means turning this into an actual **fallback chain**, not just a third static option. Here's the concrete plan.

## 1. Config additions (`server/src/antai/config.py`)

Add two new provider blocks + a configurable chain, alongside the existing `groq_*` fields in `ProvidersConfig`:

```python
    # Gemini (Google) — LLM fallback #2
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.0-flash"
    gemini_endpoint: str = "https://generativelanguage.googleapis.com/v1beta/models"

    # OpenRouter — LLM fallback #3 (OpenAI-compatible; can itself route to many models)
    openrouter_api_key: str = ""
    openrouter_model: str = "openai/gpt-oss-120b"
    openrouter_endpoint: str = "https://openrouter.ai/api/v1/chat/completions"

    # Order to try LLM backends in; first with a usable key/load wins per call.
    # "local" is always the final entry in practice — it needs no network/key.
    llm_fallback_chain: list[str] = field(default_factory=lambda: ["groq", "gemini", "openrouter", "local"])
```

In `ProvidersConfig.from_dict()`, add:

```python
        ge = d.get("gemini", {}) or {}
        orr = d.get("openrouter", {}) or {}
        ...
        gemini_api_key=os.environ.get("GEMINI_API_KEY", ""),
        gemini_model=ge.get("model", "gemini-2.0-flash"),
        gemini_endpoint=ge.get("endpoint", "https://generativelanguage.googleapis.com/v1beta/models"),
        openrouter_api_key=os.environ.get("OPENROUTER_API_KEY", ""),
        openrouter_model=orr.get("model", "openai/gpt-oss-120b"),
        openrouter_endpoint=orr.get("endpoint", "https://openrouter.ai/api/v1/chat/completions"),
        llm_fallback_chain=d.get("llm_fallback_chain", ["groq", "gemini", "openrouter", "local"]),
```

## 2. `config.yaml` additions

```yaml
providers:
  llm: groq                    # kept for backward-compat / display; chain below is what actually runs
  llm_fallback_chain: ["groq", "gemini", "openrouter", "local"]
  gemini:
    model: "gemini-2.0-flash"
  openrouter:
    model: "openai/gpt-oss-120b"   # OpenRouter can proxy many providers under one key
```

## 3. New adapter functions in `inference/providers.py`

**OpenRouter is OpenAI-compatible** — same shape as `groq_chat`, just a different endpoint/headers:

```python
def openrouter_chat(messages: list[dict], *, api_key: str, model: str, endpoint: str,
                    max_tokens: int = 256, temperature: float = 0.3) -> str:
    """OpenRouter chat completion (OpenAI-compatible). Returns "" on failure."""
    if not api_key:
        log.error("OpenRouter chat skipped: OPENROUTER_API_KEY not set")
        return ""
    headers = {"Authorization": f"Bearer {api_key}",
               "HTTP-Referer": "https://antai.local", "X-Title": "antAI"}
    payload = {"model": model, "messages": messages,
               "max_tokens": max_tokens, "temperature": temperature}
    try:
        status, body = _http_post(endpoint, headers, json_body=payload, timeout=30.0)
        if status != 200:
            log.error("OpenRouter chat failed — HTTP %s: %s", status, body[:300])
            return ""
        content = (_dig(json.loads(body), "choices.0.message.content") or "").strip()
        return content
    except Exception as e:
        log.error("OpenRouter chat raised — %s", e, exc_info=True)
        return ""
```

**Gemini has a different request/response shape** (no `messages` array — `contents` + `systemInstruction`):

```python
def gemini_chat(messages: list[dict], *, api_key: str, model: str, endpoint: str,
                max_tokens: int = 256, temperature: float = 0.3) -> str:
    """Gemini generateContent. Converts OpenAI-style messages -> Gemini format."""
    if not api_key:
        log.error("Gemini chat skipped: GEMINI_API_KEY not set")
        return ""
    system_parts = [m["content"] for m in messages if m.get("role") == "system"]
    contents = [{"role": "user" if m["role"] == "user" else "model",
                 "parts": [{"text": m["content"]}]}
                for m in messages if m.get("role") in ("user", "assistant")]
    payload = {"contents": contents,
               "generationConfig": {"maxOutputTokens": max_tokens, "temperature": temperature}}
    if system_parts:
        payload["systemInstruction"] = {"parts": [{"text": "\n".join(system_parts)}]}
    url = f"{endpoint}/{model}:generateContent?key={api_key}"
    try:
        status, body = _http_post(url, {}, json_body=payload, timeout=30.0)
        if status != 200:
            log.error("Gemini chat failed — HTTP %s: %s", status, body[:300])
            return ""
        content = (_dig(json.loads(body), "candidates.0.content.parts.0.text") or "").strip()
        return content
    except Exception as e:
        log.error("Gemini chat raised — %s", e, exc_info=True)
        return ""
```

## 4. Rework `LlmEngine.complete()` into a real fallback chain (`inference/llm/llm.py`)

Instead of picking one backend at load time, try each configured backend in order and use the first non-empty result:

```python
def complete(self, messages: list[dict], max_tokens: int = 256,
             temperature: float = 0.3) -> str:
    prov = get_config().providers
    for backend in prov.llm_fallback_chain:
        if backend == "groq" and prov.groq_api_key:
            result = groq_chat(messages, api_key=prov.groq_api_key, model=prov.groq_model,
                               endpoint=prov.groq_endpoint, max_tokens=max_tokens, temperature=temperature)
        elif backend == "gemini" and prov.gemini_api_key:
            result = gemini_chat(messages, api_key=prov.gemini_api_key, model=prov.gemini_model,
                                 endpoint=prov.gemini_endpoint, max_tokens=max_tokens, temperature=temperature)
        elif backend == "openrouter" and prov.openrouter_api_key:
            result = openrouter_chat(messages, api_key=prov.openrouter_api_key, model=prov.openrouter_model,
                                     endpoint=prov.openrouter_endpoint, max_tokens=max_tokens, temperature=temperature)
        elif backend == "local":
            result = self._complete_local(messages, max_tokens, temperature)  # lazy-loads GGUF on first use
        else:
            continue
        if result:
            self.last_backend_used = backend   # exposed via /api/debug/models, matches existing transparency pattern
            return result
    self.last_backend_used = None
    return ""
```

This preserves the project's existing "no fabricated values" contract — if every backend in the chain fails, you get `""` and the reasoner falls back to its deterministic template, exactly like today, just with three more chances to succeed first.

## 5. `.env` additions

```
GEMINI_API_KEY=...
OPENROUTER_API_KEY=...
```

## Notes / trade-offs

- **`complete_stream()`** (used for live in-call guidance) is trickier to fall back on mid-stream — recommend: try the first backend for streaming, and only fall back to the next if it errors *before* yielding any tokens (not mid-response). Full try-all-in-order fallback is best reserved for `complete()` (verdict/report generation), since it's not time-critical the same way.
- **OpenRouter can itself multiplex models** — a single OpenRouter request can include a `"models": [...]` array and it auto-falls-back across underlying providers server-side. If you want to simplify, you could let OpenRouter be your *only* backup (letting it fan out to Gemini/other models under one key) instead of wiring Gemini separately — but since you explicitly want both, keeping them as distinct chain entries gives you provider-level redundancy (if Google or OpenRouter itself has an outage, the other still works).
- Same file-write caveat as before — I don't have write tools in this session, so these are ready-to-paste blocks. Say the word if write access gets enabled and I'll apply all of this directly.
