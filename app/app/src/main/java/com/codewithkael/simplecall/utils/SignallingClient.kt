package com.codewithkael.simplecall.utils

import com.codewithkael.simplecall.remote.socket.SignalMessageModel
import com.codewithkael.simplecall.remote.socket.SignalMessageType
import com.codewithkael.simplecall.remote.socket.SocketClient
import javax.inject.Inject

class SignallingClient @Inject constructor(
    private val socketClient: SocketClient
) {

    fun findUser(target:String){
        socketClient.sendDataToHost(
            SignalMessageModel(
                type = SignalMessageType.FindUser,
                sender = SimpleCallApplication.USER_ID,
                target = target
            )
        )
    }

    fun sendStartCallSignal(target: String, kind: String = "video") {
        socketClient.sendDataToHost(
            SignalMessageModel(
                type = SignalMessageType.StartCall, sender = SimpleCallApplication.USER_ID,
                target = target, data = kind
            )
        )
    }

    fun sendRejectCall(target: String) {
        socketClient.sendDataToHost(
            SignalMessageModel(
                type = SignalMessageType.RejectCall, sender = SimpleCallApplication.USER_ID,target = target
            )
        )
    }

    fun sendAcceptCall(target: String) {
        socketClient.sendDataToHost(
            SignalMessageModel(
                type = SignalMessageType.AcceptCall, sender = SimpleCallApplication.USER_ID,target = target
            )
        )
    }

    fun sendEndCall(target: String) {
        socketClient.sendDataToHost(
            SignalMessageModel(
                type = SignalMessageType.EndCall,
                sender = SimpleCallApplication.USER_ID,
                target = target
            )
        )
    }
}