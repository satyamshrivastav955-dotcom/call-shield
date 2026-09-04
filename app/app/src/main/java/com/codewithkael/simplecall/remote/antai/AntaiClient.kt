package com.codewithkael.simplecall.remote.antai

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

/**
 * Control WebSocket to the antAI server (/ws/tap). Deliberately a SEPARATE
 * client class from SocketClient (which force-closes any previous connection
 * and speaks the Node signaling protocol).
 *
 * Contract (see server gateway/tap.py):
 *   out: tap.start {peer, kind, offer{sdp,type}} | tap.ice {candidate}
 *        | tap.stop {} | ping
 *   in:  tap.started {session_key} | tap.answer {sdp} | tap.ice {candidate}
 *        | transcript.update | verdict.update | guidance.update | signals.update
 *        | deepfake.alert | voiceprint.result | verify.prompt | verify.result
 *        | freeze.request | report.ready | pong | error
 *
 * All callbacks arrive on the WebSocket's own thread — implementations must
 * only write to thread-safe state (flows), never touch WebRTC/UI objects.
 */
class AntaiClient(
    private val url: String,
    private val callback: Callback
) {

    private var socket: WebSocketClient? = null

    fun connect() {
        close()
        Log.d("AI_TAP", "connecting to $url")
        socket = object : WebSocketClient(URI(url)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("AI_TAP", "onOpen (httpStatus=${handshakedata?.httpStatus})")
                callback.onOpened()
            }

            override fun onMessage(message: String?) {
                Log.d("AI_TAP", "onMessage: $message")
                message?.let { runCatching { route(JSONObject(it)) }
                    .onFailure { e -> Log.e("AI_TAP", "route failed: $message", e) } }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w("AI_TAP", "onClose: code=$code reason=$reason remote=$remote")
                socket = null
                callback.onClosed()
            }

            override fun onError(ex: Exception?) {
                Log.e("AI_TAP", "onError", ex)
                callback.onError(ex)
            }
        }.apply { connect() }
    }

    private fun route(msg: JSONObject) {
        when (msg.optString("type")) {
            "tap.started" -> callback.onTapStarted(msg.optString("session_key"))
            "tap.answer" -> callback.onTapAnswer(msg.optString("sdp"))
            "tap.ice" -> msg.optJSONObject("candidate")?.let { callback.onTapIce(it) }
            "transcript.update" -> callback.onTranscript(
                TranscriptEntry(
                    speaker = msg.optString("speaker"),
                    text = msg.optString("text"),
                    t = msg.optString("t")
                )
            )
            "verdict.update" -> callback.onVerdict(
                band = msg.optString("band", "passive"),
                risk = msg.optDouble("risk_score", 0.0),
                verdict = msg.optString("verdict"),
                why = msg.optString("why"),
                action = msg.optString("action")
            )
            "guidance.update" -> callback.onGuidance(
                msg.optString("guidance"), msg.optDouble("risk_score", 0.0)
            )
            "signals.update" -> callback.onSignals(
                AiSignals(
                    risk = msg.optDouble("risk", 0.0),
                    band = msg.optString("band", "passive"),
                    // optDouble(..., 0.0) would turn the server's explicit
                    // "no detector answered" null into a confident 0% — keep the
                    // null so the UI can say "unavailable" instead.
                    voiceDeepfake = msg.optDoubleOrNull("voice_deepfake"),
                    voicePerModel = parseDoubleMap(msg.optJSONObject("voice_per_model")),
                    voiceSources = msg.optIntOrNull("voice_sources") ?: 0,
                    videoDeepfake = msg.optDoubleOrNull("video_deepfake"),
                    videoVotes = msg.optInt("video_votes", 0),
                    videoAgreement = msg.optDouble("video_agreement", 0.0),
                    scamProb = msg.optDouble("scam_prob", 0.0),
                    scamType = msg.optString("scam_type", ""),
                    deviation = msg.optDouble("deviation", 0.0),
                    urgency = msg.optDouble("urgency", 0.0),
                    requestType = msg.optString("request_type", ""),
                    requestDetected = msg.optBoolean("request_detected", false),
                    lipsyncMismatch = msg.optBoolean("lipsync_mismatch", false),
                    identityMismatch = msg.optBoolean("identity_mismatch", false),
                    enginesReady = parseBoolMap(msg.optJSONObject("engines_ready")),
                    audioSegments = msg.optInt("audio_segments", 0),
                    asrFailures = msg.optInt("asr_failures", 0)
                )
            )
            "deepfake.alert" -> callback.onDeepfakeAlert(
                DeepfakeAlert(
                    source = msg.optString("source", "voice"),
                    title = msg.optString("title"),
                    voiceDeepfake = msg.optDouble("voice_deepfake", 0.0),
                    videoDeepfake = msg.optDouble("video_deepfake", 0.0),
                    videoVotes = msg.optInt("video_votes", 0),
                    voicePerModel = parseDoubleMap(msg.optJSONObject("voice_per_model")),
                    voiceSources = msg.optInt("voice_sources", 0),
                    // null when only one detector scored the audio; optString would
                    // hand the UI the literal text "null" to render
                    voiceAgreement = msg.optStringOrNull("voice_agreement").orEmpty(),
                    voiceBackend = msg.optStringOrNull("voice_backend").orEmpty(),
                    canCrossVerify = msg.optBoolean("can_cross_verify", false),
                    message = msg.optStringOrNull("message").orEmpty()
                )
            )
            // Independent second opinion on an AI-voice alert: the buffered call
            // audio compared against the contact's enrolled voiceprint.
            "voiceprint.result" -> callback.onVoiceprintResult(VoiceprintResult.from(msg))
            // Outcome of "is this really who they claim to be?" — pending while we
            // wait for the contact, then confirmed / denied / timeout. Parsed into
            // the same VerifyOutcome the chat socket produces, because the server
            // may address this event to either socket's user id.
            "verify.result" -> callback.onVerifyResult(VerifyOutcome.from(msg))
            "freeze.request" -> callback.onFreeze(
                requestType = msg.optString("request_type", "unknown"),
                message = msg.optString("message"),
                reason = msg.optString("reason")
            )
            "verify.prompt" -> callback.onVerifyPrompt(msg.toString())
            "report.ready" -> callback.onReportReady(msg.optString("title"))
            "error" -> Log.w("AI_TAP", "server error: ${msg.optString("message")}")
            else -> Log.d("AI_TAP", "unhandled type=${msg.optString("type")}")
        }
    }

    private fun parseDoubleMap(obj: JSONObject?): Map<String, Double> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, Double>()
        obj.keys().forEach { k -> runCatching { out[k] = obj.optDouble(k) } }
        return out
    }

    private fun parseBoolMap(obj: JSONObject?): Map<String, Boolean> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, Boolean>()
        obj.keys().forEach { k -> runCatching { out[k] = obj.optBoolean(k) } }
        return out
    }

    private fun send(obj: JSONObject) {
        runCatching { socket?.send(obj.toString()) }
            .onFailure { Log.w("AI_TAP", "send failed (socket down?)", it) }
    }

    fun sendTapStart(peer: String, kind: String, sdp: String) {
        send(JSONObject()
            .put("type", "tap.start")
            .put("peer", peer)
            .put("kind", kind)
            .put("offer", JSONObject().put("sdp", sdp).put("type", "offer")))
    }

    fun sendTapIce(candidate: JSONObject) {
        send(JSONObject().put("type", "tap.ice").put("candidate", candidate))
    }

    fun sendTapStop() {
        send(JSONObject().put("type", "tap.stop"))
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    interface Callback {
        fun onOpened()
        fun onClosed()
        fun onError(e: Exception?)
        fun onTapStarted(sessionKey: String)
        fun onTapAnswer(sdp: String)
        fun onTapIce(candidate: JSONObject)
        fun onTranscript(entry: TranscriptEntry)
        fun onVerdict(band: String, risk: Double, verdict: String, why: String, action: String)
        fun onGuidance(guidance: String, risk: Double)
        fun onSignals(signals: AiSignals)
        fun onDeepfakeAlert(alert: DeepfakeAlert)
        fun onFreeze(requestType: String, message: String, reason: String)
        fun onVerifyPrompt(raw: String)
        fun onReportReady(title: String)

        // Default no-op bodies: these events are additive, so an implementer that
        // predates them still compiles and simply ignores them.
        /** Voiceprint cross-check finished (pushed for every requester of the session). */
        fun onVoiceprintResult(result: VoiceprintResult) {}

        /** pending | confirmed | denied | timeout for the "Verify caller" challenge. */
        fun onVerifyResult(outcome: VerifyOutcome) {}
    }
}
