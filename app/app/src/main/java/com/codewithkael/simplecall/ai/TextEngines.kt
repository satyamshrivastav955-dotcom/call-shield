package com.codewithkael.simplecall.ai

/**
 * On-device text signals. Mirrors server contracts:
 * - ScamPatternEngine.analyze(): {scam_prob, scam_type} (9 labels: benign + 8)
 * - IntentEngine.classify(): {request_detected, request_type, confidence}
 * - UrgencyEngine.score(): {urgency 0..100}
 *
 * Each has: ORT session hook (weights from export_onnx.py) + keyword-heuristic
 * fallback identical in spirit to the server _heuristic() paths, extended with
 * Hindi/Hinglish phrases for the multilingual requirement. ORT path activates
 * automatically once the .onnx + vocab files are present; until then the
 * heuristic keeps the pipeline fully functional offline.
 */
object TextEngines {

    // ---- scam keywords (server _PHRASES + Hindi/Hinglish additions) ----
    private val SCAM_KEYWORDS: Map<String, List<String>> = mapOf(
        "bank_fraud" to listOf(
            "account blocked", "account suspended", "kyc", "verify account",
            "bank", "branch", "ifsc", "khata band", "khata blocked",
            "kyc update", "bank se bol",
        ),
        "otp_fraud" to listOf(
            "otp", "one time password", "verification code", "code aaya",
            "otp batao", "otp share", "code batao",
        ),
        "upi_fraud" to listOf(
            "upi", "gpay", "phonepe", "paytm", "collect request",
            "upi pin", "qr code scan", "qr scan karo", "paisa bhejo",
        ),
        "lottery" to listOf(
            "lottery", "winner", "congratulations", "prize", "bumper",
            "inaam", "jeet gaye", "lottery laga",
        ),
        "threat" to listOf(
            "police", "arrest", "cbi", "court", "warrant", "jail",
            "giraftar", "police station", "case ho jayega",
        ),
        "loan" to listOf("loan", "emi", "credit", "kist", "karz", "loan approve"),
        "job" to listOf("job", "naukri", "part-time", "work from home", "ghar baithe kamao"),
        "customs" to listOf("customs", "parcel", "courier", "fedex", "parcel ruka"),
    )

    private val PRESSURE = listOf(
        "urgent", "immediately", "right now", "hurry", "last chance",
        "turant", "abhi", "jaldi", "foran", "aakhri mauka",
    )
    private val TIME_PRESSURE = listOf(
        "today only", "expires", "24 hours", "deadline", "aaj hi",
        "aaj last", "kal tak", "time khatm",
    )
    private val THREAT = listOf(
        "blocked", "suspended", "arrest", "fine", "legal action",
        "band ho jayega", "giraftar", "case",
    )

    private val REQUEST_KEYWORDS: Map<String, List<String>> = mapOf(
        "money" to listOf("transfer", "send money", "upi", "pay", "paisa bhejo", "transfer karo"),
        "otp" to listOf("otp", "verification code", "code batao", "otp batao"),
        "credential" to listOf("password", "pin", "cvv", "card number", "upi pin", "password batao"),
        "remote-access" to listOf("anydesk", "teamviewer", "screen share", "app download karo", "apk"),
        "link" to listOf("click", "link", "website kholo", "link par click"),
    )

    data class ScamResult(val prob: Float, val type: String?)
    data class IntentResult(val detected: Boolean, val type: String, val confidence: Float)

    /** Heuristic scam score: 1 hit ~0.33 (lone keyword never alerts alone). */
    fun scamHeuristic(text: String): ScamResult {
        val lower = text.lowercase()
        val scores = SCAM_KEYWORDS.mapValues { (_, kws) -> kws.count { it in lower } }
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
        val scores = REQUEST_KEYWORDS.mapValues { (_, kws) -> kws.count { it in lower } }
        val total = scores.values.sum()
        if (total == 0) return IntentResult(false, "none", 0.6f)
        val top = scores.maxBy { it.value }
        return IntentResult(true, top.key, minOf(0.95f, 0.4f + 0.2f * top.value))
    }

    /** Mirrors server urgency heuristic: base 10 + phrase weights. */
    fun urgencyHeuristic(text: String): Float {
        val lower = text.lowercase()
        val hits = PRESSURE.count { it in lower }
        val timeH = TIME_PRESSURE.count { it in lower }
        val threatH = THREAT.count { it in lower }
        val excl = text.count { it == '!' } / (text.length + 1f)
        var score = 10 + hits * 12 + timeH * 14 + threatH * 22
        score += minOf(15, (excl * 600).toInt())
        return minOf(100, score).toFloat()
    }
}

/**
 * ASR slot. On-device whisper.cpp / sherpa-onnx JNI binds here once the
 * native lib ships; until then transcripts arrive from file metadata or the
 * (deprecated) server path, and the pipeline still scores acoustic signals.
 */
interface Transcriber {
    /** Returns (text, language). Language: en|hi|hinglish. */
    fun transcribe(window4s: FloatArray): Pair<String, String>?
}

object NoOpTranscriber : Transcriber {
    override fun transcribe(window4s: FloatArray): Pair<String, String>? = null
}
