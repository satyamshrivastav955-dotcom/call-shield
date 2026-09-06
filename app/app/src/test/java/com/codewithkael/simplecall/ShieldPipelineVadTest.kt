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

    @Test fun lowNoiseFloor_isGated() {
        // ~0.004 RMS constant hum sits below the 0.008 floor.
        assertTrue(ShieldPipeline.isSilence(FloatArray(16000) { 0.004f }))
    }

    @Test fun normalSpeechLevel_passes() {
        // 200 Hz tone at 0.2 amplitude -> RMS ~0.14, clearly speech-level.
        val w = FloatArray(16000) { i -> 0.2f * sin(2.0 * Math.PI * 200 * i / 16000).toFloat() }
        assertFalse(ShieldPipeline.isSilence(w))
    }
}
