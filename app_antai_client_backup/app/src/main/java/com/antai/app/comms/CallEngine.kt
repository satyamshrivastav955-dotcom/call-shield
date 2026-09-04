package com.antai.app.comms

import android.content.Context
import android.util.Log
import com.antai.app.realtime.WsClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStatsReport
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * WebRTC engine that works with the antAI server SFU:
 *   caller: offer -> server answers -> call.answer {session_key, answer}
 *   callee: call.incoming {session_key, offer} -> accept -> call.accept {answer}
 * ICE candidates flow both ways over the WS control channel.
 */
class CallEngine(
    private val context: Context,
    private val ws: WsClient,
    private val isVideo: Boolean,
) {
    var sessionKey: String? = null
    var isCaller = false
    var onConnected: (() -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onRemoteVideoReady: ((SurfaceViewRenderer) -> Unit)? = null

    private var iceBuffer = mutableListOf<JSONObject>()
    private var iceGatheringDone = false
    private var onGatheringComplete: (() -> Unit)? = null

    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var eglBase: EglBase? = null
    private var localVideo: SurfaceViewRenderer? = null
    private var remoteVideo: SurfaceViewRenderer? = null
    // Hold the remote video track so attachment is order-independent: whichever
    // of onAddTrack / attachRemoteVideo happens second wires the sink. This stops
    // the remote frame from being silently dropped if the track arrives before
    // the renderer is attached (or the Activity is recreated mid-call).
    private var remoteVideoTrack: VideoTrack? = null

    fun initialize() {
        val egl = EglBase.create()
        eglBase = egl
        val init = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false).createInitializationOptions()
        PeerConnectionFactory.initialize(init)

        val adm = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(
                org.webrtc.DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(
                org.webrtc.DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()

        setSpeakerOn(true)
    }

    fun setSpeakerOn(enable: Boolean) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val speakerDevice = devices.firstOrNull { 
                    it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                }
                val earpieceDevice = devices.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                if (enable && speakerDevice != null) {
                    am.setCommunicationDevice(speakerDevice)
                } else if (!enable && earpieceDevice != null) {
                    am.setCommunicationDevice(earpieceDevice)
                } else {
                    am.clearCommunicationDevice()
                }
            }
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = enable
        }
    }

    fun attachLocalVideo(view: SurfaceViewRenderer?) {
        localVideo = view
        if (view != null) {
            view.setMirror(true)
            view.setZOrderMediaOverlay(true)
            view.setEnableHardwareScaler(true)
            view.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            view.init(eglBase?.eglBaseContext, null)
            videoTrack?.addSink(view)
        }
    }

    fun attachRemoteVideo(view: SurfaceViewRenderer?) {
        remoteVideo = view
        if (view != null) {
            view.setMirror(false)
            view.setEnableHardwareScaler(true)
            view.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            view.init(eglBase?.eglBaseContext, null)
            // If the remote track already arrived (onAddTrack ran first), wire it now.
            remoteVideoTrack?.let { t ->
                Log.d(TAG, "attachRemoteVideo: remote track already present, attaching sink")
                view.post { runCatching { t.addSink(view) } }
            }
        }
    }

    private fun createPeerConnection(): PeerConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(listOf())
        // P1-G FIX: two STUN servers + TURN for cross-network (symmetric NAT) traversal.
        // Replace TURN_URL / TURN_USER / TURN_PASS with real credentials before deploy.
        val iceServers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        val turnUrl = TURN_URL
        if (turnUrl.isNotBlank()) {
            iceServers.add(
                PeerConnection.IceServer.builder(turnUrl)
                    .setUsername(TURN_USER)
                    .setPassword(TURN_PASS)
                    .createIceServer()
            )
        }
        rtcConfig.iceServers = iceServers
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        // GATHER_ONCE: gathering finishes -> waitGathering() completes and the
        // SDP is self-contained; any stragglers still trickle via call.ice.
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        

        val pc = factory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val payload = JSONObject()
                    .put("session_key", sessionKey)
                    .put("candidate", candidate.toJson())
                if (sessionKey == null) {
                    iceBuffer.add(payload)   // server can't route yet - hold it
                } else {
                    ws.send("call.ice", payload)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.CONNECTED) onConnected?.invoke()
                if (state == PeerConnection.IceConnectionState.FAILED) onEnded?.invoke()
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringDone = true
                    onGatheringComplete?.invoke()
                }
            }
            override fun onAddStream(stream: MediaStream) {
                stream.videoTracks.firstOrNull()?.let { track ->
                    remoteVideo?.let { track.addSink(it) }
                }
            }

            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {
                val track = receiver.track() ?: run {
                    Log.w(TAG, "onAddTrack: receiver has no track")
                    return
                }
                // P1-D FIX: always explicitly enable the remote track.
                // Tracks may arrive disabled; leaving them disabled = silence / black video.
                track.setEnabled(true)
                Log.d(TAG, "onAddTrack kind=${track.kind()} enabled=${track.enabled()} " +
                        "id=${track.id()}")
                when (track.kind()) {
                    "video" -> (track as? VideoTrack)?.let { t ->
                        // Remember the track so attachRemoteVideo can (re)wire it even
                        // if the renderer isn't attached yet — order-independent.
                        remoteVideoTrack = t
                        remoteVideo?.let { view ->
                            Log.d(TAG, "onAddTrack: attaching remote video to surface")
                            view.post {
                                runCatching { t.addSink(view) }
                            }
                        }
                    }
                    "audio" -> (track as? AudioTrack)?.let { a ->
                        a.setEnabled(true)
                        Log.d(TAG, "onAddTrack: remote audio track active and enabled")
                    }
                }
            }

            override fun onRemoveTrack(receiver: org.webrtc.RtpReceiver) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
            override fun onTrack(track: org.webrtc.RtpTransceiver) {}
        })!!
        this.pc = pc

        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        audioSource = factory!!.createAudioSource(audioConstraints)
        audioTrack = factory!!.createAudioTrack("audio0", audioSource)
        pc.addTrack(audioTrack, listOf("audio"))

        if (isVideo) {
            videoCapturer = createCameraCapturer()
            videoSource = factory!!.createVideoSource(false)
            val helper = SurfaceTextureHelper.create("captureThread", eglBase!!.eglBaseContext)
            videoCapturer?.initialize(helper, context, videoSource!!.capturerObserver)
            videoTrack = factory!!.createVideoTrack("video0", videoSource)
            localVideo?.let { videoTrack?.addSink(it) }
            pc.addTrack(videoTrack, listOf("video"))
        }
        return pc
    }

    private suspend fun waitGathering(timeoutMs: Long = 5000) {
        val start = System.currentTimeMillis()
        while (!iceGatheringDone && System.currentTimeMillis() - start < timeoutMs) {
            kotlinx.coroutines.delay(50)
        }
    }

    private fun flushIce() {
        if (sessionKey == null) return
        val pending = iceBuffer
        iceBuffer = mutableListOf()
        for (p in pending) {
            p.put("session_key", sessionKey)
            ws.send("call.ice", p)
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Log.d(TAG, "Using Camera2Enumerator")
            Camera2Enumerator(context)
        } else {
            Log.d(TAG, "Using Camera1Enumerator")
            Camera1Enumerator(false)
        }
        val names = enumerator.deviceNames
        val front = names.firstOrNull { enumerator.isFrontFacing(it) }
        val back = names.firstOrNull { enumerator.isBackFacing(it) }
        val target = front ?: back ?: names.firstOrNull() ?: return null
        Log.d(TAG, "Creating camera capturer for device: $target")
        return enumerator.createCapturer(target, null)
    }

    fun startCall(calleePhone: String, kind: String, onReady: (JSONObject) -> Unit) {
        Log.d(TAG, "startCall -> callee: $calleePhone, kind: $kind")
        isCaller = true
        iceGatheringDone = false
        val pc = createPeerConnection()
        startCapturing()
        pc.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                Log.d(TAG, "createOffer success, setting local description")
                pc.setLocalDescription(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onSetSuccess() {
                        GlobalScope.launch {
                            waitGathering()   // SDP must carry the candidates
                            val sdpStr = pc.localDescription?.description ?: desc.description
                            Log.d(TAG, "Sending call.start with gathered SDP (length=${sdpStr.length})")
                            ws.send("call.start", JSONObject()
                                .put("callee_phone", calleePhone)
                                .put("kind", kind)
                                .put("offer", JSONObject()
                                    .put("sdp", sdpStr).put("type", "offer")))
                        }
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "setLocalDescription failure: $error")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "setLocalDescription onSetFailure: $error")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failure: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "createOffer onSetFailure: $error")
            }
        }, offerConstraints())
    }

    fun acceptIncoming(sessionKey: String, offer: JSONObject) {
        Log.d(TAG, "acceptIncoming -> sessionKey: $sessionKey")
        this.sessionKey = sessionKey
        isCaller = false
        flushIce()
        iceGatheringDone = false
        val pc = createPeerConnection()
        startCapturing()
        val sdp = SessionDescription(
            SessionDescription.Type.OFFER, offer.optString("sdp"))
        pc.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "setRemoteDescription success, creating answer")
                pc.createAnswer(object : org.webrtc.SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc == null) return
                        Log.d(TAG, "createAnswer success, setting local description")
                        pc.setLocalDescription(object : org.webrtc.SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription?) {}
                            override fun onSetSuccess() {
                                GlobalScope.launch {
                                    waitGathering()  // answer SDP carries candidates
                                    val sdpStr = pc.localDescription?.description ?: desc.description
                                    Log.d(TAG, "Sending call.accept with gathered SDP (length=${sdpStr.length})")
                                    ws.send("call.accept", JSONObject()
                                        .put("session_key", sessionKey)
                                        .put("answer", JSONObject()
                                            .put("sdp", sdpStr)
                                            .put("type", "answer")))
                                }
                            }

                            override fun onCreateFailure(error: String?) {
                                Log.e(TAG, "acceptIncoming setLocalDescription failure: $error")
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "acceptIncoming setLocalDescription onSetFailure: $error")
                            }
                        }, desc)
                    }

                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "createAnswer failure: $error")
                    }
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "createAnswer onSetFailure: $error")
                    }
                }, offerConstraints())
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "acceptIncoming setRemoteDescription failure: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "acceptIncoming setRemoteDescription onSetFailure: $error")
            }
        }, sdp)
    }

    fun handleAnswer(sessionKey: String, answer: JSONObject) {
        Log.d(TAG, "handleAnswer -> sessionKey: $sessionKey")
        this.sessionKey = sessionKey
        flushIce()  // release the candidates buffered before the answer arrived
        val sdp = SessionDescription(
            SessionDescription.Type.ANSWER, answer.optString("sdp"))
        pc?.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "handleAnswer setRemoteDescription success ✓")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "handleAnswer setRemoteDescription failure: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "handleAnswer setRemoteDescription onSetFailure: $error")
            }
        }, sdp)
    }

    fun handleIce(candidateJson: JSONObject) {
        val cand = candidateJson.optJSONObject("candidate") ?: return
        Log.d(TAG, "handleIce candidate: ${cand.optString("candidate")}")
        val candidate = IceCandidate(
            cand.optString("sdpMid"),
            cand.optInt("sdpMLineIndex", 0),
            cand.optString("candidate"))
        pc?.addIceCandidate(candidate)
    }

    fun toggleMute(): Boolean {
        val nowEnabled = audioTrack?.enabled() ?: true
        audioTrack?.setEnabled(!nowEnabled)
        return audioTrack?.enabled() ?: true
    }

    fun toggleSpeaker(enable: Boolean) {
        setSpeakerOn(enable)
    }

    fun switchCamera(): Boolean {
        val capturer = videoCapturer
        if (capturer is CameraVideoCapturer) {
            runCatching { capturer.switchCamera(null) }.onFailure { return false }
            return true
        }
        return false
    }

    fun startCapturing() {
        val capturer = videoCapturer ?: return
        try {
            capturer.startCapture(640, 480, 30)
            Log.d(TAG, "startCapture: 640x480@30fps")
        } catch (e: Exception) {
            Log.w(TAG, "startCapture 640x480 failed: ${e.message}")
            try {
                capturer.startCapture(320, 240, 30)
            } catch (e2: Exception) {
                Log.e(TAG, "startCapture fallback failed: ${e2.message}")
            }
        }
    }

    fun hangup() {
        if (sessionKey != null) ws.send("call.end", JSONObject().put("session_key", sessionKey))
        release()
    }

    fun decline() {
        if (sessionKey != null) ws.send("call.decline", JSONObject().put("session_key", sessionKey))
        release()
    }

    fun release() {
        runCatching { pc?.close() }
        runCatching { videoCapturer?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { localVideo?.release() }
        runCatching { remoteVideo?.release() }
        runCatching { eglBase?.release() }
        pc = null
        remoteVideoTrack = null
        iceBuffer = mutableListOf()
        iceGatheringDone = false
    }

    private fun offerConstraints(): MediaConstraints {
        val c = MediaConstraints()
        c.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (isVideo) c.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        return c
    }

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
                        j.put("relatedPort", parts.getOrNull(i + 1)?.toIntOrNull() ?: 0)
                        i += 2
                    }
                    "tcptype" -> { j.put("tcpType", parts.getOrNull(i + 1)); i += 2 }
                    "generation" -> {
                        j.put("generation", parts.getOrNull(i + 1)?.toIntOrNull() ?: 0)
                        i += 2
                    }
                    "ufrag" -> { j.put("usernameFragment", parts.getOrNull(i + 1)); i += 2 }
                    else -> i += 1
                }
            }
        }
        return j
    }

    companion object {
        private const val TAG = "CallEngine"

        // USB-only media relay. adb reverse only tunnels TCP, and WebRTC media is
        // UDP, so over a pure-USB link the media is relayed through a local coturn
        // TURN server on the computer, reached over the adb-reversed port:
        //     adb reverse tcp:3478 tcp:3478   (usb_setup.bat does this)
        // On the phone, 127.0.0.1:3478 => the computer's coturn via that tunnel.
        // Non-forcing: this is ADDED alongside the STUN servers above, so a
        // same-Wi-Fi call still uses host/STUN candidates; if coturn isn't running
        // the TURN allocation just fails and is skipped. Credentials must match
        // server/turnserver.conf and the ice: section of server/config.yaml.
        const val TURN_URL  = "turn:127.0.0.1:3478?transport=tcp"
        const val TURN_USER = "antai"   // TURN username (see turnserver.conf)
        const val TURN_PASS = "antaipass"   // TURN credential (see turnserver.conf)
    }
}