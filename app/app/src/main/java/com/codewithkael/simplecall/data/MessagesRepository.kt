package com.codewithkael.simplecall.data

import android.content.Context
import android.util.Log
import com.codewithkael.simplecall.ai.TextEngines
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.AntaiSession
import com.codewithkael.simplecall.remote.antai.ChatMessage
import com.codewithkael.simplecall.remote.antai.ChatSocketClient
import com.codewithkael.simplecall.remote.antai.Conversation
import com.codewithkael.simplecall.remote.antai.MessageChannel
import com.codewithkael.simplecall.remote.antai.NotificationEvent
import com.codewithkael.simplecall.remote.antai.RiskBand
import com.codewithkael.simplecall.remote.antai.VerdictPayload
import com.codewithkael.simplecall.remote.antai.VerifyOutcome
import com.codewithkael.simplecall.remote.antai.VerifyPrompt
import com.codewithkael.simplecall.sms.SmsReader
import com.codewithkael.simplecall.sms.SmsSender
import com.codewithkael.simplecall.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the unified messaging + notification-guard UI.
 *
 * Merges three inputs into thread state, keyed by a normalized phone number:
 *   1. device SMS (read on start; new ones via SmsReceiver)
 *   2. antAI in-app chat (sent via REST, received via ChatSocketClient)
 *   3. scanned notifications from other apps (NotificationListenerService)
 *
 * Risk is always decided by the antAI server; this class only stores the
 * returned scores/verdicts and exposes flows to the UI. It is completely
 * separate from the calling/WebRTC stack.
 */
@Singleton
class MessagesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rest: AntaiRestClient,
    private val session: AntaiSession
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val smsReader = SmsReader(context)
    private val smsSender = SmsSender(context)

    private val lock = Any()
    // insertion-ordered thread store: peerKey -> messages
    private val threads = LinkedHashMap<String, MutableList<ChatMessage>>()
    private val displayNames = HashMap<String, String>()
    private val notifs = ArrayList<NotificationEvent>()
    private val localSeq = AtomicLong(0)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationEvent>>(emptyList())
    val notifications: StateFlow<List<NotificationEvent>> = _notifications.asStateFlow()

    private val _socketConnected = MutableStateFlow(false)
    val socketConnected: StateFlow<Boolean> = _socketConnected.asStateFlow()

    // Non-null while someone is claiming to be this user on a call and the server
    // is waiting for them to confirm or deny it.
    private val _verifyPrompt = MutableStateFlow<VerifyPrompt?>(null)
    val verifyPrompt: StateFlow<VerifyPrompt?> = _verifyPrompt.asStateFlow()

    /**
     * Outcome of a verification THIS user asked for from their call screen.
     *
     * It arrives on the chat socket because the server addresses it to the
     * phone/OTP user id, while the call screen's own socket is registered under a
     * different, auto-provisioned user row. MainViewModel collects this so the
     * in-call bar can show confirmed/denied/timeout.
     */
    private val _verifyOutcome = MutableStateFlow<VerifyOutcome?>(null)
    val verifyOutcome: StateFlow<VerifyOutcome?> = _verifyOutcome.asStateFlow()

    private var socket: ChatSocketClient? = null
    private var started = false

    // ---------------- lifecycle ----------------

    /** Hydrate from device SMS (always works, sign-in or not). */
    fun start() {
        if (started) return
        started = true
        scope.launch { hydrateSms() }
        // Socket needs a server-issued token; only try when actually signed in.
        connectSocket()
    }

    /** Re-hydrate after real (server) login or when SMS permission is newly granted. */
    fun refreshSms() {
        scope.launch { hydrateSms() }
    }

    /** Called by the ViewModel right after a successful login. */
    fun onLoggedIn() {
        scope.launch { hydrateSms() }
        connectSocket()
    }

    private fun connectSocket() {
        // A single socket: teardown any stale one first so re-login with a new
        // token doesn't leak the old connection (or keep using a dead one).
        socket?.close()
        socket = null
        if (!session.isLoggedIn) return
        val host = callHost()
        val url = Constants.getAntaiChatWsUrl(host, session.token)
        socket = ChatSocketClient(url, socketCallback).also { it.connect() }
    }

    fun shutdown() {
        socket?.close()
        socket = null
        started = false
    }

    // ---------------- sending ----------------

    /**
     * Send a message to [peerPhone]. When [viaSms] is true it goes out as a real
     * SMS (SmsManager); otherwise it goes through the antAI in-app chat (REST,
     * which also runs scam detection). The outgoing message is stored optimistically.
     */
    fun sendMessage(peerPhone: String, body: String, viaSms: Boolean) {
        if (body.isBlank()) return
        val key = keyOf(peerPhone)
        val now = System.currentTimeMillis()
        if (viaSms) {
            val ok = smsSender.send(peerPhone, body)
            addMessage(
                ChatMessage(
                    id = "sms-out-${localSeq.incrementAndGet()}",
                    peerPhone = key, body = body, timestamp = now,
                    outgoing = true, channel = MessageChannel.SMS
                )
            )
            if (!ok) Log.w(TAG, "SMS send returned false for $peerPhone")
        } else {
            val localId = "msg-out-${localSeq.incrementAndGet()}"
            addMessage(
                ChatMessage(
                    id = localId, peerPhone = key, body = body, timestamp = now,
                    outgoing = true, channel = MessageChannel.IN_APP, analysisPending = true
                )
            )
            scope.launch {
                rest.sendChat(peerPhone, body)
                    .onSuccess { r ->
                        // reconcile local id -> server message_id, clear pending
                        replaceMessage(key, localId) {
                            it.copy(
                                id = if (r.messageId >= 0) "msg-${r.messageId}" else it.id,
                                riskScore = r.riskScore, verdict = r.verdict,
                                analysisPending = r.analysisPending
                            )
                        }
                    }
                    .onFailure { Log.w(TAG, "chat/send failed", it) }
            }
        }
    }

    // ---------------- degraded local fallback ----------------

    /**
     * Bug 1 — degraded local scoring. When the /notify/external round-trip fails
     * (offline, server down, or not signed in), fall back to the SAME on-device
     * scam heuristics the Shield uses so an inbound scam still surfaces a verdict
     * instead of silently reading "safe". Provenance is flagged (onDevice=true)
     * so the UI can label it honestly; a benign body carries NO verdict and a
     * sub-threshold score, so it renders SAFE — an honest absence, never a fake
     * alarm (same discipline as the acoustic pipeline's null-on-absent signals).
     */
    private data class LocalScore(
        val risk: Double, val verdict: String?, val why: String?, val scamType: String?
    )

    private fun localScore(body: String): LocalScore {
        val r = try { TextEngines.scamHeuristic(body) } catch (_: Exception) { null }
        val risk = (r?.prob ?: 0f) * 100.0
        val elevated = risk >= RiskBand.CAUTION_AT
        val typeSuffix = r?.type?.let { " — looks like $it" } ?: ""
        return LocalScore(
            risk = risk,
            verdict = if (elevated) "On-device scam check" else null,
            why = if (elevated) "Scored on this device because the server was unreachable$typeSuffix" else null,
            scamType = r?.type,
        )
    }

    // ---------------- inbound: device SMS ----------------

    /** Called by SmsReceiver for a newly received SMS. Stores it, then scores it. */
    fun onSmsReceived(address: String, body: String, date: Long) {
        val key = keyOf(address)
        val id = "sms-in-${localSeq.incrementAndGet()}"
        addMessage(
            ChatMessage(
                id = id, peerPhone = key, body = body, timestamp = date,
                outgoing = false, channel = MessageChannel.SMS, analysisPending = true
            )
        )
        scope.launch {
            rest.notifyExternal(source = "sms", sender = address, text = body)
                .onSuccess { res ->
                    replaceMessage(key, id) {
                        it.copy(
                            riskScore = res.verdict?.riskScore ?: res.riskScore,
                            verdict = res.verdict?.verdict,
                            why = res.verdict?.why,
                            action = res.verdict?.action,
                            scamType = res.verdict?.scamType,
                            analysisPending = false
                        )
                    }
                }
                .onFailure {
                    Log.w(TAG, "notify/external (sms) failed — scoring on-device", it)
                    val ls = localScore(body)
                    replaceMessage(key, id) { m ->
                        m.copy(
                            riskScore = ls.risk,
                            verdict = ls.verdict,
                            why = ls.why,
                            scamType = ls.scamType,
                            analysisPending = false,
                            onDevice = true,
                        )
                    }
                }
        }
    }

    // ---------------- inbound: other-app notifications ----------------

    /** Called by the NotificationListenerService for a captured notification. */
    fun onNotificationPosted(source: String, sender: String, text: String) {
        if (text.isBlank()) return
        val id = "notif-${localSeq.incrementAndGet()}"
        val event = NotificationEvent(
            id = id, source = source, sender = sender, text = text,
            timestamp = System.currentTimeMillis(), analysisPending = true
        )
        synchronized(lock) { notifs.add(0, event) }
        emitNotifs()
        scope.launch {
            rest.notifyExternal(source = source, sender = sender, text = text)
                .onSuccess { res ->
                    replaceNotif(id) {
                        it.copy(
                            riskScore = res.verdict?.riskScore ?: res.riskScore,
                            verdict = res.verdict?.verdict,
                            why = res.verdict?.why,
                            action = res.verdict?.action,
                            scamType = res.verdict?.scamType,
                            analysisPending = false
                        )
                    }
                }
                .onFailure {
                    Log.w(TAG, "notify/external ($source) failed — scoring on-device", it)
                    val ls = localScore(text)
                    replaceNotif(id) { ev ->
                        ev.copy(
                            riskScore = ls.risk,
                            verdict = ls.verdict,
                            why = ls.why,
                            scamType = ls.scamType,
                            analysisPending = false,
                            onDevice = true,
                        )
                    }
                }
        }
    }

    // ---------------- server history ----------------

    /** Merge server-side in-app chat history for a peer into the thread. */
    fun refreshHistory(peerPhone: String) {
        if (!session.isLoggedIn) return
        scope.launch {
            rest.history(peerPhone).onSuccess { items ->
                val key = keyOf(peerPhone)
                synchronized(lock) {
                    val list = threads.getOrPut(key) { ArrayList() }
                    val known = list.mapTo(HashSet()) { it.id }
                    items.forEach { h ->
                        val mid = "msg-${h.id}"
                        if (mid !in known) {
                            list.add(
                                ChatMessage(
                                    id = mid, peerPhone = key, body = h.body,
                                    timestamp = h.createdAt, outgoing = h.fromMe,
                                    channel = MessageChannel.IN_APP,
                                    riskScore = h.riskScore,
                                    verdict = if (h.intercepted) "Flagged" else null,
                                    analysisPending = false
                                )
                            )
                        }
                    }
                    list.sortBy { it.timestamp }
                }
                rebuild()
            }.onFailure { Log.w(TAG, "history failed", it) }
        }
    }

    // ---------------- socket callback ----------------

    private val socketCallback = object : ChatSocketClient.Callback {
        override fun onOpened() { _socketConnected.value = true }
        override fun onClosed() { _socketConnected.value = false }
        override fun onError(e: Exception?) { _socketConnected.value = false }

        override fun onChatRecv(messageId: Long, senderId: Long, senderPhone: String, body: String) {
            val key = keyOf(senderPhone)
            addMessage(
                ChatMessage(
                    id = if (messageId >= 0) "msg-$messageId" else "msg-in-${localSeq.incrementAndGet()}",
                    peerPhone = key, body = body, timestamp = System.currentTimeMillis(),
                    outgoing = false, channel = MessageChannel.IN_APP, analysisPending = true
                )
            )
        }

        override fun onChatSent(messageId: Long, riskScore: Double) { /* ack; REST path already tracked */ }

        override fun onVerdict(peerPhone: String, messageId: Long, kind: String, payload: VerdictPayload) {
            val key = keyOf(peerPhone)
            val mid = if (messageId >= 0) "msg-$messageId" else null
            synchronized(lock) {
                val list = threads[key] ?: return
                val idx = when {
                    mid != null -> list.indexOfFirst { it.id == mid }
                    else -> list.indexOfLast { !it.outgoing }
                }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(
                        riskScore = payload.riskScore,
                        verdict = payload.verdict.ifBlank { "Flagged" },
                        why = payload.why, action = payload.action,
                        scamType = payload.scamType, analysisPending = false
                    )
                }
            }
            rebuild()
        }

        override fun onFreeze(peerPhone: String, message: String, reason: String) {
            Log.w(TAG, "freeze.request for $peerPhone: $message")
        }

        override fun onVerifyPrompt(prompt: VerifyPrompt) {
            Log.w(TAG, "verify.prompt for session ${prompt.sessionKey}")
            _verifyPrompt.value = prompt
        }

        /**
         * Outcome of a verification this user asked for from their own call screen.
         * Only stored here; MainViewModel owns the in-call presentation.
         */
        override fun onVerifyResult(outcome: VerifyOutcome) {
            Log.d(TAG, "verify.result state=${outcome.state} verified=${outcome.verified}")
            _verifyOutcome.value = outcome
        }
    }

    /** Called once the in-call bar has consumed an outcome, so it isn't replayed. */
    fun clearVerifyOutcome() {
        _verifyOutcome.value = null
    }

    // ---------------- identity verification (responder side) ----------------

    /**
     * Answer the "is that really you on the call?" challenge.
     *
     * [answer] = false is the important path: it tells the server the caller is an
     * impostor, which escalates the other person's call to critical. Only clear the
     * prompt once the server has taken the answer — dropping it on a network error
     * would silently discard the one signal that could stop a scam in progress.
     */
    fun answerVerifyPrompt(answer: Boolean) {
        val p = _verifyPrompt.value ?: return
        _verifyPrompt.value = p.copy(answering = true)
        scope.launch {
            rest.respondToVerify(p.sessionKey, answer)
                .onSuccess { accepted ->
                    // The server refuses answers for challenges that already timed
                    // out. Showing "thanks, we warned them" in that case would be a
                    // lie, so surface it as an expiry instead.
                    _verifyPrompt.value = if (accepted) {
                        p.copy(answering = false, answered = answer)
                    } else {
                        p.copy(
                            answering = false,
                            error = "This request has expired. Your contact was " +
                                "already told the call could not be verified — call " +
                                "them directly if you need to sort it out."
                        )
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "verify/respond failed", e)
                    _verifyPrompt.value = p.copy(
                        answering = false,
                        error = "Couldn't reach the antAI server. Try again."
                    )
                }
        }
    }

    /** Dismiss without answering (the server treats silence as unverified). */
    fun dismissVerifyPrompt() {
        _verifyPrompt.value = null
    }

    // ---------------- internals ----------------

    private suspend fun hydrateSms() {
        val records = smsReader.loadRecent()
        if (records.isEmpty()) { rebuild(); return }
        synchronized(lock) {
            records.forEach { r ->
                val key = keyOf(r.address)
                val list = threads.getOrPut(key) { ArrayList() }
                // de-dupe historical rows by (timestamp, body, direction)
                val dup = list.any {
                    it.channel == MessageChannel.SMS && it.timestamp == r.date &&
                        it.body == r.body && it.outgoing == !r.incoming
                }
                if (!dup) {
                    list.add(
                        ChatMessage(
                            id = "sms-hist-${localSeq.incrementAndGet()}",
                            peerPhone = key, body = r.body, timestamp = r.date,
                            outgoing = !r.incoming, channel = MessageChannel.SMS,
                            analysisPending = false
                        )
                    )
                }
            }
            threads.values.forEach { it.sortBy { m -> m.timestamp } }
        }
        rebuild()
    }

    private fun addMessage(msg: ChatMessage) {
        synchronized(lock) {
            val list = threads.getOrPut(msg.peerPhone) { ArrayList() }
            list.add(msg)
            list.sortBy { it.timestamp }
        }
        rebuild()
    }

    private fun replaceMessage(key: String, id: String, transform: (ChatMessage) -> ChatMessage) {
        synchronized(lock) {
            val list = threads[key] ?: return
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = transform(list[idx])
        }
        rebuild()
    }

    private fun replaceNotif(id: String, transform: (NotificationEvent) -> NotificationEvent) {
        synchronized(lock) {
            val idx = notifs.indexOfFirst { it.id == id }
            if (idx >= 0) notifs[idx] = transform(notifs[idx])
        }
        emitNotifs()
    }

    private fun rebuild() {
        val snapshot = synchronized(lock) {
            threads.map { (key, msgs) ->
                Conversation(
                    peerPhone = key,
                    displayName = displayNames[key] ?: key,
                    messages = msgs.toList()
                )
            }
        }.sortedByDescending { it.lastTimestamp }
        _conversations.value = snapshot
    }

    private fun emitNotifs() {
        _notifications.value = synchronized(lock) { notifs.toList() }
    }

    fun conversationFor(peerPhone: String): Conversation? {
        val key = keyOf(peerPhone)
        return _conversations.value.firstOrNull { it.peerPhone == key }
    }

    private fun callHost() =
        context.getSharedPreferences(Constants.CALL_PREFS, Context.MODE_PRIVATE)
            .getString(Constants.KEY_SERVER_HOST, Constants.DEFAULT_SERVER_HOST)
            ?: Constants.DEFAULT_SERVER_HOST

    /**
     * Thread key for an address. Uses the normalized phone when it has enough
     * digits; otherwise keeps the raw alphanumeric sender id (bank/OTP shortcodes
     * like "HDFCBK" have no phone form).
     */
    private fun keyOf(address: String): String {
        val norm = AntaiSession.normalize(address)
        return if (norm.count { it.isDigit() } >= 3) norm else address.trim()
    }

    companion object {
        private const val TAG = "MsgRepo"
    }
}
