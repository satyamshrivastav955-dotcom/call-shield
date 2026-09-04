# antAI Windows Protection Client

A lightweight desktop companion for real-time scam and deepfake protection during VoIP, conference calls (Zoom, Teams, Google Meet), and phone-link sessions on Windows.

---

## 🎯 What it Does

1. **Audio Capture**: Continuously reads mono 16 kHz PCM audio from your default microphone or virtual audio cable using `sounddevice`.
2. **WebSocket Streaming**: Streams binary float32 chunks to the antAI server's `/api/stream/ws` endpoint in real-time (identical protocol to the Android app).
3. **Always-On-Top Risk Overlay**: A sleek, dark-themed Tkinter HUD displaying:
   - Live **Risk Gauge** (0–100 animated arc).
   - **Signal Family Breakdown**: Voice Authenticity (deepfake probability), Message Patterns (scam classification), and Speaker Identity (voiceprint verification).
   - **Status Badge**: Monitoring (Green), Review (Yellow), or High Risk (Red).
   - **Action Recommendation**: Clear guidance on what to do next.
4. **Desktop Toast Notifications**: Emits Windows notifications when a call crosses verification or critical thresholds, keeping you protected even when the overlay is minimized or behind fullscreen apps.

---

## 🚀 Quick Start

### 1. Install Dependencies

Ensure Python 3.9+ is installed, then install the required packages:

```bash
cd windows_client
pip install -r requirements.txt
```

> **Note**: If you want native Windows toast notifications, ensure `plyer` is installed. If `plyer` is absent, the client falls back to logging notifications to the terminal.

### 2. Run the Client

Connect to your local antAI server:

```bash
python main.py --server ws://localhost:8765 --scenario high_value_txn
```

Connect to a remote server on your local network:

```bash
python main.py --server ws://192.168.1.100:8765 --scenario routine_call
```

Run in **Demo Mode** (simulates audio with silence for testing UI and connection):

```bash
python main.py --server ws://localhost:8765 --demo
```

Run in **Headless / CLI Mode** (no Tkinter GUI overlay):

```bash
python main.py --server ws://localhost:8765 --no-overlay
```

---

## ⚙️ Command-Line Options

| Argument | Type | Default | Description |
|---|---|---|---|
| `--server` | string | `ws://localhost:8765` | antAI streaming WebSocket URL. |
| `--scenario` | choice | `high_value_txn` | Scenario profile: `routine_call`, `high_value_txn`, `privileged_access`. |
| `--rate` | int | `16000` | Audio sample rate in Hz (16 kHz expected by models). |
| `--chunk` | float | `2.0` | PCM chunk duration in seconds sent to server. |
| `--verify-at` | float | `50.0` | Risk score threshold triggering elevated alert. |
| `--critical-at` | float | `70.0` | Risk score threshold triggering high-risk alert. |
| `--no-overlay` | flag | `False` | Run in background without Tkinter overlay window. |
| `--demo` | flag | `False` | Stream silence without opening microphone hardware. |

---

## 🛡️ Architecture & Data Flow

```
[ Default Microphone / Stereo Mix ]
              │
      (sounddevice float32)
              ▼
    [ audio_capture.py ]
              │
    (16kHz mono chunks)
              ▼
   [ streaming_client.py ] ──── WebSocket ────► antAI Server (/api/stream/ws)
              ▲                                           │
              │                                           ▼
              └─────── normalized_result JSON ◄───────────┘
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
     [ ui_overlay.py ]          [ notifications.py ]
  (Tkinter Live HUD)         (Windows Toast Alert)
```

---

## 🔒 Privacy & Local Processing

- Audio chunks are processed in-memory and buffered in a ring buffer.
- When connected to a local antAI server instance (`localhost`), zero audio leaves your computer.
- When connected to an on-premises or enterprise antAI gateway, audio is transferred over encrypted WebSockets and handled according to the server's privacy and retention policies.
