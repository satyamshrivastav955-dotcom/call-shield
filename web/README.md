# antAI Guardian — Web Platform (SIH PS26104)

Production-grade Next.js 14+ web application for **antAI Guardian**, featuring real-time deepfake voice and scam interception, a live security console, and an interactive pipeline DAG explainer.

---

## 1. Features & Architecture

- **Section 1 — Marketing & Product Overview (`/`, `/features`, `/how-it-works`, `/demo`)**:
  - Live animated Risk Pulse gauge smoothly cycling through Safe (< 40), Verify (40–70), and Critical (> 70) states with urgent freeze banner.
  - In-depth specifications: sub-2s latency, dual-model AI-voice detection (AST + wav2vec2), ECAPA speaker biometrics, and zero-knowledge privacy.
  - 5-step pipeline architecture breakdown.
  - Interactive demo sandbox to test pre-loaded attack vectors or upload audio files for scoring.
- **Section 2 — Real-Time Dashboard (`/dashboard-live`)**:
  - Live WebSocket stream connected to `ws://localhost:8765/api/stream/ws`.
  - Half-circle SVG risk arc gauge with scenario threshold needles and band indicators.
  - Per-signal visual breakdown bars: Acoustic synthetic probability, speaker match, scam NLP, urgency, behavioral drift, and video lip-sync.
  - Model health strip from `GET /api/debug/models` showing engine readiness and the active LLM fallback chain (`groq` → `gemini` → `openrouter` → `local`).
  - Active Freeze Intercept banner with **Approve** and **Reject & Block** buttons sending `POST /api/freeze/decide`.
  - Monospace scrolling live transcript ticker with timestamps.
  - Graceful Demo Mode: Automatically operates with realistic synthetic telemetry when the backend is offline.
- **Section 3 — Dataflow Explainer (`/dataflow`)**:
  - Interactive SVG flowchart of the 10-node DAG.
  - Clickable node inspector detailing function, latency budget, active model architectures, and failure behavior.
  - "Trace a Scam Call" animation button with glowing packet transit and climbing risk score (12 → 87 → Freeze).
- **Single Source of Truth (`src/content/content.ts`)**:
  - All marketing copy, technical benchmarks, and scenario definitions are centralized in one file for easy editing.

---

## 2. Getting Started

### Prerequisites
- Node.js 18.17+ (tested on Node v20/v22+)
- npm 9+

### Installation
```bash
# Navigate to the web directory
cd web

# Install dependencies
npm install
```

---

## 3. Environment Configuration

Copy `.env.local.example` to `.env.local`:
```bash
cp .env.local.example .env.local
```

Default configuration:
```ini
# FastAPI REST endpoint
NEXT_PUBLIC_API_URL=http://localhost:8765

# FastAPI WebSocket stream endpoint
NEXT_PUBLIC_WS_URL=ws://localhost:8765
```

If your FastAPI server runs on a different port or remote host, update `NEXT_PUBLIC_API_URL` and `NEXT_PUBLIC_WS_URL` accordingly.

---

## 4. Running the Development Server

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser.

- Landing page: [http://localhost:3000/](http://localhost:3000/)
- Features: [http://localhost:3000/features](http://localhost:3000/features)
- How It Works: [http://localhost:3000/how-it-works](http://localhost:3000/how-it-works)
- Audio Demo: [http://localhost:3000/demo](http://localhost:3000/demo)
- Live Console: [http://localhost:3000/dashboard-live](http://localhost:3000/dashboard-live)
- Dataflow Explainer: [http://localhost:3000/dataflow](http://localhost:3000/dataflow)

---

## 5. Running Against the Real FastAPI Backend

To stream live audio and view genuine model scores:

### Step 1: Start the FastAPI Server
In a separate terminal, navigate to the `server/` directory:
```bash
cd ../server
# Activate your Python virtual environment if applicable
python -m uvicorn antai.gateway.main:create_app --host 0.0.0.0 --port 8765
```

Verify backend health:
```bash
curl http://localhost:8765/api/debug/models
```

### Step 2: Open the Live Console
Navigate to [http://localhost:3000/dashboard-live](http://localhost:3000/dashboard-live). The connection pill in the header will show a green **API Connected** status.

### Step 3: Stream an Audio File via WebSocket
Use the bundled Python streamer to stream sample audio at wall-clock speed:
```bash
cd ../server
python scripts/stream_demo_file.py audio_samples/test_call.wav --url ws://localhost:8765/api/stream/ws
```
Watch the live gauge, signal bars, and transcript ticker update in real time on the dashboard!

---

## 6. Production Build & Linting

Verify TypeScript types and build production bundles:
```bash
# Linting
npm run lint

# Production build
npm run build

# Start production server
npm start
```
