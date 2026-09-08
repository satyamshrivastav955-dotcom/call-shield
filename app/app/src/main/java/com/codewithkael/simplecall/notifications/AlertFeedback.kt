package com.codewithkael.simplecall.notifications

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2.2 — sensory alert on a confirmed on-device detection: a triple-pulse
 * VIBRATION waveform plus a short PULSATING beep, so a scam / cloned-voice
 * verdict is felt and heard even when the user isn't looking at the screen
 * (e.g. phone at the ear on a call).
 *
 * HONEST SCOPE: this fires ONLY from a real elevated pipeline verdict handed in
 * by [com.codewithkael.simplecall.shield.ShieldService] — it never self-triggers
 * and never fabricates a detection. Both effects degrade gracefully: no VIBRATE
 * hardware / no vibrator -> silent no-op; a ToneGenerator construction failure
 * (some OEMs throw under audio load) is swallowed. Needs the VIBRATE permission
 * (normal, auto-granted).
 *
 * DEBOUNCE: the mic loop yields a verdict every ~4s; without debounce a sustained
 * CRITICAL would buzz every 4s. So it fires on an ESCALATION (a band strictly
 * more severe than the last alerted band) or, if the band is merely sustained, at
 * most once per [REPEAT_MS]. De-escalation never re-fires.
 */
@Singleton
class AlertFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "AlertFeedback"
        /** Re-buzz cadence for a SUSTAINED elevated band (ms). */
        const val REPEAT_MS = 20_000L
        /** Spec waveform: wait 0, buzz 200, pause 100, buzz 200, pause 100, buzz 300. */
        val WAVEFORM = longArrayOf(0, 200, 100, 200, 100, 300)
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastBand = "passive"
    @Volatile private var lastAt = 0L

    private fun severity(band: String) = when (band) {
        "critical" -> 2
        "verify" -> 1
        else -> 0
    }

    /** Called with every pipeline verdict; decides internally whether to fire. */
    fun onVerdict(band: String) {
        if (severity(band) == 0) { lastBand = band; return }
        val now = SystemClock.elapsedRealtime()
        val escalated = severity(band) > severity(lastBand)
        val sustainedRepeat = now - lastAt > REPEAT_MS
        if (!escalated && !sustainedRepeat) { lastBand = band; return }
        lastBand = band
        lastAt = now
        val critical = band == "critical"
        vibrate(critical)
        beep(critical)
    }

    /** Clear debounce state at session start/end so a new call alerts cleanly. */
    fun reset() {
        lastBand = "passive"
        lastAt = 0L
    }

    private fun vibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private fun vibrate(critical: Boolean) {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Amplitudes apply per waveform segment (0 during the pauses);
                // heavier on the "on" bursts for critical.
                val amp = if (critical) 255 else 180
                val amplitudes = intArrayOf(0, amp, 0, amp, 0, amp)
                val effect = if (v.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(WAVEFORM, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(WAVEFORM, -1)
                }
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(WAVEFORM, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "vibrate failed: ${e.message}")
        }
    }

    /**
     * Short PULSATING beep — three quick tones lined up with the three vibration
     * pulses (start offsets 0 / 500 / 900 ms). STREAM_ALARM for critical (cuts
     * through call audio), STREAM_NOTIFICATION for verify. The ToneGenerator is
     * released after the sequence so it doesn't leak an AudioTrack.
     */
    private fun beep(critical: Boolean) {
        val stream = if (critical) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
        val volume = if (critical) 100 else 80
        val tone = try {
            ToneGenerator(stream, volume)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator unavailable: ${e.message}")
            return
        }
        val toneType = if (critical) ToneGenerator.TONE_CDMA_HIGH_L else ToneGenerator.TONE_PROP_BEEP
        val fire = { durMs: Int -> try { tone.startTone(toneType, durMs) } catch (_: Exception) {} }
        main.post { fire(200) }
        main.postDelayed({ fire(200) }, 500)
        main.postDelayed({ fire(300) }, 900)
        main.postDelayed({ try { tone.release() } catch (_: Exception) {} }, 1500)
    }
}
