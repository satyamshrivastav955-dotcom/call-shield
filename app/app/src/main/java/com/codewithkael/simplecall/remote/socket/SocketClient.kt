package com.codewithkael.simplecall.remote.socket

import android.util.Log
import com.google.gson.Gson
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketClient @Inject constructor(
    private val gson:Gson
) {

    private var socketClient:WebSocketClient? = null
    fun init(
        socketUrl:String,
        listener:SocketCallback
    ){
        // Always tear down any previous connection so a retry (e.g. after correcting
        // the server IP) actually reconnects, instead of silently reporting "opened".
        runCatching { socketClient?.close() }
        Log.d("SOCKET", "init: connecting to $socketUrl")
        socketClient = object :WebSocketClient(URI(socketUrl)){
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("SOCKET", "onOpen: connected (httpStatus=${handshakedata?.httpStatus} ${handshakedata?.httpStatusMessage})")
                listener.onRemoteSocketClientOpened()
            }

            override fun onMessage(message: String?) {
                Log.d("SOCKET", "onMessage: $message")
                runCatching {
                    gson.fromJson(message.toString(),SignalMessageModel::class.java)
                }.onSuccess {
                    // Guard the handler so an exception while processing a message
                    // cannot bubble into the WebSocket thread and tear the socket down.
                    runCatching { listener.onRemoteSocketClientNewMessage(it) }
                        .onFailure { e -> Log.e("SOCKET", "handler failed for: $message", e) }
                }.onFailure { e ->
                    Log.e("SOCKET", "parse failed for: $message", e)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w("SOCKET", "onClose: code=$code reason=$reason remote=$remote")
                socketClient = null
                listener.onRemoteSocketClientClosed()
            }

            override fun onError(ex: java.lang.Exception?) {
                Log.e("SOCKET", "onError", ex)
                listener.onRemoteSocketClientConnectionError(ex)
            }
        }.apply {
            connect()
        }
    }

    fun sendDataToHost(data:Any){
        Log.d("TAG", "onMessage Send: $data")

        runCatching {
            socketClient?.send(gson.toJson(data))
        }
    }

    fun close(){
        socketClient?.close()
    }


    interface SocketCallback {
        fun onRemoteSocketClientOpened()
        fun onRemoteSocketClientClosed()
        fun onRemoteSocketClientConnectionError(e: Exception?)
        fun onRemoteSocketClientNewMessage(message: SignalMessageModel)
    }
}