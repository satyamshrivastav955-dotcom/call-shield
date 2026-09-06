package com.codewithkael.simplecall

import com.codewithkael.simplecall.ai.FusionEngine
import com.codewithkael.simplecall.ai.RiskBand
import com.codewithkael.simplecall.ai.ShieldSignals
import org.junit.Assert.*
import org.junit.Test

/**
 * Parity with server fusion_node() (nodes.py):
 * 0.90 voice -> ~42 verify; >=0.95 -> 60; single soft capped; two softs pass.
 */
class FusionEngineTest {

    @Test fun voice90_scores42() {
        val r = FusionEngine.fuse(ShieldSignals(voiceDeepfake = 0.90f))
        assertEquals(42.3f, r.risk, 0.6f)
        assertTrue(r.hard.contains("voice_deepfake"))
        // routine_call verifyAt=75 -> 42 is passive; high_value (50) -> verify
        assertEquals(RiskBand.PASSIVE, r.band)
        val hv = FusionEngine.fuse(ShieldSignals(voiceDeepfake = 0.90f), scenario = "high_value_txn")
        assertEquals(RiskBand.VERIFY, hv.band)
    }

    @Test fun voice95_scores60() {
        val r = FusionEngine.fuse(ShieldSignals(voiceDeepfake = 0.95f))
        assertEquals(60f, r.risk, 0.5f)
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

    @Test fun highValueScenario_boostsAndLowersBand() {
        val r = FusionEngine.fuse(
            ShieldSignals(voiceDeepfake = 0.85f),
            scenario = "high_value_txn",
        )
        // voice contrib ~24.7 + boost 15 = ~39.7 -> still passive under 50? no:
        // verifyAt=50 so 39.7 passive; assert boost applied vs routine
        val routine = FusionEngine.fuse(ShieldSignals(voiceDeepfake = 0.85f))
        assertTrue(r.risk > routine.risk)
    }
}
