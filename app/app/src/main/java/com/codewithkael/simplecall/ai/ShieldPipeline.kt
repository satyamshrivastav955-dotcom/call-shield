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
/**
 * One enrolled family voiceprint, labeled so results can say WHO matched,
 * not just a canned "family match". Embeddings come from SpeakerEngine
 * (ECAPA) enrollment in TrustedStore — never fabricated.
 */
data class LabeledVoiceprint(
    val name: String,
    val phoneHash: String,
    val embedding: FloatArray,
)

@Singleton
class ShieldPipeline @Inject constructor(private val models: ModelManager) {

    data class LiveResult(
        val risk: Float,
        val band: String,
        val spoofProb: Float?,
        val speakerSim: Float?,
        val speakerName: String? = null,  // which contact the sim refers to
        val speakerClaimed: Boolean = false, // a specific contact was selected (see evaluate)
        val transcript: String = "",
        val explanation: String,
        val hard: List<String>,
        val soft: List<String>,
        // Phase 3.1 forensic telemetry: the dominant scam category the text
        // heuristic matched this window (e.g. "digital_arrest", "otp_fraud"), or
        // null when no scam keyword fired. Carried so the incident log can name
        // the scam type for the FIR draft without re-scoring the transcript.
        val scamType: String? = null,
    )

    private val spoof = SpoofEngine(models)
    private val speaker = SpeakerEngine(models)

    private val window = FloatArray(4 * 16000)
    private var filled = 0

    private val _results = MutableStateFlow<LiveResult?>(null)
    val results: StateFlow<LiveResult?> = _results.asStateFlow()

    var scenario: String? = null
    var enrolledVoiceprint: FloatArray? = null
    var lang: String = "en"
    var transcriber: Transcriber = NoOpTranscriber

    /**
     * DUAL-BUFFER TRANSCRIPT (Phase 1.2b). Two independent buffers, by design:
     *
     *  1. [fullTranscript] — the FULL session transcript, evidence for the FIR
     *     draft / incident history. Grows for the whole call (capped at the tail
     *     so it can't grow unbounded). NEVER scored — using the whole session as
     *     a scoring input is exactly the "one scam phrase alarms every later
     *     window forever" bug Phase 0.C removed.
     *
     *  2. [recentTranscript] — the FAST ~16s window (4 x 4s windows), the ONLY
     *     text the scam/intent/urgency heuristics score. New speech scrolls old
     *     speech out, so risk follows what is being said NOW. The long-horizon
     *     "don't let a scammer pause to evade" concern is handled separately by
     *     the decaying [sessionScamMemory] below, NOT by widening this window.
     */
    var fullTranscript: String = ""
        private set

    /**
     * FAST scoring buffer: recent speech (~last 16s = 4 windows) joined into one
     * string. See the dual-buffer note on [fullTranscript]. This is the text
     * [evaluate] feeds to TextEngines; [fullTranscript] is evidence only.
     */
    private var recentTranscript: String = ""
    private var recentWindows = ArrayDeque<String>()

    /**
     * ANTI-STALL session scam memory (#5). The decaying ~12s transcript window
     * above stops a single scam phrase alarming forever, but it swung too far: a
     * scammer could say the scam line, then fill the next 12s with small talk to
     * drop the risk back to passive. This holds the decayed PEAK scam score for
     * the whole session (~90s half-life), so a scam pattern detected earlier keeps
     * influence even when the current window is benign — the scammer can't simply
     * pause the scam content to evade. It fades on a genuinely benign call and is
     * cleared by reset() on a new session. Still only a SOFT fusion input (it is
     * fed as scamProb), so it never alarms on its own.
     */
    private var sessionScamMemory: Float = 0f

    /**
     * Per-contact selection (#4): the contact whose voiceprint live verification
     * targets. Null -> ALL enrolled contacts are checked and the BEST match is
     * reported (the "unknown caller" default). Enrolling/re-enrolling a contact
     * selects them automatically.
     */
    var selectedPhoneHash: String? = null

    /** All enrolled family voiceprints, labeled — set by ShieldService/ViewModel. */
    var voiceprints: List<LabeledVoiceprint> = emptyList()
        set(v) {
            field = v
            // Keep the legacy single-print field consistent: the selected
            // contact's print, else null (evaluate() then falls back to
            // best-match across all prints below).
            field.filter { it.phoneHash == selectedPhoneHash }
                .firstOrNull()?.let { enrolledVoiceprint = it.embedding }
        }

    /** Select which contact to verify against; null = best-match across all. */
    fun selectContact(phoneHash: String?) {
        selectedPhoneHash = phoneHash
        enrolledVoiceprint = voiceprints
            .filter { it.phoneHash == phoneHash }
            .firstOrNull()?.embedding
    }

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
                transcript = recentTranscript, explanation = Explainer.explain(0f, emptyList(), emptyList(), lang),
                hard = emptyList(), soft = emptyList(),
            ).also { _results.value = it }
        }
        try {
            transcriber.transcribe(w)?.let { (t, l) ->
                if (t.isNotBlank()) {
                    appendTranscript(t)
                    lang = l
                } else {
                    // No speech recognized this window: scroll one empty window
                    // through the decay buffer so stale phrases age out.
                    appendTranscriptWindow("")
                }
            }
        } catch (_: Exception) { }

        val scoredText = recentTranscript
        val scam = if (scoredText.isNotBlank()) {
            try { TextEngines.scamHeuristic(scoredText) } catch (_: Exception) { null }
        } else null
        val intent = if (scoredText.isNotBlank()) {
            try { TextEngines.intentHeuristic(scoredText) } catch (_: Exception) { null }
        } else null
        val textUrgency = if (scoredText.isNotBlank()) {
            try { TextEngines.urgencyHeuristic(scoredText) } catch (_: Exception) { null }
        } else null

        // Speech-likeness gate on the ACOUSTIC models: a pure tone (or any
        // stationary signal) is not speech, and the AST spoof model happily
        // calls it synthetic (a sine scores 0.9999). Only run spoof/scorer
        // engines on windows that plausibly contain speech.
        val spoofProb = if (isSpeechLike(w)) {
            try { spoof.score(w) } catch (_: Exception) { null }
        } else null

        // Per-contact verification (#4):
        //  - A specific contact IS selected: the verdict is about THEM —
        //    mismatch = this caller is not that person (a legitimate HARD
        //    signal: an identity was claimed and the voice contradicts it).
        //  - NO contact selected (unknown caller): NO identity was claimed, so
        //    a low best-match cosine is NOT evidence of a mismatch — it used to
        //    fire the hard +50 identity_mismatch on any audio whose replay
        //    similarity fell below 0.60, which is how ordinary WhatsApp chats
        //    got CRITICAL 100. Now the best match is reported informationally
        //    (speakerSim + name, no risk contribution).
        //  - Engine absent/failed: sim stays null, mismatch stays false —
        //    honest absence, never a fabricated mismatch.
        var sim: Float? = null
        var mismatch = false
        var inconclusive = false
        var simName: String? = null
        var claimed = false
        try {
            val selected = selectedPhoneHash
            val targets = if (selected != null) {
                voiceprints.filter { it.phoneHash == selected }
            } else voiceprints
            val chosen: LabeledVoiceprint? =
                targets.firstOrNull() ?: voiceprints.firstOrNull()
            if (selected != null && chosen != null) {
                claimed = true
                val r = speaker.verify(w, chosen.embedding)
                if (r != null) {
                    sim = r.first
                    simName = chosen.name
                    // Compressed/replayed audio (speaker->mic, WhatsApp codecs)
                    // legitimately degrades cosine. With a bonafide-voice window
                    // and a borderline similarity, report "no match" only past a
                    // lower floor; the borderline band reads inconclusive (#7).
                    val floor = if ((spoofProb ?: 0f) < 0.5f) REPLAY_SIM_FLOOR else SpeakerEngine.SIM_THRESHOLD
                    when (classifyIdentity(r.first, floor)) {
                        IdentityVerdict.MISMATCH -> mismatch = true
                        IdentityVerdict.INCONCLUSIVE -> inconclusive = true
                        IdentityVerdict.MATCH -> {}
                    }
                }
            } else if (selected == null && chosen != null) {
                // Unknown-caller identification: who does this voice best match?
                // Informational only — no identity was claimed, so a low score
                // is not a mismatch (see comment above).
                val r = speaker.verify(w, chosen.embedding)
                if (r != null) {
                    var best: Pair<Float, Boolean> = r
                    var bestVp: LabeledVoiceprint = chosen
                    if (voiceprints.size > 1) {
                        for (vp in voiceprints) {
                            if (vp === chosen) continue
                            speaker.verify(w, vp.embedding)?.let { cand ->
                                if (cand.first > best.first) { best = cand; bestVp = vp }
                            }
                        }
                    }
                    sim = best.first
                    simName = bestVp.name
                    mismatch = false
                }
            } else {
                // Legacy single-print path (enrolled directly, no labeled list).
                enrolledVoiceprint?.let { vp ->
                    speaker.verify(w, vp)?.let {
                        sim = it.first
                        // Single-print: someone enrolled this print intending to
                        // verify against it, treat as a claimed identity.
                        claimed = true
                        val floor = if ((spoofProb ?: 0f) < 0.5f) REPLAY_SIM_FLOOR else SpeakerEngine.SIM_THRESHOLD
                        when (classifyIdentity(it.first, floor)) {
                            IdentityVerdict.MISMATCH -> mismatch = true
                            IdentityVerdict.INCONCLUSIVE -> inconclusive = true
                            IdentityVerdict.MATCH -> {}
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        val prosody = try { ProsodyEngine.analyze(w) } catch (_: Exception) { null }
        val prosodyHint = prosody?.let { ProsodyEngine.urgencyHint(it) }
        val urgency = maxOf(textUrgency ?: 0f, prosodyHint ?: 0f).takeIf { it > 0 }

        // ANTI-STALL session memory (#5): decay the running peak, then lift it to
        // the live score. A scam pattern detected earlier keeps influence even when
        // THIS window is benign, so a scammer can't evade by pausing scam content
        // until the 12s decay window forgets. Only fed as scamProb (a SOFT signal),
        // so it can raise a "verify" nudge but never alarms on its own.
        val liveScam = scam?.prob ?: 0f
        sessionScamMemory = scamMemory(sessionScamMemory, liveScam)
        val effectiveScam = maxOf(liveScam, sessionScamMemory).takeIf { it > 0f }

        val fused = FusionEngine.fuse(
            ShieldSignals(
                voiceDeepfake = spoofProb,
                identityMismatch = mismatch,
                identityInconclusive = inconclusive,
                urgency = urgency,
                scamProb = effectiveScam,
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
            speakerName = simName,
            speakerClaimed = claimed,
            transcript = scoredText,
            explanation = explanation,
            hard = fused.hard,
            soft = fused.soft,
            // Only surface a category when the LIVE window actually matched scam
            // keywords (scam?.type); the decaying sessionScamMemory can keep risk
            // up without a fresh keyword hit, and in that case there is no honest
            // category to name for THIS window.
            scamType = scam?.type,
        ).also { _results.value = it }
    }

    /** Append one window's transcript to both the decay buffer and the FIR record. */
    private fun appendTranscript(text: String) = appendTranscriptWindow(text)

    /**
     * TEST HOOK (androidTest only): inject a transcript AS IF the ASR produced
     * it for the current window — feeds the same decay buffer the live path
     * uses, so scenario tests exercise the real text-scoring path (not a
     * hand-typed string bypassing it). Visible for the instrumentation tests.
     */
    fun ingestTestTranscript(text: String) {
        appendTranscriptWindow(text)
    }

    private fun appendTranscriptWindow(text: String) {
        recentWindows.addLast(text)
        while (recentWindows.size > RECENT_WINDOW_COUNT) recentWindows.removeFirst()
        recentTranscript = recentWindows.joinToString(" ").trim().take(600)
        if (text.isNotBlank()) {
            fullTranscript = ((fullTranscript + " " + text).trim().takeLast(2000))
        }
    }

    /**
     * Clear all TRANSIENT session state so the next session (or file scan) starts
     * clean. Resets the audio window, the decay/transcript buffers, AND the
     * published `_results` verdict — otherwise the last session's CRITICAL card
     * would linger in the UI after disarm and reappear on the next arm (the
     * cross-session leak). Configuration (voiceprints, scenario, selected contact,
     * transcriber) is intentionally preserved — it is not per-session state.
     */
    fun reset() {
        filled = 0
        recentWindows.clear()
        recentTranscript = ""
        fullTranscript = ""
        sessionScamMemory = 0f
        _results.value = null
    }

    companion object {
        /**
         * RMS floor below which a 4s window is treated as TRUE silence and the
         * whole pipeline (ORT + prosody + ASR) is skipped (#4/#6 VAD gate).
         * Lowered from 0.008 -> 0.0035: the old floor dropped QUIET speech (a soft
         * talker or low mic gain lands ~0.004-0.007 RMS) entirely, so those windows
         * were never scanned. Stationary hum at this faint level is still kept off
         * the ACOUSTIC models by isSpeechLike()'s variance gate below.
         */
        const val SILENCE_RMS = 0.0035f

        /**
         * Mean-energy floor for the ACOUSTIC speech-likeness gate. Kept above the
         * raw silence floor so the spoof/speaker models don't burn battery on the
         * very faint windows that isSilence() now lets through for cheap
         * transcription + prosody scoring. Quiet-but-audible speech (>= ~0.006)
         * still reaches the acoustic models.
         */
        const val SPEECH_RMS_FLOOR = 0.006f

        /**
         * Windows of transcript kept in the FAST scoring buffer (~16s of speech,
         * 4 x 4s windows — Phase 1.2b, widened from the earlier 3/~12s). This is
         * the dual-buffer "fast" horizon; the "full" horizon is fullTranscript.
         * NOTE: this is the Phase 0.C/0.D-tuned scoring window — de-sat and band
         * operating points were validated at 3 windows, so re-running the Phase
         * 0.D device harness after this bump is part of the host gate.
         */
        const val RECENT_WINDOW_COUNT = 4

        /**
         * Speaker-similarity floor when the audio is likely a CODEC/REPLAY
         * artifact (spoof model says bonafide): compressed WhatsApp audio played
         * through a speaker and re-captured legitimately lands well under the
         * 0.60 clean-audio threshold, so a mismatch verdict needs a lower bar.
         */
        const val REPLAY_SIM_FLOOR = 0.45f

        /**
         * Similarity margin BELOW the mismatch floor within which an identity
         * verdict is INCONCLUSIVE rather than a confident mismatch (#7). ECAPA
         * cosine on a short/noisy 4s window is jittery, and a sibling or a
         * cold/codec-degraded GENUINE voice lands just under the match floor —
         * firing a hard identity mismatch there produced false CRITICALs. Only a
         * similarity this far below the floor is confidently a different speaker.
         */
        const val IDENTITY_GRAY_MARGIN = 0.15f

        /** Three-state identity verdict for a CLAIMED contact (#7). */
        enum class IdentityVerdict { MATCH, INCONCLUSIVE, MISMATCH }

        /**
         * Classify a claimed-contact similarity against the mismatch floor with a
         * gray band below it (#7): sim >= floor -> MATCH; floor-margin <= sim <
         * floor -> INCONCLUSIVE (soft nudge, not an alarm); sim < floor-margin ->
         * MISMATCH (confidently a different speaker). Pure/stateless for unit
         * testing without a Context.
         */
        fun classifyIdentity(
            sim: Float,
            floor: Float,
            margin: Float = IDENTITY_GRAY_MARGIN,
        ): IdentityVerdict = when {
            sim >= floor -> IdentityVerdict.MATCH
            sim >= floor - margin -> IdentityVerdict.INCONCLUSIVE
            else -> IdentityVerdict.MISMATCH
        }

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

        /**
         * Speech-likeness gate for the ACOUSTIC engines: speech is non-stationary
         * — its frame-level energy varies a lot — while a tone, hum, or codec
         * washout is stationary. Variance of per-50ms-frame RMS below the floor
         * means "not speech-like", and the spoof model's verdict on it is
         * meaningless (it calls a sine 0.9999 synthetic). Still a cheap gate, not
         * a model — honest and unit-testable.
         */
        fun isSpeechLike(w: FloatArray): Boolean {
            if (w.size < 800) return false           // <50ms: not enough to judge
            val frames = w.size / 800
            var sum = 0.0; var sumSq = 0.0
            for (f in 0 until frames) {
                var e = 0.0
                val base = f * 800
                for (i in 0 until 800) {
                    val v = w[base + i].toDouble()
                    e += v * v
                }
                val rms = sqrt(e / 800)
                sum += rms; sumSq += rms * rms
            }
            val mean = sum / frames
            val variance = sumSq / frames - mean * mean
            // Speech: mean RMS above the ACOUSTIC floor (higher than the raw
            // silence floor, so the spoof model doesn't run on the faint windows
            // isSilence now lets through) AND frame-energy spread clearly
            // non-stationary.
            return mean > SPEECH_RMS_FLOOR && variance > STATIONARY_VARIANCE_FLOOR
        }

        /** Frame-RMS variance below which a window is considered stationary (non-speech). */
        const val STATIONARY_VARIANCE_FLOOR = 1e-5

        /**
         * Per-window (~4s) decay of the anti-stall session scam memory (#5).
         * 0.97 gives a ~90s half-life: a detected scam pattern stays influential
         * for roughly a minute and a half of benign speech before fading, long
         * enough to defeat "say the scam line then chat" stalling, short enough
         * that a genuinely benign call returns to passive.
         */
        const val SCAM_MEMORY_DECAY = 0.97f

        /**
         * Anti-stall scam-memory update: the decayed previous peak, lifted to the
         * live score. Pure/stateless so it is unit-testable without a Context.
         * `max(live, prev*decay)` means a fresh scam snaps the memory up instantly
         * while a benign window only lets it decay gently.
         */
        fun scamMemory(prev: Float, live: Float, decay: Float = SCAM_MEMORY_DECAY): Float =
            maxOf(live, prev * decay)
    }
}
