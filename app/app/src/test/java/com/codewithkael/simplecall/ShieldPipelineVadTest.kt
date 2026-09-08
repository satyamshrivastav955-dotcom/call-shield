package com.codewithkael.simplecall

import com.codewithkael.simplecall.ai.ShieldPipeline
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

/**
 * #4 VAD gate: near-silent windows are skipped so the pipeline neither wastes
 * battery on ORT/prosody nor scores meaningless silence. Pure RMS, no Silero.
 */
class ShieldPipelineVadTest {

    @Test fun pureSilence_isGated() {
        assertTrue(ShieldPipeline.isSilence(FloatArray(4 * 16000)))
    }

    @Test fun emptyWindow_isGated() {
        assertTrue(ShieldPipeline.isSilence(FloatArray(0)))
    }

    @Test fun veryFaintNoise_isGated() {
        // ~0.002 RMS — genuine near-silence (faint room tone) stays gated below
        // the lowered 0.0035 floor.
        assertTrue(ShieldPipeline.isSilence(FloatArray(16000) { 0.002f }))
    }

    @Test fun quietSpeech_isNotGated() {
        // #6 low-volume fix: a soft talker / low mic gain lands ~0.0057 RMS —
        // UNDER the old 0.008 floor that silently dropped the whole window. The
        // lowered floor lets quiet speech reach the pipeline instead of being
        // treated as silence.
        val w = FloatArray(16000) { i -> 0.008f * sin(2.0 * Math.PI * 180 * i / 16000).toFloat() }
        assertFalse(ShieldPipeline.isSilence(w))
    }

    @Test fun constantHum_passesEnergyGateButNotSpeechLike() {
        // #6: lowering the silence floor lets a faint steady hum clear the energy
        // gate, so the ACOUSTIC models must still be protected by the variance
        // gate — otherwise a stationary tone scores 0.9999 "synthetic". A constant
        // signal has zero frame-energy spread, so isSpeechLike rejects it.
        val hum = FloatArray(4 * 16000) { 0.02f }
        assertFalse("hum clears the lowered energy gate", ShieldPipeline.isSilence(hum))
        assertFalse("but hum is rejected as non-speech", ShieldPipeline.isSpeechLike(hum))
    }

    @Test fun normalSpeechLevel_passes() {
        // 200 Hz tone at 0.2 amplitude -> RMS ~0.14, clearly speech-level.
        val w = FloatArray(16000) { i -> 0.2f * sin(2.0 * Math.PI * 200 * i / 16000).toFloat() }
        assertFalse(ShieldPipeline.isSilence(w))
    }

    // ---- #5 anti-stall session scam memory ----

    @Test fun scamMemory_freshScamSnapsUp() {
        // A new high scam score lifts the memory immediately (no smoothing lag).
        assertEquals(0.9f, ShieldPipeline.scamMemory(prev = 0.2f, live = 0.9f), 1e-4f)
    }

    @Test fun scamMemory_benignWindowDecaysGently() {
        // A benign (0) window only lets the peak decay by one step, not reset.
        assertEquals(0.873f, ShieldPipeline.scamMemory(prev = 0.9f, live = 0f), 1e-3f)
    }

    @Test fun scamMemory_survivesStallingButFadesEventually() {
        // Scam detected once (0.9), then the caller fills windows with benign
        // small talk. The memory must stay above the fusion scam floor (0.55) long
        // enough to defeat stalling (~10 windows ≈ 40s), yet fade well below it on
        // a truly benign call (~30 windows ≈ 2 min).
        var m = ShieldPipeline.scamMemory(0f, 0.9f)
        repeat(10) { m = ShieldPipeline.scamMemory(m, 0f) }
        assertTrue("still influential after ~40s of small talk", m > 0.55f)
        repeat(20) { m = ShieldPipeline.scamMemory(m, 0f) }
        assertTrue("faded below the scam floor after ~2 min benign", m < 0.55f)
    }

    // ---- #7 gray-zone identity classification ----

    @Test fun identity_clearMatchAboveFloor() {
        // At or above the floor is a genuine match — no signal.
        assertEquals(ShieldPipeline.IdentityVerdict.MATCH,
            ShieldPipeline.classifyIdentity(0.72f, floor = 0.60f))
        assertEquals(ShieldPipeline.IdentityVerdict.MATCH,
            ShieldPipeline.classifyIdentity(0.60f, floor = 0.60f))
    }

    @Test fun identity_grayZoneIsInconclusive() {
        // Just under the floor (within the 0.15 margin): a sibling / cold voice /
        // codec-degraded genuine speaker -> INCONCLUSIVE, never a hard mismatch.
        assertEquals(ShieldPipeline.IdentityVerdict.INCONCLUSIVE,
            ShieldPipeline.classifyIdentity(0.52f, floor = 0.60f))
        // Bonafide/replay floor 0.45: a 0.40 sim is still only inconclusive.
        assertEquals(ShieldPipeline.IdentityVerdict.INCONCLUSIVE,
            ShieldPipeline.classifyIdentity(0.40f, floor = 0.45f))
        // Lower edge of the band is inclusive.
        assertEquals(ShieldPipeline.IdentityVerdict.INCONCLUSIVE,
            ShieldPipeline.classifyIdentity(0.45f, floor = 0.60f))
    }

    @Test fun identity_confidentMismatchBelowMargin() {
        // A true impostor scores far below the floor -> confident MISMATCH (hard).
        assertEquals(ShieldPipeline.IdentityVerdict.MISMATCH,
            ShieldPipeline.classifyIdentity(0.20f, floor = 0.45f))
        assertEquals(ShieldPipeline.IdentityVerdict.MISMATCH,
            ShieldPipeline.classifyIdentity(0.10f, floor = 0.60f))
    }
}
