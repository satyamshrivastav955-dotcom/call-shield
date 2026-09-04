package com.codewithkael.simplecall.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Small helpers for the NotificationListener permission, which is NOT a runtime
 * permission — the user must toggle it in system settings. We only read the
 * granted state and open the settings screen; nothing here touches call logic.
 */
object NotificationAccess {

    /** True if antAI is currently allowed to read notifications. */
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        val pkg = context.packageName
        return !flat.isNullOrEmpty() && flat.split(":").any { it.contains(pkg) }
    }

    /** Open the system "Notification access" settings screen. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
