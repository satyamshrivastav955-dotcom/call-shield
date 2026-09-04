package com.antai.app.realtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.antai.app.AppContainer
import com.antai.app.R
import com.antai.app.ui.IncomingCallActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Keeps the realtime socket alive in the background and surfaces Android
 * notifications: incoming calls, verify prompts, report-ready, verdicts.
 *
 * The shared [WsRouter] has a single owner per callback. Ownership is
 * lifecycle-driven: a foreground activity CLAIMS the alerting callbacks in
 * onResume (to show them inline) and RELEASES them back here in onPause via
 * [armNotificationHandlers], so that whenever no screen is visible the
 * background notification path owns them. This keeps routing deterministic
 * instead of depending on whoever happened to set a handler last.
 */
class RealtimeService : Service() {

    companion object {
        const val CHANNEL_ID = "antai_realtime"
        const val CHANNEL_CALLS = "antai_calls"
        const val NOTIF_ONGOING = 1
        const val NOTIF_INCOMING_CALL = 2
        const val NOTIF_VERIFY = 3
        const val NOTIF_REPORT = 4

        /**
         * Number of realtime-aware activities currently in the resumed state.
         * During a normal A→B navigation Android calls B.onResume BEFORE
         * A.onPause, so this count is 1 in steady state, 2 mid-transition, and
         * only reaches 0 when the app truly has no visible screen. We use it to
         * decide whether the background notification path should own the router.
         */
        @Volatile
        private var foregroundScreens = 0

        /** A realtime-aware screen became visible. It claims the router itself. */
        fun onActivityResumed() {
            foregroundScreens++
        }

        /**
         * A realtime-aware screen was hidden. When the last one goes away, hand
         * the alerting callbacks back to the notification path.
         */
        fun onActivityPaused(context: Context) {
            foregroundScreens = (foregroundScreens - 1).coerceAtLeast(0)
            if (foregroundScreens == 0) armNotificationHandlers(context)
        }

        /**
         * Arm notifications only if no screen is currently visible. Called from
         * the service's onCreate so a late service start (it is created after the
         * launching activity's onResume) can't clobber a foreground screen's claim.
         */
        fun armNotificationHandlersIfBackground(context: Context) {
            if (foregroundScreens == 0) armNotificationHandlers(context)
        }

        fun createChannels(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "antAI protection", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Keeps scam protection running" })
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_CALLS, "Calls & alerts", NotificationManager.IMPORTANCE_HIGH))
            }
        }

        /**
         * Point the shared router's alerting callbacks at Android notifications.
         * Called by the service on create AND by foreground activities in onPause,
         * so the background notification path owns these events whenever no in-app
         * screen is visible. Foreground activities re-claim them in onResume.
         * Uses the application context so it never holds an Activity reference.
         */
        fun armNotificationHandlers(context: Context) {
            val ctx = context.applicationContext
            val router = AppContainer.router
            router.onCallIncoming = { notifyCall(ctx, it) }
            router.onVerifyPrompt = { notifyVerify(ctx, it) }
            router.onReportReady = { notifyReport(ctx, it) }
            router.onVerdict = { notifyVerdict(ctx, it) }
        }

        internal fun ongoingNotification(ctx: Context): Notification =
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle("antAI is protecting you")
                .setContentText("Calls and messages are being checked for scams")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        private fun notifyCall(ctx: Context, msg: JSONObject) {
            val sessionKey = msg.optString("session_key")
            val kind = msg.optString("kind", "voice")
            val offer = msg.optJSONObject("offer")?.toString().orEmpty()
            val open = Intent(ctx, IncomingCallActivity::class.java).apply {
                putExtra("session_key", sessionKey)
                putExtra("kind", kind)
                putExtra("offer", offer)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(ctx, sessionKey.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle("Incoming ${kind} call")
                .setContentText("Someone is calling you through antAI")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_INCOMING_CALL, n)
        }

        private fun notifyVerify(ctx: Context, msg: JSONObject) {
            val pi = pendingOpen(ctx, "verify", msg.optString("verify_token"))
            val n = NotificationCompat.Builder(ctx, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle("Identity check")
                .setContentText(msg.optString("question", "Is someone claiming to be you?"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_VERIFY, n)
        }

        private fun notifyReport(ctx: Context, msg: JSONObject) {
            val pi = pendingOpen(ctx, "report", msg.optString("report_id"))
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle("Report ready")
                .setContentText(msg.optString("title", "Your safety report"))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_REPORT, n)
        }

        private fun notifyVerdict(ctx: Context, v: JSONObject) {
            if (v.optString("band") != "critical") return
            val n = NotificationCompat.Builder(ctx, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle("⚠️ Possible scam")
                .setContentText(v.optString("verdict", "Be careful with this call"))
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(v.optString("why", "")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_INCOMING_CALL, n)
        }

        private fun pendingOpen(ctx: Context, kind: String, key: String): PendingIntent {
            val i = Intent(ctx, com.antai.app.ui.HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(ctx, key.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        startForeground(NOTIF_ONGOING, ongoingNotification(this))
        // Own the alerting callbacks only if no screen is visible. The service is
        // created after the launching activity's onResume, so guarding on the
        // foreground count prevents us from clobbering that screen's claim.
        armNotificationHandlersIfBackground(this)

        GlobalScope.launch(Dispatchers.IO) {
            runCatching { AppContainer.ws.connect() }
        }
    }
}
