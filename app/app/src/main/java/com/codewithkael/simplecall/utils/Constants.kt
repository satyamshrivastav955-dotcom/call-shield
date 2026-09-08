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
    // Default signaling server host = Cloudflare Worker
    const val DEFAULT_SERVER_HOST = "antai-signaling-server.opaque-preface.workers.dev"

    // Default antAI AI detection server host = Cloudflare Worker
    const val DEFAULT_ANTAI_HOST = "antai-edge-api.hardly-rumba.workers.dev"

    // Port the antAI Python server (FastAPI: LangGraph pipeline + local LLM)
    // listens on. It runs on the SAME laptop as the Node signaling server, so
    // the same user-entered host is reused — only the port differs.
    const val ANTAI_PORT = 8765

    fun cleanHost(raw: String): String {
        var h = raw.trim()
        if (h.startsWith("https://", ignoreCase = true)) h = h.substring(8)
        else if (h.startsWith("http://", ignoreCase = true)) h = h.substring(7)
        else if (h.startsWith("wss://", ignoreCase = true)) h = h.substring(6)
        else if (h.startsWith("ws://", ignoreCase = true)) h = h.substring(5)
        return h.trimEnd('/')
    }

    fun isCloudflareOrSecureDomain(raw: String): Boolean {
        val h = cleanHost(raw)
        return raw.startsWith("https://", ignoreCase = true) ||
               raw.startsWith("wss://", ignoreCase = true) ||
               h.contains(".trycloudflare.com", ignoreCase = true) ||
               h.contains(".workers.dev", ignoreCase = true) ||
               h.contains(".pages.dev", ignoreCase = true)
    }

    // Emulator note: to test on an Android emulator instead of a phone, enter
    // 10.0.2.2 as the server host (the emulator's alias for the host machine).
    fun getWebSocketUrl(host: String, username: String): String {
        val h = cleanHost(host)
        return if (isCloudflareOrSecureDomain(host)) {
            // If the host entered is the AI tunnel, route signaling to the Cloudflare Worker
            val targetHost = if (h.contains("trycloudflare.com", ignoreCase = true)) DEFAULT_SERVER_HOST else h
            "wss://$targetHost/?username=$username"
        } else {
            "ws://$h:$SIGNALING_PORT/?username=$username"
        }
    }

    // Control channel for the antAI real-time analysis tap: carries the
    // send-only WebRTC offer/ICE for scam detection AND receives the live
    // transcript / verdict / guidance / report pushes.
    fun getAntaiTapUrl(host: String, username: String): String {
        val h = cleanHost(host)
        return if (isCloudflareOrSecureDomain(host)) {
            // If the host entered is the signaling worker, route AI tap to the Cloudflare Tunnel
            val targetHost = if (h.contains("workers.dev", ignoreCase = true)) DEFAULT_ANTAI_HOST else h
            "wss://$targetHost/ws/tap?user=$username"
        } else {
            "ws://$h:$ANTAI_PORT/ws/tap?user=$username"
        }
    }

    // REST base for auth, chat send/history, external-notify (SMS + app notifs),
    // and contacts. See server gateway/rest_api.py.
    fun getAntaiRestBase(host: String): String {
        val h = cleanHost(host)
        return if (isCloudflareOrSecureDomain(host)) {
            val targetHost = if (h.contains("workers.dev", ignoreCase = true)) DEFAULT_ANTAI_HOST else h
            "https://$targetHost"
        } else {
            "http://$h:$ANTAI_PORT"
        }
    }

    // Chat WebSocket: RECEIVE-only in practice (incoming chat.recv + verdict/
    // freeze pushes over the realtime hub). Auth via the bearer token in query.
    fun getAntaiChatWsUrl(host: String, token: String): String {
        val h = cleanHost(host)
        return if (isCloudflareOrSecureDomain(host)) {
            val targetHost = if (h.contains("workers.dev", ignoreCase = true)) DEFAULT_ANTAI_HOST else h
            "wss://$targetHost/ws/chat?token=$token"
        } else {
            "ws://$h:$ANTAI_PORT/ws/chat?token=$token"
        }
    }

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