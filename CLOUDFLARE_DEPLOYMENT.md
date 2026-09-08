# antAI — Cloudflare Deployment Guide

This guide details how to run and deploy the antAI backends on Cloudflare:
1. **WebRTC Signaling Backend (`SimpleVideoCallBackend`)** on **Cloudflare Workers** (Global serverless edge WebSockets with Durable Objects).
2. **antAI AI & Detection Backend (`server`, port 8765)** on **Cloudflare Tunnel (`cloudflared`)** (Zero-trust public HTTPS / WSS edge tunnel for FastAPI, LangGraph, and real-time audio/video inference).

---

## 🏗️ Architecture Overview

```
                          ┌────────────────────────┐
                          │    Cloudflare Edge     │
                          └───────────┬────────────┘
                                      │
            ┌─────────────────────────┴─────────────────────────┐
            │                                                   │
  [WebRTC Signaling]                                    [AI Detection Tap]
         WSS                                                   WSS / HTTPS
            │                                                   │
            ▼                                                   ▼
┌───────────────────────┐                           ┌───────────────────────┐
│   Cloudflare Worker   │                           │   Cloudflare Tunnel   │
│ (Durable Object Room) │                           │     (cloudflared)     │
└───────────────────────┘                           └───────────┬───────────┘
            │ (Signaling offer/answer/ICE)                      │ (Proxies traffic)
            │                                                   ▼
┌───────────────────────┐                           ┌───────────────────────┐
│   Web / Android App   │ ════ P2P Media Stream ═══ │ antAI FastAPI Engine  │
│      (Call UI)        │                           │ (LangGraph / Models)  │
└───────────────────────┘                           └───────────────────────┘
```

---

## 1. WebRTC Signaling Server on Cloudflare Workers

The signaling server manages peer discovery and WebRTC handshake negotiation (`offer`, `answer`, `candidate`). It is now implemented as a Cloudflare Worker using **Durable Objects**, allowing peers anywhere in the world to connect to a single edge coordination room.

### Files
- [`SimpleVideoCallBackend/worker.js`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/SimpleVideoCallBackend/worker.js): Cloudflare Worker & Durable Object implementation.
- [`SimpleVideoCallBackend/wrangler.jsonc`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/SimpleVideoCallBackend/wrangler.jsonc): Worker configuration and Durable Object bindings.
- [`SimpleVideoCallBackend/signal_test.js`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/SimpleVideoCallBackend/signal_test.js): Automated 2-party handshake test suite.

### Local Development & Testing
```powershell
cd SimpleVideoCallBackend

# 1. Start the Worker locally on port 8787
npx wrangler dev --port 8787

# 2. Run the smoke test (in a second terminal)
node signal_test.js --port 8787
```

**Expected output:**
```text
Connecting smoke test to: ws://127.0.0.1:8787/
[alice] opened
[bob] opened
[alice] <- welcome
[bob] <- welcome
[alice] <- UserOnline
[bob] <- HelloBob
RELAY OK - message forwarded between peers
```

### Deploying to Cloudflare Workers (Production)
```powershell
cd SimpleVideoCallBackend

# Login to your Cloudflare account (if not already logged in)
npx wrangler login

# Deploy globally to Cloudflare Edge
npx wrangler deploy
```

Once deployed, Cloudflare will output your public Worker URL:
`https://antai-signaling-server.<your-subdomain>.workers.dev`

---

## 2. antAI AI & Detection Backend via Cloudflare Tunnel

The antAI Python server (`server/`, port `8765`) runs heavy deep learning stacks (PyTorch, faster-whisper, speechbrain, mediapipe, Qwen/Groq, and LangGraph). Because native Python C-extensions cannot run inside edge V8 sandboxes, **Cloudflare Tunnel (`cloudflared`)** exposes the running engine to Cloudflare's edge with:
- Dedicated Public HTTPS endpoint (`https://*.trycloudflare.com` or custom domain)
- Dedicated Secure WebSocket endpoint (`wss://*.trycloudflare.com`) for `/ws/tap`, `/ws/chat`, `/api/stream/ws`
- Automatic SSL termination and DDoS protection
- Zero port forwarding or router configuration needed

### Files
- [`tools/cloudflared.exe`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/tools/cloudflared.exe): Standalone Cloudflare Tunnel binary.
- [`server/scripts/start_tunnel.ps1`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/start_tunnel.ps1): Automated launcher and URL extractor.
- [`server/start_tunnel.bat`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/start_tunnel.bat): Double-click Windows launcher.
- [`server/scripts/test_tunnel.py`](file:///C:/Users/sidde/OneDrive/Desktop/FSD/Hackthonss/SIH26P1/server/scripts/test_tunnel.py): Live endpoint verification test.

### How to Run

#### Step 1: Start the antAI Server
```powershell
cd server
python run_dev.py
```
*(Runs on `http://localhost:8765`)*

#### Step 2: Start Cloudflare Tunnel
In a new terminal:
```powershell
cd server
.\start_tunnel.bat
# or: powershell -File scripts/start_tunnel.ps1
```

You will see:
```text
==========================================================
  CLOUDFLARE PUBLIC TUNNEL ACTIVE!
  Public HTTPS:  https://<unique-name>.trycloudflare.com
  Public WSS:    wss://<unique-name>.trycloudflare.com
  Dashboard:     https://<unique-name>.trycloudflare.com/dashboard/
  API Docs:      https://<unique-name>.trycloudflare.com/docs
==========================================================
```

#### Step 3: Verify Connectivity
Run the test script against your active tunnel URL:
```powershell
python server/scripts/test_tunnel.py https://<unique-name>.trycloudflare.com
```

Both HTTP REST (`200 OK`) and WebSocket WSS streaming will test with `PASS [OK]`.

---

## 3. Client Configuration (Connecting Devices)

### Android App (`app/`)
The Android app now automatically detects Cloudflare URLs and uses HTTPS/WSS without appending local ports!

1. Open the **antAI** app on your phone.
2. In the **Server address** field on the home card, enter your Cloudflare Tunnel URL:
   ```text
   https://<unique-name>.trycloudflare.com
   ```
3. Tap **Connect**.
4. The app now routes signaling, live media tap, and chat pushes over the secure Cloudflare tunnel globally — no USB tethering or shared Wi-Fi required!

### React Web Client (`SimpleVideoCallReacJs/`)
To test signaling against your Cloudflare Worker:
```powershell
cd SimpleVideoCallReacJs

# Set environment variable (or create .env.local):
# VITE_SIGNALING_URL=wss://antai-signaling-server.<your-subdomain>.workers.dev

npm run dev
```

### Chrome Extension (`extension/`)
1. Open Chrome Extension Options.
2. Under **Server Host**, enter:
   `<unique-name>.trycloudflare.com`
3. Click Save.

---

## 4. Production Custom Domain (Optional)
If you own a domain managed on Cloudflare (e.g., `example.com`):
```powershell
# Authenticate cloudflared
.\tools\cloudflared.exe tunnel login

# Create a permanent named tunnel
.\tools\cloudflared.exe tunnel create antai-backend

# Route traffic to your domain
.\tools\cloudflared.exe tunnel route dns antai-backend api.example.com

# Run the tunnel
.\tools\cloudflared.exe tunnel run --url http://localhost:8765 antai-backend
```
This gives you a permanent URL (`https://api.example.com`) that never changes between reboots.
