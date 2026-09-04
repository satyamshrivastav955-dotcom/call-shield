package com.antai.app.data.remote

import com.antai.app.data.local.AuthPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = -1) : Exception(message)

class ApiClient(private val prefs: AuthPrefs) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private suspend fun base(): String = prefs.currentBaseUrl().trim().trimEnd('/')

    private fun authHeader(token: String?) = "Bearer ${token.orEmpty()}"

    private suspend fun request(
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        multipart: MultipartBody? = null,
        needAuth: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = "${base()}$path"
        val builder = Request.Builder().url(url)
        when {
            jsonBody != null -> builder.method(method, jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType()))
            multipart != null -> builder.method(method, multipart)
            else -> builder.method(method, null)
        }
        if (needAuth) builder.header("Authorization", authHeader(prefs.currentToken()))
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text).optString("detail", text)
                }.getOrDefault(text)
                throw ApiException(detail, resp.code)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    // ---------------- auth ----------------
    suspend fun requestOtp(phone: String): JSONObject =
        request("POST", "/api/auth/otp", JSONObject().put("phone", phone), needAuth = false)

    suspend fun verifyOtp(phone: String, otp: String, name: String): JSONObject =
        request("POST", "/api/auth/verify",
            JSONObject().put("phone", phone).put("otp", otp).put("display_name", name),
            needAuth = false)

    suspend fun me(): JSONObject = request("GET", "/api/auth/me")

    // ---------------- contacts ----------------
    suspend fun listContacts(): JSONObject = request("GET", "/api/contacts")

    suspend fun addContact(phone: String, label: String, tag: String?, trusted: Boolean): JSONObject =
        request("POST", "/api/contacts", JSONObject()
            .put("peer_phone", phone).put("label", label)
            .put("relationship_tag", tag ?: JSONObject.NULL).put("is_trusted", trusted))

    suspend fun linkTrusted(phone: String, label: String, tag: String?): JSONObject =
        request("POST", "/api/trust-circle/link", JSONObject()
            .put("peer_phone", phone).put("label", label)
            .put("relationship_tag", tag ?: JSONObject.NULL))

    // ---------------- chat ----------------
    suspend fun sendChat(recipientPhone: String, body: String): JSONObject =
        request("POST", "/api/chat/send",
            JSONObject().put("recipient_phone", recipientPhone).put("body", body))

    suspend fun chatHistory(peerPhone: String, limit: Int = 100): JSONObject =
        request("GET", "/api/chat/history?peer_phone=${peerPhone}&limit=$limit")

    // ---------------- history ----------------
    suspend fun verdicts(): JSONObject = request("GET", "/api/verdicts")

    suspend fun reports(): JSONObject = request("GET", "/api/reports")

    suspend fun calls(): JSONObject = request("GET", "/api/calls")

    // ---------------- voiceprint enrollment ----------------
    suspend fun enrollVoiceprint(wavBytes: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        val url = "${base()}/api/voiceprints/enroll"
        val part = MultipartBody.Part.createFormData(
            "file", "enroll.wav",
            wavBytes.toRequestBody("audio/wav".toMediaType()))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addPart(part).build()
        val req = Request.Builder().url(url).post(body)
            .header("Authorization", authHeader(prefs.currentToken())).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(text, resp.code)
            JSONObject(text)
        }
    }

    // ---------------- verify / freeze ----------------
    suspend fun respondVerify(sessionKey: String, answer: Boolean): JSONObject =
        request("POST", "/api/verify/respond",
            JSONObject().put("session_key", sessionKey).put("answer", answer))

    suspend fun requestVerify(sessionKey: String, claimPeerPhone: String): JSONObject =
        request("POST", "/api/verify/request",
            JSONObject().put("session_key", sessionKey)
                .put("claim_peer_phone", claimPeerPhone))

    suspend fun decideFreeze(freezeId: Long, decision: String): JSONObject =
        request("POST", "/api/freeze/decide",
            JSONObject().put("freeze_id", freezeId).put("decision", decision))

    // ---------------- collective flags ----------------
    suspend fun submitFlag(phone: String, kind: String, note: String?): JSONObject =
        request("POST", "/api/flags", JSONObject()
            .put("phone", phone).put("kind", kind)
            .put("note", note ?: JSONObject.NULL))

    // ---------------- push device token ----------------
    suspend fun registerDevice(token: String, platform: String = "android"): JSONObject =
        request("POST", "/api/devices",
            JSONObject().put("token", token).put("platform", platform))

    // ---------------- external notification ingestion ----------------
    suspend fun externalNotify(source: String, sender: String, text: String): JSONObject =
        request("POST", "/api/notify/external",
            JSONObject().put("source", source).put("sender", sender).put("text", text))

    // ---------------- profile ----------------
    suspend fun updateProfile(name: String? = null, avatar: String? = null): JSONObject =
        request("PATCH", "/api/profile", JSONObject()
            .put("display_name", name ?: JSONObject.NULL)
            .put("avatar", avatar ?: JSONObject.NULL))
}