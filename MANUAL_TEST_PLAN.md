# antAI — Manual LAN Test Plan (post-fix verification)

This plan validates the fixes for the four reported problems:

1. Voice call audio was butchered / ~10s late / jittery.
2. Video calls were fully dead (black screen, no audio).
3. Scam-alert popups felt hardcoded / fired on normal messages.
4. UI glitches (half-screen video, banner flicker, stale handlers after navigation).

> Models were **not** touched. All fixes are in the app/integration/relay layers:
> the SFU media relay, the audio ingestion tap, model-inference offloading,
> verdict gating, and the Android UI/realtime routing.

Test on **two physical Android phones on the same Wi-Fi/LAN** as the server
machine (STUN-only is fine on one LAN; no TURN needed).

---

## 0. Build & run

**Server** (from `server/`, Windows venv):

```
.venv\Scripts\activate
python run_dev.py
```

Watch the console. On the first call you should see a line like:

```
pipeline model ready states (kind=voice): {'asr': True, 'voice_deepfake': True, ...}
```

- If **every** value is `False`, the pipeline can only ever output `risk=0`
  ("passive") — that is the old "hardcoded results" feeling. Fix model loading
  first (`GET /api/debug/models`) before judging detection quality.

**App** (from `app/`):

```
./gradlew :app:installDebug        # or "Run" in Android Studio on each phone
```

Confirm both phones point at the server's LAN IP (same base URL / WS URL in the
app config), and both granted **microphone** and **camera** permissions.

Phones used below: **A = caller**, **B = callee**.

---

## Test A — Voice call: clarity & lag

**Steps**

1. From A, open B's chat → tap the **voice call** (📞) button.
2. Accept on B.
3. Speak in normal sentences, alternating: count "one, two, three…", then a full
   sentence, then let the other side reply.
4. Continue for ~60 seconds.

**Pass criteria**

- Speech is **intelligible** end-to-end (words clear, not robotic/garbled).
- Audio is **continuous** — no repeating stutter, no long dropouts.
- Latency feels like a normal VoIP call (roughly < 1s each way). Some lag is
  acceptable per the goal; **butchered/10s-late is a fail**.
- No progressive drift: the delay at 60s should be about the same as at 5s
  (drift = the relay is being starved again).

**Why this should now pass**

- The relay uses aiortc `MediaRelay`, which preserves each frame's original
  `pts`/`time_base` instead of rewriting timestamps from wall-clock / a fixed
  48 kHz assumption.
- The detection tap no longer runs inference inline on the event loop: ASR and
  VAD are offloaded with `asyncio.to_thread`, so the media relay is never
  blocked.

**If it fails**

- Growing delay / stutter → something is still blocking the loop. On the server,
  temporarily set the pipeline to skip heavy models and re-test; watch for slow
  `_evaluate`/graph timings in the log.
- Garbled but low-latency → check the phone's sample rate vs the server
  resampler (`AudioResampler(rate=self.sr)`), and hardware AEC on the device.

---

## Test B — Video call: video + audio

**Steps**

1. From A, open B's chat → tap the **video call** button.
2. Accept on B.
3. Confirm **both** directions show live video and carry audio.
4. Rotate/move each phone; tap **Camera** (📷) to switch front/back.

**Pass criteria**

- Remote video is **visible and moving** on both phones (not a black screen).
- Local self-preview shows in the small top-right tile.
- Audio works simultaneously with video.
- The video fills the frame — **no empty half-screen gap** under the video.

**Why this should now pass**

- Same relay/offload fix as audio: heavy per-frame face/deepfake/lipsync work no
  longer freezes the shared event loop (which had starved *both* audio and video).
- Client attaches the remote track order-independently: whether `onAddTrack`
  fires before or after the surface is ready, `remoteVideoTrack` is remembered
  and the sink is wired by whichever happens second.
- The caller-identity panel is hidden in video mode, so `videoFrame` expands to
  fill the space (fixes the half-screen glitch).

**Log signals (client, `adb logcat | grep CallEngine`)**

```
onAddTrack kind=video enabled=true ...
onAddTrack: attaching remote video to surface
onAddTrack: remote audio track active and enabled
```

If you see `attachRemoteVideo: remote track already present, attaching sink`,
that is the race-safe path working (surface came up after the track).

---

## Test C — Scam-alert sanity

The goal: **normal conversation is silent; real scam scripts alert.**

### C1. Normal messages must NOT alert

Send these one-by-one in a chat (each should produce **no** verdict popup):

- "Hi beta, did you eat?"
- "Papa, call me when you're free."
- "Congratulations on the new job!"
- "The bank is closed on Sunday."
- "Send me the address for the party."

**Pass:** no verdict card appears for any of these. (These contain words like
*beta / papa / congratulations / bank* that used to trigger false alerts; they
were removed from the heuristic and, more importantly, benign verdicts are now
**gated** and never pushed.)

### C2. Real scam scripts SHOULD alert

Send a short scripted sequence, e.g.:

- "This is the bank. Your account is **blocked**. Share the **OTP** we just sent
  to verify your identity immediately or you lose access."
- "Grandma, I **got arrested**, please send **bail money** now, don't tell mom."
- "Install **AnyDesk** so I can fix your account remotely."

**Pass:** a verdict card appears with a sensible band (verify/critical) and a
matching `scam_type` (otp/bank, family_emergency, remote_access). A freeze/verify
prompt may appear on the actionable step (OTP share, money transfer).

**Why this should now behave**

- Benign text still runs the full pipeline but the verdict is only **surfaced**
  when it's actionable (`band ∈ {verify, critical}`) or intercepted. Passive
  verdicts are dropped server-side (`dispatch_message`), so ordinary messages
  can't raise a popup.
- The fallback heuristic scores a lone keyword modestly (~0.33), so a single
  word can't push fused risk into an alerting band by itself.

### C3. In-call verdict banner (no flicker/clobber)

During a voice or video call that trips detection, confirm the verdict card and
any live guidance appear **together in one card** and don't rapidly overwrite
each other. At call start (no risk) there should be **no** card.

---

## Test D — Navigation / realtime routing (UI staleness)

This checks the single-owner router hand-off between screens and the background
notification path.

1. **Foreground message:** On A, stay on Home. From B, send A a message.
   → A saves/shows it (Home owns the router).
2. **Open a chat, receive from a THIRD party:** On A, open B's chat. From a third
   contact C, send A a message.
   → the message is filed under **C's** thread, *not* misfiled into B's open chat.
3. **Return and receive:** On A, back out of the chat to Home, then have B send
   another message. → still received/saved (Home re-claims handlers in `onResume`).
4. **Background call:** Put A's app fully in the background (home button). From B,
   call A. → A gets a **full-screen incoming-call notification** and can accept.
5. **Foreground call:** With A on Home (visible), from B call A. → A shows the
   incoming-call screen directly.
6. **Mid-call leave:** During a call on A, press home and come back. → the call
   keeps running (audio continues); returning restores the in-call verdict card.

**Pass:** each case behaves as described; no "messages/calls stop arriving after
I opened another screen."

**Why:** ownership of the shared router is now lifecycle-driven — a visible
realtime screen *claims* the alerting callbacks in `onResume` and *releases* them
back to the notification service in `onPause`, tracked by a foreground-screen
counter so the background service can't clobber a visible screen at startup.

---

## What to watch in the server log (all tests)

| Log line | Meaning |
|---|---|
| `pipeline model ready states (...)` | which detectors are actually loaded; all-False ⇒ risk always 0 |
| `ALL models are not-ready ...` | detection is effectively disabled — fix model load first |
| `silero VAD loaded for participant N` | VAD is running per speaker (speech will be segmented) |
| `fusion result: risk=.. band=..` | per-evaluation fused risk/band |
| `decision: freeze/verify/log ...` | what the pipeline decided to enforce |

---

## Notes & limits

- Kotlin/app changes here were verified by static review; run a Gradle build on
  your machine to compile — there is no Android toolchain in the review sandbox.
- All server Python in this change set passes `python -m py_compile` and
  `compileall` cleanly.
- No model weights or model internals were modified.
- Known minor: `ChatActivity.onGuidance` still writes the banner directly, but
  `guidance.update` is only emitted on the call path (never for messages), so it
  is dead code in chat and left untouched to avoid needless risk.
