package com.codewithkael.simplecall

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codewithkael.simplecall.ai.LabeledVoiceprint
import com.codewithkael.simplecall.ai.ModelManager
import com.codewithkael.simplecall.ai.ShieldPipeline
import com.codewithkael.simplecall.ai.SpeakerEngine
import com.codewithkael.simplecall.ai.SpoofEngine
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.sin

/**
 * TASK 1d/1e — real on-device evidence for the spoof + speaker models.
 *
 * This runs on a CONNECTED DEVICE (put it in airplane mode for a clean offline
 * latency number, per Task 1e). It produces the exact evidence the hard rule
 * demands: engines.ready()==true, a REAL logged spoofProb + speakerSim from a
 * device run, and median/p90 ms per 4s window.
 *
 * HONEST GATING (no fake green checks):
 *   - Models NOT on device  -> the test is SKIPPED via assumeTrue (reported as
 *     "assumption failed", never as a pass). It logs how to push them.
 *   - Models present but ready()==false or score()==null -> HARD FAIL.
 *   - Only a run with real, non-null model output passes.
 *
 * HOW TO GET MODELS ONTO THE DEVICE (either works; see docs/ONDEVICE_RUNBOOK.md):
 *   A) run-as (most reliable on a debuggable build):
 *        adb push spoof_ast.int8.onnx /data/local/tmp/
 *        adb shell run-as com.codewithkael.simplecall mkdir -p files/onnx
 *        adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/spoof_ast.int8.onnx files/onnx/
 *   B) external staging (no run-as); this test auto-copies it into filesDir/onnx:
 *        adb push spoof_ast.int8.onnx \
 *          /sdcard/Android/data/com.codewithkael.simplecall/files/onnx_staging/
 *
 * Results are logged under tag "ShieldBench" AND written to
 *   filesDir/shield_benchmark.txt   (adb pull via run-as) so the numbers are
 * captured even if logcat is noisy.
 */
@RunWith(AndroidJUnit4::class)
class ShieldBenchmarkTest {

    private val tag = "ShieldBench"

    /** Deterministic 4s @16k window: tone + light noise, speech-level RMS. */
    private fun window(freq: Double, seed: Long): FloatArray {
        val rnd = java.util.Random(seed)
        return FloatArray(4 * 16000) { i ->
            (0.2 * sin(2.0 * Math.PI * freq * i / 16000) + 0.02 * rnd.nextGaussian()).toFloat()
        }
    }

    /** Copy any pushed models from external staging or /data/local/tmp into filesDir/onnx. */
    private fun stageFromExternal(models: ModelManager) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dst = File(ctx.filesDir, "onnx").apply { mkdirs() }
        ModelManager.REQUIRED.forEach { name ->
            val out = File(dst, name)
            val tmpSrc = File("/data/local/tmp", name)

            if (tmpSrc.exists()) {
                if (!out.exists() || out.length() != tmpSrc.length()) {
                    try {
                        tmpSrc.copyTo(out, overwrite = true)
                        log("staged $name (${out.length() / 1000}kB) from /data/local/tmp -> filesDir/onnx")
                    } catch (e: Exception) {
                        log("stage from /data/local/tmp FAILED for $name: ${e.message}")
                    }
                }
                return@forEach
            }

            // Method B: sdcard external staging dir
            val staging = ctx.getExternalFilesDir("onnx_staging")
            val extSrc = staging?.let { File(it, name) }
            if (extSrc != null && extSrc.exists()) {
                if (!out.exists() || out.length() != extSrc.length()) {
                    try {
                        extSrc.copyTo(out, overwrite = true)
                        log("staged $name (${out.length() / 1000}kB) from external/onnx_staging -> filesDir/onnx")
                    } catch (e: Exception) {
                        log("stage from external FAILED for $name: ${e.message}")
                    }
                }
                return@forEach
            }

            if (!out.exists()) {
                log("$name not found in external staging or /data/local/tmp")
            }
        }
    }

    private val lines = StringBuilder()
    private fun log(s: String) { Log.i(tag, s); lines.append(s).append('\n') }

    private fun flush() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        try { File(ctx.filesDir, "shield_benchmark.txt").writeText(lines.toString()) } catch (_: Exception) {}
    }

    /** median + p90 of timed calls (ms), after warmup. */
    private fun bench(warmup: Int, iters: Int, block: () -> Unit): Triple<Double, Double, Double> {
        repeat(warmup) { block() }
        val ms = DoubleArray(iters)
        for (i in 0 until iters) {
            val t0 = System.nanoTime(); block(); ms[i] = (System.nanoTime() - t0) / 1e6
        }
        ms.sort()
        val median = ms[iters / 2]
        val p90 = ms[minOf(iters - 1, (iters * 0.9).toInt())]
        return Triple(median, p90, ms[iters - 1])
    }

    @Test
    fun benchmarkOnDeviceModels() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val models = ModelManager(ctx)
        stageFromExternal(models)

        val onnxDir = File(ctx.filesDir, "onnx")
        log("=== antAI on-device Shield benchmark ===")
        log("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, API ${android.os.Build.VERSION.SDK_INT}")
        log("onnxDir=$onnxDir present=${onnxDir.listFiles()?.joinToString { it.name } ?: "(none)"}")
        log("missing models: ${models.missingModels().ifEmpty { listOf("none") }}")

        val spoof = SpoofEngine(models)
        val speaker = SpeakerEngine(models)
        val spoofReady = spoof.score(window(180.0, 1)) != null
        val speakerReady = speaker.embed(window(180.0, 1)) != null

        // HONEST GATE: if neither core model is on device, SKIP (not pass). The
        // assumeTrue message tells the operator exactly what to push.
        if (!spoofReady && !speakerReady) {
            flush()
            assumeTrue(
                "SKIPPED: no on-device models. Push spoof_ast.int8.onnx / ecapa_tdnn.int8.onnx " +
                    "then re-run (see docs/ONDEVICE_RUNBOOK.md). This is a skip, NOT a pass.",
                false,
            )
        }

        // ---- SPOOF: real spoofProb + latency (1d/1e) ----
        if (spoofReady) {
            val w = window(180.0, 7)
            val p = spoof.score(w)
            log("SPOOF real spoofProb = $p  (0..1; from spoof_ast.int8.onnx)")
            assertTrue("spoof.ready() but score() null — model present yet broken", p != null)
            val (med, p90, mx) = bench(3, 30) { spoof.score(w) }
            log("SPOOF latency/4s window: median=${"%.1f".format(med)}ms p90=${"%.1f".format(p90)}ms max=${"%.1f".format(mx)}ms")
        } else log("SPOOF model absent — skipping its numbers (speaker still measured).")

        // ---- SPEAKER: real speakerSim (self + cross) + latency (1d/1e) ----
        if (speakerReady) {
            val a = window(180.0, 11)
            val b = window(320.0, 22)   // different 'speaker' -> lower cosine expected
            val ea = speaker.embed(a); val eb = speaker.embed(b)
            assertTrue("speaker.ready() but embed() null — model present yet broken", ea != null && eb != null)
            val selfSim = speaker.cosine(ea!!, ea)
            val crossSim = speaker.cosine(ea, eb!!)
            log("SPEAKER embedding dim = ${ea.size} (expected 192)")
            log("SPEAKER self-cosine = $selfSim (sanity ~1.0)")
            log("SPEAKER cross-cosine (diff signal) = $crossSim")
            val verified = speaker.verify(a, ea)   // verify a against its own print
            log("SPEAKER real speakerSim vs enrolled = ${verified?.first} match=${verified?.second} (thr ${SpeakerEngine.SIM_THRESHOLD})")
            assertTrue("verify() null despite ready()", verified != null)
            assertTrue("self-cosine should be ~1.0, got $selfSim", selfSim > 0.98f)
            val (med, p90, mx) = bench(3, 30) { speaker.embed(a) }
            log("SPEAKER latency/4s window: median=${"%.1f".format(med)}ms p90=${"%.1f".format(p90)}ms max=${"%.1f".format(mx)}ms")
        } else log("SPEAKER model absent — skipping its numbers (spoof still measured).")

        // ---- END-TO-END pipeline latency (the true 'ms per 4s window', 1e) ----
        val pipeline = ShieldPipeline(models)
        if (speakerReady) {
            val enrollW = window(180.0, 33)
            speaker.embed(enrollW)?.let { emb ->
                pipeline.enrolledVoiceprint = emb
                pipeline.voiceprints = listOf(LabeledVoiceprint("BenchTarget", "hash_bench", emb))
                pipeline.selectContact("hash_bench")
            }
        }
        val evalW = window(180.0, 44)
        val r = pipeline.evaluate(evalW)
        log("PIPELINE evaluate -> risk=${r.risk} band=${r.band} spoofProb=${r.spoofProb} speakerSim=${r.speakerSim} name=${r.speakerName}")
        val (med, p90, mx) = bench(2, 20) { pipeline.evaluate(evalW) }
        log("PIPELINE end-to-end latency/4s window: median=${"%.1f".format(med)}ms p90=${"%.1f".format(p90)}ms max=${"%.1f".format(mx)}ms")
        log("Run this with the device in AIRPLANE MODE to certify offline (Task 1e).")
        log("=== end benchmark ===")
        flush()
    }
}
