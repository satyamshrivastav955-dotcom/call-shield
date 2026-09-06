package com.codewithkael.simplecall.ai

/**
 * On-device signal bundle. Mirrors server AnalysisState keys consumed by
 * fusion_node() in server/src/antai/orchestration/nodes.py.
 * All probs 0..1 except urgency 0..100.
 */
data class ShieldSignals(
    val voiceDeepfake: Float? = null,      // spoof_prob
    val videoDeepfake: Float? = null,      // unused on audio-only shield
    val identityMismatch: Boolean = false, // ECAPA vs claimed voiceprint
    val collectiveFlagged: Boolean = false,// local reputation DB hit
    val scamProb: Float? = null,
    val urgency: Float? = null,            // 0..100
    val deviation: Float? = null,
    val lipsyncMismatch: Boolean = false,
    val requestDetected: Boolean = false,
    val requestType: String? = null,       // money|otp|credential|remote-access|link
    val requestConfidence: Float? = null,
)

enum class RiskBand { PASSIVE, VERIFY, CRITICAL }

data class ScenarioThresholds(
    val verifyAt: Float,
    val criticalAt: Float,
    val contextBoost: Float,
) {
    companion object {
        val ROUTINE = ScenarioThresholds(75f, 90f, 0f)
        val HIGH_VALUE_TXN = ScenarioThresholds(50f, 70f, 15f)
        val PRIVILEGED_ACCESS = ScenarioThresholds(40f, 60f, 20f)
        fun of(name: String?): ScenarioThresholds = when (name) {
            "high_value_txn" -> HIGH_VALUE_TXN
            "privileged_access" -> PRIVILEGED_ACCESS
            else -> ROUTINE
        }
    }
}

data class FusionResult(
    val risk: Float,
    val band: RiskBand,
    val hard: List<String>,
    val soft: List<String>,
    val capped: Boolean,
    val decision: String, // log|verify|freeze|escalate
)

/**
 * Exact Kotlin port of server fusion_node() + decision_node().
 * Dead zones + corroboration rule preserved so server eval numbers transfer:
 * capped to (verifyAt-1) unless a HARD signal fired or >=2 SOFT agree.
 */
object FusionEngine {

    private val REQUEST_WEIGHT = mapOf(
        "money" to 1.0f,
        "otp" to 1.0f,
        "credential" to 1.0f,
        "remote-access" to 0.9f,
        "link" to 0.5f,
    )

    private fun above(value: Float?, floor: Float, ceil: Float, weight: Float): Float {
        if (value == null) return 0f
        if (value <= floor) return 0f
        val span = maxOf(1e-6f, ceil - floor)
        return weight * minOf(1f, (value - floor) / span)
    }

    fun fuse(
        s: ShieldSignals,
        scenario: String? = null,
        txnAmountInr: Double? = null,
    ): FusionResult {
        var risk = 0f
        val hard = mutableListOf<String>()
        val soft = mutableListOf<String>()

        if (s.identityMismatch) { risk += 50; hard += "identity_mismatch" }
        if (s.collectiveFlagged) { risk += 45; hard += "collective_flagged" }
        val vd = above(s.voiceDeepfake, 0.78f, 0.95f, 60f)
        if (vd > 0f) { risk += vd; hard += "voice_deepfake" }
        val gd = above(s.videoDeepfake, 0.80f, 0.97f, 50f)
        if (gd > 0f) { risk += gd; hard += "video_deepfake" }

        if (s.requestDetected) {
            val rtype = s.requestType ?: "link"
            val conf = s.requestConfidence ?: 0.5f
            val sev = REQUEST_WEIGHT[rtype] ?: 0.5f
            val contrib = above(conf, 0.60f, 0.95f, 50f * sev)
            if (contrib > 0f) {
                risk += contrib
                if (sev >= 0.9f && conf >= 0.80f) hard += "request:$rtype"
                else soft += "request:$rtype"
            }
        }

        val sp = above(s.scamProb, 0.55f, 0.95f, 35f)
        if (sp > 0f) { risk += sp; soft += "scam_pattern" }
        val ug = above(s.urgency, 60f, 95f, 22f)
        if (ug > 0f) { risk += ug; soft += "urgency" }
        val dv = above(s.deviation, 0.55f, 1.0f, 12f)
        if (dv > 0f) { risk += dv; soft += "behavioral_deviation" }
        if (s.lipsyncMismatch) { risk += 12; soft += "lipsync_mismatch" }

        val sc = ScenarioThresholds.of(scenario)
        var boost = sc.contextBoost
        if (txnAmountInr != null && txnAmountInr >= 500_000) {
            boost = minOf(boost + 10f, 20f)
        }
        risk = minOf(100f, risk + boost)

        var capped = false
        if (hard.isEmpty() && soft.size < 2) {
            val ceiling = maxOf(0f, sc.verifyAt - 1f)
            if (risk > ceiling) { risk = ceiling; capped = true }
        }

        val band = when {
            risk < sc.verifyAt -> RiskBand.PASSIVE
            risk < sc.criticalAt -> RiskBand.VERIFY
            else -> RiskBand.CRITICAL
        }
        val decision = when {
            risk >= 70 && s.requestDetected -> "freeze"
            risk >= 70 -> "escalate"
            band == RiskBand.VERIFY -> "verify"
            else -> "log"
        }
        return FusionResult(
            risk = ((risk * 10).toInt()) / 10f,
            band = band,
            hard = hard,
            soft = soft,
            capped = capped,
            decision = decision,
        )
    }
}
