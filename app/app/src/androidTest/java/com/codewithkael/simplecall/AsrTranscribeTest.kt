package com.codewithkael.simplecall

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codewithkael.simplecall.ai.NoOpTranscriber
import com.codewithkael.simplecall.ai.SherpaOnnxTranscriber
import com.codewithkael.simplecall.ai.Transcriber
import com.codewithkael.simplecall.shield.AudioFileDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * TASK 2 verification — REAL on-device ASR transcripts (Hindi + English), offline.
 *
 * This is the gate the hard rule demands for ASR: it feeds actual speech clips
 * through the SAME [Transcriber] the pipeline uses and logs the ACTUAL transcript
 * + detected language. It never fabricates: if the sherpa AAR/dependency or the
 * Whisper model isn't present, the provider yields [NoOpTranscriber] and this test
 * is SKIPPED (assumption failed), not passed.
 *
 * OFFLINE PROOF: run the phone in AIRPLANE MODE. The test logs the live network
 * state (active network == null) as corroborating evidence that transcription
 * happened with no connectivity. sherpa runs 100% locally regardless.
 *
 * SETUP (see docs/ONDEVICE_RUNBOOK.md §ASR):
 *   1. Add the sherpa-onnx AAR/dependency so com.k2fsa.sherpa.onnx.* is on the
 *      classpath, and push the Whisper model:
 *        adb shell run-as com.codewithkael.simplecall mkdir -p files/asr
 *        adb push encoder.onnx  /data/local/tmp/ ; adb push decoder.onnx /data/local/tmp/
 *        adb push tokens.txt    /data/local/tmp/
 *        adb shell run-as com.codewithkael.simplecall cp /data/local/tmp/{encoder.onnx,decoder.onnx,tokens.txt} files/asr/
 *   2. Push speech clips (Hindi + English) — any decodable audio (wav/mp3/m4a):
 *        adb shell mkdir -p /sdcard/Android/data/com.codewithkael.simplecall/files/asr_test
 *        adb push en_sample.wav /sdcard/Android/data/com.codewithkael.simplecall/files/asr_test/
 *        adb push hi_sample.wav /sdcard/Android/data/com.codewithkael.simplecall/files/asr_test/
 *      (cv-hi = Common Voice Hindi is a good Hindi source; any clear English clip works.)
 *
 * Evidence is logged under tag "AsrTest" AND written to filesDir/asr_transcripts.txt.
 */
@RunWith(AndroidJUnit4::class)
class AsrTranscribeTest {

    private val tag = "AsrTest"
    private val lines = StringBuilder()
    private fun log(s: String) { Log.i(tag, s); lines.append(s).append('\n') }
    private fun flush() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        try { File(ctx.filesDir, "asr_transcripts.txt").writeText(lines.toString()) } catch (_: Exception) {}
    }

    /** 4s @16k windows — the exact granularity ShieldPipeline transcribes at. */
    private fun windows(pcm: FloatArray, size: Int = 64000): List<FloatArray> {
        if (pcm.isEmpty()) return emptyList()
        val out = ArrayList<FloatArray>()
        var off = 0
        while (off < pcm.size) {
            val end = minOf(off + size, pcm.size)
            val w = FloatArray(size)
            for (i in off until end) w[i - off] = pcm[i]   // zero-padded tail
            out.add(w)
            off += size
        }
        return out
    }

    private fun networkEvidence(ctx: Context): String = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        "activeNetwork=${active ?: "null"} internet=$online  (airplane mode should show null/false)"
    } catch (e: Exception) {
        "network state unavailable: ${e.message}"
    }

    @Test
    fun transcribeHindiAndEnglishOffline() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val transcriber: Transcriber = SherpaOnnxTranscriber.createOrNoOp(ctx)

        log("=== antAI on-device ASR test (sherpa-onnx Whisper) ===")
        log("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, API ${android.os.Build.VERSION.SDK_INT}")
        log("network: ${networkEvidence(ctx)}")
        log("transcriber = ${if (transcriber is NoOpTranscriber) "NoOp (ASR NOT active)" else transcriber.javaClass.simpleName}")

        // HONEST GATE 1: no real ASR backend -> SKIP (not a pass).
        if (transcriber is NoOpTranscriber) {
            flush()
            assumeTrue(
                "SKIPPED: ASR not active. Add the sherpa-onnx dependency AND push the Whisper " +
                    "model to files/asr/ (encoder.onnx, decoder.onnx, tokens.txt). See ONDEVICE_RUNBOOK.md §ASR. " +
                    "This is a skip, NOT a pass.",
                false,
            )
        }

        val clipsDir = ctx.getExternalFilesDir("asr_test")
        val clips = clipsDir?.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedBy { it.name }
            ?: emptyList()

        // HONEST GATE 2: ASR active but no audio to transcribe -> SKIP with guidance.
        if (clips.isEmpty()) {
            log("no clips in ${clipsDir?.absolutePath}")
            flush()
            assumeTrue(
                "SKIPPED: ASR is active but no clips were pushed. Push Hindi + English audio to " +
                    "asr_test/ (see setup in this file / ONDEVICE_RUNBOOK.md §ASR).",
                false,
            )
        }

        var anyText = false
        for (clip in clips) {
            val pcm = try {
                runBlocking { AudioFileDecoder.decodeToMono16k(ctx, Uri.fromFile(clip)) }
            } catch (e: Exception) {
                log("clip ${clip.name}: decode FAILED (${e.message})"); continue
            }
            if (pcm.isEmpty()) { log("clip ${clip.name}: decoded to 0 samples — skipping"); continue }

            val sb = StringBuilder()
            val langs = LinkedHashSet<String>()
            for (w in windows(pcm)) {
                transcriber.transcribe(w)?.let { (t, l) ->
                    if (t.isNotBlank()) { sb.append(t).append(' '); langs.add(l) }
                }
            }
            val transcript = sb.toString().trim()
            log("---- clip: ${clip.name} (${pcm.size / 16000}s @16k) ----")
            log("detected language(s): ${if (langs.isEmpty()) "(none)" else langs.joinToString()}")
            log("transcript: ${if (transcript.isBlank()) "(empty)" else transcript}")
            if (transcript.isNotBlank()) anyText = true
        }

        log("Run this with the device in AIRPLANE MODE to certify offline transcription.")
        log("=== end ASR test ===")
        flush()

        // HARD FAIL: ASR active + real clips, yet not a single transcript -> the
        // backend is broken (or the model can't decode). Surface it, don't hide it.
        assertTrue(
            "ASR active and clips present but produced NO transcript — model/backend broken. " +
                "Check tokens.txt matches the encoder/decoder, and that clips contain speech.",
            anyText,
        )
    }
}
