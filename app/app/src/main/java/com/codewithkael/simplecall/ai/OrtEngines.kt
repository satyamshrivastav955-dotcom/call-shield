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
            if (!f.exists()) {
                android.util.Log.e("OrtEngine", "$modelFile not found at ${f.absolutePath}")
                return false
            }
            env = OrtEnvironment.getEnvironment()
            session = env!!.createSession(models.pathFor(modelFile))
            android.util.Log.i("OrtEngine", "$modelFile session READY")
            true
        } catch (e: Exception) {
            android.util.Log.e("OrtEngine", "$modelFile session FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            false
        }
    }

    protected fun run(inputs: Map<String, ai.onnxruntime.OnnxTensor>): OrtSession.Result? =
        try { session?.run(inputs) } catch (e: Exception) {
            android.util.Log.e("OrtEngine", "run() failed: ${e.message}", e)
            null
        }
}


/**
 * Spoof score 0..1 from the AST ASVspoof5 model (server/models/voice_deepfake),
 * exported waveform-in by scripts/export_ast_spoof_onnx.py. The ONNX output is a
 * [1,1] tensor, so .maxOrNull() below returns exactly its single value.
 * (A [1,2] logits/probs output would make .maxOrNull() pick max(bonafide,spoof)
 * and flag a genuine caller as a clone — hence the single-column contract.)
 * Input: 4s window @16k mono float. NOT AASIST-L — that model was never shipped.
 *
 * PHASE 1.2 ON-DEVICE DE-SATURATION (temperature scaling, Guo et al. 2017).
 * The int8 AST head is over-confident: casual speech saturates its spoof prob to
 * ~1.0, so the raw score carries almost no information and the fusion soft/hard
 * bands (0.90 / 0.97 / 0.995) can't separate anything. Temperature scaling divides
 * the spoof-vs-bonafide LOGIT GAP by a fitted T>1 before the sigmoid, spreading
 * that piled-up mass back toward the middle WITHOUT changing the model's ranking
 * (ROC-AUC is invariant). Two honest facts govern this:
 *   1. DEFAULT T = 1.0 is an exact identity — with no calibration file the score
 *      is byte-for-byte the model's raw output, so an uncalibrated device behaves
 *      EXACTLY as before. T is only ever a value FIT on the int8 outputs by
 *      scripts/fit_temperature_ondevice.py (shipped as spoof_ast.calibration.json),
 *      never a hand-picked number — that would violate the project HARD RULE. This
 *      T is SEPARATE from the server fp32 head's T (different artifact).
 *   2. De-saturation is NOT the false-positive cure. Preserving ranking means a
 *      biased bonafide can stay above 0.5; what actually stops a casual call from
 *      alarming is FusionEngine's corroboration gate (a lone spoof score can't
 *      escalate). De-saturation only makes the score informative so those bands mean
 *      something.
 * Two export contracts are handled transparently (detected by ONNX output name):
 *   * "spoof_logit"  -> the [1,1] value IS the logit gap; p = sigmoid(gap / T). Exact
 *                       at full range (recommended: export with --emit logit).
 *   * "spoof_prob"   -> the [1,1] value is the 2-class softmax spoof prob (the
 *                       currently-shipped contract); the logit gap is recovered as
 *                       logit(clamp(p)) then rescaled. Exact EXCEPT where p already
 *                       saturated to the fp32/int8 boundary, where the clamp bounds
 *                       the recovered gap (the logit export removes that limit).
 */
class SpoofEngine(models: ModelManager) : OrtEngine(models, "spoof_ast.int8.onnx") {

    // Resolved once on first score() (after the session is READY): fitted T and
    // whether the ONNX emits a logit gap vs a post-softmax prob. Defaults are the
    // honest no-op: T=1.0 (identity) + prob contract (the shipped model).
    private var temperature: Float = 1.0f
    private var emitsLogit: Boolean = false
    private var contractResolved = false

    private fun resolveContract() {
        if (contractResolved) return
        contractResolved = true
        emitsLogit = try {
            (session?.outputNames?.firstOrNull()?.lowercase() ?: "").contains("logit")
        } catch (_: Exception) { false }
        val t = models.temperatureFor(modelFile)
        if (t != null && t > 0f) {
            temperature = t
            android.util.Log.i("SpoofEngine",
                "de-saturation ON: T=$temperature (fitted, spoof_ast.calibration.json) emitsLogit=$emitsLogit")
        } else {
            temperature = 1.0f
            android.util.Log.w("SpoofEngine",
                "AST spoof head UNCALIBRATED — T=1.0 identity (emitsLogit=$emitsLogit), raw score " +
                "passes through unchanged. Fit T on the int8 outputs with " +
                "scripts/fit_temperature_ondevice.py and push spoof_ast.calibration.json to enable " +
                "de-saturation. NOTE: FusionEngine's corroboration gate is the actual false-positive " +
                "guard; this only spreads the score so the soft/hard bands carry information.")
        }
    }

    /**
     * Turn the model's [1,1] output into a de-saturated spoof prob in [0,1].
     * With T=1.0 this returns the model's original spoof prob exactly (identity):
     * for the prob contract it's a pass-through; for the logit contract sigmoid(gap)
     * IS the original 2-class softmax spoof prob. So an uncalibrated head is a no-op.
     */
    private fun desaturate(raw: Float): Float {
        val t = temperature
        if (emitsLogit) return sigmoid(raw / t)          // raw is the logit gap
        if (t == 1.0f) return raw                        // prob pass-through, no precision loss
        val p = raw.coerceIn(PROB_EPS, 1f - PROB_EPS)    // recover gap from post-softmax prob
        val z = kotlin.math.ln(p / (1f - p))             // logit(p) = spoof-vs-bonafide gap
        return sigmoid(z / t)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))

    fun score(window4s: FloatArray): Float? {
        if (!ready()) return null
        resolveContract()
        return try {
            val env = env!!; val s = session!!
            val buf = FloatBuffer.wrap(window4s)
            ai.onnxruntime.OnnxTensor.createTensor(env, buf, longArrayOf(1, window4s.size.toLong())).use { t ->
                s.run(mapOf("audio" to t)).use { r ->
                    @Suppress("UNCHECKED_CAST")
                    val raw = ((r[0].value as Array<FloatArray>)[0]).maxOrNull()
                    // A missing/empty output is a contract violation, not a 0.5
                    // "spoof-maybe": report it as honest-unknown (null) so fusion
                    // treats the signal as absent rather than fabricating a value.
                    if (raw == null) null else desaturate(raw)
                }
            }
        } catch (_: Exception) { null }
    }

    companion object {
        /** Clamp bound for recovering a logit from a post-softmax prob (avoids ±inf). */
        private const val PROB_EPS = 1e-6f
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
