package com.codewithkael.simplecall.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.remote.socket.SignalMessageModel
import com.codewithkael.simplecall.remote.socket.SignalMessageType
import com.codewithkael.simplecall.remote.socket.SignalMessageType.AcceptCall
import com.codewithkael.simplecall.remote.socket.SignalMessageType.Answer
import com.codewithkael.simplecall.remote.socket.SignalMessageType.EndCall
import com.codewithkael.simplecall.remote.socket.SignalMessageType.ICE
import com.codewithkael.simplecall.remote.socket.SignalMessageType.Offer
import com.codewithkael.simplecall.remote.socket.SignalMessageType.RejectCall
import com.codewithkael.simplecall.remote.socket.SignalMessageType.StartCall
import com.codewithkael.simplecall.remote.socket.SignalMessageType.UserOnline
import com.codewithkael.simplecall.remote.socket.SocketClient
import com.codewithkael.simplecall.data.MessagesRepository
import com.codewithkael.simplecall.remote.antai.AiInsight
import com.codewithkael.simplecall.remote.antai.AiSignals
import com.codewithkael.simplecall.remote.antai.AntaiClient
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.CallerVerification
import com.codewithkael.simplecall.remote.antai.DeepfakeAlert
import com.codewithkael.simplecall.remote.antai.IncidentItem
import com.codewithkael.simplecall.remote.antai.NormalizedResult
import com.codewithkael.simplecall.remote.antai.TranscriptEntry
import com.codewithkael.simplecall.remote.antai.VerifyOutcome
import com.codewithkael.simplecall.remote.antai.VoiceprintResult
import com.codewithkael.simplecall.ui.components.ProtectionMode
import com.codewithkael.simplecall.webrtc.AiTapEngine
import com.codewithkael.simplecall.utils.ConnectionState
import com.codewithkael.simplecall.utils.ConnectionState.CallingTarget
import com.codewithkael.simplecall.utils.ConnectionState.New
import com.codewithkael.simplecall.utils.ConnectionState.WaitingForCall
import com.codewithkael.simplecall.utils.Constants
import com.codewithkael.simplecall.utils.Constants.TIME_OUT_DURATION_MS
import com.codewithkael.simplecall.utils.Constants.getWebSocketUrl
import com.codewithkael.simplecall.utils.SignallingClient
import com.codewithkael.simplecall.utils.SimpleCallApplication
import com.codewithkael.simplecall.webrtc.MyPeerObserver
import com.codewithkael.simplecall.webrtc.RTCAudioManager
import com.codewithkael.simplecall.webrtc.RTCClient
import com.codewithkael.simplecall.webrtc.RTCClientImpl
import com.codewithkael.simplecall.webrtc.WebRTCFactory
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import com.codewithkael.simplecall.notifications.RiskNotificationManager
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class MainViewModel @Inject constructor(
    private val socketClient: SocketClient,
    private val signalSender: SignallingClient,
    private val webrtcFactory: WebRTCFactory,
    private val gson :Gson,
    private val antaiRest: AntaiRestClient,
    // Read-only here, and only for the verification event bridge below: the chat
    // socket lives in this singleton and is the socket the server addresses a
    // button-initiated verify.result to. Nothing in the call path uses it.
    private val messagesRepo: MessagesRepository,
    private val riskNotificationManager: RiskNotificationManager,
    application: Application
) : ViewModel() {

    var connectionState: MutableStateFlow<ConnectionState> = MutableStateFlow(New)
    private fun setConnectionState(state: ConnectionState) {
        connectionState.value = state
    }

    val eventState: MutableSharedFlow<String> = MutableSharedFlow(replay = 0)
    private var target: String = ""
    private var callTimeoutJob: Job? = null
    private var rtcClient: RTCClient? = null
    private var remoteSurface:SurfaceViewRenderer?=null
    private val rtcAudioManager by lazy { RTCAudioManager.create(application) }

    // ---------------- antAI real-time analysis tap ----------------
    // Live transcript (Whisper ASR on the tapped call audio) and the local
    // LLM's merged verdict/guidance state rendered by the in-call AI window.
    val transcriptState: MutableStateFlow<List<TranscriptEntry>> = MutableStateFlow(emptyList())
    val aiInsightState: MutableStateFlow<AiInsight?> = MutableStateFlow(null)

    // Deepfake pause-and-alert: non-null while the call is paused pending a
    // user decision ("End call" / "Resume anyway").
    val deepfakeAlertState: MutableStateFlow<DeepfakeAlert?> = MutableStateFlow(null)

    // Voiceprint cross-check of the live call audio against the peer's enrolled
    // voiceprint — the second, independent opinion offered on an AI-voice alert.
    val voiceprintState: MutableStateFlow<VoiceprintResult?> = MutableStateFlow(null)

    // "Verify caller": ask the contact they claim to be whether it's really them.
    val callerVerifyState: MutableStateFlow<CallerVerification?> = MutableStateFlow(null)

    private var antaiClient: AntaiClient? = null
    private var aiTapEngine: AiTapEngine? = null
    private var localSurfaceReady = false
    private var pendingTapTarget: String? = null

    // The server's analysis session for this call. Every verification endpoint is
    // keyed on it, so without it the in-call verify buttons cannot work.
    private var tapSessionKey: String? = null

    // call mode: "video" (default) or "voice" (audio-only, no camera/video)
    val callKind: MutableStateFlow<String> = MutableStateFlow("video")

    // user mic/camera preference (hoisted so the pause can reflect in the UI)
    private var userMicEnabled = true
    private var userCameraEnabled = true
    private var remoteMediaStream: MediaStream? = null

    // Signaling server host (LAN IP of the laptop running the Node backend). Saved on
    // the device so it survives restarts and never needs an app rebuild to change.
    private val prefs = application.getSharedPreferences("antai_call_prefs", Context.MODE_PRIVATE)
    val serverHost: MutableStateFlow<String> = MutableStateFlow(
        prefs.getString("server_host", Constants.DEFAULT_SERVER_HOST) ?: Constants.DEFAULT_SERVER_HOST
    )

    fun updateServerHost(host: String) {
        val cleaned = host.trim()
        serverHost.value = cleaned
        prefs.edit().putString("server_host", cleaned).apply()
    }

    val protectionMode: MutableStateFlow<ProtectionMode> = MutableStateFlow(ProtectionMode.ON_DEVICE)
    val lastIncident: MutableStateFlow<IncidentItem?> = MutableStateFlow(null)

    data class FileAnalysisUiState(
        val isAnalyzing: Boolean = false,
        val result: NormalizedResult? = null,
        val error: String? = null,
        val fileName: String? = null
    )
    val fileAnalysisState = MutableStateFlow(FileAnalysisUiState())

    init {
        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        // Bridge for the "Verify caller" outcome. The REST request is authenticated
        // as the phone/OTP user, so the server pushes its verify.result to THAT
        // user's socket — the chat socket — while the call tap is registered under a
        // separate auto-provisioned user row. Collecting it here is what lets the
        // in-call bar move off "pending" to confirmed / denied / timeout.
        viewModelScope.launch {
            messagesRepo.verifyOutcome.collect { outcome ->
                if (outcome != null) {
                    applyVerifyResult(outcome)
                    messagesRepo.clearVerifyOutcome()
                }
            }
        }

        // Auto-connect if server host is set, and query models / incidents
        if (serverHost.value.isNotBlank()) {
            connectSocket()
            refreshProtectionMode()
            loadLastIncident()
        }
    }

    fun refreshProtectionMode() {
        viewModelScope.launch {
            antaiRest.debugModels().fold(
                onSuccess = { models ->
                    val anyReady = models.values.any { it }
                    protectionMode.value = if (anyReady) ProtectionMode.SERVER_BACKED else ProtectionMode.ON_DEVICE
                },
                onFailure = {
                    protectionMode.value = if (connectionState.value !is New) ProtectionMode.ON_DEVICE else ProtectionMode.OFFLINE
                }
            )
        }
    }

    fun loadLastIncident() {
        viewModelScope.launch {
            antaiRest.listVerdicts().onSuccess { list ->
                lastIncident.value = list.maxByOrNull { it.createdAt }
            }
        }
    }

    fun analyzeAudioUri(context: Context, uri: Uri) {
        fileAnalysisState.value = FileAnalysisUiState(
            isAnalyzing = true,
            fileName = uri.lastPathSegment ?: "audio_sample"
        )
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read audio data")
                val mime = context.contentResolver.getType(uri) ?: "audio/*"
                val name = uri.lastPathSegment ?: "recording.wav"
                val scenario = context.getSharedPreferences("antai_settings", Context.MODE_PRIVATE)
                    .getString("scenario", "high_value_txn")
                antaiRest.analyzeAudioFile(bytes, name, mime, scenario = scenario)
            }.fold(
                onSuccess = { res ->
                    res.onSuccess { norm ->
                        fileAnalysisState.value = FileAnalysisUiState(isAnalyzing = false, result = norm)
                        loadLastIncident()
                    }.onFailure { e ->
                        fileAnalysisState.value = FileAnalysisUiState(isAnalyzing = false, error = e.message ?: "Analysis failed")
                    }
                },
                onFailure = { e ->
                    fileAnalysisState.value = FileAnalysisUiState(isAnalyzing = false, error = e.message ?: "Could not open audio file")
                }
            )
        }
    }

    fun clearFileAnalysis() {
        fileAnalysisState.value = FileAnalysisUiState()
    }

    fun connectSocket() {
        socketClient.init(getWebSocketUrl(serverHost.value, SimpleCallApplication.USER_ID),
            object : SocketClient.SocketCallback {
                override fun onRemoteSocketClientOpened() {
                    setConnectionState(WaitingForCall)
                    refreshProtectionMode()
                    loadLastIncident()
                }

                override fun onRemoteSocketClientClosed() {
                    // Connection dropped -> return to the server screen so the user can reconnect.
                    setConnectionState(New)
                    protectionMode.value = ProtectionMode.OFFLINE
                }

                override fun onRemoteSocketClientConnectionError(e: Exception?) {
                    setConnectionState(New)
                    protectionMode.value = ProtectionMode.OFFLINE
                    viewModelScope.launch {
                        eventState.emit(
                            "Can't reach server at ${serverHost.value}:${Constants.SIGNALING_PORT}. " +
                                "Check the IP and that the backend is running."
                        )
                    }
                }

                override fun onRemoteSocketClientNewMessage(message: SignalMessageModel) {
                    handleIncomingMessage(message)
                }
            })
    }

    private fun handleIncomingMessage(message: SignalMessageModel) {
        when (message.type) {
            UserOnline -> handleUserOnline(message)
            SignalMessageType.UserOffline -> handleUserOffline(message)
            StartCall -> handleStartCall(message)
            AcceptCall -> handleAcceptCall(message)
            RejectCall -> handleRejectCall()
            Offer -> handleOffer(message)
            Answer -> handleAnswer(message)
            ICE -> handleICE(message)
            EndCall -> handleEndCall()
            else -> {}
        }
    }

    private fun handleEndCall() {
        finishCall()
    }

    private fun handleICE(message: SignalMessageModel) {
        runCatching {
            val iceCandidate = gson.fromJson(message.data.toString(),IceCandidate::class.java)
            rtcClient?.onIceCandidateReceived(iceCandidate)
        }
    }

    private fun handleAnswer(message: SignalMessageModel) {
        val sessionDescription =
            SessionDescription(SessionDescription.Type.ANSWER,message.data.toString())
        rtcClient?.onRemoteSessionReceived(sessionDescription)
    }

    private fun handleOffer(message: SignalMessageModel) {
        this.target = message.sender
        val sessionDescription =
            SessionDescription(SessionDescription.Type.OFFER,message.data.toString())
        setupRTCConnection(message.sender)?.also {
            it.onRemoteSessionReceived(sessionDescription)
            it.answer(message.sender)
        }
    }

    private fun handleAcceptCall(message: SignalMessageModel) {
        setConnectionState(ConnectionState.OnCall(message.sender))
        pendingTapTarget = message.sender
        ensureLocalMedia()
        maybeStartAiTap()
        setupRTCConnection(message.sender)?.offer(message.sender)
    }

    private fun handleRejectCall() {
        setConnectionState(WaitingForCall)
        viewModelScope.launch {
            eventState.emit("Call Rejected")
        }
        stopAiTap()
        webrtcFactory.onDestroy()
    }

    private fun handleStartCall(message: SignalMessageModel) {
        if (connectionState.value is ConnectionState.OnCall) {
            signalSender.sendRejectCall(message.sender)
            return
        }
        // the caller signals the call kind in the StartCall `data` field
        callKind.value = message.data?.toString()?.takeIf { it == "voice" } ?: "video"
        setConnectionState(ConnectionState.ReceivedCall(message.sender))
    }

    private fun handleUserOnline(message: SignalMessageModel) {
        setConnectionState(CallingTarget(message.target))
        startCallWithTime(message.target)
    }

    private fun startCallWithTime(target: String) {
        this.target = target
        signalSender.sendStartCallSignal(target, callKind.value)
        callTimeoutJob?.cancel()
        callTimeoutJob = viewModelScope.launch {
            delay(TIME_OUT_DURATION_MS)
            if (connectionState.value is CallingTarget) {
                setConnectionState(WaitingForCall)
                stopAiTap()
                webrtcFactory.onDestroy()
            }
        }
    }

    private fun handleUserOffline(message: SignalMessageModel) {
        setConnectionState(ConnectionState.UserOffline(message.target))
        viewModelScope.launch {
            eventState.emit("${message.target} is Offline")
        }
    }

    fun findUser(target: String, isVoice: Boolean = false) {
        if (target == SimpleCallApplication.USER_ID) {
            viewModelScope.launch {
                eventState.emit("You cannot call yourself")
            }
            return
        }
        callKind.value = if (isVoice) "voice" else "video"
        setConnectionState(WaitingForCall)
        //send signal to the server
        signalSender.findUser(target)
    }

    fun incomingCallDismissed() {
        setConnectionState(WaitingForCall)
        stopAiTap()
        webrtcFactory.onDestroy()
    }

    fun acceptIncomingCall(target: String) {
        setConnectionState(ConnectionState.OnCall(target))
        pendingTapTarget = target
        ensureLocalMedia()
        maybeStartAiTap()
        signalSender.sendAcceptCall(target)
    }

    fun rejectIncomingCall(target: String) {
        setConnectionState(WaitingForCall)
        signalSender.sendRejectCall(target)

    }

    fun onSurfaceLocalReady(localRenderer: SurfaceViewRenderer) {
        webrtcFactory.prepareLocalStream(localRenderer)
        localSurfaceReady = true
        maybeStartAiTap()
    }

    // ------------------------------------------------------------ antAI tap
    private fun ensureLocalMedia() {
        // Voice calls have no local video surface, so the mic must be set up
        // here explicitly (video calls do it via onSurfaceLocalReady).
        if (callKind.value == "voice") {
            webrtcFactory.prepareAudioOnly()
        }
    }

    private fun maybeStartAiTap() {
        if (aiTapEngine != null || antaiClient != null) return          // already running
        val peer = pendingTapTarget ?: return
        if (connectionState.value !is ConnectionState.OnCall) return
        if (peer.isBlank() || peer == SimpleCallApplication.USER_ID) return
        startAiTap(peer)
    }

    private fun startAiTap(peer: String) {
        transcriptState.value = emptyList()
        aiInsightState.value = null
        voiceprintState.value = null
        callerVerifyState.value = null
        tapSessionKey = null
        val url = Constants.getAntaiTapUrl(serverHost.value, SimpleCallApplication.USER_ID)
        val client = AntaiClient(url, object : AntaiClient.Callback {
            override fun onOpened() {
                if (aiTapEngine != null) return
                val engine = AiTapEngine(webrtcFactory, object : AiTapEngine.Controller {
                    override fun onTapOfferReady(sdp: String) {
                        antaiClient?.sendTapStart(peer, callKind.value, sdp)
                    }

                    override fun onLocalIceCandidate(candidateJson: org.json.JSONObject) {
                        antaiClient?.sendTapIce(candidateJson)
                    }
                })
                aiTapEngine = engine
                engine.start()
            }

            override fun onClosed() {
                // socket gone: clear refs so a later surface/state event may retry
                runCatching { aiTapEngine?.close() }
                aiTapEngine = null
                antaiClient = null
            }

            override fun onError(e: Exception?) {
                viewModelScope.launch {
                    eventState.emit(
                        "antAI offline at ${serverHost.value}:${Constants.ANTAI_PORT} — " +
                            "call continues without AI protection"
                    )
                }
            }

            override fun onTapStarted(sessionKey: String) {
                // Needed by every verification endpoint; without it the in-call
                // "cross-check" / "verify caller" buttons have nothing to key on.
                tapSessionKey = sessionKey.ifBlank { null }
            }
            override fun onTapAnswer(sdp: String) { aiTapEngine?.handleAnswer(sdp) }
            override fun onTapIce(candidate: org.json.JSONObject) {
                aiTapEngine?.handleRemoteIce(candidate)
            }

            override fun onTranscript(entry: TranscriptEntry) {
                transcriptState.value = (transcriptState.value + entry).takeLast(100)
            }

            override fun onVerdict(band: String, risk: Double, verdict: String,
                                   why: String, action: String) {
                val cur = aiInsightState.value
                // copy(), not a fresh AiInsight(): building a new object here threw
                // away every detector value from the last signals.update, so the
                // window blanked its scores to 0% after each verdict until the next
                // signals push arrived. The verdict only owns these five fields.
                aiInsightState.value = (cur ?: AiInsight()).copy(
                    band = band, riskScore = risk, verdict = verdict, why = why,
                    action = action
                )
            }

            override fun onGuidance(guidance: String, risk: Double) {
                val cur = aiInsightState.value
                aiInsightState.value = (cur ?: AiInsight()).copy(
                    guidance = guidance,
                    riskScore = if (risk > 0) risk else cur?.riskScore ?: 0.0
                )
            }

            override fun onSignals(signals: AiSignals) {
                // P1.7: Post a status-bar notification if risk crosses the verify
                // threshold while the user is backgrounded.
                riskNotificationManager.onRiskUpdate(
                    risk = signals.risk,
                    band = signals.band,
                    isAppForegrounded = SimpleCallApplication.isForegrounded
                )
                val cur = aiInsightState.value
                aiInsightState.value = (cur ?: AiInsight()).copy(
                    band = signals.band,
                    riskScore = signals.risk,
                    voiceDeepfake = signals.voiceDeepfake,
                    voicePerModel = signals.voicePerModel,
                    voiceSources = signals.voiceSources,
                    videoDeepfake = signals.videoDeepfake,
                    videoVotes = signals.videoVotes,
                    videoAgreement = signals.videoAgreement,
                    scamProb = signals.scamProb,
                    scamType = signals.scamType,
                    deviation = signals.deviation,
                    urgency = signals.urgency,
                    requestType = signals.requestType,
                    requestDetected = signals.requestDetected,
                    lipsyncMismatch = signals.lipsyncMismatch,
                    identityMismatch = signals.identityMismatch,
                    enginesReady = signals.enginesReady,
                    audioSegments = signals.audioSegments,
                    asrFailures = signals.asrFailures
                )
            }

            override fun onDeepfakeAlert(alert: DeepfakeAlert) {
                pauseForDeepfakeAlert(alert)
            }

            override fun onFreeze(requestType: String, message: String, reason: String) {
                val cur = aiInsightState.value
                val text = message.ifBlank { reason }
                aiInsightState.value = (cur ?: AiInsight()).copy(
                    band = "critical",
                    guidance = if (text.isBlank()) "Request held: $requestType" else text
                )
                viewModelScope.launch { eventState.emit("antAI froze a $requestType request") }
            }

            override fun onVerifyPrompt(raw: String) {
                viewModelScope.launch {
                    eventState.emit("antAI suggests verifying this caller with a trusted contact")
                }
            }

            override fun onVoiceprintResult(result: VoiceprintResult) {
                voiceprintState.value = result
            }

            override fun onVerifyResult(outcome: VerifyOutcome) {
                applyVerifyResult(outcome)
            }

            override fun onReportReady(title: String) {
                viewModelScope.launch { eventState.emit("antAI report ready: $title") }
            }
        })
        antaiClient = client
        client.connect()
    }

    private fun stopAiTap() {
        pendingTapTarget = null
        localSurfaceReady = false
        runCatching { antaiClient?.sendTapStop() }
        runCatching { aiTapEngine?.close() }
        runCatching { antaiClient?.close() }
        aiTapEngine = null
        antaiClient = null
        // verification state belongs to one call only — never let a result from a
        // previous call bleed into the next one's UI
        tapSessionKey = null
        voiceprintState.value = null
        callerVerifyState.value = null
    }

    // -------------------------------------------- in-call identity verification
    /**
     * Cross-check the caller's voice against the saved voiceprint of the number
     * they are calling from. This is a different question from "is this voice
     * synthetic?" — it asks "is this the person who owns this number?" — so a
     * clear answer here either corroborates the AI-voice alert or defuses it.
     */
    fun crossVerifyVoiceprint() {
        val session = tapSessionKey
        val peer = pendingTapTarget
        if (session == null || peer.isNullOrBlank()) {
            voiceprintState.value = VoiceprintResult(
                ok = false, reason = "no_session",
                hint = "antAI isn't analysing this call yet — give it a few seconds."
            )
            return
        }
        voiceprintState.value = VoiceprintResult(checking = true)
        viewModelScope.launch {
            antaiRest.crossVerifyVoiceprint(session, peer)
                .onSuccess { voiceprintState.value = it }
                .onFailure { e ->
                    voiceprintState.value = VoiceprintResult(
                        ok = false, reason = "request_failed",
                        hint = e.message ?: "Could not reach the antAI server."
                    )
                }
        }
    }

    fun dismissVoiceprintResult() { voiceprintState.value = null }

    /**
     * Ask the contact the caller claims to be to confirm it's really them.
     * [claimPhone] defaults to the number on the call; pass a different number when
     * the caller claims to be someone else ("it's your son, I lost my phone").
     */
    fun requestCallerVerify(claimPhone: String? = null) {
        val session = tapSessionKey
        val phone = (claimPhone ?: pendingTapTarget)?.trim()
        if (session == null || phone.isNullOrBlank()) {
            callerVerifyState.value = CallerVerification(
                requested = true,
                error = "antAI isn't analysing this call yet — give it a few seconds."
            )
            return
        }
        callerVerifyState.value = CallerVerification(
            requested = true, peerPhone = phone, sending = true
        )
        viewModelScope.launch {
            antaiRest.requestCallerVerify(session, phone)
                .onSuccess { r ->
                    callerVerifyState.value = CallerVerification(
                        requested = true, peerPhone = phone, sending = false,
                        promptSent = r.promptSent, delivered = r.delivered,
                        state = if (r.promptSent) "pending" else "",
                        message = if (r.promptSent) {
                            if (r.delivered) "Waiting for them to confirm…"
                            else "Asked, but their phone may be offline right now."
                        } else "",
                        error = if (r.promptSent) "" else r.hint.ifBlank { r.reason }
                    )
                }
                .onFailure { e ->
                    callerVerifyState.value = CallerVerification(
                        requested = true, peerPhone = phone, sending = false,
                        error = e.message ?: "Could not reach the antAI server."
                    )
                }
        }
    }

    fun dismissCallerVerify() { callerVerifyState.value = null }

    /**
     * Single place where a `verify.result` becomes in-call UI, regardless of which
     * socket carried it.
     *
     * Both sockets can deliver this event: the server addresses a button-initiated
     * result to the phone/OTP user (chat socket) and an automatically triggered one
     * to the tap user, and those are two different DB rows. Funnelling both through
     * here is what stops the bar sitting on "Waiting for them to confirm…" forever.
     */
    private fun applyVerifyResult(outcome: VerifyOutcome) {
        // Ignore results that arrive after the call ended — the state is cleared in
        // stopAiTap() precisely so one call's answer can't decorate the next call.
        if (tapSessionKey == null) return
        val cur = callerVerifyState.value ?: CallerVerification(requested = true)
        callerVerifyState.value = cur.copy(
            sending = false,
            promptSent = true,
            // Only the "pending" event reports delivery. A later confirmed/denied
            // omits it, and defaulting to false there would tell the user "their
            // phone may be offline" moments after that phone answered.
            delivered = outcome.delivered ?: cur.delivered,
            state = outcome.state,
            answer = outcome.verified,
            message = outcome.message,
            // a fresh server answer supersedes any earlier local failure text
            error = ""
        )
        // A denial is the strongest possible evidence of impersonation, so surface
        // it with the same weight as a detector alert instead of leaving it as a
        // line of text on a button.
        if (outcome.state == "denied") {
            pauseForDeepfakeAlert(
                DeepfakeAlert(
                    source = "identity",
                    title = "Your contact says this is NOT them",
                    message = outcome.message.ifBlank {
                        "The person you think you're talking to has confirmed " +
                            "they are not on this call. Hang up now. Do not " +
                            "send money or share any codes."
                    }
                )
            )
        }
        viewModelScope.launch {
            if (outcome.message.isNotBlank()) eventState.emit(outcome.message)
        }
    }

    // ------------------------------------------------ deepfake pause + popup
    // "Pause the call": freeze what the user sees/hears (disable remote
    // playback) and show a blocking modal — but KEEP the local mic/camera and
    // the analysis tap running so the transcript / models / LLM never stop
    // analyzing the ongoing call. The user must consciously choose to resume.
    private fun pauseForDeepfakeAlert(alert: DeepfakeAlert) {
        if (deepfakeAlertState.value != null) return   // already paused
        setRemoteMediaEnabled(false)
        deepfakeAlertState.value = alert
    }

    fun resumeAfterDeepfakeAlert() {
        setRemoteMediaEnabled(true)
        deepfakeAlertState.value = null
    }

    fun endCallFromDeepfakeAlert() {
        deepfakeAlertState.value = null
        endCall()
    }

    private fun setRemoteMediaEnabled(enabled: Boolean) {
        runCatching {
            remoteMediaStream?.let { s ->
                s.videoTracks?.forEach { it.setEnabled(enabled) }
                s.audioTracks?.forEach { it.setEnabled(enabled) }
            }
        }
    }

    private fun setupRTCConnection(target: String): RTCClient? {
        runCatching {
            rtcClient?.onDestroy()
        }
        rtcClient = null
        rtcClient = webrtcFactory.createRTCClient(object : MyPeerObserver(){
            override fun onIceCandidate(p0: IceCandidate?) {
                super.onIceCandidate(p0)
                p0?.let { rtcClient?.onLocalIceCandidateGenerated(it,target) }
            }

            override fun onAddStream(p0: MediaStream?) {
                super.onAddStream(p0)
                p0?.let { stream ->
                    remoteMediaStream = stream
                    runCatching {
                        remoteSurface?.let { remote->
                            stream.videoTracks[0]?.addSink(remote)
                        }
                    }
                }
            }
        },
            object :RTCClientImpl.TransferDataToServerCallBack {
                override fun onTransferEventToSocket(data: SignalMessageModel) {
                    socketClient.sendDataToHost(data)
                }
            })
        return rtcClient
    }

    override fun onCleared() {
        super.onCleared()
        stopAiTap()
        remoteSurface?.release()
        remoteSurface = null
        webrtcFactory.onDestroy()
        socketClient.close()
    }

    fun onSurfaceRemoteReady(remoteRenderer: SurfaceViewRenderer) {
        this.remoteSurface = remoteRenderer
        webrtcFactory.initSurfaceView(remoteRenderer)
    }

    fun switchCamera() {
        webrtcFactory.switchCamera()
    }

    fun endCall() {
        signalSender.sendEndCall(target)
        finishCall()
    }

    private fun finishCall(){
        stopAiTap()
        // P1.7: dismiss any lingering risk alert when the call ends
        riskNotificationManager.dismissRiskNotification()
        deepfakeAlertState.value = null
        remoteMediaStream = null
        userMicEnabled = true
        userCameraEnabled = true
        rtcClient?.onDestroy()
        rtcClient = null
        webrtcFactory.onDestroy()
        setConnectionState(WaitingForCall)
    }

    fun toggleMic(enabled: Boolean) {
        userMicEnabled = enabled
        webrtcFactory.toggleMic(enabled)
    }

    fun toggleCamera(enabled: Boolean){
        userCameraEnabled = enabled
        webrtcFactory.toggleCamera(enabled)
    }

    fun toggleSpeaker(speaker: Boolean) {
        if (speaker){
            rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        }else{
            rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
        }
    }
}