package com.codewithkael.simplecall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.AppConfig
import com.codewithkael.simplecall.ai.TextEngines
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
    // DEBUG-only: set when we reached OTP entry via the offline fallback (server
    // unreachable). It exists ONLY to let verifyOtp() know to accept the mock code
    // locally instead of calling the dead server. Never set in release builds.
    val debugOffline: Boolean = false,
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
        // Start unconditionally: SMS hydration + on-device scanning never
        // required sign-in. The chat socket is opened inside start() only
        // when there is a real server session.
        repo.start()
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
                    // DEBUG builds only: the server is unreachable, but a developer
                    // must never be dead-ended on the phone-entry screen (Phase 4.1).
                    // Advance to OTP entry with a mock code so on-device features can
                    // be exercised offline. This whole branch is compiled out of
                    // release builds (BuildConfig.DEBUG is a false constant there), so
                    // a shipped app ALWAYS requires a real server-issued OTP.
                    if (AppConfig.DEBUG) {
                        _login.value = LoginUiState(
                            phase = LoginPhase.ENTER_OTP, phone = clean,
                            devOtp = DEBUG_MOCK_OTP, debugOffline = true, loading = false,
                            error = "Server unreachable — DEBUG build only: enter " +
                                "$DEBUG_MOCK_OTP to continue offline (no real sign-in)."
                        )
                    } else {
                        // Accurate, non-conflated message (Bug 1): only PHONE
                        // VERIFICATION needs the server. Local SMS scam scanning
                        // below works without it — don't imply the whole feature
                        // is server-blocked.
                        _login.value = _login.value.copy(
                            loading = false,
                            error = "Phone verification needs the CallShield server — check the IP on the Calls tab. " +
                                "(SMS scanning continues to work on-device without sign-in.)"
                        )
                    }
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
        // DEBUG builds only: when we reached OTP entry because the server was
        // unreachable (debugOffline), verify the mock code locally so the app is
        // usable offline (Phase 4.1). Gated on BuildConfig.DEBUG AND debugOffline
        // AND the exact mock code. Release builds compile this out and can only
        // authenticate against the real server below. The minted session is a
        // clearly-labeled local debug identity — the chat socket will still fail
        // to connect while the server is down, and the UI reflects that honestly
        // (socketConnected == false); nothing here fakes a server verification.
        if (AppConfig.DEBUG && _login.value.debugOffline) {
            if (code == DEBUG_MOCK_OTP) {
                session.save(
                    phone = phone,
                    token = "debug-offline-${System.currentTimeMillis()}",
                    displayName = displayName.trim().ifBlank { "Debug user" }
                )
                _login.value = _login.value.copy(phase = LoginPhase.DONE, loading = false, error = null)
                _loggedIn.value = true
                repo.onLoggedIn()
            } else {
                _login.value = _login.value.copy(
                    error = "DEBUG offline mode: enter $DEBUG_MOCK_OTP to continue."
                )
            }
            return
        }
        _login.value = _login.value.copy(loading = true, error = null)
        viewModelScope.launch {
            rest.verifyOtp(phone, code, displayName.trim().ifBlank { null })
                .onSuccess { r ->
                    session.save(phone = phone, token = r.token, displayName = r.displayName)
                    _login.value = _login.value.copy(phase = LoginPhase.DONE, loading = false)
                    _loggedIn.value = true
                    repo.onLoggedIn()
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

    // ---------------- local (serverless) scam scoring ----------------

    /**
     * OFFLINE scam verdict for an arbitrary message text (Bug 1 fix): the same
     * on-device heuristics the Shield uses, zero network. Used to render
     * verdict chips on SMS rows when the server path is unavailable or the
     * user isn't signed in — provenance is always labeled "on-device", never
     * presented as a server verdict. Honest absence: no text -> null.
     */
    fun localScamVerdict(text: String): TextEngines.ScamResult? =
        if (text.isBlank()) null else TextEngines.scamHeuristic(text)

    // ---------------- identity verification (responder side) ----------------

    /**
     * Answer "is that really you on this call?". A `false` here is what actually
     * stops the scam: the server escalates the other person's call to critical and
     * tells them to hang up.
     */
    fun answerVerifyPrompt(answer: Boolean) = repo.answerVerifyPrompt(answer)

    fun dismissVerifyPrompt() = repo.dismissVerifyPrompt()

    companion object {
        /**
         * Fixed OTP accepted ONLY in the debug-build offline fallback. Never a
         * bypass in release: every call site is guarded by BuildConfig.DEBUG.
         */
        private const val DEBUG_MOCK_OTP = "123456"
    }
}
