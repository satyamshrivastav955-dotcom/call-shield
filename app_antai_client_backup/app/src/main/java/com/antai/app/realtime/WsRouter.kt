package com.antai.app.realtime

import org.json.JSONObject

/** Routes inbound WS messages to the components that care about them. */
class WsRouter {
    var onVerdict: ((JSONObject) -> Unit)? = null
    var onGuidance: ((JSONObject) -> Unit)? = null
    var onFreeze: ((JSONObject) -> Unit)? = null
    var onVerifyPrompt: ((JSONObject) -> Unit)? = null
    var onReportReady: ((JSONObject) -> Unit)? = null
    var onChatRecv: ((JSONObject) -> Unit)? = null
    var onCallAnswer: ((JSONObject) -> Unit)? = null
    var onCallIncoming: ((JSONObject) -> Unit)? = null
    var onCallState: ((JSONObject) -> Unit)? = null
    var onCallIce: ((JSONObject) -> Unit)? = null
    var onChatSent: ((JSONObject) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun handle(msg: JSONObject) {
        when (msg.optString("type")) {
            "verdict.update" -> onVerdict?.invoke(msg)
            "guidance.update" -> onGuidance?.invoke(msg)
            "freeze.request" -> onFreeze?.invoke(msg)
            "verify.prompt" -> onVerifyPrompt?.invoke(msg)
            "report.ready" -> onReportReady?.invoke(msg)
            "chat.recv" -> onChatRecv?.invoke(msg)
            "chat.sent" -> onChatSent?.invoke(msg)
            "call.answer" -> onCallAnswer?.invoke(msg)
            "call.incoming" -> onCallIncoming?.invoke(msg)
            "call.state" -> onCallState?.invoke(msg)
            "call.ice" -> onCallIce?.invoke(msg)
            "error" -> onError?.invoke(msg.optString("message", "server error"))
        }
    }
}