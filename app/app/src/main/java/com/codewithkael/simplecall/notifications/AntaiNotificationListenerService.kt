package com.codewithkael.simplecall.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.codewithkael.simplecall.di.MessagingEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Reads notifications posted by OTHER apps (WhatsApp, Telegram, bank apps, …)
 * and routes their text to the antAI server for scam scanning. Requires the
 * user to grant notification access in system settings (a special permission
 * that cannot be granted with a normal runtime prompt).
 *
 * Only notifications that [NotificationTriage] judges scam-relevant are sent
 * onward. Everything else — media players, system chrome, ordinary chatter,
 * greetings — is dropped here and never costs a server round trip, which is
 * what stopped music notifications and group messages from being flagged.
 *
 * This never blocks or dismisses notifications; it only observes their text.
 */
class AntaiNotificationListenerService : NotificationListenerService() {

    // small ring of recently seen (pkg|title|text) hashes to avoid re-scoring
    // the same notification each time an app updates it.
    private val recent = object : LinkedHashMap<Int, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?) = size > 100
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return                 // ignore our own notifications
        val n = sbn.notification ?: return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // relevance gate: drop anything that is not plausibly a scam attempt
        when (val d = NotificationTriage.decide(sbn, title, text)) {
            is NotificationTriage.Decision.Drop -> {
                Log.v(TAG, "skip $pkg: ${d.reason}")
                return
            }
            is NotificationTriage.Decision.Analyze -> {
                Log.d(TAG, "analyse $pkg (matched: ${d.matched.take(6).joinToString()})")
            }
        }

        val key = (pkg + "|" + title + "|" + text).hashCode()
        synchronized(recent) {
            if (recent.containsKey(key)) return
            recent[key] = true
        }

        try {
            val repo = EntryPointAccessors
                .fromApplication(applicationContext, MessagingEntryPoint::class.java)
                .messagesRepository()
            repo.onNotificationPosted(source = sourceOf(pkg), sender = title, text = text)
        } catch (e: Exception) {
            Log.w(TAG, "failed to route notification from $pkg", e)
        }
    }

    private fun sourceOf(pkg: String): String = when {
        pkg.contains("whatsapp") -> "whatsapp"
        pkg.contains("telegram") -> "telegram"
        pkg.contains("signal") -> "signal"
        pkg.contains("instagram") -> "instagram"
        pkg.contains("orca") || pkg.contains("facebook") -> "messenger"
        pkg.contains("gm") || pkg.contains("mail") || pkg.contains("outlook") -> "email"
        else -> "other"
    }

    companion object {
        private const val TAG = "AntaiNotifSvc"
    }
}
