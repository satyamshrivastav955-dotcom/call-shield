# 📱 antAI — Android Client (Jetpack Compose & WebRTC)

Native Android client for the **antAI** Real-Time Scam, Deepfake & Impersonation Defense System.

---

## 🌟 Key Capabilities

- **VoIP & Video Calling Engine**: Built on WebRTC with dual-stream architecture (Peer-to-Peer media + secondary send-only `AiTapEngine` stream for server analysis).
- **In-Call `AiInsightWindow`**: Floating Compose overlay showing real-time risk bands (🟢 *MONITORING*, 🟡 *CAUTION*, 🔴 *HIGH RISK*), live transcription, and plain-language action guidance.
- **`CallerVerifyBar`**: One-tap escalation to cross-verify callers with trusted family contacts.
- **`DeepfakeAlertDialog`**: Instant warning modal and call pause when synthetic voice or video is detected.
- **SMS & Notification Guard**: Native `SmsReceiver` and `AntaiNotificationListenerService` scanning third-party app messages in real time.
- **Voiceprint Enrollment**: Guided audio recording to capture family speaker embeddings for ECAPA-TDNN verification.

---

## 🛠️ Tech Stack & Dependencies

- **Language**: Kotlin 2.0.0 (AGP 8.7.3)
- **UI Framework**: Jetpack Compose (Material 3)
- **Dependency Injection**: Dagger Hilt 2.50
- **Real-Time Media**: WebRTC (`com.mesibo.api:webrtc:1.0.5`)
- **Networking**: Java-WebSocket, Retrofit/OkHttp, Gson
- **Min SDK**: 24 (Android 7.0) | **Target SDK**: 34 (Android 14) | **Compile SDK**: 35

---

## 🚀 Building & Running

1. Open `/app` in **Android Studio Ladybug (or newer)**.
2. Ensure your phone and development PC are on the same Wi-Fi network.
3. Build and install to your connected device:
   ```bash
   ./gradlew installDebug
   ```
4. Enter the local IP address of your antAI server in the app configuration screen.
