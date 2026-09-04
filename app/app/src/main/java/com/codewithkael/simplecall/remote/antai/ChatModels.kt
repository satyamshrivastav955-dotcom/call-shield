package com.codewithkael.simplecall.remote.antai

/**
 * Data models for the unified messaging + notification-guard feature.
 *
 * A single [ChatMessage] can originate from the device SMS inbox OR the antAI
 * in-app chat; both are keyed by a normalized phone number so one thread shows
 * everything from a given contact. Risk is always decided server-side; the
 * client only maps a numeric score / server verdict onto a display [RiskBand].
 */

/** Truecaller-style risk semantics used by every verdict chip in the app. */
enum class RiskBand { SAFE, CAUTION, CRITICAL, PENDING;
    companion object {
        /**
         * The server's risk scale is **0..100**, not 0..1 — see `fusion_node`
         * in server/src/antai/orchestration/nodes.py, and note that the in-call
         * insight panel already renders it as "risk N/100". These thresholds
         * mirror the server's own band boundaries (`pipeline.risk_bands`).
         *
         * This used to be compared against 0.40/0.70 as if the score were a
         * fraction, which made a perfectly benign message scored 1.8/100 render
         * as CRITICAL — the cause of "everything is flagged as a scam".
         */
        const val CAUTION_AT = 40.0
        const val CRITICAL_AT = 70.0

        /**
         * Map the server's numeric risk (0..100) + optional verdict onto a band.
         *
         * The server only *surfaces* a verdict when its own gating judged the
         * event actionable (band verify/critical — see `dispatch_message`), so a
         * non-blank verdict is authoritative and never renders as SAFE. When the
         * server stays quiet, so do we.
         */
        fun of(riskScore: Double, verdict: String?, analysisPending: Boolean): RiskBand {
            val actionable = !verdict.isNullOrBlank()
            if (analysisPending && !actionable && riskScore <= 0.0) return PENDING
            return when {
                riskScore >= CRITICAL_AT -> CRITICAL
                riskScore >= CAUTION_AT || actionable -> CAUTION
                else -> SAFE
            }
        }
    }
}

/** Which transport a message came in / went out on. */
enum class MessageChannel { SMS, IN_APP }

/**
 * One message in a conversation. `outgoing` = sent by this device.
 * `riskScore` is 0..100 (the server's scale); `verdict`/`why`/`action`/
 * `scamType` are populated once the server's async detection returns (pushed
 * over the chat socket for in-app, or returned inline by
 * /api/notify/external for SMS).
 *
 * `why` is the REASON the event was flagged; `action` is the ADVICE on what to
 * do next. They are deliberately separate fields so the UI can show them as
 * distinct lines.
 */
data class ChatMessage(
    val id: String,
    val peerPhone: String,          // normalized E.164-ish key for the thread
    val body: String,
    val timestamp: Long,
    val outgoing: Boolean,
    val channel: MessageChannel,
    val riskScore: Double = 0.0,
    val verdict: String? = null,
    val why: String? = null,
    val action: String? = null,
    val scamType: String? = null,
    val analysisPending: Boolean = false
) {
    val band: RiskBand get() = RiskBand.of(riskScore, verdict, analysisPending)
}

/**
 * A thread with one contact. `worstBand` is the highest risk seen in the thread
 * so the conversation row can carry a Truecaller-style badge at a glance.
 */
data class Conversation(
    val peerPhone: String,
    val displayName: String,
    val messages: List<ChatMessage> = emptyList()
) {
    val lastMessage: ChatMessage? get() = messages.maxByOrNull { it.timestamp }
    val lastTimestamp: Long get() = lastMessage?.timestamp ?: 0L
    val worstBand: RiskBand
        get() = when {
            messages.any { it.band == RiskBand.CRITICAL } -> RiskBand.CRITICAL
            messages.any { it.band == RiskBand.CAUTION } -> RiskBand.CAUTION
            messages.any { it.band == RiskBand.PENDING } -> RiskBand.PENDING
            else -> RiskBand.SAFE
        }
}

/**
 * A scanned notification from another app (WhatsApp, Telegram, bank app, …)
 * captured by the NotificationListenerService and scored by the server's
 * /api/notify/external endpoint.
 *
 * Only notifications that pass [com.codewithkael.simplecall.notifications.NotificationTriage]
 * ever reach the server, so ordinary chatter and media notifications never
 * appear here at all.
 */
data class NotificationEvent(
    val id: String,
    val source: String,             // app label / package (e.g. "whatsapp")
    val sender: String,             // title / sender line from the notification
    val text: String,
    val timestamp: Long,
    val riskScore: Double = 0.0,    // server scale: 0..100
    val verdict: String? = null,
    val why: String? = null,        // reason it was flagged
    val action: String? = null,     // advice: what to do next
    val scamType: String? = null,
    val analysisPending: Boolean = false
) {
    val band: RiskBand get() = RiskBand.of(riskScore, verdict, analysisPending)
}
