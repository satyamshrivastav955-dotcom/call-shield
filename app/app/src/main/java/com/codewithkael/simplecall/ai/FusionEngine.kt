package com.codewithkael.simplecall.ai

/**
 * On-device signal bundle. Mirrors server AnalysisState keys consumed by
 * fusion_node() in server/src/antai/orchestration/nodes.py.
 * All probs 0..1 except urgency 0..100.
 */
data class ShieldSignals(
    val voiceDeepfake: Float? = null,      // spoof_prob
    val videoDeepfake: Float? = null,      // unused on audio-only shield
    val identityMismatch: Boolean = false, // ECAPA vs claimed voiceprint (CONFIDENT mismatch)
    val identityInconclusive: Boolean = false, // #7 gray zone: borderline sim (sibling / cold / codec)
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

    /**
     * Minimum independent (content/identity) corroboration required before a
     * near-certain acoustic spoof verdict is allowed to escalate to a HARD
     * voice_deepfake signal. Below this, a saturated spoof score is treated as a
     * soft "spoof_suspect" hint only. 20f is comfortably below every corroborator
     * (identity_mismatch=50, collective=45, request:money≈28+, video≥~40) so any
     * ONE genuine supporting signal restores the hard verdict, while acoustics
     * ALONE (the false-positive case) never can.
     */
    private const val CORROBORATION_FLOOR = 20f

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

        // ---- CORROBORATION ACCUMULATOR (P0 root-cause fix, 2026-09-08) ----
        // The int8 AST spoof model SATURATES on this device: a pure sine scores
        // 0.99999 and ASVspoof5 validation showed ~93% of bonafide clips flagged
        // at thr 0.5 (eer_threshold ≈ 1.0, ROC-AUC ≈ 0.71). So the raw acoustic
        // "this is synthetic" verdict alone is NOT trustworthy — on a casual call
        // it would otherwise add +60 HARD and, under high_value_txn (+15 boost),
        // reach 75 = CRITICAL with zero supporting evidence (the reported false
        // positive). Fix: a high spoof score only escalates to a HARD signal when
        // an INDEPENDENT content/identity channel agrees. We sum those corroborating
        // contributions FIRST, then gate the voice signal on them below.
        // NOTE (honest trade-off, per project HARD RULE): voice acoustics alone no
        // longer raise an alarm. To demo escalation, pair a clone with scam content
        // or run it against an ENROLLED contact (identity_mismatch) — either
        // corroborates and restores the HARD voice_deepfake verdict.
        var corroboration = 0f

        // Identity + local reputation: independent of acoustics, always HARD.
        if (s.identityMismatch) { risk += 50; hard += "identity_mismatch"; corroboration += 50f }
        // Gray-zone identity (#7): the caller's voiceprint neither clearly matches
        // the claimed contact NOR clearly mismatches — a sibling, a cold/ill voice,
        // or codec degradation lands here, and ECAPA cosine on a short 4s window is
        // jittery. Firing the hard +50 identity_mismatch on this used to push a
        // GENUINE relative to CRITICAL (esp. under privileged_access, verify=40).
        // Treat it as a SOFT "identity_unconfirmed" nudge: it can help raise a
        // verify prompt alongside a second soft signal, but never alarms alone and
        // deliberately does NOT corroborate a spoof verdict (an ambiguous identity
        // is not independent proof of a clone).
        else if (s.identityInconclusive) { risk += 12f; soft += "identity_unconfirmed" }
        if (s.collectiveFlagged) { risk += 45; hard += "collective_flagged"; corroboration += 45f }

        // Video deepfake (separate sensory channel) — corroborates a voice clone.
        val gd = above(s.videoDeepfake, 0.80f, 0.97f, 50f)
        if (gd > 0f) { risk += gd; hard += "video_deepfake"; corroboration += gd }

        // Request-for-action (money/otp/credential/remote-access/link): CONTENT
        // evidence — a clone asking for money is the actual threat. Corroborates.
        if (s.requestDetected) {
            val rtype = s.requestType ?: "link"
            val conf = s.requestConfidence ?: 0.5f
            val sev = REQUEST_WEIGHT[rtype] ?: 0.5f
            val contrib = above(conf, 0.60f, 0.95f, 50f * sev)
            if (contrib > 0f) {
                risk += contrib
                corroboration += contrib
                if (sev >= 0.9f && conf >= 0.80f) hard += "request:$rtype"
                else soft += "request:$rtype"
            }
        }

        // Scam-pattern text — CONTENT evidence, corroborates.
        val sp = above(s.scamProb, 0.55f, 0.95f, 35f)
        if (sp > 0f) { risk += sp; soft += "scam_pattern"; corroboration += sp }

        // ---- VOICE SPOOF, corroboration-gated (recalibrated 2026-09) ----
        //   HARD  voice_deepfake: 0.97..0.995 (w60) — near-certain synthetic
        //   SOFT  spoof_suspect:  0.90..0.97  (w15) — corroborating hint only
        // A HARD acoustic verdict escalates to the +60 HARD risk signal ONLY when
        // corroboration >= CORROBORATION_FLOOR. Otherwise it is DOWNGRADED to the
        // soft "spoof_suspect" hint (+15), which the dead-zone cap below then holds
        // under verify unless a second soft signal agrees. This is what kills the
        // lone-saturated-spoof CRITICAL on casual calls.
        val vdHard = above(s.voiceDeepfake, 0.97f, 0.995f, 60f)
        val vdSoft = above(s.voiceDeepfake, 0.90f, 0.97f, 15f)
        if (vdHard > 0f && corroboration >= CORROBORATION_FLOOR) {
            risk += vdHard; hard += "voice_deepfake"
        } else if (vdHard > 0f) {
            // Saturated acoustics, nothing corroborates -> suspect hint only.
            risk += 15f; soft += "spoof_suspect"
        } else if (vdSoft > 0f) {
            risk += vdSoft; soft += "spoof_suspect"
        }

        // ---- Signals that must NOT corroborate a clone ----
        // Urgency, prosody-driven stress, behavioral deviation and lipsync all
        // co-occur with a STRESSED-BUT-GENUINE caller, so they add to risk but are
        // deliberately excluded from `corroboration` (they cannot, on their own,
        // turn an acoustic hunch into a hard clone verdict).
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
