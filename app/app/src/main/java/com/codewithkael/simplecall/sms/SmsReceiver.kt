package com.codewithkael.simplecall.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.codewithkael.simplecall.di.MessagingEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Receives incoming SMS (SMS_RECEIVED) and hands each message to the
 * MessagesRepository, which stores it in the unified thread and asks the antAI
 * server to score it. The app is NOT the default SMS app; this is a passive
 * observer alongside the system messaging app.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull() ?: return
        if (messages.isEmpty()) return

        // Multipart SMS arrive as several PDUs from the same sender; concatenate.
        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val ts = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        if (body.isBlank()) return

        try {
            val repo = EntryPointAccessors
                .fromApplication(context.applicationContext, MessagingEntryPoint::class.java)
                .messagesRepository()
            repo.onSmsReceived(sender, body, ts)
        } catch (e: Exception) {
            Log.w(TAG, "failed to route incoming SMS", e)
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
