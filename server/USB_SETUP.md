# antAI — Running everything over USB

This guide gets **messaging, voice calls, and video calls** working with two
Android phones connected to your computer by **USB only** (no Wi-Fi required for
the app to function).

The short version:

1. Start the server on your computer.
2. Run `usb_setup.bat` (Windows) or `usb_setup.sh` (Mac/Linux) — this tunnels
   each phone to the server over its USB cable.
3. Open the app on each phone. Messaging, contacts, and calls now work.
4. (Only for live call/video **media** with *no Wi-Fi at all*) start the local
   TURN relay — see step 5.

---

## Why USB works this way

The app is already built to talk to `http://127.0.0.1:18765`. The command
`adb reverse tcp:18765 tcp:8765` makes the phone's own `localhost:18765` point,
through the USB cable, at the server running on your computer's `localhost:8765`.

`adb reverse` tunnels **TCP**, which covers everything the app does over HTTP and
WebSocket:

- account setup / login
- contacts
- **messaging**
- **call & video signaling** (who's calling, offers/answers, ICE)

The one thing that is **not** TCP is the actual audio/video **media** of a live
call — that's UDP, and `adb reverse` cannot tunnel UDP. That only matters once a
call connects, and step 5 handles it. Messaging and call setup do **not** need it.

---

## Prerequisites (once)

- **Android platform-tools (`adb`)** installed on the computer.
  Download: https://developer.android.com/tools/releases/platform-tools
  Either add it to your PATH, or (Windows) let it sit at
  `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` — `usb_setup.bat` looks there.
- On **each phone**: enable **Settings → Developer options → USB debugging**.
- Use a **data-capable** USB cable (some cables are charge-only).
- The first time you plug in, tap **Allow** on the phone's "Allow USB debugging?"
  prompt.

---

## Step 1 — Start the server

From the `server/` folder:

```
python run_dev.py
```

You should see `antAI server -> http://0.0.0.0:8765`. Leave it running.

## Step 2 — Tunnel each phone over USB

Plug in both phones, then from the `server/` folder run:

- **Windows:** `usb_setup.bat`
- **Mac / Linux / WSL / Git-Bash:** `bash usb_setup.sh`

You should see one `[device N] …  OK` block per phone. If it says
**"No authorized devices found,"** unlock the phone and accept the USB-debugging
prompt, then run it again.

> Re-run this script every time you **replug a phone** or **reboot adb** — the
> reverse tunnels are dropped on disconnect.

## Step 3 — Open the app

Launch antAI on each phone. On the onboarding screen it should show
`server: http://127.0.0.1:18765`. Enter a phone number + name and continue.
If setup succeeds, messaging and contacts are working over USB.

> If it says "Cannot reach server," step 2 didn't take — confirm `python run_dev.py`
> is still running and re-run `usb_setup.bat` (check that the phone shows as
> `device`, not `unauthorized`, in `adb devices`).

## Step 4 — Make a call

Call the other phone. **Signaling** (ringing, accept) works purely over USB. The
call will **connect**. Whether you also get live audio/video depends on the media
path — see step 5.

---

## Step 5 — Live call/video media

Media is UDP and can't ride the `adb reverse` (TCP) tunnel, so you have two
choices:

### Option A — Same Wi-Fi (easiest, nothing to install)

If both phones are on the **same Wi-Fi network** as the computer (they can still
be plugged in for power and USB signaling), the media flows over the LAN using the
phones' built-in STUN/host candidates. **No TURN server needed** — just make sure
Wi-Fi is on. This is the simplest way to get audio/video.

### Option B — Pure USB, no Wi-Fi at all (local TURN relay)

To carry media over the USB cable with no network whatsoever, run the bundled
**coturn** TURN relay, which relays media over **TCP** (which USB *can* tunnel).
`usb_setup.bat` already forwards the TURN port (`adb reverse tcp:3478 tcp:3478`),
and the client + server are already pointed at `turn:127.0.0.1:3478?transport=tcp`.

Start coturn from the `server/` folder using the bundled config:

- **Linux / Mac / WSL:**
  ```
  turnserver -c turnserver.conf
  ```
  (Install coturn first: `sudo apt install coturn`, or `brew install coturn`.)

- **Windows:** coturn doesn't ship a native Windows build, so run it under one of:
  - **WSL2** (recommended): open your WSL distro, `sudo apt install coturn`, `cd`
    to the `server/` folder (it's under `/mnt/c/...`), then `turnserver -c turnserver.conf`.
    WSL2's localhost is shared with Windows, so `127.0.0.1:3478` lines up.
  - **Docker:**
    ```
    docker run --rm -p 3478:3478 -p 3478:3478/udp -p 50000-50100:50000-50100/udp ^
      -v "%CD%\turnserver.conf:/etc/coturn/turnserver.conf" coturn/coturn
    ```

Leave coturn running alongside the server. Now place a call — the media relays
through coturn over the USB tunnel.

> coturn is **optional and non-breaking**: if it isn't running, the TURN
> allocation simply fails and is ignored, so Option A (same-Wi-Fi) calls are
> unaffected. The credentials in `turnserver.conf`, `CallEngine.kt`, and
> `config.yaml` (`ice:` section) must all match (`antai` / `antaipass`).

---

## Quick troubleshooting

| Symptom | Fix |
| --- | --- |
| App: "Cannot reach server" | Server not running, or step 2 not done / needs re-run after replug. Check `adb devices` shows `device`. |
| `usb_setup`: "No authorized devices" | Unlock phone, tap **Allow** on USB-debugging prompt, re-run. Use a data cable. |
| Phone shows `unauthorized` in `adb devices` | Accept the prompt on the phone; if none appears, toggle USB debugging off/on and replug. |
| Messaging works, call connects, but no audio/video | That's the media path — do **step 5** (Wi-Fi Option A, or coturn Option B). |
| Call/video still silent with coturn running | Confirm `adb reverse --list` shows `tcp:3478`, coturn is up, and credentials match across the three files. |

## What each piece does

| File | Role |
| --- | --- |
| `usb_setup.bat` / `usb_setup.sh` | Sets up the `adb reverse` USB tunnels (port 18765 for the app, 3478 for media). Run after each replug. |
| `turnserver.conf` | coturn config for the local TURN relay used only for pure-USB media (step 5B). |
| `config.yaml` → `ice:` | Server-side TURN settings for the SFU's peer connections. |
| `CallEngine.kt` → `TURN_*` | Client-side TURN settings for the phone's peer connection. |
