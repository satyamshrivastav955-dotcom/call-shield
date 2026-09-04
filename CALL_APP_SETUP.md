# antAI Call — Setup & Run Book

A working peer-to-peer WebRTC voice/video calling app, based on CodeWithKael's
SimpleVideoCall stack, wired to run against **your own local backend**. This replaces
the old (broken) antAI client. Get normal calls working first; routing calls through
the antAI detection models is the next phase (see the last section).

## What's in the project now

| Folder | What it is |
| --- | --- |
| `app/` | **The new Android calling app** (Kotlin, Jetpack Compose, WebRTC). This is what you build and install. |
| `SimpleVideoCallBackend/` | **Node.js signaling server** (WebSocket, port 3007). Relays call setup between phones. |
| `SimpleVideoCallReacJs/` | Optional **web client** (React) — handy for testing a call without a second phone. |
| `app_antai_client_backup/` | Your **old** antAI Android client, kept as a backup. Not used anymore. |
| `server/` | Your antAI Python server + ML models. Untouched — used later for detection. |

## How it works (important)

1. Each phone opens the app and gets a **random 5-character ID** (it changes every time
   the app restarts).
2. Both phones connect to the **Node signaling server** over Wi-Fi.
3. Caller enters the other phone's ID and taps **Call**. The server just passes the
   offer/answer/ICE messages between them.
4. Once connected, **audio and video flow directly phone-to-phone (P2P)** — the media
   does **not** pass through the server.

> Because media is peer-to-peer, both phones must be on the **same Wi-Fi network** as
> the laptop for the default setup.

---

## Prerequisites

- **Node.js** installed on the laptop (`node --version` should print something).
- **Android Studio** (with an Android SDK) on the laptop.
- **Two Android phones** with USB debugging enabled, on the **same Wi-Fi** as the laptop.
- The laptop and both phones on the **same Wi-Fi** (not a guest network; guest/"AP
  isolation" networks block phone-to-phone media).

---

## Step 1 — Start the signaling backend

```bat
cd C:\Users\satya\OneDrive\Desktop\antAI\SimpleVideoCallBackend
npm install
node server.js
```

You should see:

```
FCM credentials not found, offline-call notifications disabled
Server running on http://localhost:8080
```

- The **`8080`** in that line is a harmless leftover log string. The server actually
  listens on **port 3007** — that's correct, leave it.
- "FCM disabled" is expected and fine — Firebase is optional and not needed for calls.
- Leave this window open while testing. (`npm run` also works and auto-restarts on edits.)

## Step 2 — Find your laptop's Wi-Fi IP

Open a new terminal and run:

```bat
ipconfig
```

Look under your **Wi-Fi adapter** for **IPv4 Address**, e.g. `192.168.1.23`.
This is the address both phones will connect to. (It usually stays the same on your home
Wi-Fi, but can change if you switch networks — that's why the app lets you edit it without
rebuilding.)

### No Wi-Fi router? Using a hotspot

The rule is unchanged: the laptop (running the Node server) and **both** phones must be on one
network that lets devices talk to each other. Two ways, best first:

**Best — make the _laptop_ the hotspot.** Settings → Network & Internet → **Mobile hotspot** →
On, then connect both phones to the laptop's hotspot. Because the laptop is the host, both
phones can always reach it, and Windows doesn't isolate connected devices (so phone-to-phone
media works too). With Windows Mobile Hotspot the laptop's address is **192.168.137.1** — enter
that in the app on both phones (confirm with `ipconfig`). Windows may show "no internet" —
that's fine, a local call doesn't need it.

**Alternative — one phone hosts the hotspot.** That host phone can still be one of the two
callers; the laptop and the other phone join its hotspot. This _can_ work, but many phone
hotspots block device-to-device traffic. Test it: from the **other** phone's browser, open
`http://<laptop-IP>:3007` — if it shows "WebSocket Server is running", you're fine; if it times
out, the hotspot is isolating clients, so switch to the laptop-hotspot option above.

Whichever you pick, the laptop's IP changes with the network — always re-run `ipconfig` and
enter the **current** laptop IP in the app.

## Step 3 — Allow port 3007 through Windows Firewall

Phones can't reach the backend if the firewall blocks it. In an **Administrator** terminal:

```bat
netsh advfirewall firewall add rule name="antai-signaling-3007" dir=in action=allow protocol=TCP localport=3007
```

(One-time. To remove later: `netsh advfirewall firewall delete rule name="antai-signaling-3007"`.)

## Step 4 — Build & install the app on both phones

Open the **`app`** folder in Android Studio:
`C:\Users\satya\OneDrive\Desktop\antAI\app`

**Important — set the Gradle JDK to Java 17.** This project's Gradle 8.9 does not run on
newer JDKs (Java 24/25). Use the JDK bundled with Android Studio:
**File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK →**
pick the entry labelled **jbr-17** (or **"Embedded JDK"** / **"Android Studio default JDK"**).
Building from Android Studio uses this automatically and ignores whatever `java` is on your
system PATH.

Let Gradle sync, then Run ▶ onto each phone. Or from the command line (only if you've pinned
a Java 17/21 JDK via `org.gradle.java.home` — see Troubleshooting):

```bat
cd C:\Users\satya\OneDrive\Desktop\antAI\app
gradlew.bat installDebug
```

Repeat for the second phone (select the other device, or plug it in and run again).
The SDK path is already set in `app\local.properties`; if your SDK lives elsewhere, edit
that file.

## Step 5 — Connect each phone to the server

1. Open **antAI Call** on a phone and grant camera + microphone permission.
2. On the **Connect to server** card, type your laptop's IP from Step 2
   (e.g. `192.168.1.23`) and tap **Connect**. It's saved on the device, so next time it's
   already filled in.
3. The screen now shows that phone's **5-character ID**.
4. Do the same on the second phone.

## Step 6 — Make a call

1. On phone A, type phone B's 5-character ID into **Who to Call?** and tap **Call**.
2. Phone B shows an incoming-call banner → tap **Accept**.
3. You should see video both ways and hear audio. Buttons let you mute, turn the camera
   off, switch camera, toggle speaker, and end the call.

---

## Optional — test with the web client (no second phone needed)

The React client runs in a browser **on the laptop** and calls a phone.

```bat
cd C:\Users\satya\OneDrive\Desktop\antAI\SimpleVideoCallReacJs
npm install
npm run dev
```

Open the URL Vite prints (usually `http://localhost:5173`). The web client connects to
`ws://localhost:3007` automatically (same laptop as the backend — no config needed). It
shows its own ID; call between the browser and a phone (the phone still uses the laptop's
LAN IP from Step 5).

---

## Troubleshooting

- **"Can't reach server at …:3007" toast** → the backend isn't running, the IP is wrong,
  or the firewall is blocking 3007. Recheck Steps 1–3. Confirm from a phone browser that
  `http://<laptop-IP>:3007` shows "WebSocket Server is running".
- **Both phones must be on the same Wi-Fi.** Mobile data or different networks won't work
  without a TURN server.
- **Call connects but no video/audio** → your Wi-Fi may have "AP/client isolation" on
  (common on guest networks), which blocks phone-to-phone traffic. Use a normal/home Wi-Fi
  or a phone hotspot shared by both devices + laptop.
- **"User is Offline" when calling** → the other phone hasn't tapped **Connect**, or its ID
  changed after an app restart. Re-check the ID on the callee's screen.
- **Camera is black** → make sure camera permission was granted; some emulators have no
  camera (use a real phone for video).
- **Build fails instantly with a bare version like `25.0.2`** (plus "restricted method" /
  "native-access" warnings) → your system Java is too new for Gradle 8.9. Build from Android
  Studio with its bundled **jbr-17**, or pin `org.gradle.java.home` to a Java 17/21 install in
  `app\gradle.properties`. Do **not** bump the Gradle/AGP versions.
- **Want to detach from the tutorial's git history?** The `app/` folder still contains a
  `.git` from the original clone. It doesn't affect building; you can delete `app\.git` if
  you want a clean start.

---

## Next phase — real-time antAI scam detection on calls (DONE)

Calls are pure peer-to-peer, so media never reaches the Python server. To run the antAI
detection pipeline live on an ongoing call, the app uses a **parallel analysis tap** —
the call itself stays P2P and is never touched:

- Keep the existing P2P call + Node signaling exactly as-is.
- While a call is running, each phone opens a **second, send-only PeerConnection** to the
  antAI Python server (`/ws/tap` on port 8765, aiortc) and attaches a copy of its local
  mic/camera tracks (same tracks → mute/camera toggles apply to both legs).
- The server correlates the two taps into one session (deterministic key from the two app
  usernames), runs the full pipeline in real time — Silero VAD → faster-whisper ASR (live
  transcript) → voice/video deepfake ensembles → LangGraph fusion → local Qwen LLM — and
  pushes `transcript.update` / `verdict.update` / `guidance.update` / `report.ready` back
  to **both** phones.
- Each phone renders a small collapsible **antAI window** at the top of the call screen:
  risk band (MONITORING / CAUTION / HIGH RISK), the LLM's "what's wrong" reasoning, "what
  to do now" advice, and the live transcript. Nothing is hardcoded — every value comes off
  the wire from the models/LLM.

### Running the AI tap

1. Start the antAI server (same laptop as the Node server):
   ```bat
   cd C:\Users\satya\OneDrive\Desktop\antAI\server
   conda run -n antai-server python run_dev.py
   ```
   Wait for the models to warm up (watch the log; LLM GGUF load takes tens of seconds).
2. Allow the port through Windows Firewall (one-time):
   ```bat
   netsh advfirewall firewall add rule name="antai-server-8765" dir=in action=allow protocol=TCP localport=8765
   ```
3. Make a normal call (Node server still handles signaling). On connection, both phones
   auto-open the analysis tap and the antAI window appears.
4. **Server-down is safe:** if the antAI server is unreachable, the call continues normally
   and the app shows a single "antAI offline" toast — the tap never affects the call.

The tap's video uplink is capped at ~200 kbps (the server samples at 6 fps), so it adds
negligible load. The full SFU-relay alternative (antAI server *in the middle* of the call,
also seeing remote media directly) remains a future option if cross-network protection
becomes the goal.
