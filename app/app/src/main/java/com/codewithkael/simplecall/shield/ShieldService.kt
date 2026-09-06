package com.codewithkael.simplecall.shield

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ai.ShieldPipeline
import com.codewithkael.simplecall.notifications.RiskNotificationManager
import com.codewithkael.simplecall.ui.MainActivity
import com.codewithkael.simplecall.utils.SimpleCallApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * On-device Shield: foreground mic service running ShieldPipeline locally.
 * No server: mic PCM -> rolling 4s window -> ORT engines -> FusionEngine ->
 * notification + overlay state. Toggle from UI; survives other apps.
 */
@AndroidEntryPoint
class ShieldService : Service() {

    companion object {
        const val CHANNEL_ID = "antai_shield"
        const val NOTIF_ID = 2001
        const val ACTION_STOP = "antai.shield.STOP"
        const val SAMPLE_RATE = 16000

        fun startIntent(ctx: Context) = Intent(ctx, ShieldService::class.java)
    }

    @Inject lateinit var riskNotifications: RiskNotificationManager

    // Shared singletons — the SAME pipeline the UI observes (#2), so mic verdicts
    // render in the live card and the UI's scenario/voiceprint apply here.
    @Inject lateinit var pipeline: ShieldPipeline
    @Inject lateinit var store: TrustedStore
    @Inject lateinit var overlay: ShieldOverlay

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordJob: Job? = null
    private var recorder: AudioRecord? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Restore the operator-selected profile (#7) + enrolled family voiceprint
        // (#6) into the shared pipeline before the mic loop starts. Scenario lives
        // in "antai_settings" — the same pref SettingsScreen/MainViewModel use, so
        // one choice drives both the server path and the on-device Shield.
        try {
            val prefs = getSharedPreferences("antai_settings", MODE_PRIVATE)
            prefs.getString("scenario", null)?.let { pipeline.scenario = it }
            store.loadContacts().firstOrNull { it.voiceprint != null }?.voiceprint?.let {
                pipeline.enrolledVoiceprint = it
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopShield()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, shieldNotification("Shield armed — listening"))
        startMic()
        return START_STICKY
    }

    private fun startMic() {
        if (recordJob != null) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 32000),
        )
        val rec = recorder ?: run { stopSelf(); return }
        rec.startRecording()
        recordJob = scope.launch {
            val buf = ShortArray(3200) // 200ms
            val fbuf = FloatArray(3200)
            while (true) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                for (i in 0 until n) fbuf[i] = buf[i] / 32768f
                val res = try {
                    pipeline.push(fbuf.copyOf(n))
                } catch (_: Exception) { null }
                if (res != null) {
                    val fg = try { SimpleCallApplication.isForegrounded } catch (_: Exception) { false }
                    riskNotifications.onRiskUpdate(
                        risk = res.risk.toDouble(),
                        band = res.band,
                        recommendation = res.explanation,
                        isAppForegrounded = fg,
                    )
                    // Real system overlay (#5): only when NOT already in the app,
                    // and only for actionable bands. Passive dismisses it.
                    try {
                        if (!fg && (res.band == "verify" || res.band == "critical")) {
                            overlay.show(res.risk.toInt(), res.band, res.explanation)
                        } else {
                            overlay.hide()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun stopShield() {
        try { recordJob?.cancel() } catch (_: Exception) {}
        recordJob = null
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null
        riskNotifications.dismissRiskNotification()
        try { overlay.hide() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "antAI Shield", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun shieldNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ShieldService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java)
                .apply { putExtra("open_live_call", true) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("antAI Shield")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        try { overlay.hide() } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }
}
