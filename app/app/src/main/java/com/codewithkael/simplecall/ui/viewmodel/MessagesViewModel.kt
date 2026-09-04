package com.codewithkael.simplecall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.data.MessagesRepository
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.AntaiSession
import com.codewithkael.simplecall.remote.antai.Conversation
import com.codewithkael.simplecall.remote.antai.NotificationEvent
import com.codewithkael.simplecall.remote.antai.VerifyPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginPhase { ENTER_PHONE, ENTER_OTP, DONE }

data class LoginUiState(
    val phase: LoginPhase = LoginPhase.ENTER_PHONE,
    val phone: String = "",
    val devOtp: String? = null,     // dev-mode convenience (server returns it)
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the messaging + notification-guard feature. Fully independent of
 * MainViewModel (which owns calling). Handles phone-OTP login, exposes the
 * repository's conversation/notification flows, and relays send/refresh actions.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repo: MessagesRepository,
    private val session: AntaiSession,
    private val rest: AntaiRestClient
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = repo.conversations
    val notifications: StateFlow<List<NotificationEvent>> = repo.notifications
    val socketConnected: StateFlow<Boolean> = repo.socketConnected

    /**
     * Set when someone is impersonating this user on a call with one of their
     * contacts and the server is waiting for a yes/no. It rides the chat socket
     * (this user is not on that call), which is why it lives here and not in
     * MainViewModel.
     */
    val verifyPrompt: StateFlow<VerifyPrompt?> = repo.verifyPrompt

    private val _loggedIn = MutableStateFlow(session.isLoggedIn)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    val myDisplayName: String get() = session.displayName.ifBlank { session.phone }
    val myPhone: String get() = session.phone

    init {
        if (session.isLoggedIn) repo.start()
    }

    // ---------------- login ----------------

    fun requestOtp(phone: String) {
        val clean = phone.trim()
        if (clean.isBlank()) {
            _login.value = _login.value.copy(error = "Enter your phone number")
            return
        }
        _login.value = _login.value.copy(loading = true, error = null, phone = clean)
        viewModelScope.launch {
            rest.requestOtp(clean)
                .onSuccess { r ->
                    // auto_verify dev mode: server will accept any 6-digit OTP;
                    // prefill the returned dev_otp to make testing one tap.
                    _login.value = LoginUiState(
                        phase = LoginPhase.ENTER_OTP, phone = clean,
                        devOtp = r.devOtp, loading = false
                    )
                }
                .onFailure {
                    _login.value = _login.value.copy(
                        loading = false, error = "Couldn't reach server. Check the IP on the Calls tab."
                    )
                }
        }
    }

    fun verifyOtp(otp: String, displayName: String) {
        val phone = _login.value.phone
        val code = otp.trim()
        if (code.isBlank()) {
            _login.value = _login.value.copy(error = "Enter the code")
            return
        }
        _login.value = _login.value.copy(loading = true, error = null)
        viewModelScope.launch {
            rest.verifyOtp(phone, code, displayName.trim().ifBlank { null })
                .onSuccess { r ->
                    session.save(phone = phone, token = r.token, displayName = r.displayName)
                    _login.value = _login.value.copy(phase = LoginPhase.DONE, loading = false)
                    _loggedIn.value = true
                    repo.start()
                }
                .onFailure {
                    _login.value = _login.value.copy(loading = false, error = "Verification failed. Try again.")
                }
        }
    }

    fun backToPhone() {
        _login.value = LoginUiState(phase = LoginPhase.ENTER_PHONE, phone = _login.value.phone)
    }

    fun logout() {
        session.clear()
        repo.shutdown()
        _loggedIn.value = false
        _login.value = LoginUiState()
    }

    // ---------------- messaging ----------------

    fun openThread(peerPhone: String) = repo.refreshHistory(peerPhone)

    fun conversationFor(peerPhone: String): Conversation? = repo.conversationFor(peerPhone)

    fun sendMessage(peerPhone: String, body: String, viaSms: Boolean) =
        repo.sendMessage(peerPhone, body, viaSms)

    /** Call after SMS read permission is (re)granted so old threads populate. */
    fun onSmsPermissionGranted() = repo.refreshSms()

    // ---------------- identity verification (responder side) ----------------

    /**
     * Answer "is that really you on this call?". A `false` here is what actually
     * stops the scam: the server escalates the other person's call to critical and
     * tells them to hang up.
     */
    fun answerVerifyPrompt(answer: Boolean) = repo.answerVerifyPrompt(answer)

    fun dismissVerifyPrompt() = repo.dismissVerifyPrompt()
}
