package com.codewithkael.simplecall.ai

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

/**
 * TASK 2 — real on-device ASR via sherpa-onnx (offline Whisper, multilingual so
 * it covers the Hindi + English requirement). No network: the model runs locally.
 *
 * WHY REFLECTION (and not direct `import com.k2fsa.sherpa.onnx.*`):
 *   The Claude sandbox has no network to fetch/verify the sherpa-onnx AAR, and we
 *   must not leave the repo non-compiling for the other tasks. So this binds to
 *   sherpa's classes REFLECTIVELY: the app compiles today with zero sherpa
 *   dependency, and the transcriber ACTIVATES automatically once satya (a) adds
 *   the sherpa-onnx AAR/dependency so the classes are on the classpath, and (b)
 *   pushes the Whisper model to filesDir/asr/. If either is missing, it returns
 *   null (NoOp) — the pipeline keeps scoring acoustic signals, and NO FAKE
 *   TRANSCRIPT is ever produced. Config is built with Kotlin `callBy`, which fills
 *   sherpa's data-class defaults, so extra params in newer sherpa versions do not
 *   break construction — only a renamed class/param would (logged loudly).
 *
 * PRODUCTION SWAP (recommended once the dep is in): replace the reflective
 * `buildRecognizer()` body with the direct sherpa API — the exact snippet is in
 * docs/ONDEVICE_RUNBOOK.md (§ASR). Direct calls give compile-time safety.
 *
 * MODEL FILES expected in filesDir/asr/ (push with adb; see runbook):
 *   encoder.onnx  decoder.onnx  tokens.txt   (sherpa Whisper export; int8 ok)
 *
 * PERF NOTE: Whisper decodes per 4s window (heavy — see ShieldBenchmark latency).
 * A streaming zipformer transducer is lighter for continuous ASR; documented as
 * an alternative in the runbook. Whisper is chosen here for hi+en quality.
 */
class SherpaOnnxTranscriber private constructor(
    private val recognizer: Any,
    private val pkg: String,
) : Transcriber {

    private val tag = "SherpaASR"

    @Synchronized
    override fun transcribe(window4s: FloatArray): Pair<String, String>? {
        return try {
            val stream = call(recognizer, "createStream") ?: return null
            try {
                call(stream, "acceptWaveform", window4s, 16000)
                call(recognizer, "decode", stream)
                val result = call(recognizer, "getResult", stream) ?: return null
                val text = (getter(result, "getText") as? String)?.trim().orEmpty()
                if (text.isBlank()) return null
                val rawLang = (getter(result, "getLang") as? String).orEmpty()
                text to normalizeLang(rawLang, text)
            } finally {
                try { call(stream, "release") } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(tag, "transcribe failed: ${e.message}")
            null
        }
    }

    /** Map Whisper's detected language token -> the pipeline's en|hi|hinglish. */
    private fun normalizeLang(raw: String, text: String): String {
        val l = raw.lowercase().removeSurrounding("<|", "|>").trim()
        val hasDevanagari = text.any { it.code in 0x0900..0x097F }
        return when {
            l.startsWith("hi") || l.contains("hindi") -> if (looksHinglish(text)) "hinglish" else "hi"
            l.startsWith("en") || l.contains("english") -> if (hasDevanagari) "hinglish" else "en"
            hasDevanagari -> "hi"
            else -> "en"
        }
    }

    /** Latin-script Hindi words mixed with English -> hinglish (matches TextEngines). */
    private fun looksHinglish(text: String): Boolean {
        val t = text.lowercase()
        val hindiRomanized = listOf("hai", "kar", "karo", "batao", "paisa", "abhi", "turant", "bhejo", "aap")
        return hindiRomanized.count { t.contains(it) } >= 1 && t.any { it in 'a'..'z' }
    }

    // ---- reflection helpers (localized; every call guarded by the caller) ----
    private fun call(obj: Any, method: String, vararg args: Any?): Any? {
        val m = obj.javaClass.methods.firstOrNull {
            it.name == method && it.parameterCount == args.size
        } ?: throw NoSuchMethodException("$method/${args.size} on ${obj.javaClass.name}")
        return m.invoke(obj, *args)
    }

    private fun getter(obj: Any, name: String): Any? =
        try { obj.javaClass.getMethod(name).invoke(obj) } catch (_: Exception) { null }

    companion object {
        private const val TAG = "SherpaASR"

        /**
         * Build a real transcriber IF sherpa classes are on the classpath AND the
         * model files are present; otherwise return NoOpTranscriber (honest
         * fallback — the pipeline still runs, just without text signals).
         */
        fun createOrNoOp(context: Context): Transcriber {
            val dir = File(context.filesDir, "asr")
            val enc = File(dir, "encoder.onnx")
            val dec = File(dir, "decoder.onnx")
            val tok = File(dir, "tokens.txt")
            if (!enc.exists() || !dec.exists() || !tok.exists()) {
                Log.i(TAG, "ASR model absent in ${dir.absolutePath} -> NoOp (push encoder/decoder/tokens; see runbook)")
                return NoOpTranscriber
            }
            return try {
                val recognizer = buildRecognizer(enc.absolutePath, dec.absolutePath, tok.absolutePath)
                if (recognizer == null) {
                    Log.w(TAG, "sherpa classes not on classpath (add the AAR/dependency) -> NoOp")
                    NoOpTranscriber
                } else {
                    Log.i(TAG, "sherpa-onnx ASR ACTIVE (offline Whisper, model in ${dir.absolutePath})")
                    SherpaOnnxTranscriber(recognizer, "com.k2fsa.sherpa.onnx")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "sherpa init failed -> NoOp: ${e.message}")
                NoOpTranscriber
            }
        }

        /**
         * Reflectively construct sherpa's OfflineRecognizer for Whisper. Returns
         * null if the sherpa classes aren't present. Uses Kotlin callBy so sherpa's
         * data-class defaults fill everything we don't set (version-drift tolerant).
         *
         * Direct-API equivalent (paste after adding the dep — see runbook):
         *   val cfg = OfflineRecognizerConfig(
         *     featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
         *     modelConfig = OfflineModelConfig(
         *       whisper = OfflineWhisperModelConfig(encoder = enc, decoder = dec),
         *       tokens = tok, modelType = "whisper", numThreads = 2, provider = "cpu"),
         *     decodingMethod = "greedy_search")
         *   val recognizer = OfflineRecognizer(config = cfg)
         */
        private fun buildRecognizer(enc: String, dec: String, tok: String): Any? {
            val p = "com.k2fsa.sherpa.onnx"
            // classes must exist; if not, sherpa isn't on the classpath yet.
            val recognizerClass = try { Class.forName("$p.OfflineRecognizer") }
                catch (_: ClassNotFoundException) { return null }

            val feat = construct("$p.FeatureConfig",
                mapOf("sampleRate" to 16000, "featureDim" to 80))
            val whisper = construct("$p.OfflineWhisperModelConfig",
                mapOf("encoder" to enc, "decoder" to dec))
            val model = construct("$p.OfflineModelConfig", mapOf(
                "whisper" to whisper, "tokens" to tok, "modelType" to "whisper",
                "numThreads" to 2, "provider" to "cpu", "debug" to false))
            val config = construct("$p.OfflineRecognizerConfig", mapOf(
                "featConfig" to feat, "modelConfig" to model,
                "decodingMethod" to "greedy_search"))
                ?: error("could not build OfflineRecognizerConfig (param names changed?)")

            // OfflineRecognizer(assetManager: AssetManager? = null, config: ...)
            val kctor = recognizerClass.kotlin.primaryConstructor
                ?: error("OfflineRecognizer has no primary constructor")
            val cfgParam = kctor.parameters.firstOrNull { it.name == "config" }
                ?: error("OfflineRecognizer ctor has no 'config' param")
            return kctor.callBy(mapOf(cfgParam to config))
        }

        /** Build a Kotlin data class by NAMED args; defaults fill the rest. */
        private fun construct(className: String, args: Map<String, Any?>): Any? {
            val kclass = Class.forName(className).kotlin
            val ctor = kclass.primaryConstructor ?: return null
            val byName = ctor.parameters.associateBy { it.name }
            val callArgs = HashMap<KParameter, Any?>()
            for ((k, v) in args) byName[k]?.let { callArgs[it] = v }
            return ctor.callBy(callArgs)
        }
    }
}
