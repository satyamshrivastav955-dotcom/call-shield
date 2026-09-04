package com.codewithkael.simplecall.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codewithkael.simplecall.remote.antai.AntaiRestClient
import com.codewithkael.simplecall.remote.antai.IncidentItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [IncidentHistoryScreen].
 *
 * Fetches from [AntaiRestClient.listVerdicts] which calls GET /api/verdicts.
 * The user must be logged-in for the server to return their own verdicts; the
 * REST client passes the stored bearer token automatically. An unauthenticated
 * call silently returns an empty list (the server returns 401 → the client's
 * recoverCatching transforms it to an empty list).
 */
@HiltViewModel
class IncidentHistoryViewModel @Inject constructor(
    private val antaiRest: AntaiRestClient
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
            antaiRest.listVerdicts()
                .onSuccess { list ->
                    // Show noteworthy incidents first (verify/critical), then safe ones by time
                    _incidents.value = list.sortedWith(
                        compareByDescending<IncidentItem> { it.band != "passive" }
                            .thenByDescending { it.createdAt }
                    )
                }
                .onFailure { e ->
                    _error.value = "Could not load incident history: ${e.message?.take(80)}"
                }
            _isLoading.value = false
        }
    }
}
