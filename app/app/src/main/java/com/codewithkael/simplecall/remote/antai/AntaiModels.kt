package com.codewithkael.simplecall.remote.antai

import org.json.JSONObject

/**
 * Null-preserving JSON readers.
 *
 * For verification results the difference between "absent" and "zero/false" is
 * the whole point: a null similarity means the check could not run, while 0.0
 * means it ran and the voice did not match at all. JSONObject.optDouble() and
 * optBoolean() would silently collapse those two very different answers into
 * each other, which would make "we don't know" look like "definitely not them".
 */
internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

internal fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (isNull(key)) null else optBoolean(key)

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key).takeIf { has(key) }

/**
 * Android's [JSONObject.optString] famously returns the literal text "null" for an
 * explicit JSON null, which then gets rendered to the user. The server sends null
 * for fields it has nothing to say about (`reason` on success, `voice_agreement`
 * when only one detector scored), so every optional string must come through here.
 */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/**
 * One live-transcript line pushed by the antAI server (faster-whisper ASR on
 * the tapped call audio). `speaker` is "caller" or "callee" as classified by
 * the server from which phone's tap the audio came from.
 */
data class TranscriptEntry(
    val speaker: String,
    val text: String,
    val t: String
)

/**
 * Live per-engine detector outputs pushed continuously by the server
 * (signals.update) — the "all models running" feed, independent of the verdict.
 */
data class AiSignals(
    val risk: Double = 0.0,
    val band: String = "passive",
    // null = no detector produced a score for this segment (the server sends an
    // explicit null rather than inventing a "bonafide" number). Rendering that as
    // 0% would tell the user "checked, nothing synthetic found" when in truth
    // nothing was checked at all — the exact confusion we are here to remove.
    val voiceDeepfake: Double? = null,
    val voicePerModel: Map<String, Double> = emptyMap(),
    // how many synthetic-voice detectors scored this segment (0, 1 or 2)
    val voiceSources: Int = 0,
    val videoDeepfake: Double? = null,
    val videoVotes: Int = 0,
    val videoAgreement: Double = 0.0,
    val scamProb: Double = 0.0,
    val scamType: String = "",
    val deviation: Double = 0.0,
    val urgency: Double = 0.0,
    val requestType: String = "",
    val requestDetected: Boolean = false,
    val lipsyncMismatch: Boolean = false,
    val identityMismatch: Boolean = false,
    // Diagnostic: which server engines actually loaded (name -> loaded). Lets
    // the UI show "n/m models loaded" and distinguish a 0% that means "model
    // ran and saw nothing" from a 0% that means "model never loaded".
    val enginesReady: Map<String, Boolean> = emptyMap(),
    // How many audio segments have reached the detection graph, and how many
    // failed speech-to-text. Together they make a missing voice score readable:
    // 0 segments means "nothing to score yet" (stay quiet), while segments > 0
    // with no score means the detectors were asked and answered nothing — the
    // failure that would let a cloned voice through unflagged.
    val audioSegments: Int = 0,
    val asrFailures: Int = 0
)

/**
 * Server "pause the call" alert: the voice/video detectors crossed their
 * margins — the call is paused and this modal is shown to both participants.
 *
 * The synthetic-voice score is produced by up to two independent detectors (the
 * hosted streaming API and the local SSL ensemble). [voiceSources] and
 * [voiceAgreement] say how many scored the audio and whether they agreed, so the
 * warning can explain itself instead of showing an unattributed number.
 */
data class DeepfakeAlert(
    val source: String = "voice",        // "voice" | "video" | "request" | "identity"
    val title: String = "",              // server-supplied headline
    val voiceDeepfake: Double = 0.0,
    val videoDeepfake: Double = 0.0,
    val videoVotes: Int = 0,
    val voicePerModel: Map<String, Double> = emptyMap(),
    val voiceSources: Int = 0,           // detectors that scored the audio (1 or 2)
    val voiceAgreement: String = "",     // both-flag | single-source | disagree | ...
    val voiceBackend: String = "",       // both | velma | local
    // true when the server has buffered call audio AND the voiceprint model is
    // loaded, i.e. the "cross-check against my saved voiceprint" button will work
    val canCrossVerify: Boolean = false,
    val message: String = ""
)

/**
 * Result of cross-checking the live call audio against a contact's enrolled
 * voiceprint (ECAPA speaker verification) — the independent second opinion on an
 * AI-voice alert. [ok] is false when the check could not run at all, in which
 * case [reason]/[hint] explain why rather than implying a verdict.
 */
data class VoiceprintResult(
    val ok: Boolean = false,
    val similarity: Double? = null,      // cosine similarity vs enrolled print
    val matches: Boolean? = null,        // similarity >= server threshold
    val threshold: Double? = null,
    val secondsOfAudio: Double = 0.0,
    val reason: String = "",
    val hint: String = "",
    val checking: Boolean = false        // client-side: request in flight
) {
    companion object {
        /** Shared by the WS push (voiceprint.result) and POST /api/verify/voiceprint,
         *  which return the same shape — parsed once so the two cannot drift. */
        fun from(o: JSONObject) = VoiceprintResult(
            ok = o.optBoolean("ok", false),
            similarity = o.optDoubleOrNull("similarity"),
            matches = o.optBooleanOrNull("matches"),
            threshold = o.optDoubleOrNull("threshold"),
            secondsOfAudio = o.optDouble("seconds_of_audio", 0.0),
            // null on the success path and on the WS push; must not become "null"
            reason = o.optStringOrNull("reason").orEmpty(),
            hint = o.optStringOrNull("hint").orEmpty()
        )
    }
}

/**
 * What the server has stored for *my* voice (GET /api/voiceprints/me).
 *
 * [engineReady] is separate from [enrolled] on purpose: if the speaker model is
 * not loaded server-side, recording a sample cannot possibly succeed, and saying
 * so before the user speaks is the difference between a clear explanation and a
 * mysterious failure. [minSeconds] comes from the server so the recorder's
 * "keep going" threshold can never disagree with the one that validates it.
 */
data class VoiceprintStatus(
    val enrolled: Boolean = false,
    val count: Int = 0,
    val embeddingDim: Int? = null,
    val engineReady: Boolean = false,
    val minSeconds: Double = 3.0
) {
    companion object {
        fun from(o: JSONObject) = VoiceprintStatus(
            enrolled = o.optBoolean("enrolled", false),
            count = o.optInt("count", 0),
            embeddingDim = o.optIntOrNull("embedding_dim"),
            engineReady = o.optBoolean("engine_ready", false),
            minSeconds = o.optDouble("min_seconds", 3.0)
        )
    }
}

/**
 * Outcome of uploading one recording. A rejection ([enrolled] false) is a normal
 * answer carrying [reason]/[hint] — "too quiet", "too short" — so the recorder can
 * coach the user instead of showing a network error. [status] is the refreshed
 * stored state, saved from a second round-trip.
 */
data class VoiceprintEnrollResult(
    val enrolled: Boolean = false,
    val reason: String = "",
    val hint: String = "",
    val embeddingDim: Int? = null,
    val status: VoiceprintStatus = VoiceprintStatus()
) {
    companion object {
        fun from(o: JSONObject) = VoiceprintEnrollResult(
            enrolled = o.optBoolean("enrolled", false),
            reason = o.optStringOrNull("reason").orEmpty(),
            hint = o.optStringOrNull("hint").orEmpty(),
            embeddingDim = o.optIntOrNull("embedding_dim"),
            status = VoiceprintStatus.from(o)   // the enrol reply embeds the status fields
        )
    }
}

/**
 * State of the "is this really who they say they are?" challenge sent to the
 * contact the caller claims to be.
 *
 * [state] mirrors the server's verify.result: "" (not asked) | pending |
 * confirmed | denied | timeout. [delivered] is false when the contact's phone had
 * no live connection, so the UI can say "we asked, but they may not see it" rather
 * than leaving the user waiting on a challenge that never arrived. A timeout is
 * deliberately NOT treated as innocence — the server escalates it.
 */
data class CallerVerification(
    val requested: Boolean = false,
    val peerPhone: String = "",
    val sending: Boolean = false,
    val promptSent: Boolean = false,
    val delivered: Boolean = false,
    val state: String = "",
    val answer: Boolean? = null,         // true = "yes, that's me", false = denied
    val message: String = "",
    val error: String = ""
)

/**
 * The incoming side of that challenge: someone is claiming to be *this* user on a
 * call with one of their contacts, and the server is asking them to confirm.
 *
 * This arrives on the CHAT socket, not the call tap — the real contact is by
 * definition not on the call, so their tap socket isn't open. Answering is
 * time-critical (the server gives them [timeoutSeconds] before it warns the other
 * side anyway), so the UI should surface this immediately wherever they are.
 */
/**
 * A `verify.result` event, decoupled from whichever socket carried it.
 *
 * This exists because the event can arrive on either socket: the server pushes it
 * to the user id that authenticated the REST call (the phone/OTP user, which owns
 * the chat socket), while the automatic in-call path pushes to the tap user. Both
 * funnel through this type so the in-call UI has exactly one source of truth.
 *
 * [delivered] is nullable on purpose: only the "pending" event reports it, and a
 * later confirmed/denied must not silently reset it to false.
 */
data class VerifyOutcome(
    val state: String,
    val verified: Boolean? = null,
    val delivered: Boolean? = null,
    val message: String = ""
) {
    companion object {
        fun from(o: JSONObject) = VerifyOutcome(
            state = o.optString("state", "pending"),
            verified = o.optBooleanOrNull("verified"),
            delivered = o.optBooleanOrNull("delivered"),
            message = o.optStringOrNull("message").orEmpty()
        )
    }
}

data class VerifyPrompt(
    val sessionKey: String,
    val verifyToken: String = "",
    val question: String = "",
    val options: List<String> = listOf("Yes, it's me", "No, that's not me"),
    val receivedAt: Long = System.currentTimeMillis(),
    val answering: Boolean = false,
    val answered: Boolean? = null,
    /**
     * Set when the server would not accept the answer — normally because the
     * 45s window closed and the other side was already warned. Kept distinct from
     * [answered] so the UI never says "we told them" when it didn't.
     */
    val error: String = ""
) {
    companion object {
        fun from(o: JSONObject): VerifyPrompt {
            val opts = o.optJSONArray("options")
            return VerifyPrompt(
                sessionKey = o.optString("session_key"),
                verifyToken = o.optString("verify_token"),
                question = o.optString("question"),
                options = if (opts == null || opts.length() == 0) {
                    listOf("Yes, it's me", "No, that's not me")
                } else {
                    (0 until opts.length()).map { opts.optString(it) }
                }
            )
        }
    }
}

/**
 * Merged, render-ready state of the antAI local-LLM reasoning shown in the
 * in-call AI window. Built incrementally from verdict.update / guidance.update /
 * freeze.request / signals.update pushes — nothing here is hardcoded
 * client-side; every field comes off the wire.
 */
data class AiInsight(
    val band: String = "passive",        // passive | verify | critical
    val riskScore: Double = 0.0,         // 0..100 from the fusion node
    val verdict: String = "",            // one-line "what's wrong"
    val why: String = "",                // plain-language reasoning
    val action: String = "",             // "what to do now"
    val guidance: String = "",           // throttled live LLM guidance
    // null = no synthetic-voice detector answered (see AiSignals.voiceDeepfake).
    // The window shows "—" for null so a dead detector can never look like a
    // clean 0%.
    val voiceDeepfake: Double? = null,   // synthetic-voice prob (0..1)
    val voicePerModel: Map<String, Double> = emptyMap(),
    val voiceSources: Int = 0,           // detectors that scored the audio
    val videoDeepfake: Double? = null,   // synthetic-video prob (0..1)
    val videoVotes: Int = 0,
    val videoAgreement: Double = 0.0,
    val scamProb: Double = 0.0,          // scam-pattern prob (0..1)
    val scamType: String = "",
    val deviation: Double = 0.0,
    val urgency: Double = 0.0,           // pressure/urgency score (0..100)
    val requestType: String = "",
    val requestDetected: Boolean = false,
    val lipsyncMismatch: Boolean = false,
    val identityMismatch: Boolean = false,
    // which server engines actually loaded (name -> loaded); carried from the
    // signals feed so the window can show "n/m models loaded"
    val enginesReady: Map<String, Boolean> = emptyMap(),
    // audio segments analysed so far, and how many failed speech-to-text.
    // audioSegments > 0 is what makes "no AI-voice score" a reportable fault
    // rather than the normal state of the first second of a call.
    val audioSegments: Int = 0,
    val asrFailures: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
