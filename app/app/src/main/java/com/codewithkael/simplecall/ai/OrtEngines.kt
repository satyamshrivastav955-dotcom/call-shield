package com.codewithkael.simplecall.ai

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Base for ORT-Mobile sessions. Subclasses bind the model file from
 * ModelManager and implement score(). Sessions are lazy: missing file ->
 * returns null so FusionEngine degrades gracefully (same contract as the
 * server _safe() guards in nodes.py).
 */
abstract class OrtEngine(
    protected val models: ModelManager,
    protected val modelFile: String,
) {
    protected var env: OrtEnvironment? = null
    protected var session: OrtSession? = null

    fun ready(): Boolean {
        if (session != null) return true
        return try {
            val f = java.io.File(models.pathFor(modelFile))
            if (!f.exists()) return false
            env = OrtEnvironment.getEnvironment()
            session = env!!.createSession(models.pathFor(modelFile))
            true
        } catch (_: Exception) { false }
    }

    protected fun run(inputs: Map<String, ai.onnxruntime.OnnxTensor>): OrtSession.Result? =
        try { session?.run(inputs) } catch (_: Exception) { null }
}

/** Spoof score 0..1 (AASIST-L INT8). Input: 4s window @16k mono float. */
class SpoofEngine(models: ModelManager) : OrtEngine(models, "spoof_aasist_l.int8.onnx") {
    fun score(window4s: FloatArray): Float? {
        if (!ready()) return null
        return try {
            val env = env!!; val s = session!!
            val buf = FloatBuffer.wrap(window4s)
            ai.onnxruntime.OnnxTensor.createTensor(env, buf, longArrayOf(1, window4s.size.toLong())).use { t ->
                s.run(mapOf("audio" to t)).use { r ->
                    @Suppress("UNCHECKED_CAST")
                    (((r[0].value as Array<FloatArray>)[0]).maxOrNull() ?: 0.5f)
                }
            }
        } catch (_: Exception) { null }
    }
}

/** ECAPA embedding cosine-sim vs enrolled voiceprint. Threshold 0.60 (config.yaml). */
class SpeakerEngine(models: ModelManager) : OrtEngine(models, "ecapa_tdnn.int8.onnx") {
    companion object { const val SIM_THRESHOLD = 0.60f }

    fun embed(audio16k: FloatArray): FloatArray? {
        if (!ready()) return null
        return try {
            val env = env!!; val s = session!!
            val buf = FloatBuffer.wrap(audio16k)
            ai.onnxruntime.OnnxTensor.createTensor(env, buf, longArrayOf(1, audio16k.size.toLong())).use { t ->
                s.run(mapOf("audio" to t)).use { r ->
                    @Suppress("UNCHECKED_CAST")
                    (r[0].value as Array<FloatArray>)[0]
                }
            }
        } catch (_: Exception) { null }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return dot / maxOf(1e-9f, (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)))
    }

    fun verify(audio16k: FloatArray, enrolled: FloatArray): Pair<Float, Boolean>? {
        val e = embed(audio16k) ?: return null
        val sim = cosine(e, enrolled)
        return sim to (sim >= SIM_THRESHOLD)
    }
}
