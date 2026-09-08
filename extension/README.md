# antAI Guardian — Chrome Extension (MV3)

Live deepfake / scam risk analysis for Meet, Zoom and Teams calls. Ports the proven
`windows_client/` protocol to the browser: captures the tab's audio, streams 16 kHz mono
f32le PCM to your antAI server over `ws://<host>/api/stream/ws`, and renders the server's
`normalized_result` in a floating HUD, the popup, and desktop notifications.

Every number comes from the server. Unavailable models render "unavailable" — never `0` or
a fake green.

## Install (dev)

1. `chrome://extensions` → enable **Developer mode** → **Load unpacked** → select `extension/`.
2. Open **Options** and set your antAI server host (default `localhost:8765`).
3. Join a meeting (Meet / Zoom / Teams web) → click the antAI icon → **Protect this tab**.
4. A risk pill appears over the page. Click it to expand the full card (gauge, three signal
   rows, recommendation). Drag it anywhere — position is remembered.

## Architecture

```
popup (start/stop, gauge, models-ready indicator, text-scan toggles)
  └─ background.js (SW): lifecycle, state, notifications (15 s cooldown port), HUD + scanner injection
       └─ offscreen document: tabCapture stream → AudioWorklet (16k mono f32le) → WebSocket
            └─ server /api/stream/ws → {"type":"update", ...normalized_result}
       └─ content/hud.js|css: floating pill / expandable card on the protected tab
       └─ content/textscan_core.js + textscan.js: continuous page-text watcher →
            antai-scan-text → POST /api/notify/external → real agentic verdict
options: server host, scenario, verify/critical thresholds, notification cooldown
```

Service workers have no `AudioContext`, so audio capture lives in an offscreen document
(reason: `USER_MEDIA`). The WS reconnects with exponential backoff (1 s → 15 s) until stopped.
Capture stops automatically when the tab closes.

## Ports from windows_client (do not reinvent)

- `ui_overlay.py:36` `_band_color(risk, verify_at=50, critical_at=70)` → `shared/risk.js`
  `antaiBandColor` — same thresholds, same colors (`#dc2626` / `#ca8a04` / `#16a34a`).
- `notifications.py:35` 15 s notification cooldown + verify/critical title variants →
  `background.js` `maybeNotify`.

## Scenario

`routine_call` | `high_value_txn` | `privileged_access` — forwarded in the WS `start` frame,
same semantics as the Android app's Settings screen. Threshold overrides (verify/critical)
and cooldown live in Options.

## Auth & page-text scan

- **Sign in** (Options → Account): phone-OTP login against `POST /api/auth/otp` +
  `POST /api/auth/verify` — the same flow as the Android app. The token is stored in
  `chrome.storage.local` (device-local, never synced). In dev mode the server returns the
  OTP in the response.
- **Scan text on this page** (popup): grabs your text selection (or the page text) and
  posts it to `POST /api/notify/external` with the bearer token. The server's agentic
  graph returns the verdict — a benign message comes back unflagged, and that's rendered
  honestly ("no scam-related language detected" / "no actionable risk found").
- **Watch page text (live)** (popup toggle): injects `content/textscan.js` into the
  current tab (opt-in, per-tab, activeTab gesture) to *continuously* scan NEW text —
  chat messages, live captions, incoming mail — as it appears. Each new snippet is
  deduped and rate-limited (`content/textscan_core.js`) and sent through the same
  `antai-scan-text` → `POST /api/notify/external` route; a scam verdict raises a desktop
  notification on the shared 15 s cooldown. It scores nothing locally, skips fields you
  are typing in and the antAI HUD, and logs one `[antAI text-scan] risk=… band=…` line
  per snippet to the page console for verification. A keyword-gated benign line is logged
  as "not scam-related (gated)" — an honest no-signal, never a fabricated safe verdict.
- **wss:// + token auth**: enter a `wss://host` in Options to stream through a TLS
  reverse proxy. The token rides along as `?token=` on the stream WS; the server always
  validates a provided token, and rejects unauthenticated stream clients when
  `auth.require_stream_token: true` is set in the server config (default off for the LAN
  demo clients).

## Still not built

Per-participant diarization labels — the streaming session is single-speaker by design
(one external caller per stream); separating meeting participants needs a diarization
model + orchestration changes on the server side (detection crew's territory).

## Security note

The server's stream endpoint is unauthenticated by design for the local demo. Run it on a
trusted LAN only — the extension streams meeting audio to whatever host you configure.
