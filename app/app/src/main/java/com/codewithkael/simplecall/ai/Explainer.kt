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
        val signals = (hard + soft).take(3).joinToString(", ").ifEmpty {
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
}
