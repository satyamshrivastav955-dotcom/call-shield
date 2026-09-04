package com.codewithkael.simplecall.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Records a short microphone sample and returns it as a PCM WAV, for voiceprint
 * enrolment only.
 *
 * Deliberately standalone: it uses plain [AudioRecord] and touches nothing in the
 * WebRTC / call path, because enrolment must never be able to disturb a working
 * call. It also refuses to open the microphone while the audio system says a call
 * is in progress — two things capturing the mic at once is how you get a dead
 * call, and a voiceprint recorded through call-mode processing would not match
 * later samples anyway.
 *
 * WAV (not mp3/m4a) is intentional: the server decodes plain PCM wav with the
 * Python standard library, so enrolment keeps working even on a machine where the
 * optional audio wheels (soundfile / PyAV) are missing.
 */
@Singleton
class VoiceprintRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Live recorder state for the UI. [seconds] and [level] drive the "keep
     * talking" coaching; [enoughAudio] mirrors the server's minimum so the button
     * cannot offer to upload something the server will reject.
     */
    data class Progress(
        val recording: Boolean = false,
        val seconds: Double = 0.0,
        val level: Float = 0f,               // 0..1 smoothed RMS, for the meter
        val enoughAudio: Boolean = false,
        val error: String? = null
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile private var worker: Thread? = null
    @Volatile private var stopRequested = false
    private var captured: ByteArray? = null
    private var minSeconds: Double = MIN_SECONDS_DEFAULT

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * True when the device audio mode says a voice/VoIP call is up. Read-only —
     * it inspects [AudioManager], never the call stack — so this check cannot
     * affect an in-progress call.
     */
    fun callInProgress(): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.mode == AudioManager.MODE_IN_CALL ||
            am.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    /**
     * @return null when recording started, otherwise the reason it could not.
     *
     * The permission is checked on the line above the [AudioRecord] construction,
     * which is why the lint requirement is suppressed rather than pushed onto every
     * caller.
     */
    @SuppressLint("MissingPermission")
    fun start(minSeconds: Double = MIN_SECONDS_DEFAULT): String? {
        if (worker != null) return "Already recording."
        if (!hasMicPermission()) return "Microphone permission is needed to record your voice."
        if (callInProgress()) return "Finish your call first — the microphone is in use."
        this.minSeconds = minSeconds
        captured = null
        stopRequested = false
        _progress.value = Progress(recording = true)

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            _progress.value = Progress(error = "This device won't record at 16 kHz.")
            return "This device won't record at 16 kHz."
        }
        val bufSize = minBuf * 2
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord create failed", e)
            _progress.value = Progress(error = "Couldn't open the microphone.")
            return "Couldn't open the microphone."
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            _progress.value = Progress(error = "Couldn't open the microphone.")
            return "Couldn't open the microphone."
        }

        val t = Thread({ captureLoop(rec, bufSize) }, "voiceprint-rec")
        worker = t
        t.start()
        return null
    }

    private fun captureLoop(rec: AudioRecord, bufSize: Int) {
        val pcm = ByteArrayOutputStream(SAMPLE_RATE * 2 * 8)
        val buf = ShortArray(bufSize / 2)
        var smoothed = 0f
        try {
            rec.startRecording()
            while (!stopRequested) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                // Little-endian s16 — the byte order both the WAV header below and
                // the server's decoder expect.
                for (i in 0 until n) {
                    val s = buf[i].toInt()
                    pcm.write(s and 0xFF)
                    pcm.write((s shr 8) and 0xFF)
                }
                var sum = 0.0
                var peak = 0
                for (i in 0 until n) {
                    val v = buf[i].toInt()
                    sum += (v.toDouble() * v.toDouble())
                    if (abs(v) > peak) peak = abs(v)
                }
                val rms = (sqrt(sum / n) / 32768.0).toFloat()
                smoothed = smoothed * 0.7f + min(1f, rms * 6f) * 0.3f
                val secs = pcm.size() / (SAMPLE_RATE * 2.0)
                _progress.value = Progress(
                    recording = true, seconds = secs, level = smoothed,
                    enoughAudio = secs >= minSeconds
                )
                if (secs >= MAX_SECONDS) break   // bound memory; plenty for ECAPA
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture failed", e)
            _progress.value = _progress.value.copy(
                recording = false, error = "Recording stopped unexpectedly."
            )
        } finally {
            try { rec.stop() } catch (_: Exception) { }
            rec.release()
        }
        val bytes = pcm.toByteArray()
        captured = if (bytes.isEmpty()) null else wavOf(bytes)
        val secs = bytes.size / (SAMPLE_RATE * 2.0)
        _progress.value = _progress.value.copy(
            recording = false, seconds = secs, enoughAudio = secs >= minSeconds
        )
        worker = null
    }

    /**
     * Stops the capture and returns the recording as WAV bytes, or null if nothing
     * usable was captured. Blocks briefly (bounded) while the capture thread
     * finishes writing, so the caller gets the complete sample rather than a
     * truncated one.
     */
    fun stop(): ByteArray? {
        val t = worker
        stopRequested = true
        try { t?.join(1500) } catch (_: InterruptedException) { }
        return captured
    }

    /** Throws the recording away (user tapped cancel / retake). */
    fun discard() {
        stop()
        captured = null
        _progress.value = Progress()
    }

    /** Standard 44-byte RIFF/WAVE header for 16-bit mono PCM. */
    private fun wavOf(pcm: ByteArray): ByteArray {
        val byteRate = SAMPLE_RATE * 2
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

        ascii("RIFF"); le32(36 + pcm.size); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)       // PCM, mono
        le32(SAMPLE_RATE); le32(byteRate); le16(2); le16(16)
        ascii("data"); le32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "VoiceprintRec"
        private const val SAMPLE_RATE = 16000            // matches the server pipeline
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_SECONDS = 20.0
        const val MIN_SECONDS_DEFAULT = 3.0
    }
}
