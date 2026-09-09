package com.codewithkael.simplecall.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the antAI risk alert notification channel and status-bar notifications.
 *
 * Channel: antai_risk_alerts (HIGH importance — interrupts when backgrounded).
 *
 * A notification is posted when the live-call risk score crosses the scenario's
 * verify threshold while the app is in the background. Tapping it opens the
 * main (call/live-protection) screen.
 *
 * Design rule: notifications are ONLY sent when a real detection signal fires.
 * This class never fabricates a risk number — it only reads what the detection
 * pipeline already computed and surfaced via [onRiskUpdate].
 */
@Singleton
class RiskNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "antai_risk_alerts"
        const val CHANNEL_NAME = "CallShield Risk Alerts"
        const val CHANNEL_DESC =
            "Alerts when a live call crosses the risk verification threshold"

        // Notification IDs (kept stable so a higher-risk update replaces the previous)
        const val NOTIF_ID_RISK = 1001

        /** Default verify threshold — overridden per scenario from server config. */
        const val DEFAULT_VERIFY_THRESHOLD = 50.0
    }

    /** Call once from [MainActivity.onCreate] (safe to call multiple times). */
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    /**
     * Call every time the pipeline emits a new risk score (from the viewmodel's
     * [onSignals] callback).
     *
     * @param risk          0-100 risk score from normalized_result
     * @param band          "passive" | "verify" | "critical"
     * @param verifyAt      scenario-specific verify threshold (default 50)
     * @param recommendation plain-language recommendation text from the server
     * @param isAppForegrounded pass true when the Activity is resumed — skip the
     *                          notification so we don't spam the user who is
     *                          already looking at the in-call risk view
     */
    fun onRiskUpdate(
        risk: Double,
        band: String,
        verifyAt: Double = DEFAULT_VERIFY_THRESHOLD,
        recommendation: String = "",
        isAppForegrounded: Boolean = false,
    ) {
        if (isAppForegrounded) return
        if (risk < verifyAt && band == "passive") return   // still in safe zone

        val (title, body, priority) = when {
            band == "critical" || risk >= 80 ->
                Triple(
                    "⚠️ HIGH RISK — Do not approve",
                    recommendation.ifBlank { "Detected strong signs of a scam or cloned voice." },
                    NotificationCompat.PRIORITY_MAX
                )
            band == "verify" || risk >= verifyAt ->
                Triple(
                    "Risk elevated — verify the caller",
                    recommendation.ifBlank { "Consider verifying the caller's identity before proceeding." },
                    NotificationCompat.PRIORITY_HIGH
                )
            else -> return  // below threshold; no notification
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_live_call", true)
        }
        val pi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_RISK, notif)
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS permission not granted (Android 13+); ignore
        }
    }

    /** Dismiss any active risk notification (call when the call ends). */
    fun dismissRiskNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RISK)
    }
}
