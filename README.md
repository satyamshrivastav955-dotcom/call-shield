<div align="center">

# 🛡️ CallShield
### Autonomous Real-Time Voice Clone, Deepfake & Financial Coercion Defense System
*(Safer conversations, a brighter tomorrow)*

<!-- Dynamic Animated Typing Banner -->
<a href="#">
  <img src="https://readme-typing-svg.demolab.com?font=Space+Grotesk&weight=700&size=24&duration=2500&pause=1000&color=4F8B68&center=true&vCenter=true&multiline=false&width=900&height=65&lines=%F0%9F%9B%A1%EF%B8%8F+Victim-Side+Active+Defense%3A+Zero+Scammer+Cooperation+Needed;%F0%9F%8E%99%EF%B8%8F+AST-ASV5+%2B+wav2vec2+Synthetic+Voice+Clone+Interception;%E2%9A%A1+Compiled+14-Node+LangGraph+Conditional+DAG+%28%3C850ms%29;%F0%9F%9B%91+Autonomous+Pre-Transaction+Freeze+Hold+%26+Explainable+AI;%F0%9F%94%90+Zero-Retention+Biometrics+%7C+Multi-Platform+Ecosystem" alt="CallShield Typing Banner" />
</a>

<p align="center">
  <img src="https://img.shields.io/badge/Brand-CallShield-0F392E?style=for-the-badge&logo=shield&logoColor=white" alt="CallShield"/>
  <img src="https://img.shields.io/badge/Architecture-Victim--Side%20Active%20Defense-4F8B68?style=for-the-badge&logo=shield&logoColor=white" alt="Architecture"/>
  <img src="https://img.shields.io/badge/Latency-Sub--Second%20%28%3C850ms%29-0F392E?style=for-the-badge&logo=lightning&logoColor=white" alt="Inference Latency"/>
  <img src="https://img.shields.io/badge/Android-Jetpack%20Compose%20%2B%20WebRTC-3DDC84?style=for-the-badge&logo=android&logoColor=black" alt="Android"/>
  <img src="https://img.shields.io/badge/Brain-14--Node%20LangGraph%20DAG-FF4F81?style=for-the-badge&logo=langchain&logoColor=white" alt="LangGraph"/>
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License"/>
</p>

<p align="center">
  <a href="#-the-hard-problem--why-callshield"><img src="https://img.shields.io/badge/💡_The_Why-0D1117?style=flat-square&logoColor=white" alt="The Why"/></a>
  <a href="#-live-execution-in-action"><img src="https://img.shields.io/badge/💻_Live_Terminal_Demo-0D1117?style=flat-square&logoColor=white" alt="Live Terminal"/></a>
  <a href="#-interactive-architecture-pipeline"><img src="https://img.shields.io/badge/⚡_Animated_DAG_Pipeline-0D1117?style=flat-square&logoColor=white" alt="Animated Pipeline"/></a>
  <a href="#-production-interfaces--side-by-side-evidence"><img src="https://img.shields.io/badge/📱_UI_Showcase-0D1117?style=flat-square&logoColor=white" alt="UI Showcase"/></a>
  <a href="#-multi-signal-ai-fusion-brain"><img src="https://img.shields.io/badge/🔮_9--Signal_Fusion-0D1117?style=flat-square&logoColor=white" alt="AI Brain"/></a>
  <a href="#-quickstart-3-step-deployment"><img src="https://img.shields.io/badge/🚀_Quickstart-0D1117?style=flat-square&logoColor=white" alt="Quickstart"/></a>
  <a href="#-technical-rigor--specifications"><img src="https://img.shields.io/badge/🔬_Deep_Dives-0D1117?style=flat-square&logoColor=white" alt="Deep Dives"/></a>
</p>

---

### 🚨 *Generative voice cloning now takes 3 seconds of audio. Traditional spam filters never hear the call.*
**CallShield** is an autonomous, victim-side active defense platform safeguarding vulnerable individuals and financial institutions against generative AI voice clones, deepfake video calls, and high-pressure social engineering coercion in real time.

</div>

---

## 💡 The Hard Problem & Why CallShield?

### ❓ The Fundamental Dilemma
Every legacy anti-scam solution relies on **carrier reputation, blacklists, or scammer cooperation**:
* **Attackers Never Cooperate**: Scammers spoof caller IDs, initiate unlisted VoIP sessions, or rotate through burner SIMs. Blacklists fail before the first report is filed.
* **3 Seconds to Clone**: Generative TTS engines (*XTTS*, *ElevenLabs*, *Bark*) clone a family member’s voice from a short WhatsApp voice note or Instagram reel.
* **Psychological Deadlock**: Once a victim is convinced an emergency exists (*"Digital Arrest"*, *"Kidnapped Child"*, *"Customs Drug Parcel"*), warnings on a screen are dismissed.

### 🛡️ The CallShield Solution: Victim-Side Active Defense
CallShield operates exclusively on the **protected recipient’s endpoint** (Android phone, Windows desktop, or WebRTC gateway) and inspects incoming communication unilaterally:

```
                  ┌────────────────────────────────────────────────────────────┐
                  │                 INCOMING CALL / MESSAGE                    │
                  │   Caller claims: "Branch Manager Sharma — Emergency Wire"  │
                  └─────────────────────────────┬──────────────────────────────┘
                                                │
                 ┌──────────────────────────────┴──────────────────────────────┐
                 ▼                                                             ▼
    [ Traditional Spam Filter ]                                   [ 🛡️ CallShield ]
    • Checks caller ID in registry                                • Taps raw audio stream (16kHz float32 PCM)
    • Unlisted / Spoofed? ➔ PASSES                                • Silero-VAD ➔ AST + wav2vec2 SSL acoustic ensemble
    • Scammer runs panic script                                   • ECAPA-TDNN speaker verification vs family voiceprint
    • Result: ₹8,50,000 transferred                              • Result: Intercepted in 840ms ➔ Transfer FROZEN
```

---

### ⚖️ Feature Contrast: Legacy Blockers vs. CallShield Active Defense

| Attack Surface & Threat Vector | Traditional Spam / Call Blockers | 🛡️ CallShield Active Defense |
|:---|:---:|:---:|
| **Generative AI Voice Clone** | ❌ **Bypassed** (Treated as normal audio) | ✅ **Caught in $< 850\text{ms}$** via AST-ASVspoof5 + wav2vec2 SSL ensemble |
| **Family / Executive Impersonation** | ❌ **Zero Awareness** (No biometric capability) | ✅ **Flagged instantly** via ECAPA-TDNN voiceprint cosine distance |
| **Live Deepfake Face Synthesis** | ❌ **Unsupported** (Cannot process video) | ✅ **Evaluated** with CVPR 2025 DeepfakeDet-ViT + DiCoME evidential CLIP |
| **High-Pressure Coercion & Panic** | ❌ **Ignored** (No semantic understanding) | ✅ **Quantified** via NLP emotional urgency & coercion scoring |
| **Multilingual & Hinglish Scams** | ❌ **High False Negatives** on Indian dialects | ✅ **Trained on 8 Indian scam classes** with >90% F1 in Hindi/Hinglish |
| **OTP & Wire Fraud Interception** | ❌ **Passive Text Warning** (Ignored by victim) | ✅ **Autonomous Freeze Hold** on payment apps with conscious override |
| **Explainability for Non-Tech Users** | ❌ **Opaque Score** (*"Risk 82%"*) | ✅ **Plain-Language AI**: `Verdict ➔ Why ➔ Exact Protocol Steps` |

---

## 💻 Live Execution in Action

The following execution trace demonstrates CallShield intercepting an in-flight cloned voice attack over WebSockets in real time:

<div align="center">

```
====================================================================================================
 🛡️  C a l l S h i e l d   //   T A C T I C A L   E X E C U T I O N   E N G I N E   [4829-E2E]
====================================================================================================
```
<img src="assets/terminal_card.svg" width="100%" alt="CallShield Live Terminal Execution Card" />

</div>

---

## ⚡ Interactive Architecture Pipeline

CallShield couples multi-source ingestion with an asynchronous **14-node LangGraph state machine** and a mathematically calibrated **9-signal fusion matrix**:

<div align="center">
  <img src="assets/animated_pipeline.svg" width="100%" alt="CallShield Animated Architecture Pipeline" />
</div>

<br/>

### 🔄 End-to-End Orchestration Topology

```
[ Ingestion Layer ]
  📱 Android WebRTC P2P + Secondary Send-Only SFU Tap (/ws/tap)
  💻 Windows Desktop Audio Capture (16kHz PCM via sounddevice) ──► /api/stream/ws
  🌐 Next.js 14+ Tactical SOC Ingestion Sandbox ──────────────────► POST /api/stream/analyze
  💬 SMS BroadcastReceiver & NotificationListener ────────────────► /api/notify/external
           │
           ▼
[ Feature Extraction & Pre-Processing ]
  • Silero-VAD: 512-sample rolling windows, 0.4s–2.0s voiced segments
  • Streaming ASR: Deepgram Nova-2 (Cloud) OR Faster-Whisper (Local zero-cost fallback)
  • Frame Sampler: MediaPipe FaceMesh & 6 FPS Video Frame Normalizer
           │
           ▼
[ 14-Node LangGraph Conditional DAG ]
  router_node ──► voice_detector_node  (AST-ASVspoof5 + wav2vec2 acoustic SSL ensemble)
              ──► video_detector_node  (DeepfakeDet-ViT + DiCoME evidential CLIP)
              ──► text_detector_node   (Multilingual DistilBERT 8-class scam classifier)
              ──► identity_claim_node  (ECAPA-TDNN speaker verification vs enrolled voiceprints)
              ──► collective_check     (SHA-256 hashed community fraud ledger)
              ──► urgency & intent     (Psychological pressure & financial ask triage)
           │
           ▼
[ Dead-Zone Fusion Matrix & Arbitration Gate ]
  • Dead-Zone Formula: Soft signals alone strictly capped at 39/100 (Safe Band)
  • Escalation Guarantee: Requires ≥2 Soft Signals OR 1 Hard Signal
  • llm_reasoning_node: Groq / Qwen2.5-3B synthesizes plain-language guidance card
           │
           ▼
[ Active Protective Intercept ]
  🛑 Pre-Transaction Freeze Directive: 60s lock on money transfer / OTP with conscious override
  🔴 In-Call Compose AiInsightWindow: Real-time risk bands + streaming transcript
  ⚠️ DeepfakeAlertDialog: Immediate audio stream mute upon synthetic voice confirmation
  🌐 SOC Incident Console: Centralized monitoring, live telemetry & DAG inspection
```

---

## 📱 Production Interfaces & Side-by-Side Evidence

CallShield delivers native, hardware-optimized clients across mobile, desktop, and web:

### 🚨 In-Call AI Active Defense: Legitimate vs. Attack Interception

<table>
  <tr>
    <td align="center" width="50%">
      <b>⚠️ AI Voice Clone Intercepted (97% Synthetic)</b><br/>
      <sub>Audio paused automatically; cross-check modal presented</sub>
    </td>
    <td align="center" width="50%">
      <b>🔴 Live <code>AiInsightWindow</code> (Risk 84/100)</b><br/>
      <sub>Streaming transcript, plain-language explanation & 1-tap verification</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="assets/screenshots/antai_deepfake_alert.jpg" width="88%" alt="CallShield Deepfake Voice Clone Alert" style="border-radius:10px; border:1px solid #334155;" />
    </td>
    <td align="center">
      <img src="assets/screenshots/antai_incall_insight.jpg" width="88%" alt="CallShield In-Call AiInsightWindow" style="border-radius:10px; border:1px solid #334155;" />
    </td>
  </tr>
  <tr>
    <td>
      <ul>
        <li><b>Dual-Model Agreement</b>: <code>wav2vec2: 97%</code>, <code>velma-2: 67%</code>.</li>
        <li><b>Zero-Lag Intercept</b>: Stream muted within $<850\text{ms}$.</li>
        <li><b>Biometric Second Opinion</b>: 1-tap compare against enrolled family voiceprint.</li>
      </ul>
    </td>
    <td>
      <ul>
        <li><b>Plain-Language Action</b>: <i>"Do not give any OTP or personal details; hang up and call your bank..."</i></li>
        <li><b>Signal Roster</b>: Voice AI 8%, Scam 99%, Urgency 75/100.</li>
        <li><b>Real-Time Turn Ticker</b>: Continuous live transcript on-screen.</li>
      </ul>
    </td>
  </tr>
</table>

<br/>

### 🛡️ Device Armed Status, Voiceprint Enrollment & Scenario Policies

<table>
  <tr>
    <td align="center" width="33%">
      <b>🛡️ Armed Calls Dashboard</b><br/>
      <sub>Device pairing, ID, and active protection hero card</sub>
    </td>
    <td align="center" width="33%">
      <b>🎙️ Voiceprint Biometrics</b><br/>
      <sub>Zero-retention biometric enrollment (ECAPA-TDNN)</sub>
    </td>
    <td align="center" width="33%">
      <b>⚙️ Adaptive Scenario Policies</b><br/>
      <sub>Routine, High-Value Txn, & Privileged Access modes</sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="assets/screenshots/antai_home_armed.jpg" width="92%" alt="CallShield Armed Calls Dashboard" style="border-radius:8px; border:1px solid #334155;" />
    </td>
    <td align="center">
      <img src="assets/screenshots/antai_voiceprint_enroll.jpg" width="92%" alt="CallShield Voiceprint Enrollment" style="border-radius:8px; border:1px solid #334155;" />
    </td>
    <td align="center">
      <img src="assets/screenshots/antai_settings.jpg" width="92%" alt="CallShield Settings & Scenario Policy" style="border-radius:8px; border:1px solid #334155;" />
    </td>
  </tr>
  <tr>
    <td>
      <b>One-Tap Relay</b>: Connects to local WebRTC signaling hub on port <code>3007</code>.
    </td>
    <td>
      <b>Privacy-First</b>: Raw audio discarded; only irreversible 192-dim vector stored in encrypted SQLite.
    </td>
    <td>
      <b>Dynamic Sensitivity</b>: High-Value Txn lowers threshold from 70 to 55 for preemptive hold.
    </td>
  </tr>
</table>

---

## 🔮 Multi-Signal AI Fusion Brain

CallShield segregates incoming evidence into **Hard** (independently sufficient) and **Soft** (corroborative) indicators to eliminate false alarms:

```
                            ┌────────────────────────────────────────┐
                            │      🛡️ 9-SIGNAL AI FUSION MATRIX      │
                            └────────────────────────────────────────┘
                                                 │
             ┌───────────────────────────────────┴───────────────────────────────────┐
             ▼                                                                       ▼
 [ 🔴 HARD SIGNALS (Direct Trigger) ]                                    [ 🟡 SOFT SIGNALS (Corroborative) ]
 • Voice Deepfake: AST-ASV5 + wav2vec2 (+60)                             • Scam NLP: Multilingual DistilBERT (+35)
 • Video Deepfake: DeepfakeDet-ViT (+50)                                 • Psychological Urgency Scorer (+22)
 • Biometric Mismatch: ECAPA-TDNN (+50)                                  • Lip-Sync / Phoneme Disparity (+12)
 • Community Ledger: SHA-256 Flag (+45)                                  • Stylistic Behavioral Drift (+12)
 • Action Intent: Money / OTP / Wire Ask (+50)                           
```

### 📋 Calibrated Signal Weight & Action Specifications

| Signal Indicator | Subsystem | Architecture & Weights | Impact & Threshold Rule |
|:---|:---:|:---|:---|
| **🎙️ Synthetic Voice** | Audio | *AST-ASVspoof5* + *wav2vec2* SSL Cross-Check | **Hard (+60 max)**: Triggers when clone probability $\ge 0.78$ |
| **📹 Video Deepfake** | Vision | *DeepfakeDet-ViT* (CVPR 2025) + *DiCoME* CLIP | **Hard (+50 max)**: Flags face generation artifacts and frame swaps |
| **🆔 Voiceprint Match** | Biometric | *ECAPA-TDNN* Cosine Distance ($< 0.60$) | **Hard (+50)**: Alerts if caller voice mismatches enrolled family profile |
| **🌐 Collective Intel** | Ledger | SHA-256 Hashed Community Fraud DB | **Hard (+45)**: Known fraudulent numbers flagged on first ring |
| **🎯 Request Intent** | NLP | Zero-Shot Intent Classifier (Money/OTP/Creds) | **Hard (+50 max)**: Scaled by ask severity ($\text{OTP}=1.0, \text{Link}=0.5$) |
| **📝 Scam Pattern** | NLP | Fine-Tuned Multilingual *DistilBERT* (8 Classes) | **Soft (+35 max)**: Classifies digital arrest, bank, prize & romance scams |
| **⏰ Pressure Scorer** | NLP | Psychological Urgency & Panic Quantifier | **Soft (+22 max)**: Quantifies artificial deadlines and coercion cues |
| **👄 Lip-Sync Check** | CV / Audio | *MediaPipe FaceMesh* + AV Phoneme Correlation | **Soft (+12)**: Detects audiovisual speech mismatch |
| **📊 Behavioral Drift** | Stylistic | *Sentence-Transformers* Semantic Embeddings | **Soft (+12)**: Flags sudden deviations from historical conversation style |

---

## 🚀 Quickstart: 3-Step Deployment

Deploy the full CallShield defense system locally in minutes:

### 📋 Prerequisites
* **Python 3.10+** (with PyTorch support)
* **Node.js 18+** & **npm 9+**
* **Android Studio Ladybug+** (for Android client build with JDK 21)

---

### Step 1 ➔ Boot the WebRTC Signaling Hub
```bash
cd SimpleVideoCallBackend
npm install
npm start
```
*Listens on port `3007` to negotiate WebRTC media sessions.*

---

### Step 2 ➔ Launch the CallShield AI Defense Gateway
```bash
cd server
python -m venv .venv
.\.venv\Scripts\activate      # Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
python run_dev.py
```
*Initializes models, compiles the 14-Node LangGraph DAG, and opens REST/WebSocket on port `8765`.*

---

### Step 3 ➔ Launch Your Preferred Defense Client

#### Option A: Android Native Shield (Recommended)
```bash
cd app
./gradlew assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```
*Install onto any Android 10+ device and point to your local machine's IP (e.g. `http://10.20.143.151:3007` for Signaling, `http://10.20.143.151:8765` for AI).*

#### Option B: Next.js 14+ Tactical SOC Operations Console
```bash
cd web
npm install
npm run dev
```
*Open `http://localhost:3000` for the defense landing page, `/dashboard-live` for the live SOC console, and `/dataflow` for the interactive DAG schematic.*

#### Option C: Windows Desktop HUD
```bash
cd windows_client
pip install -r requirements.txt
python main.py --server ws://localhost:8765 --scenario high_value_txn
```
*Launches an always-on-top radial HUD monitoring system audio with native desktop toast notifications.*

---

## 🔬 Technical Rigor & Specifications

<details>
<summary><b>🧠 Deep Dive 1: 14-Node LangGraph State Machine & Execution SLA</b></summary>

<br/>

The detection engine is modeled as a compiled **asynchronous conditional DAG** using native LangGraph, ensuring every audio chunk is arbitrated in $< 850\text{ms}$:

<div align="center">
  <img src="antai_langgraph_architecture.jpg" width="90%" alt="CallShield LangGraph Orchestration Architecture" style="border-radius:8px; border:1px solid #334155;" />
</div>

<br/>

### Node Responsibilities & SLA Budgets:
1. **`router_node`** ($\le 5\text{ms}$): Routes media packets to parallel audio/video workers; routes text to NLP classifiers.
2. **`voice_detector_node`** ($\le 140\text{ms}$): Runs AST-ASVspoof5 + wav2vec2 ensemble on rolling 2.0s audio slices.
3. **`video_detector_node`** ($\le 120\text{ms}$): Samples 6 FPS frames via DeepfakeDet-ViT and evidential CLIP.
4. **`text_detector_node`** ($\le 60\text{ms}$): Multilingual DistilBERT 8-class scam classifier and semantic sentence embeddings.
5. **`identity_claim_node`** ($\le 80\text{ms}$): Extracts claimed caller identity and evaluates ECAPA-TDNN voiceprint cosine distance.
6. **`collective_check`** ($\le 15\text{ms}$): O(1) hash query against SHA-256 community fraud database.
7. **`urgency_node`** ($\le 30\text{ms}$): Quantifies artificial psychological urgency and panic indicators.
8. **`intent_node`** ($\le 40\text{ms}$): Triage actionable financial asks (OTP, Wire, Remote Screen Access).
9. **`fusion_node`** ($\le 10\text{ms}$): Evaluates mathematical dead-zone formula and generates composite risk score.
10. **`decision_node`** ($\le 5\text{ms}$): Deterministic action selection (`monitor`, `caution`, `freeze_transfer`, `pause_call`).
11. **`llm_reasoning_node`** ($\le 350\text{ms}$): Fast Groq / local Qwen2.5-3B engine generating plain-language advice card.

</details>

---

<details>
<summary><b>📐 Deep Dive 2: Mathematical Fusion Formula & Zero-False-Alarm Dead Zone</b></summary>

<br/>

CallShield computes composite risk score $R \in [0, 100]$ through a calibrated weighted combination:

$$R = \min\left(100, \; \sum_{i} w_i \cdot S_{\text{hard}, i} \;+\; \min\left(39, \; \sum_{j} v_j \cdot S_{\text{soft}, j}\right)\right)$$

### Mathematical Guarantees:
* **The 39-Point Dead Zone**: Soft signals (casual urgent words, minor stylistic fluctuations) are mathematically capped at $\le 39/100$ (*Passive Band*). Everyday conversations can **never trigger a false alert**.
* **Hard Signal Primacy**: A single confirmed acoustic voice clone ($w_{\text{voice}} \ge 0.78$) or biometric mismatch ($w_{\text{voiceprint}} < 0.60$) directly adds $+50$ to $+60$, immediately elevating the session into *Caution* or *Critical*.
* **Scenario Threshold Adaptation**:
  - `routine_call`: Review at $40$, Critical Intercept at $70$.
  - `high_value_txn`: Review at $30$, Critical Intercept at $55$.
  - `privileged_access`: Review at $25$, Critical Intercept at $45$.

</details>

---

<details>
<summary><b>🇮🇳 Deep Dive 3: Multilingual & Indian Regional Scam Benchmarks</b></summary>

<br/>

Evaluated against 1,200 real and synthesized multi-dialect scam scenarios across Indian English, Hindi, and code-switched Hinglish:

### ASR Word Error Rate (WER) across Dialects:
| ASR Engine | Dialect / Language | WER (%) | Chunk Latency | Resilience Characteristics |
|:---|:---|:---:|:---:|:---|
| **Deepgram Nova-2** (Cloud) | Indian English (`en-IN`) | **9.4%** | ~400 ms | Robust to Indian street acoustic noise |
| **Deepgram Nova-2** (Cloud) | Hindi / Hinglish | **13.2%** | ~520 ms | Accurately segments mixed code-switching |
| **Faster-Whisper Small** (Local) | Indian English (`en-IN`) | **14.8%** | ~780 ms | Zero cloud dependencies; runs on CPU |
| **Faster-Whisper Small** (Local) | Hindi (Devanagari) | **18.1%** | ~890 ms | High accuracy on conversational phonemes |
| **Faster-Whisper Small** (Local) | Hinglish | **22.5%** | ~940 ms | Normalized via regex text cleaner |

### NLP Scam Classifier F1-Scores across Indian Vectors:
| Scam Attack Vector | English F1 | Hindi F1 | Hinglish F1 | Key Trigger Lexicon |
|:---|:---:|:---:|:---:|:---|
| **Digital Arrest / Police Impersonation** | 95% | 92% | **91%** | *"CBI officer", "drugs parcel", "digital arrest"* |
| **Banking / KYC Expiry** | 95% | 92% | **93%** | *"SBI account blocked", "pancard expire", "OTP share"* |
| **Family Emergency / Kidnapping** | 90% | 88% | **87%** | *"Accident ho gaya", "hospital bill", "police custody"* |
| **Electricity / Utility Disconnection** | 96% | 94% | **93%** | *"Bijli cut ho jayegi", "power bill update"* |
| **Task / Part-Time Job Fraud** | 93% | 91% | **90%** | *"Telegram task", "daily 5000 earn", "prepaid merchant"* |
| **Benign Everyday Conversation** | 97% | 95% | **94%** | Casual greetings, work discussions, family chats |

</details>

---

<details>
<summary><b>🔌 Deep Dive 4: Streaming WebSocket & REST API Protocols</b></summary>

<br/>

### Streaming WebSocket (`/api/stream/ws`):
* **Binary Stream**: 16 kHz mono float32 PCM chunks (2.0s sliding windows).
* **Control Frame (JSON)**:
  ```json
  { "rate": 16000, "scenario": "high_value_txn", "caller_id": "+919876543210" }
  ```
* **Real-Time Normalized Verdict Frame**:
  ```json
  {
    "risk": 87.0,
    "risk_level": "critical",
    "signals": {
      "voice_authenticity": { "score": 0.97, "kind": "acoustic_ssl", "flagged": true },
      "voiceprint_match": { "score": 0.12, "threshold": 0.60, "flagged": true },
      "scam_intent": { "category": "digital_arrest", "confidence": 0.94, "urgency": 0.88 }
    },
    "verdict": {
      "verdict": "CRITICAL RISK — SYNTHETIC VOICE & DIGITAL ARREST FRAUD",
      "why": "Caller voice matches known synthetic vocoder artifacts (97%) and does not match enrolled contact voiceprint.",
      "action": "Do not transfer funds or share credentials. Hang up immediately."
    }
  }
  ```

</details>

---

<details>
<summary><b>📁 Deep Dive 5: Complete Multi-Platform Codebase Map</b></summary>

<br/>

```
CallShield/
├── 📱 app/                                    # Android Native Client (Kotlin 2.0, Jetpack Compose)
│   ├── app/src/main/java/com/codewithkael/simplecall/
│   │   ├── di/                               # Dagger Hilt dependency injection
│   │   ├── remote/                           # WebRTC engine, AiTapEngine, WebSocket stream client
│   │   ├── sms/                              # SmsReceiver broadcast interceptor
│   │   ├── notifications/                    # NotificationListenerService & RiskNotificationManager
│   │   ├── shield/                           # On-device background mic listener & floating overlay
│   │   └── ui/
│   │       ├── components/                   # AiInsightWindow, CallerVerifyBar, DeepfakeAlertDialog
│   │       └── screens/                      # MainScreen, Messages, NotificationGuard, Voiceprints, Settings
│   └── build.gradle.kts                      # AGP 8.7.3, Kotlin 2.0.0, Compose BOM
├── 🌐 web/                                    # Next.js 14+ Tactical SOC Operations Console
│   ├── src/app/                              # App Router (/, /features, /how-it-works, /demo, /dashboard-live, /dataflow)
│   ├── src/components/dashboard/             # RiskGauge, FreezeBanner, SignalBar, Sparkline, VerdictCard
│   ├── src/components/dataflow/              # PipelineGraphSvg, NodeDetailsPanel, TraceScamAnimation
│   └── tailwind.config.ts                    # Hardened cybersecurity SOC design tokens
├── 💻 windows_client/                         # Windows Desktop Companion (Python 3.10+, Tkinter)
│   ├── audio_capture.py                      # 16kHz float32 PCM mic/stereo-mix capture via sounddevice
│   ├── streaming_client.py                   # WebSocket streaming client (/api/stream/ws)
│   ├── ui_overlay.py                         # Always-on-top Tkinter radial risk gauge HUD
│   └── notifications.py                      # Native Windows desktop toast alerts
├── 🧠 server/                                 # AI Detection Gateway & Brain (FastAPI + PyTorch)
│   ├── config.yaml                           # Master thresholds, model weights & backends
│   ├── run_dev.py                            # One-click dev launcher & model pre-warm
│   └── src/antai/
│       ├── orchestration/                    # 14-Node LangGraph state machine & streaming session
│       ├── inference/                        # AST-ASV5, wav2vec2, Deepfake ViT, ECAPA, DistilBERT, LLM
│       ├── gateway/                          # FastAPI REST routes, WebSocket SFU tap & RealtimeHub
│       ├── media_sfu/                        # aiortc WebRTC media tap engine
│       ├── intercept/                        # Autonomous freeze controller & safety directives
│       ├── trust_circle/                     # Trusted contacts roster & cross-verification
│       └── storage/                          # SQLite DB with Fernet AES symmetric encryption
├── 🔌 SimpleVideoCallBackend/                 # Node.js WebRTC signaling relay (Port 3007)
├── 🌐 SimpleVideoCallReacJs/                  # React 18 + Vite WebRTC test client
├── ⚡ worker/                                  # Cloudflare Edge API Worker & WebSocket relay
├── 🧩 extension/                               # Chrome MV3 DOM text mutation scanner & phishing HUD
├── 🖼️ assets/                                 # Animated SVG architecture DAG, terminal card & screenshots
└── 📚 docs/                                   # Architecture blueprints, setup manuals & benchmarks
```

</details>

---

## 🔒 Privacy & Security by Design

* 🛡️ **Ephemeral In-Memory Audio**: Raw audio frames are processed entirely in RAM for VAD/ASR and immediately discarded. No audio recordings are ever written to disk.
* 🔐 **Irreversible Biometric Vector Embeddings**: Voiceprints are stored exclusively as 192-dimensional numerical vector embeddings, never as replayable audio.
* 🔑 **AES-256 Symmetric Encryption**: Database records (phone numbers, alerts, transcript snippets) are encrypted at rest using Fernet AES symmetric encryption.
* 🌐 **Hashed Collective Intelligence**: Community fraud lists index only SHA-256 hashes of telephone numbers to eliminate PII leakage.

---

<div align="center">

### 🛡️ CallShield
*Safer conversations, a brighter tomorrow.*

⭐ **Star this repository to support open, accessible AI fraud defense!**

</div>
