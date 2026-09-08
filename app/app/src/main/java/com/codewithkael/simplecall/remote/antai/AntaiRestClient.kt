package com.codewithkael.simplecall.remote.antai

import android.content.Context
import android.util.Log
import com.codewithkael.simplecall.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocking-HTTP (HttpURLConnection) REST client for the antAI server messaging
 * surface. All public methods are suspend + run on Dispatchers.IO.
 *
 * Host is read live from the SAME SharedPreferences the calls screen writes, so
 * the messaging layer always targets the server the user configured for calls.
 * This is entirely additive: it never touches the Node signaling / WebRTC path.
 *
 * Endpoint shapes mirror server/src/antai/gateway/rest_api.py exactly.
 */
@Singleton
class AntaiRestClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: AntaiSession
) {
    private val callPrefs = context.getSharedPreferences(Constants.CALL_PREFS, Context.MODE_PRIVATE)

    private fun host(): String =
        callPrefs.getString(Constants.KEY_SERVER_HOST, Constants.DEFAULT_SERVER_HOST)
            ?: Constants.DEFAULT_SERVER_HOST

    private fun base() = Constants.getAntaiRestBase(host())

    // ---------- auth ----------

    /** POST /api/auth/otp {phone} -> {sent, dev_otp?, auto_verify} */
    suspend fun requestOtp(phone: String): Result<OtpResult> = post(
        "/api/auth/otp", JSONObject().put("phone", phone), auth = false
    ).mapCatching { body ->
        val o = JSONObject(body)
        OtpResult(
            sent = o.optBoolean("sent", true),
            devOtp = o.optString("dev_otp", "").ifBlank { null },
            autoVerify = o.optBoolean("auto_verify", false)
        )
    }

    /** POST /api/auth/verify {phone, otp, display_name?} -> {token, user_id, display_name} */
    suspend fun verifyOtp(phone: String, otp: String, displayName: String?): Result<VerifyResult> = post(
        "/api/auth/verify",
        JSONObject().put("phone", phone).put("otp", otp)
            .apply { if (!displayName.isNullOrBlank()) put("display_name", displayName) },
        auth = false
    ).mapCatching { body ->
        val o = JSONObject(body)
        VerifyResult(
            token = o.getString("token"),
            userId = o.optLong("user_id", -1),
            displayName = o.optString("display_name", displayName ?: "")
        )
    }

    // ---------- chat ----------

    /** POST /api/chat/send {recipient_phone, body} (auto-provisions recipient). */
    suspend fun sendChat(recipientPhone: String, body: String): Result<SendResult> = post(
        "/api/chat/send",
        JSONObject().put("recipient_phone", recipientPhone).put("body", body),
        auth = true
    ).mapCatching { resp ->
        val o = JSONObject(resp)
        SendResult(
            messageId = o.optLong("message_id", -1),
            riskScore = o.optDouble("risk_score", 0.0),
            intercepted = o.optBoolean("intercepted", false),
            verdict = o.optString("verdict", "").ifBlank { null },
            analysisPending = o.optBoolean("analysis_pending", true)
        )
    }

    /**
     * GET /api/chat/history?peer_phone=&limit= -> {messages:[{id,from_me,body,
     * risk_score,intercepted,created_at}]}. Returns empty list on 404 (peer not
     * on the platform yet) so callers can treat "no server history" uniformly.
     */
    suspend fun history(peerPhone: String, limit: Int = 100): Result<List<HistoryItem>> = get(
        "/api/chat/history?peer_phone=${enc(peerPhone)}&limit=$limit", auth = true
    ).mapCatching { body ->
        val arr = JSONObject(body).optJSONArray("messages") ?: return@mapCatching emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                add(
                    HistoryItem(
                        id = m.optLong("id", -1),
                        fromMe = m.optBoolean("from_me", false),
                        body = m.optString("body", ""),
                        riskScore = m.optDouble("risk_score", 0.0),
                        intercepted = m.optBoolean("intercepted", false),
                        createdAt = parseIsoToMillis(m.optString("created_at", ""))
                    )
                )
            }
        }
    }.recoverCatching { e ->
        // 404 => peer not registered; surface as empty history, not an error.
        if (e is HttpException && e.code == 404) emptyList() else throw e
    }

    // ---------- external notifications (SMS + other apps) ----------

    /**
     * POST /api/notify/external {source, sender, text} -> {ingested, risk_score,
     * verdict?}. `verdict` is an object (or null) when the pipeline flags it.
     */
    suspend fun notifyExternal(source: String, sender: String, text: String): Result<NotifyResult> = post(
        "/api/notify/external",
        JSONObject().put("source", source).put("sender", sender).put("text", text),
        auth = true
    ).mapCatching { resp ->
        val o = JSONObject(resp)
        NotifyResult(
            ingested = o.optBoolean("ingested", false),
            riskScore = o.optDouble("risk_score", 0.0),
            verdict = o.optJSONObject("verdict")?.let { VerdictPayload.from(it) }
        )
    }

    /** POST /api/contacts {peer_phone, label} (auto-provisions). Best-effort. */
    suspend fun addContact(peerPhone: String, label: String): Result<Unit> = post(
        "/api/contacts",
        JSONObject().put("peer_phone", peerPhone).put("label", label),
        auth = true
    ).map { }

    // ---------- identity verification (in-call) ----------

    /**
     * POST /api/verify/voiceprint {session_key, claim_peer_phone} — cross-check the
     * live call audio against the contact's enrolled voiceprint (ECAPA).
     *
     * The synthetic-voice detectors answer "was this voice generated?"; this answers
     * the independent question "is this actually that person?". The server replies
     * 200 with ok=false + reason/hint when the check *couldn't run* (no audio yet, no
     * enrolled print), so that case is a real result the UI can explain — never an
     * error, and never mistaken for "not them".
     */
    suspend fun crossVerifyVoiceprint(
        sessionKey: String, claimPeerPhone: String
    ): Result<VoiceprintResult> = post(
        "/api/verify/voiceprint",
        JSONObject().put("session_key", sessionKey).put("claim_peer_phone", claimPeerPhone),
        auth = true
    ).mapCatching { VoiceprintResult.from(JSONObject(it)) }
        .recoverCatching { e ->
            if (e is HttpException) VoiceprintResult(ok = false,
                reason = "http_${e.code}", hint = httpHint(e))
            else throw e
        }

    /**
     * POST /api/verify/request {session_key, claim_peer_phone} — ask the contact the
     * caller claims to be to confirm it is really them. `delivered` is false when
     * their phone has no live socket, so the button can say "we asked but they may
     * not see it" instead of implying the challenge landed.
     */
    suspend fun requestCallerVerify(
        sessionKey: String, claimPeerPhone: String
    ): Result<VerifyRequestResult> = post(
        "/api/verify/request",
        JSONObject().put("session_key", sessionKey).put("claim_peer_phone", claimPeerPhone),
        auth = true
    ).mapCatching { body ->
        val o = JSONObject(body)
        VerifyRequestResult(
            ok = o.optBoolean("ok", false),
            promptSent = o.optBoolean("prompt_sent", false),
            delivered = o.optBoolean("delivered", false),
            reason = o.optString("reason"),
            timeoutSeconds = o.optDouble("timeout_s", 45.0)
        )
    }.recoverCatching { e ->
        // 404 = not on antAI, 403 = not in the trusted circle. Both are ordinary
        // outcomes of tapping the button, not failures worth throwing over.
        if (e is HttpException) VerifyRequestResult(
            ok = false, promptSent = false, delivered = false,
            reason = when (e.code) {
                404 -> "not_registered"
                403 -> "not_linked"
                else -> "http_${e.code}"
            },
            hint = httpHint(e)
        ) else throw e
    }

    /** POST /api/verify/respond {session_key, answer} — the trusted contact's Yes/No. */
    suspend fun respondToVerify(sessionKey: String, answer: Boolean): Result<Boolean> = post(
        "/api/verify/respond",
        JSONObject().put("session_key", sessionKey).put("answer", answer),
        auth = true
    ).mapCatching { JSONObject(it).optBoolean("ok", false) }

    // ---------- voiceprint enrolment ----------

    /**
     * GET /api/voiceprints/me -> enrolment state. `engineReady` false means the
     * server's speaker model isn't loaded, so recording would be wasted effort —
     * the screen says so up front instead of failing after the user has spoken.
     */
    suspend fun voiceprintStatus(): Result<VoiceprintStatus> =
        get("/api/voiceprints/me", auth = true)
            .mapCatching { VoiceprintStatus.from(JSONObject(it)) }

    /**
     * POST /api/voiceprints/enroll (multipart, field name "file") -> enrolment
     * result. The server answers 200 with enrolled=false + reason/hint when the
     * recording is unusable (too short, too quiet, model not loaded), so those
     * cases arrive as feedback for the recorder rather than as network errors.
     */
    suspend fun enrollVoiceprint(
        wav: ByteArray, filename: String = "voiceprint.wav"
    ): Result<VoiceprintEnrollResult> =
        multipart("/api/voiceprints/enroll", wav, filename, "audio/wav")
            .mapCatching { VoiceprintEnrollResult.from(JSONObject(it)) }
            .recoverCatching { e ->
                if (e is HttpException) VoiceprintEnrollResult(
                    enrolled = false, reason = "http_${e.code}", hint = httpHint(e),
                    status = VoiceprintStatus()
                ) else throw e
            }

    /** DELETE /api/voiceprints/me — discard every stored sample and start over. */
    suspend fun deleteVoiceprints(): Result<VoiceprintStatus> =
        request("DELETE", "/api/voiceprints/me", null, auth = true)
            .mapCatching { VoiceprintStatus.from(JSONObject(it)) }

    private fun httpHint(e: HttpException): String = when (e.code) {
        401 -> "Sign in to antAI to use verification."
        403 -> "Add this person to your trusted circle first, so they can confirm it's them."
        404 -> "This number isn't on antAI, so there's nobody to ask."
        else -> "The antAI server couldn't complete the check (HTTP ${e.code})."
    }

    // ---------- transport ----------

    private suspend fun post(path: String, body: JSONObject, auth: Boolean): Result<String> =
        request("POST", path, body.toString(), auth)

    private suspend fun get(path: String, auth: Boolean): Result<String> =
        request("GET", path, null, auth)

    private suspend fun request(
        method: String, path: String, body: String?, auth: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(base() + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 15000
                if (auth && session.token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${session.token}")
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                Log.w(TAG, "$method $path -> HTTP $code: $text")
                throw HttpException(code, text)
            }
            text
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /**
     * Single-file multipart/form-data upload, hand-rolled because this client is
     * deliberately dependency-free (HttpURLConnection only) and enrolment is the
     * one endpoint that isn't JSON. Streamed with a fixed content length so a
     * several-hundred-KB recording never has to be buffered twice.
     *
     * [fields] are extra plain form fields written before the file part (used by
     * /api/stream/analyze for `scenario`).
     */
    private suspend fun multipart(
        path: String, bytes: ByteArray, filename: String, contentType: String,
        fields: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----antai${System.nanoTime()}"
            val fieldParts = fields.entries.joinToString("") { (k, v) ->
                "--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n"
            }.toByteArray()
            val head = ("--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                "Content-Type: $contentType\r\n\r\n").toByteArray()
            val tail = "\r\n--$boundary--\r\n".toByteArray()
            val conn = (URL(base() + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 30000      // embedding extraction is slower than a JSON call
                doOutput = true
                if (session.token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${session.token}")
                }
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setFixedLengthStreamingMode(fieldParts.size + head.size + bytes.size + tail.size)
                outputStream.use { out ->
                    out.write(fieldParts)
                    out.write(head); out.write(bytes); out.write(tail); out.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                Log.w(TAG, "POST $path (multipart ${bytes.size}B) -> HTTP $code: $text")
                throw HttpException(code, text)
            }
            text
        }
    }

    companion object {
        private const val TAG = "AntaiRest"

        // Python datetime.isoformat() variants; parsed to epoch millis for
        // ordering. Falls back to "now" so a parse miss never drops a message.
        private val ISO_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        fun parseIsoToMillis(s: String): Long {
            if (s.isBlank()) return System.currentTimeMillis()
            val clean = s.substringBefore("+").substringBefore("Z").trim()
            for (p in ISO_PATTERNS) {
                try {
                    val fmt = SimpleDateFormat(p, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    return fmt.parse(clean)?.time ?: continue
                } catch (_: Exception) { /* try next */ }
            }
            return System.currentTimeMillis()
        }
    }

    // ---------- incident history: GET /api/verdicts ----------

    /**
     * Fetch the authenticated user's verdict history (up to 50 most recent).
     * Used by [IncidentHistoryScreen] to show past risk assessments.
     * Returns an empty list gracefully when there are no verdicts or auth fails.
     */
    suspend fun listVerdicts(): Result<List<IncidentItem>> = get(
        "/api/verdicts", auth = true
    ).mapCatching { body ->
        val arr = JSONObject(body).optJSONArray("verdicts") ?: return@mapCatching emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                add(
                    IncidentItem(
                        id = v.optLong("id", -1),
                        kind = v.optString("kind", "voice"),
                        riskScore = v.optDouble("risk_score", 0.0),
                        band = v.optString("band", "passive"),
                        verdict = v.optString("verdict", ""),
                        why = v.optString("why", ""),
                        action = v.optString("action", ""),
                        scamType = v.optString("scam_type", "").ifBlank { null },
                        createdAt = parseIsoToMillis(v.optString("created_at", ""))
                    )
                )
            }
        }
    }.recoverCatching { emptyList() }

    // ---------- debug: GET /api/debug/models ----------
    // Response shape: {"models": {key: {ready: bool, ...}}, "providers": {...}}
    suspend fun debugModels(): Result<Map<String, Boolean>> = get(
        "/api/debug/models", auth = false
    ).mapCatching { body ->
        val models = JSONObject(body).optJSONObject("models") ?: JSONObject()
        buildMap {
            val keys = models.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val m = models.optJSONObject(k)
                put(k, m?.optBoolean("ready", false) ?: false)
            }
        }
    }

    // ---------- file analysis: POST /api/stream/analyze ----------
    suspend fun analyzeAudioFile(
        bytes: ByteArray,
        filename: String = "upload.wav",
        contentType: String = "audio/*",
        scenario: String? = null
    ): Result<NormalizedResult> =
        multipart(
            "/api/stream/analyze", bytes, filename, contentType,
            fields = if (scenario != null) mapOf("scenario" to scenario) else emptyMap()
        )
            .mapCatching { body ->
                val o = JSONObject(body)
                // Server signals failure with {"error": "..."} — never parse that as a
                // clean 0-risk result (ground rule: no fake "safe" verdicts).
                val error = o.optString("error")
                if (error.isNotBlank()) throw IllegalStateException(error)

                val acoustic = o.optJSONObject("acoustic")
                val prosody = o.optJSONObject("prosody")
                val voiceprint = o.optJSONObject("voiceprint")
                val reasonsArr = o.optJSONArray("reasons")
                val reasons = if (reasonsArr != null) {
                    (0 until reasonsArr.length()).map { reasonsArr.optString(it) }
                } else emptyList()

                NormalizedResult(
                    risk = o.optDouble("risk", 0.0),
                    band = o.optString("band", "passive"),
                    recommendation = o.optString("recommendation", ""),
                    reasons = reasons,
                    voiceDeepfake = acoustic?.optDoubleOrNull("voice_deepfake"),
                    scamProb = prosody?.optDoubleOrNull("scam_prob"),
                    scamType = prosody?.optStringOrNull("scam_type"),
                    urgency = prosody?.optDoubleOrNull("urgency"),
                    voiceprintSimilarity = voiceprint?.optDoubleOrNull("similarity"),
                    identityMismatch = voiceprint?.optBooleanOrNull("identity_mismatch")
                )
            }
}

// ---------- response DTOs ----------

class HttpException(val code: Int, val bodyText: String) : Exception("HTTP $code: $bodyText")

data class OtpResult(val sent: Boolean, val devOtp: String?, val autoVerify: Boolean)
data class VerifyResult(val token: String, val userId: Long, val displayName: String)
data class SendResult(
    val messageId: Long, val riskScore: Double, val intercepted: Boolean,
    val verdict: String?, val analysisPending: Boolean
)
data class HistoryItem(
    val id: Long, val fromMe: Boolean, val body: String,
    val riskScore: Double, val intercepted: Boolean, val createdAt: Long
)

/** One verdict from [AntaiRestClient.listVerdicts] / GET /api/verdicts. */
data class IncidentItem(
    val id: Long,
    val kind: String,            // "voice" | "video" | "message"
    val riskScore: Double,
    val band: String,            // "passive" | "verify" | "critical"
    val verdict: String,
    val why: String,
    val action: String,
    val scamType: String?,
    val createdAt: Long,         // epoch millis (UTC)
    // Phase 3.2 forensic detail for the inspection dialog. On-device rows carry
    // these from TrustedStore; server /api/verdicts rows leave them null (the
    // endpoint doesn't return per-window telemetry). null = not available — never
    // fabricated, so the dialog shows an honest "unavailable" instead of a fake 0.
    val transcript: String? = null,
    val spoofProb: Double? = null,   // AI-voice score 0..1, or null (not scored)
    val contact: String? = null      // claimed/matched contact name, or null (unknown)
)
data class NotifyResult(val ingested: Boolean, val riskScore: Double, val verdict: VerdictPayload?)

/**
 * Outcome of asking a claimed contact to confirm their identity. [promptSent] means
 * the server accepted and is now waiting; [delivered] means their device was
 * actually reachable. The real answer arrives later over the WS as verify.result.
 */
data class VerifyRequestResult(
    val ok: Boolean,
    val promptSent: Boolean,
    val delivered: Boolean,
    val reason: String = "",
    val hint: String = "",
    val timeoutSeconds: Double = 45.0
)

/** The flattened verdict object shared by /notify/external and verdict.update. */data class VerdictPayload(
    val band: String,
    val riskScore: Double,
    val verdict: String,
    val why: String,
    val action: String,
    val scamType: String
) {
    companion object {
        fun from(o: JSONObject) = VerdictPayload(
            band = o.optString("band", "passive"),
            riskScore = o.optDouble("risk_score", 0.0),
            verdict = o.optString("verdict", ""),
            why = o.optString("why", ""),
            action = o.optString("action", ""),
            scamType = o.optString("scam_type", "")
        )
    }
}
