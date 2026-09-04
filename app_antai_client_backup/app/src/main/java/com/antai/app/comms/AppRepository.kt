package com.antai.app.comms

import com.antai.app.AppContainer
import com.antai.app.data.local.CallLogEntity
import com.antai.app.data.local.ContactEntity
import com.antai.app.data.local.FlagCacheEntity
import com.antai.app.data.local.MessageEntity
import com.antai.app.data.local.ReportEntity
import com.antai.app.data.local.VerdictEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** High-level repository: keeps ALL storage on-device (Room) and syncs with the server. */
object AppRepository {

    // ---------------- contacts ----------------
    suspend fun syncContacts() = withContext(Dispatchers.IO) {
        val db = AppContainer.db
        runCatching {
            val res = AppContainer.api.listContacts()
            val arr = res.optJSONArray("contacts") ?: return@runCatching
            val entities = (0 until arr.length()).mapNotNull { i ->
                val c = arr.getJSONObject(i)
                val phone = c.optString("phone")
                if (phone.isBlank()) null else ContactEntity(
                    phone = phone,
                    displayName = c.optString("label"),
                    label = c.optString("label"),
                    relationshipTag = c.optString("relationship_tag").ifBlank { null },
                    isTrusted = c.optBoolean("is_trusted"),
                    linked = c.optBoolean("linked"),
                    synced = true,
                )
            }
            db.contacts().upsertAll(entities)
        }
    }

    suspend fun addContact(phone: String, label: String, tag: String?, trusted: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val db = AppContainer.db
            runCatching {
                val res = AppContainer.api.addContact(phone, label, tag, trusted)
                if (res.optJSONObject("contact_id") != null || res.length() > 0) {
                    db.contacts().upsert(ContactEntity(
                        phone = phone, displayName = label, label = label,
                        relationshipTag = tag, isTrusted = trusted, synced = true))
                }
                true
            }.getOrDefault(false)
        }

suspend fun linkTrusted(phone: String, label: String, tag: String?): Boolean =
        withContext(Dispatchers.IO) {
            val db = AppContainer.db
            runCatching {
                AppContainer.api.linkTrusted(phone, label, tag)
                db.contacts().upsert(ContactEntity(
                    phone = phone, displayName = label, label = label,
                    relationshipTag = tag, isTrusted = true, linked = true, synced = true))
                true
            }.getOrDefault(false)
        }

    // ---------------- chat ----------------
    /** Result of a send: the verdict/freeze the server computed for THIS
     * message, so the sender's own screen can show it immediately rather
     * than only relying on the WS push (which targets the recipient). */
    data class SendResult(val verdict: JSONObject?, val freeze: JSONObject?)

    suspend fun sendMessage(peerPhone: String, body: String): SendResult =
        withContext(Dispatchers.IO) {
            val db = AppContainer.db
            // optimistic: show the bubble instantly, then update in place
            val id = db.messages().insert(MessageEntity(
                peerPhone = peerPhone, fromMe = true, body = body,
                delivered = false, createdAt = System.currentTimeMillis()))
            runCatching {
                val res = AppContainer.api.sendChat(peerPhone, body)
                val risk = res.optDouble("risk_score", 0.0)
                val verdict = res.optJSONObject("verdict")
                val freeze = res.optJSONObject("freeze")
                db.messages().markResult(
                    id, risk,
                    verdict?.optString("band", "passive") ?: "passive",
                    verdict?.optString("verdict", "") ?: "",
                    freeze != null)
                if (verdict != null) {
                    db.verdicts().insert(verdict.toEntity("message", "msg:${peerPhone}"))
                }
                SendResult(verdict, freeze)
            }.getOrDefault(SendResult(null, null))
        }

    suspend fun flushQueue() = withContext(Dispatchers.IO) {
        val db = AppContainer.db
        for (m in db.messages().pending()) {
            runCatching {
                val res = AppContainer.api.sendChat(m.peerPhone, m.body)
                db.messages().markResult(
                    m.id, res.optDouble("risk_score", 0.0), "passive", "",
                    res.optJSONObject("freeze") != null)
            }
        }
    }

    suspend fun receiveMessage(peerPhone: String, body: String, senderId: Int) =
        withContext(Dispatchers.IO) {
            val db = AppContainer.db
            db.messages().insert(MessageEntity(
                peerPhone = peerPhone, fromMe = false, body = body, delivered = true))
            val peer = db.contacts().byPhone(peerPhone)
            if (peer == null) {
                db.contacts().upsert(ContactEntity(
                    phone = peerPhone, displayName = "Unknown", label = "Unknown"))
            }
        }

    suspend fun saveVerdict(v: JSONObject, kind: String, sessionKey: String) =
        withContext(Dispatchers.IO) {
            AppContainer.db.verdicts().insert(v.toEntity(kind, sessionKey))
        }

    // ---------------- history ----------------
    suspend fun syncHistory() = withContext(Dispatchers.IO) {
        val db = AppContainer.db
        runCatching {
            val v = AppContainer.api.verdicts()
            val arr = v.optJSONArray("verdicts") ?: return@withContext
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.verdicts().insert(VerdictEntity(
                    sessionKey = o.optString("id", ""),
                    kind = o.optString("kind", "call"),
                    riskScore = o.optDouble("risk_score", 0.0),
                    band = o.optString("band", "passive"),
                    verdictText = o.optString("verdict", ""),
                    why = o.optString("why", ""),
                    action = o.optString("action", ""),
                    scamType = o.optString("scam_type").ifBlank { null },
                    signals = o.optJSONObject("signals")?.toString() ?: "{}",
                    createdAt = o.optLong("created_at", 0) ?: System.currentTimeMillis()))
            }
        }
        runCatching {
            val r = AppContainer.api.reports()
            val arr = r.optJSONArray("reports") ?: return@withContext
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.reports().insert(ReportEntity(
                    sessionKey = o.optString("id", ""),
                    kind = o.optString("kind", "call"),
                    title = o.optString("title", ""),
                    body = o.optString("body", ""),
                    scamType = o.optString("scam_type").ifBlank { null },
                    createdAt = o.optLong("created_at", 0) ?: System.currentTimeMillis()))
            }
        }
        runCatching {
            val c = AppContainer.api.calls()
            val arr = c.optJSONArray("calls") ?: return@withContext
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.calls().insert(CallLogEntity(
                    peerPhone = o.optString("peer_phone", ""),
                    peerName = "",
                    kind = o.optString("kind", "voice"),
                    status = o.optString("status", ""),
                    riskPeak = o.optDouble("risk_peak", 0.0),
                    startedAt = o.optLong("started_at", 0) ?: System.currentTimeMillis(),
                    endedAt = o.optLong("ended_at", 0) ?: System.currentTimeMillis()))
            }
        }
    }

    // ---------------- collective flags ----------------
    suspend fun flagNumber(phone: String, kind: String = "scam") = withContext(Dispatchers.IO) {
        val db = AppContainer.db
        val existing = db.flags().byPhone(phone)
        db.flags().upsert(FlagCacheEntity(
            phone = phone, kind = kind,
            count = (existing?.count ?: 0) + 1,
            confidence = 1.0))
        runCatching { AppContainer.api.submitFlag(phone, kind, null) }
    }

    // ---------------- verify / freeze ----------------
    suspend fun respondVerify(sessionKey: String, answer: Boolean) =
        runCatching { AppContainer.api.respondVerify(sessionKey, answer) }

    suspend fun requestVerify(sessionKey: String, claimPeerPhone: String): Boolean =
        runCatching {
            AppContainer.api.requestVerify(sessionKey, claimPeerPhone).optBoolean("ok", false)
        }.getOrDefault(false)

    suspend fun decideFreeze(freezeId: Long, decision: String) =
        runCatching { AppContainer.api.decideFreeze(freezeId, decision) }

    // ---------------- calls ----------------
    suspend fun logCallStarted(peerPhone: String, peerName: String, kind: String): Long =
        withContext(Dispatchers.IO) {
            AppContainer.db.calls().insert(CallLogEntity(
                peerPhone = peerPhone, peerName = peerName, kind = kind,
                status = "ongoing", startedAt = System.currentTimeMillis()))
        }

    suspend fun logCallEnded(id: Long, status: String, risk: Double) =
        withContext(Dispatchers.IO) {
            AppContainer.db.calls().update(id, status, risk, System.currentTimeMillis())
        }
}

fun JSONObject.toEntity(kind: String, sessionKey: String): VerdictEntity =
    VerdictEntity(
        sessionKey = sessionKey,
        kind = kind,
        riskScore = optDouble("risk_score", 0.0),
        band = optString("band", "passive"),
        verdictText = optString("verdict", ""),
        why = optString("why", ""),
        action = optString("action", ""),
        scamType = optString("scam_type").ifBlank { null },
        signals = optJSONObject("signals")?.toString() ?: "{}",
        createdAt = System.currentTimeMillis())