package com.codewithkael.simplecall.shield

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.ai.ModelManager
import com.codewithkael.simplecall.ai.NoOpTranscriber
import com.codewithkael.simplecall.ai.ShieldPipeline
import com.codewithkael.simplecall.ai.SpeakerEngine
import com.codewithkael.simplecall.ai.Transcriber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * UI state + actions for on-device Shield. The pipeline/store/models are shared
 * @Singletons (Hilt) — the SAME instances the ShieldService mic loop uses — so
 * live mic verdicts render here and scenario/voiceprint changes reach the
 * running service. File checks borrow the shared pipeline via reset()/push().
 */
@HiltViewModel
class ShieldViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelManager,
    private val pipeline: ShieldPipeline,
    private val store: TrustedStore,
    private val voiceprintRecorder: com.codewithkael.simplecall.voice.VoiceprintRecorder,
    private val transcriber: Transcriber,
    private val overlay: ShieldOverlay,
) : ViewModel() {

    /**
     * In-app preview alert (Bug 4 fix): a Compose card state rendered INSIDE the
     * Shield screen, not the real system overlay window — the old test button
     * added a TYPE_APPLICATION_OVERLAY window on top of the app's own UI, which
     * is broken layering in-app (the system window is only correct over OTHER
     * apps, which the live mic path already exercises).
     */
    private val _previewAlert = MutableStateFlow<Pair<Int, String>?>(null)
    val previewAlert: StateFlow<Pair<Int, String>?> = _previewAlert.asStateFlow()

    fun testOverlay() {
        _previewAlert.value = 92 to
            "🛡️ antAI Live Alert: AI-generated voice clone detected (Score: 92%). Potential scam or impersonation."
    }

    fun dismissPreviewAlert() {
        _previewAlert.value = null
    }

    fun dismissOverlay() {
        overlay.hide()
    }

    private val _armed = MutableStateFlow(ShieldArmedState.armed.value)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    init {
        // Mirror the service's armed state into this VM so the toggle reflects
        // the real service lifecycle (e.g. mic permission revoked -> service
        // stops -> toggle flips off, instead of lying "Active").
        viewModelScope.launch {
            ShieldArmedState.armed.collect { _armed.value = it }
        }
    }

    val liveResult = pipeline.results

    private val _modelStatus = MutableStateFlow("checking…")
    val modelStatus: StateFlow<String> = _modelStatus.asStateFlow()

    /**
     * REAL-TIME engine status (Bug 2 fix): named per capability, derived from
     * actual file presence + a live NoOp probe for ASR, refreshed on every call
     * (screen entry, model import, etc.). The old one-shot init check said
     * "heuristic mode (N models pending)" forever because REQUIRED listed
     * models that never ship; this names what's actually loaded vs pending.
     */
    fun refreshModelStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val st = models.modelStatus()
            val asrActive = transcriber !== NoOpTranscriber
            val parts = buildList {
                // #10: plain-language capability names (was clone-detect/voice-ID/ASR
                // jargon). The ✓/✗ stay honest — they reflect real file presence and
                // a live ASR probe, never a fabricated "loaded".
                add(if (st["spoof_ast"] == true) "Clone detector ✓" else "Clone detector ✗")
                add(if (st["ecapa_tdnn"] == true) "Voice match ✓" else "Voice match ✗")
                add(if (asrActive) "Transcription ✓" else "Transcription ✗")
                add("Scam-text scan: on-device")
            }
            _modelStatus.value = parts.joinToString(" · ")
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _contacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    val contacts: StateFlow<List<TrustedContact>> = _contacts.asStateFlow()

    init {
        // TASK 2 — the file-scan path shares the same @Singleton pipeline as the
        // mic service; assign the on-device transcriber so analyzeFile() transcribes
        // locally too (NoOp if ASR isn't installed — no fabricated transcript).
        pipeline.transcriber = transcriber
        viewModelScope.launch(Dispatchers.IO) {
            _contacts.value = store.loadContacts()
            refreshVoiceprints()
            refreshModelStatus()
        }
    }

    fun setArmed(ctx: Context, on: Boolean) {
        _armed.value = on
        val intent = ShieldService.startIntent(ctx)
        if (on) {
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        } else {
            ctx.stopService(intent)
        }
    }

    /** Current scenario — pipeline if set, else the shared pref, else SettingsScreen's default. */
    val scenario: String
        get() = pipeline.scenario
            ?: context.getSharedPreferences("antai_settings", Context.MODE_PRIVATE)
                .getString("scenario", null)
            ?: "high_value_txn"

    fun setScenario(name: String) {
        val resolved = when (name) {
            // institution presets: bank/telecom reuse tuned thresholds
            "bank" -> "high_value_txn"
            "telecom" -> "routine_call"
            else -> name
        }
        // Shared pipeline -> takes effect on the running service immediately (#7).
        pipeline.scenario = resolved
        // Persist to "antai_settings" — the SAME pref SettingsScreen and the server
        // path (MainViewModel) use — so one scenario choice drives both paths. A
        // service (re)start restores it in ShieldService.onCreate.
        context.getSharedPreferences("antai_settings", Context.MODE_PRIVATE)
            .edit().putString("scenario", resolved).apply()
    }

    fun analyzeFile(ctx: Context, uri: Uri) {
        // The pipeline is shared with the live mic service. Checking a file while
        // armed would fight the mic over the same rolling window, so pause the
        // service first for a clean, deterministic file scan.
        if (_armed.value) setArmed(ctx, false)
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            try {
                val pcm = AudioFileDecoder.decodeToMono16k(ctx, uri)
                pipeline.reset()
                // Per-contact verification (#4): all labeled prints, not
                // first-saved-wins.
                refreshVoiceprints()
                var last: com.codewithkael.simplecall.ai.ShieldPipeline.LiveResult? = null
                var off = 0
                while (off < pcm.size) {
                    val n = minOf(16000, pcm.size - off)
                    last = pipeline.push(pcm.copyOfRange(off, off + n))
                    off += n
                }
                last = last ?: pipeline.evaluate(pcm.takeLast(minOf(pcm.size, 64000)).toFloatArray())
                last?.let {
                    store.appendVerdict(
                        risk = it.risk,
                        band = it.band,
                        explanation = it.explanation,
                        hard = it.hard,
                        soft = it.soft,
                        transcript = it.transcript,
                        source = "file-scan",
                        // Phase 3.1 forensic telemetry, same contract as the live path.
                        spoofProb = it.spoofProb,
                        contact = if (it.speakerClaimed) it.speakerName else null,
                        scamType = it.scamType,
                    )
                }
            } catch (_: Exception) {
            } finally {
                _busy.value = false
            }
        }
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.addContact(name, phone)
            _contacts.value = store.loadContacts()
        }
    }

    /** Push every enrolled voiceprint (labeled) into the shared pipeline. */
    private fun refreshVoiceprints() {
        try {
            pipeline.voiceprints = store.loadContacts()
                .filter { it.voiceprint != null }
                .map { com.codewithkael.simplecall.ai.LabeledVoiceprint(
                    it.displayName, it.phoneHash, it.voiceprint!!) }
        } catch (_: Exception) {}
    }

    /**
     * Per-contact selection (#4): target verification at one family member —
     * "is this really Mom?" — instead of an anonymous family match. Null
     * clears the selection: unknown-caller mode, best match across all.
     */
    fun selectContact(phoneHash: String?) {
        pipeline.selectContact(phoneHash)
    }

    private val _enrollStatus = MutableStateFlow<String?>(null)
    val enrollStatus: StateFlow<String?> = _enrollStatus.asStateFlow()

    /**
     * Live recorder telemetry (level/seconds/enoughAudio) for the inline enroll
     * meter in ShieldScreen — the same flow the dedicated VoiceprintScreen renders.
     * Exposing it kills the "frozen for 8s" feel: the meter animates while the mic
     * gathers audio instead of showing one static status line.
     */
    val enrollProgress: StateFlow<com.codewithkael.simplecall.voice.VoiceprintRecorder.Progress> =
        voiceprintRecorder.progress

    /**
     * On-device voiceprint enrollment (#6): record a short sample, compute the
     * ECAPA embedding locally, attach it to the contact, and load it into the
     * shared pipeline so live mic detection can flag identity mismatch. If the
     * ECAPA weights are not exported yet, enrollment is refused honestly rather
     * than storing a fake print. `seconds` is best-effort UI coaching handled by
     * VoiceprintRecorder's own progress flow.
     */
    fun enrollVoiceprint(phoneHash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!models.allReady() && File(models.pathFor("ecapa_tdnn.int8.onnx")).let { !it.exists() }) {
                _enrollStatus.value = "Voice model not on device yet — enroll after models are installed."
                return@launch
            }
            val err = voiceprintRecorder.start(minSeconds = 3.0)
            if (err != null) { _enrollStatus.value = err; return@launch }
            _enrollStatus.value = "Recording… keep talking."
            // Live-coached bounded wait: publish seconds each tick so neither the
            // meter nor the status text looks frozen (P1). Capture a beat past the
            // 3s minimum for a stabler embedding, hard-capped so a silent mic still
            // terminates. The meter animates off voiceprintRecorder.progress.
            var waited = 0
            while (waited < 9000) {
                val p = voiceprintRecorder.progress.value
                if (!p.recording) break
                _enrollStatus.value = if (p.enoughAudio)
                    "Got enough (%.1fs) — finishing…".format(p.seconds)
                else
                    "Recording… %.1fs — keep talking".format(p.seconds)
                if (p.enoughAudio && p.seconds >= 4.5) break
                kotlinx.coroutines.delay(150); waited += 150
            }
            val wav = voiceprintRecorder.stop()
            if (wav == null) { _enrollStatus.value = "No audio captured — try again."; return@launch }
            val pcm = pcmFromWav(wav)
            val embedding = try { SpeakerEngine(models).embed(pcm) } catch (_: Exception) { null }
            if (embedding == null) {
                _enrollStatus.value = "Couldn't compute voiceprint (model missing/failed)."
                return@launch
            }
            store.setVoiceprint(phoneHash, embedding)
            pipeline.enrolledVoiceprint = embedding
            _contacts.value = store.loadContacts()
            // Per-contact (#4): the fresh print joins the labeled list and the
            // enrolled contact becomes the verification target.
            refreshVoiceprints()
            pipeline.selectContact(phoneHash)
            _enrollStatus.value = "Voice enrolled ✓ — verifying against this contact now."
        }
    }

    /** Strip the 44-byte WAV header and convert s16LE mono -> float PCM. */
    private fun pcmFromWav(wav: ByteArray): FloatArray {
        val start = 44.coerceAtMost(wav.size)
        val n = (wav.size - start) / 2
        val out = FloatArray(n)
        var j = start
        for (i in 0 until n) {
            val lo = wav[j].toInt() and 0xFF
            val hi = wav[j + 1].toInt()
            out[i] = ((hi shl 8) or lo) / 32768f
            j += 2
        }
        return out
    }

    fun removeContact(phoneHash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.removeContact(phoneHash)
            _contacts.value = store.loadContacts()
        }
    }

    /**
     * Build the full-transcript FIR draft (session record, not the scoring
     * window) — keeps evidence that has scrolled out of the live view.
     */
    fun buildFirDraft(): String {
        val r = pipeline.results.value
        return store.buildFirDraft(
            risk = r?.risk, band = r?.band, explanation = r?.explanation,
            transcript = pipeline.fullTranscript.ifBlank { r?.transcript },
        )
    }

    fun shareFir(ctx: Context) {
        val draft = buildFirDraft()
        val file = File(ctx.cacheDir, "antai_fir_draft.txt").apply { writeText(draft) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "antAI cyber complaint draft — also report at https://cybercrime.gov.in or call 1930")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share FIR draft",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Decode any audio Uri -> mono 16kHz float PCM via MediaExtractor/MediaCodec
 * with linear resampling. Returns empty array on failure (caller degrades).
 */
object AudioFileDecoder {

    suspend fun decodeToMono16k(ctx: Context, uri: Uri): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(ctx, uri, null)
                var track = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) { track = i; format = f; break }
                }
                if (track < 0 || format == null) return@withContext floatArrayOf()
                val srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 44100
                val srcCh = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 1
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                extractor.selectTrack(track)
                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                val out = mutableListOf<Float>()
                val info = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false
                var guard = 0
                while (!sawOutputEos && guard++ < 4000) {
                    if (!sawInputEos) {
                        val idx = codec.dequeueInputBuffer(10_000)
                        if (idx >= 0) {
                            val buf = codec.getInputBuffer(idx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val idx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        idx >= 0 -> {
                            val buf = codec.getOutputBuffer(idx)!!
                            drainPcm16(buf, info, srcCh, out)
                            codec.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                        }
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (sawInputEos) sawOutputEos = true
                    }
                }
                try { codec.stop(); codec.release() } catch (_: Exception) {}
                resampleMono(out.toFloatArray(), srcRate, 16000)
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        }

    private fun drainPcm16(buf: ByteBuffer, info: MediaCodec.BufferInfo, channels: Int, out: MutableList<Float>) {
        buf.position(info.offset).limit(info.offset + info.size)
        val shorts = ShortArray(info.size / 2)
        buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val frames = shorts.size / maxOf(1, channels)
        for (f in 0 until frames) {
            var s = 0
            for (c in 0 until channels) s += shorts[f * channels + c]
            out += (s / channels) / 32768f
        }
    }

    private fun resampleMono(src: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (src.isEmpty()) return src
        if (srcRate == dstRate) return src
        val ratio = srcRate.toDouble() / dstRate
        val n = (src.size / ratio).toInt().coerceAtLeast(1)
        return FloatArray(n) { i ->
            val pos = i * ratio
            val j = pos.toInt().coerceIn(0, src.size - 2)
            val frac = (pos - j).toFloat()
            src[j] * (1 - frac) + src[j + 1] * frac
        }
    }
}
