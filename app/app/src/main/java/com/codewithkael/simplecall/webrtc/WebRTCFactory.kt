package com.codewithkael.simplecall.webrtc

import android.app.Application
import android.content.Context
import com.codewithkael.simplecall.utils.SimpleCallApplication
import com.google.gson.Gson
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCFactory @Inject constructor(
    private val application: Application, private val gson: Gson
) {
    private val eglBaseContext = EglBase.create().eglBaseContext
    private val peerConnectionFactory by lazy { createPeerConnectionFactory() }
    private var videoCapture: CameraVideoCapturer? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private var localVideoTrack: VideoTrack? = null
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private var localAudioTrack: AudioTrack? = null
    private val streamId = "${SimpleCallApplication.USER_ID}_stream"
    private var localStream: MediaStream? = null
    private var withVideo = true

    //add your turn servers here, if you wanna know how to create it, watch this series :
    //https://youtube.com/playlist?list=PLFelST8t9nqgCpdpCetHqA16CojSKbT2Q&si=JGx8lJQcyv6c4d9r

    private val iceServer = listOf<IceServer>(
        IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
        IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    init {
        initPeerConnectionFactory(application)
    }

    private fun initPeerConnectionFactory(context: Context) {
        val option = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true).setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(option)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder().setVideoDecoderFactory(
            DefaultVideoDecoderFactory(eglBaseContext)
        ).setVideoEncoderFactory(
            DefaultVideoEncoderFactory(eglBaseContext, true, true)
        ).setOptions(PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = false
        }).createPeerConnectionFactory()
    }


    fun prepareLocalStream(localRenderer: SurfaceViewRenderer) {
        withVideo = true
        initSurfaceView(localRenderer)
        startLocalMedia(localRenderer)
    }

    /** Voice-only call: mic only, no camera. Safe to call multiple times. */
    fun prepareAudioOnly() {
        withVideo = false
        if (localAudioTrack != null) return
        startLocalMedia(null)
    }

    private fun startLocalMedia(surface: SurfaceViewRenderer?) {
        localAudioTrack =
            peerConnectionFactory.createAudioTrack(streamId + "_audio", localAudioSource)
        localStream = peerConnectionFactory.createLocalMediaStream(streamId)
        localStream?.addTrack(localAudioTrack)
        if (withVideo && surface != null) {
            val surfaceTextureHelper =
                SurfaceTextureHelper.create(Thread.currentThread().name, eglBaseContext)
            videoCapture = getVideoCapture()
            videoCapture?.initialize(
                surfaceTextureHelper, surface.context, localVideoSource.capturerObserver
            )
            videoCapture?.startCapture(720, 480, 10)
            localVideoTrack =
                peerConnectionFactory.createVideoTrack(streamId + "_video", localVideoSource)
            localVideoTrack?.addSink(surface)
            localStream?.addTrack(localVideoTrack)
        }
    }

    private fun getVideoCapture(): CameraVideoCapturer {
        return Camera2Enumerator(application).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }
    }

    fun initSurfaceView(viewRenderer: SurfaceViewRenderer) {
        viewRenderer.run {
            setMirror(true)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
    }

    // ------------------------------------------------------------ AI tap
    // The antAI analysis tap re-attaches the SAME capture tracks to a second,
    // send-only PeerConnection: no extra camera/mic open, and mute/camera
    // toggles apply to both legs automatically (shared track enabled-state).
    fun getLocalAudioTrack(): AudioTrack? = localAudioTrack
    fun getLocalVideoTrack(): VideoTrack? = localVideoTrack

    fun createTapPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        // LAN analysis leg: host candidates are enough; empty ICE list keeps
        // gathering fast. The real call's PC is untouched and unaffected.
        return peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(listOf()), observer
        )
    }

    fun onDestroy(){
        runCatching {
            videoCapture?.stopCapture()
            videoCapture?.dispose()

            localAudioTrack?.let {
                it.setEnabled(false)
                it.dispose()
            }

            localVideoTrack?.dispose()
            localStream?.dispose()
        }
        videoCapture = null
        localAudioTrack = null
        localVideoTrack = null
        localStream = null
    }

    fun switchCamera(){
        videoCapture?.switchCamera(null)
    }

    fun toggleMic(enabled:Boolean){
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleCamera(enabled: Boolean){
        localVideoTrack?.setEnabled(enabled)
    }

    fun createRTCClient(
        observer:PeerConnection.Observer,
        listener:RTCClientImpl.TransferDataToServerCallBack
    ):RTCClient?{
        val connection = peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServer),observer
        )
        localStream?.let {
            connection?.addStream(it)
        }
        return connection?.let {
            RTCClientImpl(it,listener,gson)
        }
    }


}