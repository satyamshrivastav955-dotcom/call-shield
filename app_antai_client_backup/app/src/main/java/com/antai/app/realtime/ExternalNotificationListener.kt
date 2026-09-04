package com.antai.app.realtime

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.antai.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Captures notifications from OTHER apps (WhatsApp, SMS, Telegram, banking
 * apps...) and feeds them into the antAI agentic graph (server endpoint
 * /api/notify/external). The verdict comes back over the realtime channel and
 * is shown to the user in real time.
 *
 * The user must enable "Notification access" for antAI in system settings
 * (Settings -> Notifications -> Notification access).
 */
class ExternalNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val source = sbn.packageName.substringAfterLast(".").lowercase()
        val extras: Bundle = notif.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE)?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val body = listOf(title, text).filter { it.isNotEmpty() }.joinToString(": ")

        if (body.isBlank()) return
        if (source in listOf("antai")) return  // never re-ingest ourselves

        // ignore purely system/action notifications (media, calls already handled)
        if (source in listOf("launcher", "systemui", "settings", "phone")) return

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                AppContainer.api.externalNotify(source, title, body)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}