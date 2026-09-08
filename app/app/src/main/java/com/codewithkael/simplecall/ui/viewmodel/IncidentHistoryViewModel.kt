package com.codewithkael.simplecall.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.ai.Explainer
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.IncidentItem
import com.codewithkael.simplecall.shield.TrustedStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [IncidentHistoryScreen].
 *
 * MERGED SOURCES (Bug 3 fix): the list combines
 *  - server verdicts  (GET /api/verdicts, when signed in / reachable) — kind is
 *    the server session type, and
 *  - LOCAL on-device Shield verdicts (TrustedStore, always available, tagged
 *    kind "on-device") — these are what the live overlay fires; before this fix
 *    they were transient (overlay showed CRITICAL, history stayed empty).
 *
 * The server call failing (offline / 401) is NOT an error anymore: the local
 * log still renders. Only a failure of BOTH is an error state.
 */
@HiltViewModel
class IncidentHistoryViewModel @Inject constructor(
    private val antaiRest: AntaiRestClient,
    private val store: TrustedStore
) : ViewModel() {

    private val _incidents = mutableStateOf<List<IncidentItem>>(emptyList())
    val incidents: State<List<IncidentItem>> = _incidents

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    fun load() {
        if (_isLoading.value) return
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            // Local on-device log: always loaded, never blocks on the network.
            val local = runCatching { store.recentVerdictsDetailed() }.getOrDefault(emptyList())
            val serverResult = antaiRest.listVerdicts()

            val merged = buildList {
                serverResult.getOrNull()?.let { addAll(it) }
                addAll(local.map { v ->
                    IncidentItem(
                        id = -v.ts, // unique, negative so it can't collide with server ids
                        kind = "on-device",
                        riskScore = v.risk.toDouble(),
                        band = v.band,
                        // #10: humanize the raw fusion token so the tab never
                        // shows "voice_deepfake" / "identity_mismatch" to the user.
                        verdict = (v.hard.firstOrNull() ?: v.soft.firstOrNull())
                            ?.let { Explainer.humanizeSignal(it) } ?: "risk signal",
                        why = v.summary,
                        action = "",
                        // Phase 3.2: carry the on-device forensic telemetry through to
                        // the inspection dialog + complaint prefill (was dropped to null
                        // here before, so the dialog had nothing to show). null stays
                        // null — an unmeasured signal is never fabricated.
                        scamType = v.scamType,
                        createdAt = v.ts,
                        transcript = v.transcript.ifBlank { null },
                        spoofProb = v.spoofProb?.toDouble(),
                        contact = v.contact,
                    )
                })
            }

            if (merged.isEmpty()) {
                // Both sources empty. Distinguish "nothing logged yet" from
                // "server unreachable AND local log broken" — only the second
                // is an error.
                if (local.isEmpty() && serverResult.isFailure) {
                    _error.value = "Could not load incidents: ${serverResult.exceptionOrNull()?.message?.take(80)}"
                }
            } else {
                // Show noteworthy incidents first (verify/critical), then safe ones by time
                _incidents.value = merged.sortedWith(
                    compareByDescending<IncidentItem> { it.band != "passive" }
                        .thenByDescending { it.createdAt }
                )
            }
            _isLoading.value = false
        }
    }
}
