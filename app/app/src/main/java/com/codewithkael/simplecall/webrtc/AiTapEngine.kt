package com.codewithkael.simplecall.webrtc

import android.util.Log
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.RtpParameters
import org.webrtc.SessionDescription

/**
 * Send-only WebRTC leg that copies the local mic/camera to the antAI server
 * for real-time scam/deepfake analysis while the actual call stays P2P.
 *
 * Flow: start() builds the offer (audio+video sendonly, recvonly OFF), waits
 * for ICE gathering so the SDP is self-contained, then hands it to the
 * controller to send as tap.start. The server's tap.answer completes the
 * leg. Every failure is swallowed — the tap must NEVER affect the call.
 */
class AiTapEngine(
    private val factory: WebRTCFactory,
    private val controller: Controller
) {

    interface Controller {
        /** Offer SDP (with gathered candidates) ready to send as tap.start. */
        fun onTapOfferReady(sdp: String)
        /** Local ICE candidate as JSON (already filtered to usable ones). */
        fun onLocalIceCandidate(candidateJson: JSONObject)
    }

    private var pc: PeerConnection? = null
    private val iceBuffer = mutableListOf<JSONObject>()
    private var answerSet = false
    private var closed = false

    /** 200 kbps cap on the tap video: the server samples at 6 fps and its
     *  deepfake models use small inputs — full-quality uplink is pure waste
     *  and would steal bandwidth from the real call. */
    private val maxTapVideoBitrateBps = 200_000

    fun start() {
        if (closed) return
        val audioTrack = factory.getLocalAudioTrack()
        val videoTrack = factory.getLocalVideoTrack()
        if (audioTrack == null) {
            Log.w("AI_TAP", "no local audio track yet — tap not started")
            return
        }
        val connection = factory.createTapPeerConnection(object : MyPeerObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { handleLocalIce(it) }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("AI_TAP", "ice state: $state")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d("AI_TAP", "gathering state: $state")
            }
        }) ?: run {
            Log.w("AI_TAP", "could not create tap peer connection")
            return
        }
        pc = connection

        connection.addTrack(audioTrack, listOf("antai-audio"))
        videoTrack?.let { connection.addTrack(it, listOf("antai-video")) }
        capTapVideoBitrate(connection)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        connection.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                connection.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        // wait briefly for gathering so the SDP carries the
                        // candidates; late trickle still flows via tap.ice
                        Thread {
                            val deadline = System.currentTimeMillis() + 4000
                            while (System.currentTimeMillis() < deadline) {
                                if (connection.iceGatheringState() ==
                                    PeerConnection.IceGatheringState.COMPLETE || closed) break
                                Thread.sleep(50)
                            }
                            val sdp = connection.localDescription?.description ?: desc.description
                            Log.d("AI_TAP", "offer ready (len=${sdp.length})")
                            if (!closed) controller.onTapOfferReady(sdp)
                        }.apply { isDaemon = true }.start()
                    }
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                Log.e("AI_TAP", "createOffer failed: $error")
            }
        }, constraints)
    }

    private fun capTapVideoBitrate(connection: PeerConnection) {
        runCatching {
            connection.senders.filter { it.track()?.kind() == "video" }.forEach { sender ->
                val params = sender.parameters
                if (params.encodings.isEmpty()) {
                    params.encodings.add(RtpParameters.Encoding(null, true, null))
                }
                params.encodings.forEach { it.maxBitrateBps = maxTapVideoBitrateBps }
                sender.parameters = params
                Log.d("AI_TAP", "tap video bitrate capped to $maxTapVideoBitrateBps bps")
            }
        }.onFailure { Log.w("AI_TAP", "bitrate cap failed (continuing uncapped)", it) }
    }

    private fun handleLocalIce(candidate: IceCandidate) {
        val json = candidate.toJson()
        val ip = json.optString("ip")
        // mDNS .local / empty candidates can't be used by the server side
        if (ip.isBlank() || ip.endsWith(".local")) return
        synchronized(iceBuffer) {
            if (answerSet) controller.onLocalIceCandidate(json) else iceBuffer.add(json)
        }
    }

    /** Server answered the tap offer — complete the leg and flush buffered ICE. */
    fun handleAnswer(sdp: String) {
        val connection = pc ?: return
        connection.setRemoteDescription(object : MySdpObserver() {
            override fun onSetSuccess() {
                Log.d("AI_TAP", "remote description set — tap leg connected")
                answerSet = true
                synchronized(iceBuffer) {
                    iceBuffer.forEach(controller::onLocalIceCandidate)
                    iceBuffer.clear()
                }
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun handleRemoteIce(candidate: JSONObject) {
        runCatching {
            pc?.addIceCandidate(
                IceCandidate(
                    candidate.optString("sdpMid"),
                    candidate.optInt("sdpMLineIndex", 0),
                    candidate.optString("candidate")
                )
            )
        }.onFailure { Log.w("AI_TAP", "addIceCandidate failed (skipped)", it) }
    }

    fun close() {
        closed = true
        runCatching { pc?.close() }
        pc = null
        synchronized(iceBuffer) { iceBuffer.clear() }
    }

    /** Serialize an IceCandidate the way the antAI server's aiortc side expects. */
    private fun IceCandidate.toJson(): JSONObject {
        val j = JSONObject()
            .put("sdpMid", sdpMid)
            .put("sdpMLineIndex", sdpMLineIndex)
            .put("candidate", sdp)
        // the SDK only exposes sdp/sdpMid/sdpMLineIndex; parse the rest from
        // the standard candidate line: candidate:FOUND COMP PROTO PRIO IP PORT
        // TYPE [raddr ..] [rport ..] [tcptype ..] [generation ..] [ufrag ..]
        val parts = sdp.split(" ").filter { it.isNotBlank() }
        if (parts.size >= 8 && parts[0].startsWith("candidate:")) {
            j.put("foundation", parts[0].substringAfter("candidate:"))
            j.put("component", parts[1].toIntOrNull() ?: 1)
            j.put("protocol", parts[2])
            j.put("priority", parts[3].toLongOrNull() ?: 0)
            j.put("ip", parts[4])
            j.put("port", parts[5].toIntOrNull() ?: 0)
            j.put("type", parts[6])
            var i = 7
            while (i < parts.size) {
                when (parts[i]) {
                    "raddr" -> { j.put("relatedAddress", parts.getOrNull(i + 1)); i += 2 }
                    "rport" -> {
                        j.put("relatedPort", parts.getOrNull(i + 1)?.toIntOrNull() ?: 0); i += 2
                    }
                    "tcptype" -> { j.put("tcpType", parts.getOrNull(i + 1)); i += 2 }
                    "generation" -> {
                        j.put("generation", parts.getOrNull(i + 1)?.toIntOrNull() ?: 0); i += 2
                    }
                    "ufrag" -> { j.put("usernameFragment", parts.getOrNull(i + 1)); i += 2 }
                    else -> i += 1
                }
            }
        }
        return j
    }
}
