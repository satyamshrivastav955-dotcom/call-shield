package com.codewithkael.simplecall.shield

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-dependency local store (JSON in filesDir): trusted contacts with
 * optional voiceprint embeddings, verdict log, flag cache, FIR drafts.
 * Privacy: phone numbers stored as salted SHA-256 only (never plaintext).
 * Raw audio is NEVER persisted — features/embeddings only (PS26104 minimal
 * retention). TTL purge drops verdicts older than 30 days on load.
 */
data class TrustedContact(
    val phoneHash: String,
    val displayName: String,
    val label: String,
    val tag: String?,
    val voiceprint: FloatArray?,
) {
    val hasVoiceprint: Boolean get() = voiceprint != null
}

@Singleton
class TrustedStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        // Salt MUST be overridden per install; default keeps dev builds working.
        // Set via Settings -> salt is generated on first run and stored in prefs.
        const val DEFAULT_SALT = "antai-dev-salt-change-me"
        const val VERDICT_TTL_DAYS = 30L
    }

    private fun dir(): File = File(context.filesDir, "shield").apply { mkdirs() }
    private fun contactsFile() = File(dir(), "contacts.json")
    private fun verdictsFile() = File(dir(), "verdicts.json")

    private fun salt(): String {
        val prefs = context.getSharedPreferences("antai_shield", Context.MODE_PRIVATE)
        var s = prefs.getString("phone_salt", null)
        if (s.isNullOrBlank()) {
            s = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("phone_salt", s).apply()
        }
        return s!!
    }

    fun hashPhone(phone: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt() + phone.trim()).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun loadContacts(): List<TrustedContact> {
        val f = contactsFile()
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TrustedContact(
                    phoneHash = o.getString("phoneHash"),
                    displayName = o.optString("displayName", "?"),
                    label = o.optString("label", "family"),
                    tag = o.optString("tag", null),
                    voiceprint = o.optJSONArray("voiceprint")?.let { a ->
                        FloatArray(a.length()) { j -> a.getDouble(j).toFloat() }
                    },
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveContacts(list: List<TrustedContact>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("phoneHash", c.phoneHash)
                put("displayName", c.displayName)
                put("label", c.label)
                put("tag", c.tag ?: JSONObject.NULL)
                if (c.voiceprint != null) {
                    put("voiceprint", JSONArray(c.voiceprint.map { it.toDouble() }))
                }
            })
        }
        contactsFile().writeText(arr.toString())
    }

    fun addContact(name: String, phone: String, tag: String? = null) {
        val list = loadContacts().toMutableList()
        val h = hashPhone(phone)
        list.removeAll { it.phoneHash == h }
        list += TrustedContact(h, name, "family", tag, null)
        saveContacts(list)
    }

    fun removeContact(phoneHash: String) {
        saveContacts(loadContacts().filter { it.phoneHash != phoneHash })
    }

    /** Attach/replace a voiceprint embedding for a contact (enrolled on their phone). */
    fun setVoiceprint(phoneHash: String, embedding: FloatArray) {
        saveContacts(loadContacts().map {
            if (it.phoneHash == phoneHash) it.copy(voiceprint = embedding) else it
        })
    }

    fun appendVerdict(risk: Float, band: String, explanation: String) {
        try {
            val arr = if (verdictsFile().exists()) JSONArray(verdictsFile().readText()) else JSONArray()
            arr.put(JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("risk", risk.toDouble())
                put("band", band)
                put("summary", explanation.take(300))
            })
            // TTL purge: keep last 200 AND drop older than VERDICT_TTL_DAYS
            val cutoff = System.currentTimeMillis() - VERDICT_TTL_DAYS * 24 * 3600 * 1000
            val kept = JSONArray()
            var start = maxOf(0, arr.length() - 200)
            for (i in start until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optLong("ts", 0) >= cutoff) kept.put(o)
            }
            verdictsFile().writeText(kept.toString())
        } catch (_: Exception) {}
    }

    fun recentVerdicts(limit: Int = 20): List<String> {
        return try {
            if (!verdictsFile().exists()) return emptyList()
            val arr = JSONArray(verdictsFile().readText())
            val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            return (maxOf(0, arr.length() - limit) until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                "${fmt.format(Date(o.optLong("ts")))} · ${o.optDouble("risk").toInt()}/100 ${o.optString("band")} · ${o.optString("summary").take(80)}"
            }.reversed()
        } catch (_: Exception) { emptyList() }
    }

    fun buildFirDraft(risk: Float?, band: String?, explanation: String?, transcript: String?): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return buildString {
            appendLine("CYBER COMPLAINT DRAFT (generated on-device by antAI)")
            appendLine("Date: ${fmt.format(Date())}")
            appendLine("Report via: https://cybercrime.gov.in or helpline 1930")
            appendLine()
            appendLine("Incident: suspected voice-cloning / phone scam attempt.")
            if (risk != null) appendLine("antAI risk score: ${risk.toInt()}/100 ($band).")
            if (!explanation.isNullOrBlank()) appendLine("Signals: $explanation")
            if (!transcript.isNullOrBlank()) {
                appendLine("Transcript excerpt (user-provided):")
                appendLine(transcript.take(500))
            }
            appendLine()
            appendLine("Complainant: [name, phone, address]")
            appendLine("Suspect number: [caller number + time of call]")
            appendLine("Loss (if any): [amount, UPI ref]")
        }
    }
}
