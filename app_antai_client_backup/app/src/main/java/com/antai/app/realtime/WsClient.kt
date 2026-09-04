package com.antai.app.realtime

import android.util.Log
import com.antai.app.data.local.AuthPrefs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Single authenticated WebSocket connection. The server registers the user on
 * the realtime hub through /ws/call, which also receives verdict/guidance/
 * freeze/verify/report messages. Call signaling frames pass through the same
 * socket (call.start/call.ice/call.accept/call.end).
 */
class WsClient(private val prefs: AuthPrefs, private val router: WsRouter) {

    private var socket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var reconnectAttempts = 0

    suspend fun connect() {
        if (socket != null) return  // one live socket per process
        val token = prefs.currentToken() ?: run {
            Log.w(TAG, "connect: no auth token saved yet")
            return
        }
        val base = prefs.currentBaseUrl().trim().trimEnd('/')
        val wsUrl = base.replaceFirst("http", "ws") + "/ws/call?token=$token"
        Log.d(TAG, "Connecting to WebSocket: $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(request, listener())
    }

    fun disconnect() {
        socket?.close(1000, "bye")
        socket = null
    }

    fun send(type: String, payload: JSONObject? = null) {
        val msg = JSONObject().put("type", type)
        if (payload != null) {
            val it = payload.keys()
            while (it.hasNext()) {
                val k = it.next()
                msg.put(k, payload.get(k))
            }
        }
        val s = socket
        if (s == null) {
            Log.w(TAG, "send: socket is null! Cannot send message '$type'. Triggering reconnect...")
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { connect() }
            }
            return
        }
        Log.d(TAG, "send -> $type: $msg")
        s.send(msg.toString())
    }

    private fun listener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "onOpen: WebSocket connected!")
            reconnectAttempts = 0
            // P2-A FIX: retry any messages that failed while the socket was down.
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { com.antai.app.comms.AppRepository.flushQueue() }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "onMessage <- $text")
            GlobalScope.launch(Dispatchers.Main) {
                runCatching { router.handle(JSONObject(text)) }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosing: WebSocket closing: $code / $reason")
            if (webSocket === socket) socket = null
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosed: WebSocket closed: $code / $reason")
            if (webSocket === socket) socket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "onFailure: WebSocket connection error: ${t.message}")
            if (webSocket === socket) socket = null
            if (reconnectAttempts < 10) {
                reconnectAttempts++
                GlobalScope.launch {
                    delay(2000L * reconnectAttempts)
                    runCatching { connect() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "WsClient"
    }
}