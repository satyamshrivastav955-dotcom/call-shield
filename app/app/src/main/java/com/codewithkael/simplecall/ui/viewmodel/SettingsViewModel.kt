package com.codewithkael.simplecall.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PREFS_SETTINGS = "antai_settings"
private const val KEY_SCENARIO     = "scenario"
private const val KEY_SENSITIVITY  = "sensitivity"
private const val KEY_VERIFY_POLICY = "verify_policy"
private const val KEY_RETENTION    = "retention_days"

/**
 * Backs [SettingsScreen].
 *
 * Settings are persisted in SharedPreferences so they survive restarts.
 * The same key-values are read by the session-start logic in the main pipeline
 * when a new call begins — so these settings have real effect, not just visual.
 *
 * Mapping:
 *  scenario       → antAI scenario name ("routine_call" | "high_value_txn" | "privileged_access")
 *  sensitivity    → "standard" maps to "routine_call" thresholds; "high_security" maps to "privileged_access"
 *  verifyPolicy   → "warn" | "require" | "freeze"  (read by the deepfake alert dialog)
 *  retentionDays  → integer; stored in prefs and readable by the admin cleanup call
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    private val _scenario = mutableStateOf(prefs.getString(KEY_SCENARIO, "high_value_txn") ?: "high_value_txn")
    val scenario: State<String> = _scenario

    private val _sensitivity = mutableStateOf(prefs.getString(KEY_SENSITIVITY, "standard") ?: "standard")
    val sensitivity: State<String> = _sensitivity

    private val _verifyPolicy = mutableStateOf(prefs.getString(KEY_VERIFY_POLICY, "warn") ?: "warn")
    val verifyPolicy: State<String> = _verifyPolicy

    private val _retentionDays = mutableIntStateOf(prefs.getInt(KEY_RETENTION, 30))
    val retentionDays: State<Int> = _retentionDays

    fun setScenario(value: String) {
        _scenario.value = value
        prefs.edit().putString(KEY_SCENARIO, value).apply()
    }

    fun setSensitivity(value: String) {
        _sensitivity.value = value
        // Mirror sensitivity into the scenario key so the pipeline picks it up
        val scenarioName = when (value) {
            "high_security" -> "privileged_access"
            else            -> "routine_call"
        }
        prefs.edit()
            .putString(KEY_SENSITIVITY, value)
            .putString(KEY_SCENARIO, scenarioName)
            .apply()
        _scenario.value = scenarioName
    }

    fun setVerifyPolicy(value: String) {
        _verifyPolicy.value = value
        prefs.edit().putString(KEY_VERIFY_POLICY, value).apply()
    }

    fun setRetentionDays(days: Int) {
        _retentionDays.intValue = days
        prefs.edit().putInt(KEY_RETENTION, days).apply()
    }

    companion object {
        /**
         * Read the active scenario name from SharedPreferences without a viewmodel.
         * Used by AiTapEngine / stream session start to wire the per-session scenario.
         */
        fun readScenario(context: Context): String =
            context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .getString(KEY_SCENARIO, "high_value_txn") ?: "high_value_txn"

        fun readVerifyPolicy(context: Context): String =
            context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .getString(KEY_VERIFY_POLICY, "warn") ?: "warn"
    }
}
