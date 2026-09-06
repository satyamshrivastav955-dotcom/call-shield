package com.codewithkael.simplecall.ai

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * True acoustic prosody (PS26104 literal gap: pitch/rhythm/pauses).
 * Pure DSP, no ML, no NPU — runs on CPU per 4s window.
 * Server parity target: feeds urgency/deviation-adjacent soft signals;
 * FusionEngine consumes urgency 0..100 + deviation 0..1.
 */
object ProsodyEngine {

    data class Prosody(val pauseRatio: Float, val speechRate: Float, val energyVar: Float)

    /** RMS-based pause ratio + zero-crossing speech-rate proxy + energy variance. */
    fun analyze(pcm16k: FloatArray, sampleRate: Int = 16000): Prosody {
        if (pcm16k.isEmpty()) return Prosody(0f, 0f, 0f)
        val frame = 320 // 20ms @16k
        var pause = 0; var frames = 0
        var crossings = 0
        val energies = FloatArray((pcm16k.size + frame - 1) / frame)
        var i = 0; var fi = 0
        while (i < pcm16k.size) {
            val end = minOf(i + frame, pcm16k.size)
            var e = 0f
            for (j in i until end) e += pcm16k[j] * pcm16k[j]
            e = sqrt(e / (end - i))
            energies[fi++] = e
            if (e < 0.02f) pause++
            frames++
            for (j in i + 1 until end) {
                if ((pcm16k[j] >= 0) != (pcm16k[j - 1] >= 0)) crossings++
            }
            i = end
        }
        val mean = energies.average().toFloat()
        var v = 0f
        for (e in energies) v += (e - mean) * (e - mean)
        v /= maxOf(1, energies.size)
        val durS = pcm16k.size / sampleRate.toFloat()
        return Prosody(
            pauseRatio = pause / maxOf(1, frames).toFloat(),
            speechRate = crossings / maxOf(1e-6f, durS),
            energyVar = v,
        )
    }

    /** Heuristic urgency 0..100 from prosody (fast + loud-variable + few pauses). */
    fun urgencyHint(p: Prosody): Float {
        var u = 30f
        if (p.speechRate > 2500) u += 25
        else if (p.speechRate > 1500) u += 12
        if (p.energyVar > 0.02f) u += 15
        if (p.pauseRatio < 0.15f) u += 15
        return u.coerceIn(0f, 100f)
    }
}
