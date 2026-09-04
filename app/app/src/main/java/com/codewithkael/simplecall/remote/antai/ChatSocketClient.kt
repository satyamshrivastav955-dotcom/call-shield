package com.codewithkael.simplecall.remote.antai

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

/**
 * Chat WebSocket to the antAI server (/ws/chat). Mirrors [AntaiClient] (the tap
 * client): a separate, self-contained client so it never interferes with the
 * Node signaling SocketClient or the call/SFU path.
 *
 * In practice this is RECEIVE-oriented: sending goes through the REST
 * /api/chat/send (which auto-provisions the recipient and returns a message_id),
 * while this socket delivers:
 *   chat.recv      {message_id, sender_id, sender_phone, body}
 *   verdict.update {band, risk_score, verdict, why, action, scam_type?, kind,
 *                   message_id, peer_phone, ...}
 *   freeze.request {...}
 *
 * All callbacks arrive on the socket's own thread; implementations must only
 * write to thread-safe state (flows), never touch UI/WebRTC objects.
 */
class ChatSocketClient(
    private val url: String,
    private val callback: Callback
) {
    private var socket: WebSocketClient? = null

    fun connect() {
        close()
        Log.d(TAG, "connecting to $url")
        socket = object : WebSocketClient(URI(url)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d(TAG, "onOpen (httpStatus=${handshakedata?.httpStatus})")
                callback.onOpened()
            }

            override fun onMessage(message: String?) {
                Log.d(TAG, "onMessage: $message")
                message?.let {
                    runCatching { route(JSONObject(it)) }
                        .onFailure { e -> Log.e(TAG, "route failed: $message", e) }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w(TAG, "onClose: code=$code reason=$reason remote=$remote")
                socket = null
                callback.onClosed()
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "onError", ex)
                callback.onError(ex)
            }
        }.apply { connect() }
    }

    private fun route(msg: JSONObject) {
        when (msg.optString("type")) {
            "chat.recv" -> callback.onChatRecv(
                messageId = msg.optLong("message_id", -1),
                senderId = msg.optLong("sender_id", -1),
                senderPhone = msg.optString("sender_phone", ""),
                body = msg.optString("body", "")
            )
            "chat.sent" -> callback.onChatSent(
                messageId = msg.optLong("message_id", -1),
                riskScore = msg.optDouble("risk_score", 0.0)
            )
            "verdict.update" -> callback.onVerdict(
                peerPhone = msg.optString("peer_phone", ""),
                messageId = msg.optLong("message_id", -1),
                kind = msg.optString("kind", "message"),
                payload = VerdictPayload.from(msg)
            )
            "freeze.request" -> callback.onFreeze(
                peerPhone = msg.optString("peer_phone", ""),
                message = msg.optString("message", ""),
                reason = msg.optString("reason", "")
            )
            // Someone is claiming to be THIS user on a call with one of their
            // contacts. The real contact is not on that call, so this is the only
            // socket they have open — it must be handled here or the whole
            // "verify with a trusted contact" feature silently does nothing.
            "verify.prompt" -> callback.onVerifyPrompt(VerifyPrompt.from(msg))
            // The outcome of a "Verify caller" tap arrives HERE, not on the call
            // socket: the server pushes it to the user id that authenticated the
            // REST request (the phone/OTP user, which owns this socket), while the
            // tap socket is registered under a separate auto-provisioned user row.
            // Without this case the in-call bar would wait on "pending" forever.
            "verify.result" -> callback.onVerifyResult(VerifyOutcome.from(msg))
            "pong" -> { /* keepalive */ }
            "error" -> Log.w(TAG, "server error: ${msg.optString("message")}")
            else -> Log.d(TAG, "unhandled type=${msg.optString("type")}")
        }
    }

    private fun send(obj: JSONObject) {
        runCatching { socket?.send(obj.toString()) }
            .onFailure { Log.w(TAG, "send failed (socket down?)", it) }
    }

    /** Optional: send over WS. Repository uses REST instead, but kept for parity. */
    fun sendChat(recipientPhone: String, body: String) {
        send(JSONObject().put("type", "chat.send")
            .put("recipient_phone", recipientPhone).put("body", body))
    }

    fun ping() = send(JSONObject().put("type", "ping"))

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    interface Callback {
        fun onOpened()
        fun onClosed()
        fun onError(e: Exception?)
        fun onChatRecv(messageId: Long, senderId: Long, senderPhone: String, body: String)
        fun onChatSent(messageId: Long, riskScore: Double)
        fun onVerdict(peerPhone: String, messageId: Long, kind: String, payload: VerdictPayload)
        fun onFreeze(peerPhone: String, message: String, reason: String)

        /** A contact is being impersonated to someone; ask them to confirm. */
        fun onVerifyPrompt(prompt: VerifyPrompt) {}

        /** Outcome of a verification this user asked for while on a call. */
        fun onVerifyResult(outcome: VerifyOutcome) {}
    }

    companion object {
        private const val TAG = "AI_CHAT"
    }
}
