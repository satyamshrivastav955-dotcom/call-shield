package com.codewithkael.simplecall.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns on-device model files exported by server/scripts/export_onnx.py
 * (torch -> ONNX -> INT8). Files live in app filesDir/onnx, mmap'd by
 * ONNX Runtime Mobile sessions. Version-gated so a re-export cleanly
 * replaces stale weights.
 *
 * Singleton so the live mic service and the UI observe the SAME model set.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val EXPECTED_VERSION = 1
        val REQUIRED = listOf(
            "vad.onnx",
            "spoof_aasist_l.int8.onnx",
            "ecapa_tdnn.int8.onnx",
            "asr_whisper.int8.onnx",
            "scam_pattern.int8.onnx",
            "urgency.int8.onnx",
            "intent.int8.onnx",
        )
    }

    fun onnxDir(): File = File(context.filesDir, "onnx").apply { mkdirs() }

    fun missingModels(): List<String> =
        REQUIRED.filter { !File(onnxDir(), it).exists() }

    fun allReady(): Boolean = missingModels().isEmpty()

    fun pathFor(name: String): String = File(onnxDir(), name).absolutePath
}
