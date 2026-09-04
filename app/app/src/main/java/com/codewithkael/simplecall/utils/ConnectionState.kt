package com.codewithkael.simplecall.utils

sealed class ConnectionState {
    data object New : ConnectionState()
    data object WaitingForCall : ConnectionState()
    data class UserOffline(val target: String?=null) : ConnectionState()
    data class CallingTarget(val target: String?=null) : ConnectionState()
    data class ReceivedCall(val sender: String?=null) : ConnectionState()
    data class OnCall(val target: String) : ConnectionState()
}