package com.codewithkael.simplecall.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.VoiceprintStatus
import com.codewithkael.simplecall.voice.VoiceprintRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * Multi-profile family voiceprint model (Task A5).
 */
data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val samplesCount: Int = 1,
    val lastVerified: String = "Active guardian",
    val isPrimary: Boolean = false
)

data class AddMemberFlow(
    val active: Boolean = false,
    val step: Int = 0, // 0: details input, 1: phrase recording (0..2), 2: confirmation
    val name: String = "",
    val relation: String = "",
    // ponytail: server supports ONE voiceprint per user (the owner's). Self enrollments
    // upload to the server; family members are a local roster only — no per-speaker API exists.
    val isSelf: Boolean = true,
    val phraseIndex: Int = 0,
    val phrasesRecorded: Int = 0
)

val ENROLL_PHRASES = listOf(
    "This is my real voice. I am registering it with antAI so my family is protected.",
    "Never transfer funds or share verification OTPs based on an unexpected call.",
    "If someone clones my voice, antAI can cross-check against this registered print."
)

/**
 * Multi-profile family voiceprint enrollment and management (Task A5).
 */
@HiltViewModel
class VoiceprintViewModel @Inject constructor(
    private val rest: AntaiRestClient,
    private val recorder: VoiceprintRecorder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("antai_family_voiceprints", Context.MODE_PRIVATE)

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
        loadFamilyMembers()
        refresh()
    }

    private fun loadFamilyMembers() {
        val json = prefs.getString("members", "[]") ?: "[]"
        val list = mutableListOf<FamilyMember>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FamilyMember(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        relation = o.getString("relation"),
                        samplesCount = o.optInt("samplesCount", 1),
                        lastVerified = o.optString("lastVerified", "Active guardian"),
                        isPrimary = o.optBoolean("isPrimary", false)
                    )
                )
            }
        } catch (_: Exception) {}

        _ui.value = _ui.value.copy(members = list)
    }

    private fun saveFamilyMembers(list: List<FamilyMember>) {
        val arr = JSONArray()
        list.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("name", m.name)
            o.put("relation", m.relation)
            o.put("samplesCount", m.samplesCount)
            o.put("lastVerified", m.lastVerified)
            o.put("isPrimary", m.isPrimary)
            arr.put(o)
        }
        prefs.edit().putString("members", arr.toString()).apply()
        _ui.value = _ui.value.copy(members = list)
    }

    fun refresh() {
        _ui.value = _ui.value.copy(loadingStatus = true)
        viewModelScope.launch {
            rest.voiceprintStatus()
                .onSuccess { st ->
                    _ui.value = _ui.value.copy(
                        loadingStatus = false, status = st, serverUnreachable = false
                    )
                    // If primary self member doesn't exist, seed it with server count
                    val current = _ui.value.members.toMutableList()
                    if (st.enrolled && current.none { it.isPrimary }) {
                        current.add(
                            0,
                            FamilyMember(
                                id = "self",
                                name = "Me (Account Owner)",
                                relation = "Self",
                                samplesCount = maxOf(1, st.count),
                                lastVerified = "Enrolled",
                                isPrimary = true
                            )
                        )
                        saveFamilyMembers(current)
                    }
                }
                .onFailure {
                    _ui.value = _ui.value.copy(loadingStatus = false, serverUnreachable = true)
                }
        }
    }

    // ── Flow controls ────────────────────────────────────────────────────────
    fun startAddMember() {
        _ui.value = _ui.value.copy(addFlow = AddMemberFlow(active = true, step = 0))
    }

    fun setMemberDetails(name: String, relation: String, isSelf: Boolean) {
        val current = _ui.value.addFlow
        if (isSelf) {
            _ui.value = _ui.value.copy(addFlow = current.copy(name = name, relation = relation, isSelf = true, step = 1))
        } else {
            // Family member: local roster entry only — no server upload (single-profile API).
            val member = FamilyMember(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Family Member" },
                relation = relation.ifBlank { "Family" },
                samplesCount = 0,
                lastVerified = "Local profile",
                isPrimary = false
            )
            saveFamilyMembers(_ui.value.members + member)
            _ui.value = _ui.value.copy(addFlow = current.copy(name = name, relation = relation, isSelf = false, step = 2))
        }
    }

    fun cancelAddMember() {
        recorder.discard()
        _ui.value = _ui.value.copy(addFlow = AddMemberFlow(active = false), message = null)
    }

    fun startRecording() {
        val why = recorder.start(minSeconds = _ui.value.status.minSeconds)
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
                            val newMember = FamilyMember(
                                id = "self",
                                name = currentFlow.name.ifBlank { "Me (Account Owner)" },
                                relation = "Self",
                                samplesCount = 3,
                                lastVerified = "Enrolled (3 samples)",
                                isPrimary = true
                            )
                            val updated = _ui.value.members.filterNot { it.isPrimary } + newMember
                            saveFamilyMembers(updated)
                            _ui.value = _ui.value.copy(
                                addFlow = currentFlow.copy(step = 2),
                                message = null
                            )
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

    fun removeMember(id: String) {
        val updated = _ui.value.members.filterNot { it.id == id }
        saveFamilyMembers(updated)
        if (id == "self") {
            deleteAll()
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
                    saveFamilyMembers(emptyList())
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
