package com.codewithkael.simplecall.shield

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the on-device ShieldService is currently armed and listening.
 *
 * A plain process-global (set by ShieldService, read by any ViewModel/Composable)
 * so the UI can show the honest protection mode: with the shield armed the user
 * IS protected on-device even when the analysis server is unreachable — the old
 * UI showed "Offline" then, which was true only for the server-backed path.
 */
object ShieldArmedState {
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    fun setArmed(value: Boolean) {
        _armed.value = value
    }
}
