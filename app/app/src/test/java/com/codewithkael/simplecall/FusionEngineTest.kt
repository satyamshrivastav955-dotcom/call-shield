package com.codewithkael.simplecall

import com.codewithkael.simplecall.ai.FusionEngine
import com.codewithkael.simplecall.ai.RiskBand
import com.codewithkael.simplecall.ai.ShieldSignals
import org.junit.Assert.*
import org.junit.Test

/**
 * Fusion contract + the P0 corroboration gate (2026-09-08).
 *
 * The int8 AST spoof model saturates (~1.0) on ordinary speech, so a high spoof
 * score ALONE must not raise a hard alarm. These tests pin that behaviour:
 *  - a lone saturated spoof is a soft "spoof_suspect" and stays PASSIVE, even
 *    under the aggressive high_value_txn profile (the reported false positive);
 *  - the SAME spoof escalates to HARD voice_deepfake / CRITICAL the moment an
 *    independent identity or content signal corroborates it.
 */
class FusionEngineTest {

    @Test fun loneSaturatedSpoof_notCriticalEvenHighValue() {
        // P0 REGRESSION: 0.999 spoof, nothing else. Under high_value_txn this
        // used to be 60 (spoof) + 15 (boost) = 75 = CRITICAL on a casual call.
        // Now it is downgraded to a soft hint and stays PASSIVE.
        val r = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.999f),
            scenario = "high_value_txn",
        )
        assertFalse("lone spoof must not be HARD", r.hard.contains("voice_deepfake"))
        assertTrue("downgraded to a soft suspect hint", r.soft.contains("spoof_suspect"))
        assertEquals(RiskBand.PASSIVE, r.band)
        // And under the most aggressive profile it is still not critical.
        val priv = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.999f),
            scenario = "privileged_access",
        )
        assertNotEquals(RiskBand.CRITICAL, priv.band)
    }

    @Test fun spoofPlusIdentityMismatch_escalatesToHardCritical() {
        // Independent IDENTITY corroboration (enrolled contact, voice contradicts)
        // -> corroboration 50 >= floor -> voice_deepfake becomes HARD -> CRITICAL.
        val r = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.999f, identityMismatch = true),
        )
        assertTrue(r.hard.contains("voice_deepfake"))
        assertTrue(r.hard.contains("identity_mismatch"))
        assertEquals(RiskBand.CRITICAL, r.band)
    }

    @Test fun spoofPlusScamContent_escalatesToHard() {
        // Independent CONTENT corroboration (a high-confidence money request,
        // contrib ~35 >= floor) also restores the HARD voice_deepfake verdict.
        val r = FusionEngine.fuse(
            ShieldSignals(
                voiceDeepfake = 0.999f,
                requestDetected = true,
                requestType = "money",
                requestConfidence = 0.85f,
            ),
            scenario = "high_value_txn",
        )
        assertTrue(r.hard.contains("voice_deepfake"))
        assertTrue(r.hard.contains("request:money"))
        assertEquals(RiskBand.CRITICAL, r.band)
    }

    @Test fun urgencyDoesNotCorroborateSpoof() {
        // Urgency co-occurs with a stressed-but-genuine caller, so it must NOT
        // turn an acoustic hunch into a hard clone verdict. Spoof stays soft.
        val r = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.999f, urgency = 95f),
            scenario = "high_value_txn",
        )
        assertFalse(r.hard.contains("voice_deepfake"))
        assertTrue(r.soft.contains("spoof_suspect"))
        assertTrue(r.soft.contains("urgency"))
    }

    @Test fun singleSoft_cappedBelowVerify() {
        // routine_call verifyAt=75 -> ceiling 74. A lone strong soft that would
        // exceed it gets capped; a weak one passes through uncapped but passive.
        val weak = FusionEngine.fuse(ShieldSignals(scamProb = 0.9f))
        assertEquals(RiskBand.PASSIVE, weak.band)
        val strong = FusionEngine.fuse(
            ShieldSignals(scamProb = 0.99f, urgency = null),
            scenario = "privileged_access", // verifyAt=40, boost=20
        )
        // scam contrib 35 + boost 20 = 55 alone -> capped to 39
        assertTrue(strong.capped)
        assertTrue(strong.risk <= 39f)
    }

    @Test fun twoSofts_agreeAndPass() {
        val r = FusionEngine.fuse(ShieldSignals(scamProb = 0.95f, urgency = 95f))
        assertFalse(r.capped)
        assertTrue(r.risk >= 50f)
    }

    @Test fun identityMismatch_isHard50() {
        val r = FusionEngine.fuse(ShieldSignals(identityMismatch = true))
        assertEquals(50f, r.risk, 0.01f)
    }

    // ---- #7 gray-zone identity: inconclusive is a SOFT nudge, never a hard alarm ----

    @Test fun identityInconclusive_isSoftNotHard() {
        // A borderline similarity (sibling / cold voice / codec degradation) must
        // NOT fire the hard +50 identity_mismatch — only a soft "identity_unconfirmed".
        val r = FusionEngine.fuse(ShieldSignals(identityInconclusive = true))
        assertFalse("gray zone must not be a hard mismatch", r.hard.contains("identity_mismatch"))
        assertTrue(r.soft.contains("identity_unconfirmed"))
        assertEquals(RiskBand.PASSIVE, r.band)
    }

    @Test fun loneIdentityInconclusive_notCriticalUnderPrivileged() {
        // #7 CORE GUARANTEE: under the most aggressive profile a CONFIDENT mismatch
        // is CRITICAL (50 + 20 boost), but a GRAY-ZONE verdict on the same call must
        // stay well below that — a genuine relative is never alarmed as an impostor.
        val gray = FusionEngine.fuse(
            ShieldSignals(identityInconclusive = true),
            scenario = "privileged_access",
        )
        assertNotEquals(RiskBand.CRITICAL, gray.band)
        val confident = FusionEngine.fuse(
            ShieldSignals(identityMismatch = true),
            scenario = "privileged_access",
        )
        assertEquals(RiskBand.CRITICAL, confident.band)
    }

    @Test fun inconclusiveDoesNotCorroborateSpoof() {
        // An ambiguous identity is not independent proof of a clone, so it must NOT
        // corroborate a saturated spoof into a HARD voice_deepfake. Both stay soft.
        val r = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.999f, identityInconclusive = true),
            scenario = "high_value_txn",
        )
        assertFalse(r.hard.contains("voice_deepfake"))
        assertTrue(r.soft.contains("spoof_suspect"))
        assertTrue(r.soft.contains("identity_unconfirmed"))
        assertNotEquals(RiskBand.CRITICAL, r.band)
    }

    @Test fun inconclusivePlusScam_raisesVerifyNotCritical() {
        // Gray-zone identity + real scam content: two soft signals agree, so this
        // escapes the dead-zone cap and raises a VERIFY prompt — but content-driven
        // ambiguity alone must not reach CRITICAL.
        val r = FusionEngine.fuse(
            ShieldSignals(scamProb = 0.9f, identityInconclusive = true),
            scenario = "high_value_txn",
        )
        assertEquals(RiskBand.VERIFY, r.band)
        assertTrue(r.soft.contains("identity_unconfirmed"))
        assertTrue(r.soft.contains("scam_pattern"))
    }

    @Test fun highValueScenario_boostsAndLowersBand() {
        // 0.93 lands in the SOFT band (0.90..0.97) -> a small spoof_suspect hint.
        // high_value_txn adds a +15 context boost on top, so its risk exceeds the
        // routine profile's for the identical signal. (Both stay passive: a lone
        // uncorroborated soft can't reach verify — that's the P0 gate above.)
        val r = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.93f),
            scenario = "high_value_txn",
        )
        val routine = FusionEngine.fuse(ShieldSignals(voiceDeepfake = 0.93f))
        assertTrue(r.risk > routine.risk)
        assertTrue(r.soft.contains("spoof_suspect"))
    }
}
