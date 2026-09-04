package com.codewithkael.simplecall.webrtc

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

interface RTCClient {

    val peerConnection:PeerConnection
    fun onDestroy()
    fun offer(target:String)
    fun answer(target: String)
    fun onRemoteSessionReceived(sessionDescription: SessionDescription)
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
    fun onLocalIceCandidateGenerated(iceCandidate: IceCandidate, target: String)
}