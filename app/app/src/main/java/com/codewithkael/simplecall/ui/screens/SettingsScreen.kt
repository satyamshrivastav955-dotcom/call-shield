package com.codewithkael.simplecall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.ui.viewmodel.MainViewModel
import com.codewithkael.simplecall.ui.viewmodel.SettingsViewModel

/**
 * Settings screen — maps to real pipeline behavior via [SettingsViewModel].
 *
 * Scenario maps to antAI's per-scenario risk thresholds (low/verify/critical).
 * Sensitivity maps to the scenario choice (standard → routine_call,
 * high-security → privileged_access). Verification policy maps to the in-app
 * deepfake-alert action gate (warn / require / freeze).
 *
 * Nothing here is cosmetic: every setting changes what the detection pipeline does.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val scenario by viewModel.scenario
    val sensitivity by viewModel.sensitivity
    val verifyPolicy by viewModel.verifyPolicy
    val retentionDays by viewModel.retentionDays
    val host by mainViewModel.serverHost.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Protection behaviour and privacy controls", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Server connection section ─────────────────────────────────────
            SectionHeader("Signaling & AI Server")
            com.codewithkael.simplecall.ui.components.ServerConfig(
                initialHost = host,
                onConnect = { newHost ->
                    mainViewModel.updateServerHost(newHost)
                    mainViewModel.connectSocket()
                }
            )

            // ── Scenario section ──────────────────────────────────────────────
            SectionHeader("Session Scenario")
            SettingsCard {
                SettingLabel(
                    title = "Detection scenario",
                    subtitle = "Sets per-scenario risk thresholds. High-value and privileged sessions " +
                               "use lower thresholds — the same signals trigger earlier action."
                )
                Spacer(Modifier.height(12.dp))
                RadioGroup(
                    options = listOf(
                        "routine_call" to "Routine Call",
                        "high_value_txn" to "High-Value Transaction",
                        "privileged_access" to "Privileged Access"
                    ),
                    selected = scenario,
                    onSelect = { viewModel.setScenario(it) }
                )
            }

            // ── Risk sensitivity section ──────────────────────────────────────
            SectionHeader("Risk Sensitivity")
            SettingsCard {
                SettingLabel(
                    title = "Detection sensitivity",
                    subtitle = "Standard uses default thresholds. High Security uses the Privileged Access " +
                               "profile (verify at 40, alert at 60)."
                )
                Spacer(Modifier.height(12.dp))
                RadioGroup(
                    options = listOf(
                        "standard"      to "Standard (recommended for most calls)",
                        "high_security" to "High Security (earlier alerts, more interruptions)"
                    ),
                    selected = sensitivity,
                    onSelect = { viewModel.setSensitivity(it) }
                )
            }

            // ── Verification policy section ───────────────────────────────────
            SectionHeader("Verification Policy")
            SettingsCard {
                SettingLabel(
                    title = "When risk crosses verify threshold",
                    subtitle = "Controls what happens when the pipeline flags a call as elevated risk."
                )
                Spacer(Modifier.height(12.dp))
                RadioGroup(
                    options = listOf(
                        "warn"    to "Warn — show in-app alert (default)",
                        "require" to "Require — pause until you dismiss the alert",
                        "freeze"  to "Freeze — block the call until verified"
                    ),
                    selected = verifyPolicy,
                    onSelect = { viewModel.setVerifyPolicy(it) }
                )
            }

            // ── Privacy section ───────────────────────────────────────────────
            SectionHeader("Privacy & Retention")
            SettingsCard {
                SettingLabel(
                    title = "Verdict retention period",
                    subtitle = "Verdicts and reports older than this are purged from the server. " +
                               "Raw audio is never stored — only derived scores and explanations."
                )
                Spacer(Modifier.height(12.dp))
                RadioGroup(
                    options = listOf(
                        7   to "7 days",
                        30  to "30 days",
                        90  to "90 days"
                    ).map { (d, l) -> d.toString() to l },
                    selected = retentionDays.toString(),
                    onSelect = { viewModel.setRetentionDays(it.toIntOrNull() ?: 30) }
                )
            }

            // ── Info footer ───────────────────────────────────────────────────
            Text(
                text = "CallShield never stores raw audio. Voice deepfake detection uses short rolling " +
                       "windows of speech derived values only. All ML inference runs on the server " +
                       "you configured, not on a third-party cloud.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingLabel(title: String, subtitle: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
}

@Composable
private fun RadioGroup(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    options.forEach { (value, label) ->
        RadioRow(
            label = label,
            selected = selected == value,
            onClick = { onSelect(value) }
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
