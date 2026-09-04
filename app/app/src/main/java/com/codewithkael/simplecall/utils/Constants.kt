package com.codewithkael.simplecall.utils

object Constants {
    const val TIME_OUT_DURATION_MS = 20000L

    // Port the SimpleVideoCallBackend (Node.js signaling server) listens on.
    const val SIGNALING_PORT = 3007

    // Default signaling server host = the LAN IPv4 of the laptop running the Node
    // backend. Find it on Windows with `ipconfig` (the IPv4 of your Wi-Fi adapter,
    // e.g. 192.168.1.23). You can also change this at runtime on the app's home
    // screen (it is saved on the device), so you never need to rebuild just because
    // the laptop's IP changed. This default is only the pre-filled value.
    const val DEFAULT_SERVER_HOST = "192.168.1.100"

    // Port the antAI Python server (FastAPI: LangGraph pipeline + local LLM)
    // listens on. It runs on the SAME laptop as the Node signaling server, so
    // the same user-entered host is reused — only the port differs.
    const val ANTAI_PORT = 8765

    // Emulator note: to test on an Android emulator instead of a phone, enter
    // 10.0.2.2 as the server host (the emulator's alias for the host machine).
    fun getWebSocketUrl(host: String, username: String) =
        "ws://$host:$SIGNALING_PORT/?username=$username"

    // Control channel for the antAI real-time analysis tap: carries the
    // send-only WebRTC offer/ICE for scam detection AND receives the live
    // transcript / verdict / guidance / report pushes.
    fun getAntaiTapUrl(host: String, username: String) =
        "ws://$host:$ANTAI_PORT/ws/tap?user=$username"

    // ---- Messaging / notification-guard (antAI REST + chat WebSocket) ----
    // These reuse the SAME laptop host as calling; only the antAI port is used.
    // They are additive to the calling path and never touch it.

    // REST base for auth, chat send/history, external-notify (SMS + app notifs),
    // and contacts. See server gateway/rest_api.py.
    fun getAntaiRestBase(host: String) = "http://$host:$ANTAI_PORT"

    // Chat WebSocket: RECEIVE-only in practice (incoming chat.recv + verdict/
    // freeze pushes over the realtime hub). Auth via the bearer token in query.
    fun getAntaiChatWsUrl(host: String, token: String) =
        "ws://$host:$ANTAI_PORT/ws/chat?token=$token"

    // SharedPreferences store shared with MainViewModel so the messaging layer
    // reads the very same server host the user set on the calls screen.
    const val CALL_PREFS = "antai_call_prefs"
    const val KEY_SERVER_HOST = "server_host"

    // Separate store for the messaging identity (phone + bearer token).
    const val MSG_PREFS = "antai_msg_prefs"
    const val KEY_PHONE = "my_phone"
    const val KEY_TOKEN = "auth_token"
    const val KEY_DISPLAY_NAME = "display_name"
}