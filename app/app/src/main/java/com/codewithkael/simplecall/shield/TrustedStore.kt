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

    /**
     * Verdict retention window in days (#8). Wired to the user's Settings choice
     * (antai_settings / retention_days) instead of the old hardcoded 30, so the
     * "keep incidents for N days" control actually governs the on-device purge.
     * Clamped to >=1 so a stray 0 can't wipe the whole history on the next write,
     * and falls back to VERDICT_TTL_DAYS if the pref is unset or unreadable.
     */
    private fun retentionDays(): Long =
        try {
            context.getSharedPreferences("antai_settings", Context.MODE_PRIVATE)
                .getInt("retention_days", VERDICT_TTL_DAYS.toInt())
                .toLong().coerceIn(1L, 3650L)
        } catch (_: Exception) { VERDICT_TTL_DAYS }

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

    fun appendVerdict(
        risk: Float,
        band: String,
        explanation: String,
        hard: List<String> = emptyList(),
        soft: List<String> = emptyList(),
        transcript: String = "",
        source: String = "on-device",
        // Phase 3.1 forensic telemetry (all optional / honest-absent):
        //  - spoofProb: the AI-voice score for the window, or null when the spoof
        //    model didn't score it (silence / non-speech) — persisted as JSON null,
        //    never a fabricated 0.
        //  - contact: the matched/claimed contact display name, or null (unknown
        //    caller). scamType: the dominant scam category, or null (no keyword hit).
        spoofProb: Float? = null,
        contact: String? = null,
        scamType: String? = null,
    ) {
        try {
            val arr = if (verdictsFile().exists()) JSONArray(verdictsFile().readText()) else JSONArray()
            arr.put(JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("risk", risk.toDouble())
                put("band", band)
                put("summary", explanation.take(300))
                put("hard", JSONArray(hard))
                put("soft", JSONArray(soft))
                put("transcript", transcript.take(300))
                put("source", source)
                // JSONObject.NULL keeps an explicit null in the record (honest
                // "not measured"), distinct from a fabricated 0.0 / empty string.
                put("spoofProb", spoofProb?.toDouble() ?: JSONObject.NULL)
                put("contact", contact ?: JSONObject.NULL)
                put("scamType", scamType ?: JSONObject.NULL)
            })
            // TTL purge: keep last 200 AND drop older than the retention window
            val cutoff = System.currentTimeMillis() - retentionDays() * 24 * 3600 * 1000
            val kept = JSONArray()
            var start = maxOf(0, arr.length() - 200)
            for (i in start until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optLong("ts", 0) >= cutoff) kept.put(o)
            }
            verdictsFile().writeText(kept.toString())
        } catch (_: Exception) {}
    }

    /** Structured local verdict (for the merged Incident History). */
    data class LocalVerdict(
        val ts: Long,
        val risk: Float,
        val band: String,
        val summary: String,
        val hard: List<String>,
        val soft: List<String>,
        val transcript: String,
        val source: String,
        // Phase 3.1 forensic fields — null = not measured / unknown (never faked).
        val spoofProb: Float? = null,
        val contact: String? = null,
        val scamType: String? = null,
    )

    fun recentVerdictsDetailed(limit: Int = 50): List<LocalVerdict> {
        return try {
            if (!verdictsFile().exists()) return emptyList()
            val arr = JSONArray(verdictsFile().readText())
            (maxOf(0, arr.length() - limit) until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LocalVerdict(
                    ts = o.optLong("ts"),
                    risk = o.optDouble("risk").toFloat(),
                    band = o.optString("band", "passive"),
                    summary = o.optString("summary", ""),
                    hard = o.optJSONArray("hard")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                    soft = o.optJSONArray("soft")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                    transcript = o.optString("transcript", ""),
                    source = o.optString("source", "on-device"),
                    // isNull -> honest null; older records without the key also read null.
                    spoofProb = if (o.isNull("spoofProb")) null else o.optDouble("spoofProb").toFloat(),
                    contact = if (o.isNull("contact")) null else o.optString("contact").ifBlank { null },
                    scamType = if (o.isNull("scamType")) null else o.optString("scamType").ifBlank { null },
                )
            }.reversed()
        } catch (_: Exception) { emptyList() }
    }

    fun recentVerdicts(limit: Int = 20): List<String> {
        return recentVerdictsDetailed(limit).map {
            val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            "${fmt.format(Date(it.ts))} · ${it.risk.toInt()}/100 ${it.band} · ${it.summary.take(80)}"
        }
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
