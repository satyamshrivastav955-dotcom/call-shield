package com.codewithkael.simplecall.ai

/**
 * On-device text signals. Mirrors server contracts:
 * - ScamPatternEngine.analyze(): {scam_prob, scam_type} (9 labels: benign + 8)
 * - IntentEngine.classify(): {request_detected, request_type, confidence}
 * - UrgencyEngine.score(): {urgency 0..100}
 *
 * These are keyword heuristics ONLY — no ONNX model backs them. The server's
 * fine-tuned DistilBERT classifiers (models/classifiers/) exist server-side;
 * exporting them to on-device ONNX is tracked as future work. Heuristics are
 * the permanent fallback, extended with Hindi/Hinglish phrases for the
 * multilingual requirement.
 *
 * MATCHING (Phase 1.2b — "process segments reliably"): matching is
 * whole-word for single-token keywords and substring for multi-word / hyphenated
 * PHRASES. Whole-word is why a keyword like "otp" no longer fires inside
 * "biotope", "case" inside "because", or "emi" inside "premier" — the old
 * blanket `keyword in text` substring test produced exactly those spurious hits,
 * so scores drifted on benign speech. Phrases (anything containing a space or
 * hyphen, e.g. "khata band", "part-time") still match as substrings because
 * their internal spacing already anchors them. See [keywordHit]/[tokenize].
 *
 * SCRIPT ASSUMPTION (honest caveat): the dictionaries are romanized
 * English + Hindi/Hinglish (Latin script), so they match ASR output transcribed
 * in Latin/Hinglish (the "hinglish"/"en" language tags the Transcriber returns).
 * If the on-device ASR is configured to emit Devanagari for pure Hindi, these
 * Latin keywords will NOT match that text — that is a known coverage gap, not a
 * silent pass: a Devanagari transcript simply scores as benign here rather than
 * pretending to detect scam content. Romanized Hinglish (the common code-mixed
 * register on Indian calls) is covered.
 */
object TextEngines {

    // ---- scam keywords (server _PHRASES + expanded Hindi/Hinglish, Phase 1.2b) ----
    private val SCAM_KEYWORDS: Map<String, List<String>> = mapOf(
        "bank_fraud" to listOf(
            "account blocked", "account suspended", "account freeze", "kyc",
            "kyc update", "kyc pending", "verify account", "update your details",
            "bank", "branch", "ifsc", "credit card block", "debit card block",
            "sim block", "sim band", "pan card update", "aadhaar update",
            "aadhaar link", "khata band", "khata bandh", "khata blocked",
            "bank se bol", "bank se call", "bank se baat",
        ),
        "otp_fraud" to listOf(
            "otp", "one time password", "verification code", "code aaya",
            "otp batao", "otp share", "otp bhejo", "code batao", "share the code",
            "read out the otp", "otp bata do",
        ),
        "upi_fraud" to listOf(
            "upi", "gpay", "phonepe", "paytm", "collect request", "payment request",
            "upi pin", "qr code scan", "qr scan karo", "scan this qr", "paisa bhejo",
            "autopay", "mandate",
        ),
        "lottery" to listOf(
            "lottery", "winner", "congratulations", "prize", "bumper",
            "inaam", "jeet gaye", "lottery laga", "lucky draw", "kbc",
            "kaun banega", "gift voucher",
        ),
        "threat" to listOf(
            "police", "arrest", "cbi", "court", "warrant", "jail", "fir",
            "giraftar", "police station", "case ho jayega", "ed officer",
            "enforcement directorate", "narcotics", "illegal",
        ),
        // Digital-arrest scam — the dominant Indian phone scam (fake CBI/police/ED
        // on a video call claiming the victim is under "digital arrest"). Distinct
        // category so results can name it in the FIR draft.
        "digital_arrest" to listOf(
            "digital arrest", "do not disconnect", "stay on the call",
            "video call par raho", "camera on rakho", "phone band mat karo",
            "supreme court", "money laundering", "drugs parcel", "narcotics parcel",
            "aadhaar misuse", "statement record", "under investigation",
            "cyber crime", "custody", "kisi ko mat batao",
        ),
        "loan" to listOf(
            "loan", "emi", "credit", "kist", "karz", "loan approve",
            "instant loan", "pre approved", "loan offer",
        ),
        "job" to listOf(
            "job", "naukri", "part-time", "work from home", "ghar baithe kamao",
            "earn daily", "registration fee", "task complete",
        ),
        "customs" to listOf(
            "customs", "parcel", "courier", "fedex", "dhl", "parcel ruka",
            "parcel seized", "custom duty", "parcel me drugs",
        ),
        "investment" to listOf(
            "investment", "trading", "guaranteed return", "double your money",
            "paisa double", "stock tip", "crypto profit", "telegram group",
        ),
        "refund" to listOf(
            "refund", "cashback", "wrong transaction", "wapas karo",
            "reverse the payment", "extra amount aa gaya", "overcharged",
        ),
    )

    private val PRESSURE = listOf(
        "urgent", "immediately", "right now", "hurry", "last chance",
        "turant", "abhi", "jaldi", "foran", "aakhri mauka", "ek minute me",
        "warna", "otherwise",
    )
    private val TIME_PRESSURE = listOf(
        "today only", "expires", "24 hours", "deadline", "aaj hi",
        "aaj last", "kal tak", "time khatm", "time limit", "aaj shaam tak",
        "within an hour", "ek ghante me",
    )
    private val THREAT = listOf(
        "blocked", "suspended", "arrest", "fine", "legal action", "penalty",
        "band ho jayega", "giraftar", "case", "jail", "seize",
        "block ho jayega", "kar di jayegi",
    )

    private val REQUEST_KEYWORDS: Map<String, List<String>> = mapOf(
        "money" to listOf(
            "transfer", "send money", "upi", "pay now", "paisa bhejo",
            "transfer karo", "payment karo", "deposit", "gpay karo",
        ),
        "otp" to listOf(
            "otp", "verification code", "code batao", "otp batao", "otp bhejo",
            "read the code", "otp bata do",
        ),
        "credential" to listOf(
            "password", "pin", "cvv", "card number", "upi pin", "password batao",
            "atm pin", "expiry date", "card ki detail",
        ),
        "remote-access" to listOf(
            "anydesk", "teamviewer", "quick support", "rustdesk", "screen share",
            "app download karo", "install the app", "download the app", "apk",
        ),
        "link" to listOf(
            "click", "link", "website kholo", "link par click", "click the link",
            "open the link", "install karo",
        ),
    )

    data class ScamResult(val prob: Float, val type: String?)
    data class IntentResult(val detected: Boolean, val type: String, val confidence: Float)

    /**
     * Tokenize to lowercased whole words, Unicode-aware (splits on anything that
     * is not a letter or digit, so punctuation like "URGENT!" -> "urgent" and
     * "blocked," -> "blocked"). \p{L}\p{N} keeps Devanagari runs intact too, so a
     * Devanagari transcript tokenizes cleanly even though the Latin dictionaries
     * won't match it (see the SCRIPT ASSUMPTION note above).
     */
    private fun tokenize(lower: String): Set<String> =
        lower.split(Regex("[^\\p{L}\\p{N}]+")).filterTo(HashSet()) { it.isNotEmpty() }

    /**
     * A keyword hits iff: it is a PHRASE (contains a space/hyphen/other non-alnum)
     * and occurs as a substring, OR it is a single token and appears as a WHOLE
     * word. This is the reliability fix — a bare "otp"/"case"/"emi" matches only
     * the standalone word, never a fragment of an unrelated word.
     */
    private fun keywordHit(keyword: String, lower: String, tokens: Set<String>): Boolean =
        if (keyword.any { !it.isLetterOrDigit() }) keyword in lower   // phrase / hyphenated
        else keyword in tokens                                        // single word

    /** Heuristic scam score: 1 hit ~0.33 (lone keyword never alerts alone). */
    fun scamHeuristic(text: String): ScamResult {
        val lower = text.lowercase()
        val tokens = tokenize(lower)
        val scores = SCAM_KEYWORDS.mapValues { (_, kws) -> kws.count { keywordHit(it, lower, tokens) } }
        val total = scores.values.sum()
        if (total == 0) return ScamResult(0.05f, null)
        val top = scores.maxBy { it.value }
        val prob = when {
            total >= 4 -> 0.9f
            total == 3 -> 0.75f
            total == 2 -> 0.55f
            else -> 0.33f
        }
        return ScamResult(prob, top.key)
    }

    fun intentHeuristic(text: String): IntentResult {
        val lower = text.lowercase()
        val tokens = tokenize(lower)
        val scores = REQUEST_KEYWORDS.mapValues { (_, kws) -> kws.count { keywordHit(it, lower, tokens) } }
        val total = scores.values.sum()
        if (total == 0) return IntentResult(false, "none", 0.6f)
        val top = scores.maxBy { it.value }
        return IntentResult(true, top.key, minOf(0.95f, 0.4f + 0.2f * top.value))
    }

    /** Mirrors server urgency heuristic: base 10 + phrase weights. */
    fun urgencyHeuristic(text: String): Float {
        val lower = text.lowercase()
        val tokens = tokenize(lower)
        val hits = PRESSURE.count { keywordHit(it, lower, tokens) }
        val timeH = TIME_PRESSURE.count { keywordHit(it, lower, tokens) }
        val threatH = THREAT.count { keywordHit(it, lower, tokens) }
        val excl = text.count { it == '!' } / (text.length + 1f)
        var score = 10 + hits * 12 + timeH * 14 + threatH * 22
        score += minOf(15, (excl * 600).toInt())
        return minOf(100, score).toFloat()
    }
}

/**
 * ASR slot. Implemented on-device by [SherpaOnnxTranscriber] (offline sherpa-onnx
 * Whisper), provided via Hilt (AppModule.provideTranscriber) and assigned to
 * ShieldPipeline.transcriber by ShieldService/ShieldViewModel. It returns null
 * (falls back to NoOp) when the sherpa AAR or the Whisper model isn't present, so
 * no transcript is ever fabricated; the pipeline still scores acoustic signals.
 */
interface Transcriber {
    /** Returns (text, language). Language: en|hi|hinglish. */
    fun transcribe(window4s: FloatArray): Pair<String, String>?
}

object NoOpTranscriber : Transcriber {
    override fun transcribe(window4s: FloatArray): Pair<String, String>? = null
}
