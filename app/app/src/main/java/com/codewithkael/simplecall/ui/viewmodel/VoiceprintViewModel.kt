package com.codewithkael.simplecall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.VoiceprintStatus
import com.codewithkael.simplecall.voice.VoiceprintRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Voice registration: record a short sample of the user's own voice and store it
 * on the server as the reference the AI-voice cross-check compares against.
 *
 * Without an enrolled print the cross-check can only answer "no_voiceprint", so
 * this screen is what makes the "is this actually them?" question answerable at
 * all. Kept entirely separate from MainViewModel: nothing here goes near the call
 * or WebRTC path.
 */
@HiltViewModel
class VoiceprintViewModel @Inject constructor(
    private val rest: AntaiRestClient,
    private val recorder: VoiceprintRecorder
) : ViewModel() {

    data class UiState(
        val loadingStatus: Boolean = true,
        val status: VoiceprintStatus = VoiceprintStatus(),
        val uploading: Boolean = false,
        /** Last outcome to show the user — success or the server's rejection hint. */
        val message: String? = null,
        val messageIsError: Boolean = false,
        val serverUnreachable: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Live mic level / elapsed seconds straight from the recorder. */
    val progress: StateFlow<VoiceprintRecorder.Progress> = recorder.progress

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(loadingStatus = true)
        viewModelScope.launch {
            rest.voiceprintStatus()
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        loadingStatus = false, status = it, serverUnreachable = false
                    )
                }
                .onFailure {
                    // Distinguished from "not enrolled": we simply don't know yet, and
                    // telling the user they have no voiceprint when the server is just
                    // unreachable would send them re-recording for nothing.
                    _ui.value = _ui.value.copy(loadingStatus = false, serverUnreachable = true)
                }
        }
    }

    fun startRecording() {
        val why = recorder.start(minSeconds = _ui.value.status.minSeconds)
        _ui.value = if (why == null) {
            _ui.value.copy(message = null, messageIsError = false)
        } else {
            _ui.value.copy(message = why, messageIsError = true)
        }
    }

    /** Stop, then upload. A too-short sample is refused here to save a round trip. */
    fun stopAndUpload() {
        val minSeconds = _ui.value.status.minSeconds
        val wav = recorder.stop()
        val secs = progress.value.seconds
        if (wav == null) {
            _ui.value = _ui.value.copy(
                message = "Nothing was recorded. Check the microphone and try again.",
                messageIsError = true
            )
            return
        }
        if (secs < minSeconds) {
            _ui.value = _ui.value.copy(
                message = "Too short — speak for at least ${minSeconds.toInt()} seconds.",
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
                        message = if (r.enrolled)
                            "Voice registered — sample ${r.status.count} saved."
                        else r.hint.ifBlank { "Couldn't register that recording." },
                        messageIsError = !r.enrolled,
                        serverUnreachable = false
                    )
                    if (r.enrolled) refresh()
                }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        uploading = false,
                        message = "Couldn't reach the antAI server to save your voice.",
                        messageIsError = true,
                        serverUnreachable = true
                    )
                }
        }
    }

    fun cancelRecording() {
        recorder.discard()
        _ui.value = _ui.value.copy(message = null, messageIsError = false)
    }

    fun deleteAll() {
        viewModelScope.launch {
            rest.deleteVoiceprints()
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        status = it, message = "Stored voice samples removed.",
                        messageIsError = false
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        message = "Couldn't remove the samples right now.", messageIsError = true
                    )
                }
        }
    }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(message = null, messageIsError = false)
    }

    fun hasMicPermission() = recorder.hasMicPermission()

    override fun onCleared() {
        // Never leave the mic open behind a closed screen — it would block the next
        // call from capturing audio.
        recorder.discard()
        super.onCleared()
    }
}
