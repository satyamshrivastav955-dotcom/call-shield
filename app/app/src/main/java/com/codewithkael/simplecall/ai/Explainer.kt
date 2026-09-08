package com.codewithkael.simplecall.ai

/**
 * Template explainer (on-device default). The LLM is explanation-only by
 * design invariant (server decision_node is deterministic) — so the offline
 * fallback is a slot-fill template, not an NPU LLM. Optional llama.cpp
 * Qwen2.5-1.5B Q4 plugs in later behind the same interface.
 */
object Explainer {
    fun explain(risk: Float, hard: List<String>, soft: List<String>, lang: String = "en"): String {
        val level = when {
            risk >= 70 -> if (lang == "hi") "उच्च जोखिम" else "High risk"
            risk >= 40 -> if (lang == "hi") "मध्यम जोखिम" else "Medium risk"
            else -> if (lang == "hi") "कम जोखिम" else "Low risk"
        }
        // #10: show a plain-language reason, not raw engine tokens. The raw
        // hard/soft lists are left untouched (logic + logging still key off
        // "identity_mismatch" etc.); only this human-readable string changes.
        val signals = humanizeSignals(hard + soft, lang, max = 3).ifEmpty {
            if (lang == "hi") "कोई स्पष्ट संकेत नहीं" else "no clear signals"
        }
        val action = when {
            risk >= 70 -> if (lang == "hi") "पैसे/OTP न दें। कॉल काटकर आधिकारिक नंबर पर वापस कॉल करें।"
                else "Do not send money/OTP. Hang up and call back on the official number."
            risk >= 40 -> if (lang == "hi") "पहचान सत्यापित करें।"
                else "Verify the caller before acting."
            else -> if (lang == "hi") "सावधान रहें।" else "Stay cautious."
        }
        return "$level (${risk.toInt()}/100). $signals. $action"
    }

    /**
     * Join up to [max] raw fusion signal tokens into a short human phrase for
     * display. Pure/JVM-testable. Callers pass the verdict's hard+soft lists;
     * the tokens themselves are never mutated.
     */
    fun humanizeSignals(tokens: List<String>, lang: String = "en", max: Int = 3): String =
        tokens.take(max).joinToString(", ") { humanizeSignal(it, lang) }

    /**
     * Map ONE raw signal token (e.g. "voice_deepfake", "request:money") to a
     * plain-language phrase. Unknown/new tokens degrade gracefully to a
     * de-underscored form instead of leaking snake_case to the user.
     */
    fun humanizeSignal(token: String, lang: String = "en"): String {
        val hi = lang == "hi"
        if (token.startsWith("request:")) {
            return when (token.substringAfter("request:")) {
                "money"      -> if (hi) "पैसे की मांग" else "a request for money"
                "otp"        -> if (hi) "OTP की मांग" else "a request for an OTP"
                "credential" -> if (hi) "पासवर्ड/जानकारी की मांग" else "a request for passwords"
                "link"       -> if (hi) "लिंक खोलने का दबाव" else "pressure to open a link"
                else         -> if (hi) "संदिग्ध मांग" else "a suspicious request"
            }
        }
        return when (token) {
            "voice_deepfake"       -> if (hi) "नकली (क्लोन) आवाज़" else "synthetic (cloned) voice"
            "spoof_suspect"        -> if (hi) "आवाज़ शायद नकली" else "possibly synthetic voice"
            "identity_mismatch"    -> if (hi) "आवाज़ सेव किए संपर्क से मेल नहीं खाती" else "voice doesn't match the saved contact"
            "identity_unconfirmed" -> if (hi) "कॉलर की आवाज़ की पुष्टि नहीं" else "caller's voice unconfirmed"
            "scam_pattern"         -> if (hi) "जाना-पहचाना स्कैम पैटर्न" else "a known scam script"
            "urgency"              -> if (hi) "जल्दबाज़ी का दबाव" else "high-pressure urgency"
            "behavioral_deviation" -> if (hi) "इस संपर्क के लिए असामान्य व्यवहार" else "unusual behaviour for this contact"
            "lipsync_mismatch"     -> if (hi) "वीडियो में होंठ-आवाज़ बेमेल" else "lip-sync mismatch on video"
            "collective_flagged"   -> if (hi) "अन्य लोगों द्वारा रिपोर्ट की गई" else "reported by other users"
            "video_deepfake"       -> if (hi) "डीपफेक (छेड़छाड़ की गई) वीडियो" else "manipulated (deepfake) video"
            else -> token.replace('_', ' ')
        }
    }
}
