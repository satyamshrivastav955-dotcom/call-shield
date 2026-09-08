package com.codewithkael.simplecall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.ai.ModelManager
import com.codewithkael.simplecall.ai.ShieldPipeline
import com.codewithkael.simplecall.ai.SpeakerEngine
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.VoiceprintStatus
import com.codewithkael.simplecall.shield.TrustedStore
import com.codewithkael.simplecall.voice.VoiceprintRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Family voiceprint model. Two data paths, one list:
 *  - Self: 3 phrases -> server voiceprint (AntaiRestClient) — used during analyzed calls.
 *  - Family: on-device voiceprint (TrustedStore + SpeakerEngine ECAPA embedding) —
 *    shared with the Shield stack, so the armed mic service flags voice mismatches live.
 */
data class FamilyMember(
    val id: String, // "self" or the TrustedStore phoneHash
    val name: String,
    val relation: String,
    val samplesCount: Int = 0, // self only (server sample count)
    val lastVerified: String = "Active guardian",
    val hasVoiceprint: Boolean = false, // family only (on-device)
    val isPrimary: Boolean = false
)

data class AddMemberFlow(
    val active: Boolean = false,
    val step: Int = 0, // 0: details input, 1: recording, 2: confirmation
    val name: String = "",
    val relation: String = "",
    val phone: String = "", // family only — hashed before storage, never kept plaintext
    val phoneHash: String? = null, // set when re-enrolling an existing family member
    val isSelf: Boolean = true,
    val phraseIndex: Int = 0,
    val phrasesRecorded: Int = 0
)

val ENROLL_PHRASES = listOf(
    "This is my real voice. I am registering it with antAI so my family is protected.",
    "Never transfer funds or share verification OTPs based on an unexpected call.",
    "If someone clones my voice, antAI can cross-check against this registered print."
)

@HiltViewModel
class VoiceprintViewModel @Inject constructor(
    private val rest: AntaiRestClient,
    private val recorder: VoiceprintRecorder,
    private val store: TrustedStore,
    private val models: ModelManager,
    private val pipeline: ShieldPipeline
) : ViewModel() {

    data class UiState(
        val loadingStatus: Boolean = true,
        val status: VoiceprintStatus = VoiceprintStatus(),
        val uploading: Boolean = false,
        val message: String? = null,
        val messageIsError: Boolean = false,
        val serverUnreachable: Boolean = false,
        val members: List<FamilyMember> = emptyList(),
        val addFlow: AddMemberFlow = AddMemberFlow()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val progress: StateFlow<VoiceprintRecorder.Progress> = recorder.progress

    init {
        refresh()
    }

    /** Self entry (server status) + family contacts (TrustedStore) — one list. */
    private fun membersWith(self: FamilyMember?): List<FamilyMember> {
        val family = store.loadContacts().map { c ->
            FamilyMember(
                id = c.phoneHash,
                name = c.displayName,
                relation = c.tag ?: c.label,
                hasVoiceprint = c.hasVoiceprint,
                lastVerified = if (c.hasVoiceprint) "Voice enrolled (on-device)" else "No voiceprint yet"
            )
        }
        return (self?.let { listOf(it) } ?: emptyList()) + family
    }

    private fun rebuildMembers() {
        val self = _ui.value.members.firstOrNull { it.isPrimary }
        _ui.value = _ui.value.copy(members = membersWith(self))
    }

    fun refresh() {
        _ui.value = _ui.value.copy(loadingStatus = true)
        viewModelScope.launch {
            rest.voiceprintStatus()
                .onSuccess { st ->
                    val self = if (st.enrolled) FamilyMember(
                        id = "self",
                        name = "Me (Account Owner)",
                        relation = "Self",
                        samplesCount = maxOf(1, st.count),
                        lastVerified = "Enrolled",
                        isPrimary = true
                    ) else null
                    _ui.value = _ui.value.copy(
                        loadingStatus = false, status = st, serverUnreachable = false,
                        members = membersWith(self)
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(loadingStatus = false, serverUnreachable = true)
                    rebuildMembers()
                }
        }
    }

    // ── Flow controls ────────────────────────────────────────────────────────
    fun startAddMember() {
        _ui.value = _ui.value.copy(addFlow = AddMemberFlow(active = true, step = 0))
    }

    /** Re-enroll an existing family member's on-device voiceprint. */
    fun startReenroll(member: FamilyMember) {
        if (!familyVoiceModelReady()) {
            _ui.value = _ui.value.copy(
                message = "Voice model not on device yet — enroll after models are installed.",
                messageIsError = true
            )
            return
        }
        _ui.value = _ui.value.copy(
            addFlow = AddMemberFlow(
                active = true, step = 1, name = member.name, relation = member.relation,
                phoneHash = member.id, isSelf = false
            ),
            message = null
        )
    }

    fun setMemberDetails(name: String, relation: String, phone: String, isSelf: Boolean) {
        val current = _ui.value.addFlow
        if (isSelf) {
            _ui.value = _ui.value.copy(addFlow = current.copy(name = name, relation = relation, phone = phone, isSelf = true, step = 1))
            return
        }
        // Family: real on-device voiceprint contact in the shared TrustedStore
        // (same store ShieldScreen reads). Refuse voice enrollment honestly when
        // the ECAPA model isn't installed — the contact is still added.
        val hash = store.hashPhone(phone)
        store.addContact(name.ifBlank { "Family Member" }, phone, tag = relation.ifBlank { "Family" })
        rebuildMembers()
        if (!familyVoiceModelReady()) {
            _ui.value = _ui.value.copy(
                addFlow = current.copy(name = name, relation = relation, phoneHash = hash, isSelf = false, step = 2),
                message = "Added — but the voice model isn't on this device yet, so no voiceprint was recorded.",
                messageIsError = false
            )
            return
        }
        _ui.value = _ui.value.copy(addFlow = current.copy(name = name, relation = relation, phoneHash = hash, isSelf = false, step = 1))
    }

    fun cancelAddMember() {
        recorder.discard()
        _ui.value = _ui.value.copy(addFlow = AddMemberFlow(active = false), message = null)
    }

    /** Skip voice recording for a family member — contact stays without a print. */
    fun skipFamilyVoice() {
        recorder.discard()
        val current = _ui.value.addFlow
        _ui.value = _ui.value.copy(addFlow = current.copy(step = 2), message = null)
    }

    fun startRecording() {
        val flow = _ui.value.addFlow
        // Family enrollments need the on-device ECAPA model; check before opening the mic.
        if (!flow.isSelf && !familyVoiceModelReady()) {
            _ui.value = _ui.value.copy(
                message = "Voice model not on device yet — enroll after models are installed.",
                messageIsError = true
            )
            return
        }
        // Family: ~3s free talk (like the Shield flow); self: server's minimum.
        val minSeconds = if (flow.isSelf) _ui.value.status.minSeconds else 3.0
        val why = recorder.start(minSeconds = minSeconds)
        _ui.value = if (why == null) {
            _ui.value.copy(message = null, messageIsError = false)
        } else {
            _ui.value.copy(message = why, messageIsError = true)
        }
    }

    fun cancelRecording() {
        recorder.discard()
        _ui.value = _ui.value.copy(message = null, messageIsError = false)
    }

    fun stopAndUploadCurrentPhrase() {
        if (_ui.value.addFlow.isSelf) stopAndUploadSelfPhrase() else stopAndSaveFamilyVoice()
    }

    private fun stopAndUploadSelfPhrase() {
        val minSeconds = _ui.value.status.minSeconds
        val wav = recorder.stop()
        val secs = progress.value.seconds
        if (wav == null) {
            _ui.value = _ui.value.copy(
                message = "Nothing was recorded. Check microphone permissions.",
                messageIsError = true
            )
            return
        }
        if (secs < minSeconds) {
            _ui.value = _ui.value.copy(
                message = "Too short — please speak for at least ${minSeconds.toInt()} seconds.",
                messageIsError = true
            )
            return
        }

        _ui.value = _ui.value.copy(uploading = true, message = null, messageIsError = false)
        viewModelScope.launch {
            rest.enrollVoiceprint(wav)
                .onSuccess { r ->
                    _ui.value = _ui.value.copy(
                        uploading = false,
                        status = r.status,
                        serverUnreachable = false
                    )
                    if (r.enrolled) {
                        val currentFlow = _ui.value.addFlow
                        val nextIndex = currentFlow.phraseIndex + 1
                        if (nextIndex < ENROLL_PHRASES.size) {
                            // Advance to next phrase
                            _ui.value = _ui.value.copy(
                                addFlow = currentFlow.copy(
                                    phraseIndex = nextIndex,
                                    phrasesRecorded = nextIndex
                                ),
                                message = "Phrase ${currentFlow.phraseIndex + 1} recorded ✓"
                            )
                        } else {
                            // Completed all 3 phrases! Enroll/refresh the self profile.
                            _ui.value = _ui.value.copy(
                                addFlow = currentFlow.copy(step = 2),
                                message = null
                            )
                            refresh()
                        }
                    } else {
                        _ui.value = _ui.value.copy(
                            message = r.hint.ifBlank { "Couldn't register that recording. Try again." },
                            messageIsError = true
                        )
                    }
                }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        uploading = false,
                        message = "Couldn't reach the antAI server to save sample.",
                        messageIsError = true,
                        serverUnreachable = true
                    )
                }
        }
    }

    /**
     * Family voiceprint: compute the ECAPA embedding on-device, store it in
     * TrustedStore (shared with the Shield stack) and hand it to the running
     * pipeline so an armed Shield uses it immediately. Never fabricated — if
     * the model or recording fails, the contact keeps no voiceprint.
     */
    private fun stopAndSaveFamilyVoice() {
        val wav = recorder.stop()
        if (wav == null) {
            _ui.value = _ui.value.copy(
                message = "Nothing was recorded. Check microphone permissions.",
                messageIsError = true
            )
            return
        }
        val flow = _ui.value.addFlow
        _ui.value = _ui.value.copy(uploading = true, message = null, messageIsError = false)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pcm = pcmFromWav(wav)
            val embedding = try { SpeakerEngine(models).embed(pcm) } catch (_: Exception) { null }
            if (embedding == null) {
                _ui.value = _ui.value.copy(
                    uploading = false,
                    message = "Couldn't compute voiceprint (model missing/failed).",
                    messageIsError = true
                )
                return@launch
            }
            val hash = flow.phoneHash ?: store.hashPhone(flow.phone)
            store.setVoiceprint(hash, embedding)
            pipeline.enrolledVoiceprint = embedding
            // Per-contact (#4): keep the Shield's labeled print list current
            // and focus verification on the member just enrolled.
            try {
                pipeline.voiceprints = store.loadContacts()
                    .filter { it.voiceprint != null }
                    .map { com.codewithkael.simplecall.ai.LabeledVoiceprint(
                        it.displayName, it.phoneHash, it.voiceprint!!) }
                pipeline.selectContact(hash)
            } catch (_: Exception) {}
            _ui.value = _ui.value.copy(
                uploading = false,
                addFlow = flow.copy(step = 2),
                message = null
            )
            rebuildMembers()
        }
    }

    private fun familyVoiceModelReady(): Boolean =
        models.allReady() || File(models.pathFor("ecapa_tdnn.int8.onnx")).exists()

    /** Strip the 44-byte WAV header and convert s16LE mono -> float PCM. */
    private fun pcmFromWav(wav: ByteArray): FloatArray {
        val start = 44.coerceAtMost(wav.size)
        val n = (wav.size - start) / 2
        val out = FloatArray(n)
        var j = start
        for (i in 0 until n) {
            val lo = wav[j].toInt() and 0xFF
            val hi = wav[j + 1].toInt()
            out[i] = ((hi shl 8) or lo) / 32768f
            j += 2
        }
        return out
    }

    fun removeMember(id: String) {
        if (id == "self") {
            deleteAll()
        } else {
            store.removeContact(id)
            rebuildMembers()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            rest.deleteVoiceprints()
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        status = it,
                        message = "Stored voice samples removed.",
                        messageIsError = false
                    )
                    rebuildMembers()
                }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        message = "Couldn't remove samples right now.", messageIsError = true
                    )
                }
        }
    }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(message = null, messageIsError = false)
    }

    fun hasMicPermission() = recorder.hasMicPermission()

    override fun onCleared() {
        recorder.discard()
        super.onCleared()
    }
}
