package com.codewithkael.simplecall.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Local orchestrator: rolling 4s window @16k -> engines -> FusionEngine.
 * Replaces the server round-trip (WS /api/stream/ws). Feeds EITHER live mic
 * (ShieldService) OR a decoded file (FileDetectActivity) — same pipeline.
 *
 * Engine-absent policy mirrors server _safe(): missing model file -> that
 * signal reads null and fusion runs on the rest, never crashes.
 *
 * Singleton (Hilt): ShieldService (live mic) and ShieldViewModel (UI) inject
 * the SAME instance, so mic verdicts render in the live card and the UI's
 * scenario / enrolled voiceprint apply to the running service. File checks
 * temporarily borrow the same window via reset()/push().
 */
@Singleton
class ShieldPipeline @Inject constructor(private val models: ModelManager) {

    data class LiveResult(
        val risk: Float,
        val band: String,
        val spoofProb: Float?,
        val speakerSim: Float?,
        val transcript: String = "",
        val explanation: String,
        val hard: List<String>,
        val soft: List<String>,
    )

    private val spoof = SpoofEngine(models)
    private val speaker = SpeakerEngine(models)

    private val window = FloatArray(4 * 16000)
    private var filled = 0

    private val _results = MutableStateFlow<LiveResult?>(null)
    val results: StateFlow<LiveResult?> = _results.asStateFlow()

    var scenario: String? = null
    var enrolledVoiceprint: FloatArray? = null
    var transcript: String = ""
    var lang: String = "en"
    var transcriber: Transcriber = NoOpTranscriber

    /** Push mono 16k PCM; returns a result every time the 4s window fills. */
    fun push(pcm16k: FloatArray): LiveResult? {
        var off = 0
        var last: LiveResult? = null
        while (off < pcm16k.size) {
            val room = window.size - filled
            val n = minOf(room, pcm16k.size - off)
            pcm16k.copyInto(window, filled, off, off + n)
            filled += n; off += n
            if (filled >= window.size) {
                last = evaluate(window.copyOf())
                filled = 0
            }
        }
        return last
    }

    /**
     * Cheap energy VAD gate (#4): if a whole 4s window is essentially silence,
     * skip the ORT engines + prosody entirely. This is an honest RMS gate, NOT
     * Silero — prosody on silence is meaningless and running spoof/ASR on it
     * wastes battery. Speech in a normal call sits well above this floor.
     */
    fun evaluate(w: FloatArray): LiveResult {
        if (isSilence(w)) {
            // Keep the previous transcript context but emit a passive, no-signal
            // result so the UI shows "listening" rather than a stale alarm.
            return LiveResult(
                risk = 0f, band = "passive", spoofProb = null, speakerSim = null,
                transcript = transcript, explanation = Explainer.explain(0f, emptyList(), emptyList(), lang),
                hard = emptyList(), soft = emptyList(),
            ).also { _results.value = it }
        }
        try {
            transcriber.transcribe(w)?.let { (t, l) ->
                if (t.isNotBlank()) {
                    transcript = ((transcript + " " + t).trim().takeLast(2000))
                    lang = l
                }
            }
        } catch (_: Exception) { }

        val scam = if (transcript.isNotBlank()) {
            try { TextEngines.scamHeuristic(transcript) } catch (_: Exception) { null }
        } else null
        val intent = if (transcript.isNotBlank()) {
            try { TextEngines.intentHeuristic(transcript) } catch (_: Exception) { null }
        } else null
        val textUrgency = if (transcript.isNotBlank()) {
            try { TextEngines.urgencyHeuristic(transcript) } catch (_: Exception) { null }
        } else null

        val spoofProb = try { spoof.score(w) } catch (_: Exception) { null }
        val (sim, mismatch) = try {
            val enrolled = enrolledVoiceprint
            if (enrolled != null) {
                val r = speaker.verify(w, enrolled)
                (r?.first) to (r?.let { !(it.second) } ?: false)
            } else null to false
        } catch (_: Exception) { null to false }

        val prosody = try { ProsodyEngine.analyze(w) } catch (_: Exception) { null }
        val prosodyHint = prosody?.let { ProsodyEngine.urgencyHint(it) }
        val urgency = maxOf(textUrgency ?: 0f, prosodyHint ?: 0f).takeIf { it > 0 }

        val fused = FusionEngine.fuse(
            ShieldSignals(
                voiceDeepfake = spoofProb,
                identityMismatch = mismatch,
                urgency = urgency,
                scamProb = scam?.prob,
                requestDetected = intent?.detected == true,
                requestType = intent?.type,
                requestConfidence = intent?.confidence,
            ),
            scenario = scenario,
        )
        val explanation = Explainer.explain(fused.risk, fused.hard, fused.soft, lang)
        return LiveResult(
            risk = fused.risk,
            band = fused.band.name.lowercase(),
            spoofProb = spoofProb,
            speakerSim = sim,
            transcript = transcript,
            explanation = explanation,
            hard = fused.hard,
            soft = fused.soft,
        ).also { _results.value = it }
    }

    fun reset() { filled = 0; transcript = "" }

    companion object {
        /** RMS floor below which a 4s window is treated as silence (#4 VAD gate). */
        const val SILENCE_RMS = 0.008f

        /**
         * Cheap energy VAD gate (#4): true when a whole window is essentially
         * silence, so the caller skips ORT engines + prosody. Honest RMS gate,
         * NOT Silero. Pure/stateless so it is unit-testable without a Context.
         */
        fun isSilence(w: FloatArray): Boolean {
            if (w.isEmpty()) return true
            var sum = 0.0
            for (v in w) sum += v.toDouble() * v
            val rms = sqrt(sum / w.size).toFloat()
            return rms < SILENCE_RMS
        }
    }
}
