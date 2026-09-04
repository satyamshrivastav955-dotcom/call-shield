# antAI v3 — Device-Level Scam Defense Plan

**Status:** planning only. No code changes proposed here.
**Date:** 2026-08-23
**Purpose:** fix the architectural flaw the judges identified, and define what we build if time allows.

---

## 1. The flaw the judges found

Our current calling design requires **both** people to install antAI and to type each other's in-app
user ID. Analysis then runs on that app-to-app WebRTC call.

The objection is correct and fatal to the pitch: a scammer will never install our app, never register,
and never hand us their antAI ID. So the calling half of the product can only ever analyze calls
between two cooperating users — which are, by definition, not scam calls. We were demonstrating the
pipeline on a channel where the threat cannot appear.

Note carefully that the objection applies **only to the calling half**. The SMS and notification
scanning we finished on 2026-08-23 already has the correct property: a scammer texts the victim from
any ordinary phone, installs nothing, cooperates in no way, and we still analyze the content. That
half of the product already answers the judges. The plan below extends the same property to calls.

---

## 2. The corrected thesis: victim-side install

antAI is installed by the **person being protected**, and by nobody else. It sits on the receiving end
of the phone and inspects what arrives — calls, texts, and notifications from any sender on the public
network.

This is not a workaround or a compromise. It is the same model Truecaller, Hiya, and Google's own
Pixel call screening use: the protected user installs, the caller does not participate. Our
differentiator is that we go past caller-ID reputation and analyze the **live content** of the
interaction — the language of the scam and the authenticity of the voice — rather than only judging
the number.

Reframed one-liner for the pitch: *antAI is a guardian that lives on the victim's phone and inspects
every call, text, and notification that reaches them, no matter who sent it.*

---

## 3. What we keep, and what changes

We keep essentially all of the engineering already done. The analysis brain is unchanged: the server's
LangGraph pipeline, Deepgram ASR, the Groq LLM scam-intent reasoning, the voice-deepfake model, the
verdict/band schema, the risk UI, the SMS and notification capture, and the whole messaging stack. The
`/ws/tap` ingestion endpoint already accepts `kind: "voice"`, i.e. audio-only sessions, so it can take
a phone-call audio stream without redesign.

What changes is only the **source of the call**. Today the source is an app-to-app WebRTC session
negotiated through our Node signaling server. In v3 the primary source becomes the **real cellular
call** arriving from any stranger, captured because our app holds the operating system's default
phone-app role.

The existing app-to-app P2P calling does not get deleted. It gets demoted from "the product" to two
useful supporting roles: a clean-audio channel for demonstrating the detection models without
degradation, and a future "verified antAI-to-antAI call" feature. It also remains our safest
regression baseline, since we know it works.

---

## 4. The OS roles we acquire, and what each one actually gives us

Android gates this kind of capability behind `RoleManager` roles that the user grants once, with a
system consent dialog. There are three worth holding, plus one special access grant.

**`ROLE_CALL_SCREENING`** makes us the default caller-ID and spam app. This is the highest-value,
lowest-risk role. Through `CallScreeningService` we are handed every incoming call **before the phone
rings**, with the caller's number, and we may allow it, silence it, or reject it outright. This alone
delivers pre-ring scam interception on real calls from real strangers.

**`ROLE_DIALER`** makes us the default phone app, so our `InCallService` becomes the actual in-call UI.
This gives full call control — answer, mute, hold, end — reliable real-time call state, and, critically
for us, control of the **audio route**, meaning we can put a call on speaker programmatically. It does
**not**, on its own, hand us the call's audio stream; see section 5.

**`ROLE_SMS`** makes us the default messaging app. We already read incoming SMS correctly today via the
`SMS_RECEIVED` broadcast with `RECEIVE_SMS`, so this role is *not* required to see texts — a common
misconception. What it adds is the ability to write to the SMS provider, handle MMS properly, and
legitimately present our unified thread list as the user's real inbox instead of a parallel view.
Worth taking for product completeness, not for capability.

**Notification listener access** (already implemented) is a separate special grant, not a role, and
covers scam content arriving through WhatsApp, Telegram, bank apps, and everything else.

---

## 5. The call-audio question, answered honestly

This is the single most important technical point in the plan, and the place where a well-informed
judge will probe hardest. We must get it right.

**On stock, unrooted Android, no third-party app can read the raw audio of a cellular call.** The audio
sources that would provide it — `VOICE_CALL`, `VOICE_DOWNLINK`, `VOICE_UPLINK` — are guarded by
`CAPTURE_AUDIO_OUTPUT`, which is a `signature|privileged` permission available only to the OEM or a
preinstalled system app. Google closed this path to third parties in Android 10. This is why Truecaller
dropped call recording on Android 10 and above, and why Google's Pixel scam detection can do what we
cannot: Google is the OEM.

Holding `ROLE_DIALER` does **not** lift this restriction. Being the default dialer gives us control
*over* the call; it does not give us the modem's audio.

Given that, we pursue live audio in tiers, and we are explicit about which tier any given demo is using.

**Tier A — number, metadata, and behavior. No audio at all.** Runs on any stock phone, needs no
recording, and is the backbone of the product. On the pre-ring `CallScreeningService` callback we
evaluate the caller against our server: prior reports for that number, crowd-sourced flags, whether
the caller is in contacts, first-contact status, neighbour-spoofing patterns (caller shares the
victim's prefix), call velocity, and time-of-day anomalies. We render a full-screen Truecaller-style
verdict before the phone rings, and auto-reject anything critical. This is genuinely shippable and
needs nothing from the scammer.

**Tier B — consented speakerphone acoustic capture. Degraded audio, stock phone.** When a risky call
connects, we offer the user a "Scam Shield" action. Because we hold `ROLE_DIALER` we set the audio
route to speaker, then open `AudioRecord` and capture what the microphone hears — which now includes
the remote party's voice acoustically, via the speaker. We stream that to `/ws/tap` as `kind: "voice"`
and run the existing pipeline, surfacing a live verdict overlay.

Two implementation details matter here. First, the capture source must be `MIC` (or `UNPROCESSED` where
available), **not** `VOICE_COMMUNICATION` — that source deliberately applies acoustic echo cancellation
and noise suppression, whose entire job is to remove the speaker's output from the mic signal, which is
precisely the signal we want. Choosing it would silently defeat the feature. Second, quality is
device-dependent, because some OEMs apply hardware echo cancellation we cannot disable. Tier B is real,
but it is degraded and must be described as such.

**Tier C — clean audio through privilege. Roadmap, not the shipping app.** A preinstalled or
OEM-partnered build, a carrier/IMS-side deployment, or an enterprise MDM install can hold
`CAPTURE_AUDIO_OUTPUT` and read the call stream directly at full fidelity. A rooted lab device can do
the same for internal model evaluation. This is the honest answer to "how does this become a real
product at scale," and it should appear as a partnership roadmap item rather than a current capability.

**Tier D — adjacent channels that are easy wins.** Voicemail audio, any call recordings the user's own
OEM dialer already produced and exposed, and our existing app-to-app P2P channel, which gives
undegraded audio for demonstrating the detection models at their true accuracy.

---

## 6. AI voice-clone detection under degraded audio

The flagship problem statement is cloned-voice fraud, and Tier B's degradation hurts precisely the
acoustic artefacts a deepfake detector relies on. We therefore do not stake the verdict on the acoustic
model alone. We fuse four signal families, weighting each by how well it survives bad audio.

The acoustic deepfake score comes from the voice model and is the most degradation-sensitive, so it
carries reduced weight under Tier B and full weight under Tier C or the P2P channel. The linguistic
scam-intent signal, derived from the ASR transcript through the LLM, is highly robust to audio quality
— urgency, authority impersonation, payment instructions, and secrecy demands survive a noisy speaker
path intact, and in practice this will carry most of the verdict in Tier B. Conversational and prosodic
anomalies such as unnatural turn-taking latency or flat affect provide a third, partially robust
signal. Finally, caller metadata and reputation from Tier A anchor the whole judgement.

The strongest differentiating feature sits on top of this. The canonical clone attack impersonates a
family member — the "your son has been in an accident" call. Our server already exposes voiceprint
enrolment (`POST /api/voiceprints/enroll`) and trust-circle linking, which means a user can enrol the
voices of the handful of people an attacker would plausibly imitate. When an unknown number claims to be
an enrolled contact, we compare the live voice against that person's real voiceprint. That is a
concrete, demonstrable answer to cloned-voice fraud that pure liveness detection cannot match.

**Known gap to close:** `speaker_verify` is currently `enabled: false` in `server/config.yaml`, because
the local voiceprint model was switched off during the voice-to-API pivot and no hosted replacement was
chosen. The enrolment endpoint and the trust-circle plumbing exist, but the matching model is inactive.
Re-enabling this — either by restoring a local ECAPA-style embedder or selecting a hosted speaker-
verification API — is a prerequisite for the family-voiceprint feature and should be treated as a
first-class task, not an afterthought.

---

## 7. End-to-end flow we are aiming for

A stranger dials the user's ordinary phone number from an ordinary phone. Before the handset rings,
Android hands the call to our `CallScreeningService`, and we query the server for a reputation and
context verdict on that number. If the verdict is critical we can reject or silence it; otherwise the
call proceeds and our in-call UI presents the risk band up front, so the user answers already informed.

If the user answers a call we have flagged as uncertain or risky, we offer Scam Shield. On acceptance we
route audio to the speaker and begin streaming captured audio to the server, which transcribes it, runs
scam-intent reasoning over the transcript, scores the voice for synthesis artefacts, and — where the
caller claims to be an enrolled contact — compares against that contact's voiceprint. Verdicts stream
back live and we escalate the on-screen band as evidence accumulates, up to an interrupting warning
that advises ending the call and never sending money or codes.

After the call the user can confirm or reject our judgement. That feedback writes back to the number's
reputation, which improves Tier A for every other user — a network effect that makes the product
stronger the more people run it, and a good note to end the pitch on.

Texts and third-party app notifications continue to flow through the pipeline already built, so the
same verdict language covers every channel the victim can be reached on.

---

## 8. Phasing, if time allows

The order is chosen so that each phase is independently demonstrable and nothing later is a
prerequisite for something earlier.

Phase one is Tier A: request `ROLE_CALL_SCREENING`, implement `CallScreeningService`, add a
number-reputation lookup and a report-as-scam write on the server, and build the pre-ring verdict
overlay. This is the direct answer to the judges and it is the highest value per unit of risk.

Phase two adds `ROLE_DIALER` with a minimal in-call UI, plus the Scam Shield audio path of Tier B
streaming into the existing `/ws/tap` voice session, with the live verdict overlay.

Phase three re-enables speaker verification and ships family voiceprint enrolment and trust-circle
matching, which is what makes the clone story land.

Phase four is polish and hardening: `ROLE_SMS` for inbox completeness, post-call feedback into
reputation, and a clean fallback everywhere the roles have not been granted.

Throughout, the existing WebRTC calling, signaling, and analysis-tap code stays untouched, exactly as
it has been through the restyle and the messaging build.

---

## 9. What we may claim, and what we must never claim

| Safe to claim | Never claim |
| --- | --- |
| The protected user installs; the caller needs nothing | That we read raw cellular call audio on a stock phone |
| We intercept real calls pre-ring as the default screening app | That `ROLE_DIALER` unlocks the modem audio stream |
| We control the audio route and capture consented speaker audio | That `MediaProjection` can capture voice-call audio — telephony is excluded from playback capture |
| Live transcript-based scam-intent detection on real calls | That we can take over another app's `ConnectionService`; WhatsApp/Signal calls are *self-managed* and are deliberately withheld from the default dialer |
| Tier B audio is degraded, and we fuse robust signals to compensate | That third-party `ROLE_DIALER` apps inherit OEM call-recording privileges — that is unreliable and ROM-specific |
| Clean audio at scale needs OEM/carrier partnership | That the family voiceprint feature works today (`speaker_verify` is disabled) |

Two of these deserve emphasis because they appeared in circulated notes and are wrong.
`MediaProjection` with `AudioPlaybackCaptureConfiguration` captures *app playback* audio and explicitly
excludes telephony and anything marked as voice communication, so it cannot be our call-audio path. And
a default dialer cannot register itself as the managing `ConnectionService` for another app's VoIP
calls — self-managed connections exist precisely so that the app keeps its own UI and audio, and they
are withheld from the default dialer by design.

Volunteering the Tier A/B/C distinction before a judge digs for it reads as engineering maturity. Being
caught overclaiming does the opposite.

---

## 10. Open risks

Call-audio analysis touches recording-consent law, which varies by jurisdiction, so Scam Shield must
stay explicitly per-call and user-initiated, and we should analyze without retaining raw audio. This is
worth stating in the pitch; it is not legal advice and should be checked before any real deployment.

Play Store policy is strict about the dialer and SMS roles and about anything resembling call recording,
so a store listing needs its permission use documented and justified. A hackathon or sideloaded demo is
unaffected.

Tier B quality varies by OEM, so we should test on every handset we intend to demo on and have the P2P
channel ready as a clean-audio fallback if the venue phone behaves badly. Latency also matters: a
verdict that arrives after the victim has already paid is worthless, so the streaming path should target
a few seconds end to end.

Finally, false positives carry real cost. Wrongly flagging a legitimate hospital or bank call trains the
user to ignore us, so the bands should stay conservative and the critical, interrupting state should be
reserved for strong, multi-signal evidence.
