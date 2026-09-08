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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ai.LabeledVoiceprint
import com.codewithkael.simplecall.ai.ShieldPipeline
import com.codewithkael.simplecall.ai.Transcriber
import com.codewithkael.simplecall.notifications.AlertFeedback
import com.codewithkael.simplecall.notifications.RiskNotificationManager
import com.codewithkael.simplecall.telecom.CellularCallController
import com.codewithkael.simplecall.ui.MainActivity
import com.codewithkael.simplecall.ui.viewmodel.SettingsViewModel
import com.codewithkael.simplecall.utils.SimpleCallApplication
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
        private const val TAG = "ShieldService"

        /** Same-band incident repeat cooldown (ms) for the live verdict log. */
        private const val SAME_BAND_REPEAT_MS = 60_000L

        fun startIntent(ctx: Context) = Intent(ctx, ShieldService::class.java)
    }

    @Inject lateinit var riskNotifications: RiskNotificationManager

    // Phase 2.2 — beep + triple-vibration when a verdict escalates into an
    // elevated band. Owns its own debounce so a sustained CRITICAL doesn't buzz
    // every 4s window.
    @Inject lateinit var alerts: AlertFeedback

    // Phase 2.3 — one-tap hang-up for the overlay's "End this call" button. Ends a
    // CELLULAR call only when antAI is the default phone app (honest no-op / manual
    // hint otherwise); it can never end a third-party VoIP call programmatically.
    @Inject lateinit var cellular: CellularCallController

    // Shared singletons — the SAME pipeline the UI observes (#2), so mic verdicts
    // render in the live card and the UI's scenario/voiceprint apply here.
    @Inject lateinit var pipeline: ShieldPipeline
    @Inject lateinit var store: TrustedStore
    @Inject lateinit var overlay: ShieldOverlay

    // TASK 2 — real on-device ASR. Same @Singleton instance the VM gets; assigned
    // to the shared pipeline below so the mic loop transcribes locally (or stays
    // NoOp honestly if the sherpa AAR/model aren't present).
    @Inject lateinit var transcriber: Transcriber

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordJob: Job? = null
    private var consumeJob: Job? = null
    private var recorder: AudioRecord? = null

    /**
     * Audio handoff between the (fast) mic-read loop and the (slow) inference
     * consumer. The models take ~3.2s spoof + ~0.4s speaker per 4s window, so
     * evaluate() is NOT real-time; running it inline in the read loop (the old
     * design) stalled reads until buffers overflowed and results appeared
     * "frozen". Capacity is one 4s window: when inference lags a full window,
     * the oldest chunk is dropped (audio freshness beats completeness).
     */
    @Volatile private var audioQueue: Channel<FloatArray> = Channel(capacity = 4 * 16000)

    private fun audioQueueIsClosed(): Boolean = audioQueue.isClosedForSend

    /** A closed channel cannot be reopened — swap in a fresh one. */
    private fun resetAudioQueue() {
        audioQueue = Channel(capacity = 4 * 16000)
        Log.i(TAG, "audioQueue recreated after close")
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Wire the on-device transcriber into the shared pipeline BEFORE the mic
        // loop starts, so the very first 4s window can be transcribed. NoOp if ASR
        // isn't installed — never a fabricated transcript.
        pipeline.transcriber = transcriber
        // Restore the operator-selected profile (#7) + enrolled family voiceprint
        // (#6) into the shared pipeline before the mic loop starts. Scenario lives
        // in "antai_settings" — the same pref SettingsScreen/MainViewModel use, so
        // one choice drives both the server path and the on-device Shield.
        try {
            val prefs = getSharedPreferences("antai_settings", MODE_PRIVATE)
            prefs.getString("scenario", null)?.let { pipeline.scenario = it }
            // Per-contact verification (#4): load EVERY enrolled voiceprint so
            // the pipeline can verify against the selected contact or report a
            // best-match across the whole family — not whichever contact
            // happened to be saved first.
            pipeline.voiceprints = store.loadContacts()
                .filter { it.voiceprint != null }
                .map { LabeledVoiceprint(it.displayName, it.phoneHash, it.voiceprint!!) }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopShield()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, shieldNotification("Shield armed — listening"))
        ShieldArmedState.setArmed(true)
        startMic()
        return START_STICKY
    }

    private fun startMic() {
        if (recordJob != null) return
        // Channel close() is one-shot; a fresh instance per (re)start keeps the
        // reader/consumer pair alive across arm -> disarm -> arm cycles.
        if (audioQueueIsClosed()) resetAudioQueue()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Mic permission revoked mid-session (or not granted): stop honestly
            // instead of a silent self-destruct — log it, notify, and tell the
            // shared armed-state so the UI's toggle doesn't lie.
            Log.w(TAG, "RECORD_AUDIO not granted — shield stopping (arm toggle should reflect this)")
            ShieldArmedState.setArmed(false)
            stopSelf()
            return
        }
        // Fresh listening session: drop any transient state (window/transcript/
        // last verdict) left over from a PRIOR arm or a file scan, so the first
        // window starts clean and no stale CRITICAL card carries across sessions.
        // Config (voiceprints/scenario/selected contact) is preserved by reset().
        try { pipeline.reset() } catch (_: Exception) {}
        // Phase 2.2 — fresh session: clear the alert debounce so the first
        // escalation of THIS call buzzes even if the last call ended on critical.
        try { alerts.reset() } catch (_: Exception) {}
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
        // Snapshot the queue for this session: both coroutines below must use
        // the SAME instance (audioQueue is a var only so a closed channel from a
        // prior session can be swapped out before launch).
        val queue = audioQueue

        // Consumer: all model/ASR inference happens OFF the read loop. Each
        // window's result is timestamped and logged under "ShieldLive" — the
        // frozen-vs-live evidence a device run must show.
        consumeJob = scope.launch {
            for (chunk in queue) {
                val t0 = SystemClock.elapsedRealtime()
                val res = try {
                    pipeline.push(chunk)
                } catch (e: Exception) {
                    Log.e(TAG, "pipeline.push failed: ${e.message}", e)
                    null
                }
                val ms = SystemClock.elapsedRealtime() - t0
                if (res != null) {
                    Log.i(
                        "ShieldLive",
                        "ts=${SystemClock.elapsedRealtime()} risk=${res.risk} band=${res.band} " +
                            "spoof=${res.spoofProb} sim=${res.speakerSim} " +
                            "name=${res.speakerName} transcript=\"${res.transcript.take(80)}\""
                    )
                    if (ms > 4000) {
                        Log.w("ShieldLive", "SLOW window: evaluate took ${ms}ms (models lag real time; audio was dropped)")
                    }
                    onResult(res)
                }
            }
        }

        // Reader: only AudioRecord.read + queue handoff, so reads never stall
        // behind inference. Full queue -> drop the oldest pending chunk.
        recordJob = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(3200) // 200ms
            val fbuf = FloatArray(3200)
            while (true) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                for (i in 0 until n) fbuf[i] = buf[i] / 32768f
                while (!queue.trySend(fbuf.copyOf(n)).isSuccess) {
                    // Queue full — inference is a full window behind. Drop the
                    // oldest audio and retry; freshness beats completeness.
                    queue.tryReceive()
                    Log.w("ShieldLive", "dropped 200ms chunk (inference backlog)")
                }
            }
        }
    }

    /**
     * A fresh pipeline verdict: incident log, notification, and overlay.
     * Bug 3 fix: elevated bands are PERSISTED to the local incident log (they
     * used to be transient — the overlay fired CRITICAL and Incident History
     * stayed empty). Debounced: one entry per band TRANSITION into verify/
     * critical (plus a 60s cooldown per same-band repeat), not per 4s window.
     */
    private fun onResult(res: ShieldPipeline.LiveResult) {
        logIncident(res)
        val fg = try { SimpleCallApplication.isForegrounded } catch (_: Exception) { false }
        riskNotifications.onRiskUpdate(
            risk = res.risk.toDouble(),
            band = res.band,
            recommendation = res.explanation,
            isAppForegrounded = fg,
        )
        // Phase 2.2 — multi-sensory alert. Fires regardless of foreground (the
        // user may have the phone at their ear on a call): AlertFeedback decides
        // internally whether this verdict is a fresh escalation worth buzzing.
        try { alerts.onVerdict(res.band) } catch (_: Exception) {}
        // Real system overlay (#5): only when NOT already in the app, and only for
        // actionable bands. THRESHOLD GATE (Phase 2.1): the floating popup fires
        // only when risk >= VERIFY/CRITICAL. That band is where FusionEngine has
        // already integrated a CONFIRMED synthetic-voice or fraud signal (with
        // corroboration) — so "risk >= verify/critical OR synthetic/fraud
        // confirmed" collapses to a single band check; a lone uncorroborated spoof
        // is deliberately kept passive (Phase 1.2a) and must NOT pop.
        // The user's verify_policy (#8) governs how insistent the floating alert is
        // once the risk subsides back to passive:
        //   warn    -> auto-clear the moment the band returns to passive (default;
        //              identical to the prior behaviour, so the demo is unchanged)
        //   require -> a CRITICAL alert is STICKY: it stays up until the user taps
        //              dismiss, so a peak alert can't scroll away unacknowledged
        //   freeze  -> ANY elevated alert (verify or critical) is sticky
        try {
            val elevated = res.band == "verify" || res.band == "critical"
            when {
                !fg && elevated -> {
                    overlay.show(
                        res.risk.toInt(), res.band, res.explanation,
                        onHangUp = { onOverlayHangUp() },
                    )
                    lastOverlayBand = res.band
                }
                !elevated -> {
                    // Risk subsided to passive: hold the last alert only if policy asks.
                    val policy = try { SettingsViewModel.readVerifyPolicy(this) } catch (_: Exception) { "warn" }
                    val sticky = when (policy) {
                        "freeze" -> lastOverlayBand == "verify" || lastOverlayBand == "critical"
                        "require" -> lastOverlayBand == "critical"
                        else -> false // "warn"
                    }
                    if (!sticky) { overlay.hide(); lastOverlayBand = "passive" }
                }
                else -> {
                    // Elevated but the app is in the foreground: the in-app card
                    // already covers it, so no floating overlay is needed.
                    overlay.hide()
                }
            }
        } catch (_: Exception) {}
    }

    private var lastLoggedBand: String = "passive"
    private var lastLoggedAt = 0L

    /** Band of the alert the overlay is currently showing, for verify_policy stickiness (#8). */
    private var lastOverlayBand: String = "passive"

    /**
     * Phase 2.3 — the overlay's "End this call" button was tapped. Try the ONLY
     * honest programmatic path: end a cellular call via TelecomManager, which works
     * solely when antAI is the default phone app / call-screener. If that returns
     * false (a third-party VoIP call like WhatsApp, or the role isn't held), do NOT
     * fake a disconnect — replace the card with a manual hang-up instruction. This
     * is the documented external limit: the app cannot end another app's call.
     */
    private fun onOverlayHangUp() {
        val ended = try { cellular.endCellularCall() } catch (_: Exception) { false }
        if (ended) {
            Log.i("ShieldLive", "overlay hang-up: cellular call ended via TelecomManager")
            try { overlay.hide() } catch (_: Exception) {}
            lastOverlayBand = "passive"
        } else {
            Log.i("ShieldLive", "overlay hang-up: no programmatic path (${cellular.capability()}) — manual hint")
            try {
                overlay.showManualHangupHint(
                    "Can't end this call automatically — open your phone or calling " +
                        "app and tap the red hang-up button. ${cellular.reason()}"
                )
            } catch (_: Exception) {}
        }
    }

    private fun logIncident(res: ShieldPipeline.LiveResult) {
        if (res.band != "verify" && res.band != "critical") {
            lastLoggedBand = res.band
            return
        }
        val now = SystemClock.elapsedRealtime()
        val transition = res.band != lastLoggedBand
        val cooldownOver = now - lastLoggedAt > SAME_BAND_REPEAT_MS
        if (!transition && !cooldownOver) return
        try {
            store.appendVerdict(
                risk = res.risk,
                band = res.band,
                explanation = res.explanation,
                hard = res.hard,
                soft = res.soft,
                transcript = res.transcript,
                source = "on-device",
                // Phase 3.1 forensic telemetry: the AI-voice score, the matched/
                // claimed contact (only when an identity was actually claimed — an
                // informational unknown-caller best-match is NOT a forensic
                // attribution), and the scam category. Absent values stay null.
                spoofProb = res.spoofProb,
                contact = if (res.speakerClaimed) res.speakerName else null,
                scamType = res.scamType,
            )
            Log.i(
                "ShieldLive",
                "incident logged: risk=${res.risk} band=${res.band} spoof=${res.spoofProb} " +
                    "scamType=${res.scamType} contact=${if (res.speakerClaimed) res.speakerName else null} " +
                    "hard=${res.hard} soft=${res.soft}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "incident log failed: ${e.message}")
        }
        lastLoggedBand = res.band
        lastLoggedAt = now
    }

    private fun stopShield() {
        try { recordJob?.cancel() } catch (_: Exception) {}
        try { consumeJob?.cancel() } catch (_: Exception) {}
        recordJob = null
        consumeJob = null
        audioQueue.close()
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null
        // Clear transient session state + the published verdict so the UI's live
        // card doesn't keep showing the last session's alert after disarm, and the
        // next arm starts from a clean slate (cross-session leakage fix).
        try { pipeline.reset() } catch (_: Exception) {}
        ShieldArmedState.setArmed(false)
        riskNotifications.dismissRiskNotification()
        try { alerts.reset() } catch (_: Exception) {}
        try { overlay.hide() } catch (_: Exception) {}
        lastOverlayBand = "passive"
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
        ShieldArmedState.setArmed(false)
        audioQueue.close()
        scope.cancel()
        super.onDestroy()
    }
}
