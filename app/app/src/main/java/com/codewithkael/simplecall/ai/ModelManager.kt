package com.codewithkael.simplecall.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns on-device model files exported by the server/scripts/ exporters
 * (export_onnx.py for the text/VAD models, export_ecapa_onnx.py for the speaker
 * model, export_ast_spoof_onnx.py for the spoof model) — torch -> ONNX -> INT8.
 * Files live in app filesDir/onnx, mmap'd by ONNX Runtime Mobile sessions.
 * Version-gated so a re-export cleanly replaces stale weights.
 *
 * Singleton so the live mic service and the UI observe the SAME model set.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val EXPECTED_VERSION = 1
        /**
         * The ONNX files that ACTUALLY ship to filesDir/onnx (the export scripts
         * produce exactly these two). The old REQUIRED list also named asr_whisper/
         * scam_pattern/urgency/intent/vad — models that never ship there (ASR lives
         * in filesDir/asr, the text engines are on-device heuristics by design), so
         * "allReady()" could never be true and the Guard tab permanently showed
         * "heuristic mode (5 models pending)" even with real models loaded. Honest
         * status comes from per-model probes, not an unreachable checklist.
         */
        val REQUIRED = listOf(
            "spoof_ast.int8.onnx",
            "ecapa_tdnn.int8.onnx",
        )
    }

    /** Name of each on-device capability and the file it needs. */
    fun modelStatus(): Map<String, Boolean> = mapOf(
        "spoof_ast" to File(onnxDir(), "spoof_ast.int8.onnx").exists(),
        "ecapa_tdnn" to File(onnxDir(), "ecapa_tdnn.int8.onnx").exists(),
    )

    fun onnxDir(): File = File(context.filesDir, "onnx").apply {
        mkdirs()
        importFromStaging(this)
    }

    private fun importFromStaging(targetDir: File) {
        val candidates = listOf(
            File("/data/local/tmp"),
            context.getExternalFilesDir("onnx_staging"),
            File("/sdcard/Android/data/${context.packageName}/files/onnx_staging")
        )
        for (srcDir in candidates) {
            if (srcDir == null || !srcDir.exists() || !srcDir.canRead()) continue
            val files = srcDir.listFiles() ?: continue
            for (f in files) {
                if (f.name.endsWith(".onnx")) {
                    val dest = File(targetDir, f.name)
                    if (!dest.exists() || dest.length() != f.length()) {
                        try {
                            f.copyTo(dest, overwrite = true)
                            android.util.Log.i("ModelManager", "Imported ${f.name} (${f.length()} bytes) from ${f.absolutePath}")
                        } catch (e: Exception) {
                            android.util.Log.w("ModelManager", "Could not import ${f.name}: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun missingModels(): List<String> =
        REQUIRED.filter { !File(onnxDir(), it).exists() }

    fun allReady(): Boolean = missingModels().isEmpty()

    fun pathFor(name: String): String = File(onnxDir(), name).absolutePath

    /**
     * On-device softmax TEMPERATURE for a model's head (Phase 1.2 de-saturation),
     * read from `<base>.calibration.json` in the onnx dir — the file
     * scripts/fit_temperature_ondevice.py writes after fitting T on the INT8
     * ONNX's own outputs (a SEPARATE artifact from the server fp32 head; its T
     * must never be copied here). Example: spoof_ast.int8.onnx -> looks up
     * spoof_ast.calibration.json.
     *
     * Returns null when the file is absent or malformed. A null is HONEST: the
     * caller then runs UNCALIBRATED at T=1.0 (an exact identity — the raw model
     * score passes through unchanged). This never fabricates a temperature, and a
     * hand-authored ">1" without a real fit behind it would violate the project
     * HARD RULE, so only a fitter-written file is trusted here.
     */
    fun temperatureFor(modelFile: String): Float? {
        val base = modelFile.substringBefore(".int8").substringBefore(".onnx")
        val f = File(onnxDir(), "$base.calibration.json")
        if (!f.exists()) return null
        return try {
            val t = org.json.JSONObject(f.readText()).optDouble("temperature", Double.NaN)
            if (t.isNaN() || t <= 0.0) {
                android.util.Log.w("ModelManager", "$base.calibration.json has no valid 'temperature'")
                null
            } else t.toFloat()
        } catch (e: Exception) {
            android.util.Log.w("ModelManager", "could not read $base.calibration.json: ${e.message}")
            null
        }
    }
}
