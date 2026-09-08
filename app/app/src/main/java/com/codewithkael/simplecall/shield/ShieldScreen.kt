package com.codewithkael.simplecall.shield

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * On-device Shield screen: toggle, live risk, file check, family, FIR.
 * Lives as a Guard-tab entry card + sub-screen; needs no server.
 */
@Composable
fun ShieldEntryCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🛡️ On-device Shield", style = MaterialTheme.typography.titleMedium)
            Text(
                "Offline clone + scam detection. Tap to arm, check a clip offline, or manage family voices.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldScreen(
    vm: ShieldViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onOpenVoiceprint: () -> Unit = {},
) {
    val context = LocalContext.current
    val armed by vm.armed.collectAsState()
    val live by vm.liveResult.collectAsState()
    val models by vm.modelStatus.collectAsState()
    val busy by vm.busy.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val enrollStatus by vm.enrollStatus.collectAsState()
    val enrollProgress by vm.enrollProgress.collectAsState()
    val previewAlert by vm.previewAlert.collectAsState()
    // Refresh the engine status + contact list every time the screen is entered
    // (Bug 2: status must be live, not a one-shot init snapshot).
    LaunchedEffect(Unit) { vm.refreshModelStatus() }
    // Start from the live scenario (shared with SettingsScreen), not a default.
    var scenario by remember { mutableStateOf(vm.scenario) }
    var showContacts by remember { mutableStateOf(false) }
    var showFir by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.analyzeFile(context, uri)
    }

    var pendingArm by remember { mutableStateOf<Boolean?>(null) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingArm == true) vm.setArmed(context, true)
        pendingArm = null
    }
    fun onToggle(on: Boolean) {
        if (on && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingArm = true
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        } else vm.setArmed(context, on)
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Offline Shield / ऑन-डिवाइस शील्ड",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text(
                "Zero server · 100% on-device AI protection for any call",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (armed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (armed) "🛡️ Shield is Active" else "Shield Protection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            if (armed) "Listening on mic — guards WhatsApp & normal calls"
                            else "Arm to detect voice clone & scam audio around you",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = armed, onCheckedChange = { onToggle(it) })
                }
                Text("AI Engines: $models", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Overlay permission (#5): alerts float over other apps only with this grant.
        val canOverlay = remember(armed) { Settings.canDrawOverlays(context) }
        if (!canOverlay) {
            Button(
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("⚠️ Enable Floating Alerts Over Other Apps", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Floating Alerts: Active ✓",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { vm.testOverlay() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Preview Alert", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { vm.dismissPreviewAlert() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Dismiss", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // In-app preview (Bug 4): a Compose card INSIDE this screen — the
            // real TYPE_APPLICATION_OVERLAY window only makes sense over OTHER
            // apps, which the armed live path exercises.
            previewAlert?.let { (score, message) ->
                Card(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "🛡️ antAI Live Alert · CRITICAL ($score/100)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        TextButton(onClick = { vm.dismissPreviewAlert() }) { Text("✕") }
                    }
                }
            }
        }

        Button(
            onClick = onOpenVoiceprint,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Family Voiceprints / आवाज़ पहचान (Multi-Profile) →")
        }

        var expanded by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text("Profile: $scenario")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("routine_call", "high_value_txn", "privileged_access", "bank", "telecom").forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        scenario = it; vm.setScenario(it); expanded = false
                    })
                }
            }
        }

        live?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Risk ${r.risk.toInt()}/100 · ${r.band}", style = MaterialTheme.typography.titleLarge)
                    r.spoofProb?.let { Text("Clone score: ${(it * 100).toInt()}%") }
                    // Real similarity with WHO it refers to (#4) — never a canned
                    // "verified". Null stays absent (no model / no print yet).
                    r.speakerSim?.let { sim ->
                        val pct = (sim * 100).toInt()
                        val who = r.speakerName ?: "family"
                        if (r.speakerClaimed) {
                            // Verdict mirrors the fusion decision (#7): the gray
                            // zone reads "uncertain", not a false "no match", so a
                            // sibling / cold / codec-degraded genuine voice isn't
                            // labelled an impostor.
                            val verdict = when {
                                "identity_mismatch" in r.hard -> "no match"
                                "identity_unconfirmed" in r.soft -> "uncertain"
                                else -> "match"
                            }
                            Text("Voice vs $who: $pct% ($verdict)")
                        } else {
                            // Unknown-caller mode: no identity was claimed, so this
                            // is an informational best match, not a pass/fail.
                            Text("Closest family voice: $who ($pct%)")
                        }
                    }
                    if (r.transcript.isNotBlank()) Text("“${r.transcript.take(200)}”")
                    Text(r.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(onClick = { pickFile.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Check a recording offline (on this phone)")
        }
        if (busy) CircularProgressIndicator()

        OutlinedButton(onClick = { showContacts = !showContacts }, modifier = Modifier.fillMaxWidth()) {
            Text("Family voices (${contacts.size})")
        }
        if (showContacts) {
            var name by remember { mutableStateOf("") }
            var phone by remember { mutableStateOf("") }
            OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Mom)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    vm.addContact(name.trim(), phone.trim()); name = ""; phone = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save trusted contact") }
            contacts.forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${c.displayName} · ${if (c.hasVoiceprint) "voice ✓" else "no voice"}")
                        if (c.hasVoiceprint) {
                            Text(
                                "Tap the name to verify against ${c.displayName}'s voice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row {
                        // Per-contact verification target (#4): tapping the name
                        // focuses the Shield on this one person.
                        if (c.hasVoiceprint) {
                            TextButton(onClick = { vm.selectContact(c.phoneHash) }) {
                                Text("Verify", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = { vm.enrollVoiceprint(c.phoneHash) }) {
                            Text(if (c.hasVoiceprint) "Re-enroll" else "Enroll voice")
                        }
                        TextButton(onClick = { vm.removeContact(c.phoneHash) }) { Text("Remove") }
                    }
                }
            }
            // Clear the per-contact selection: back to unknown-caller mode
            // (best match across every enrolled voice).
            OutlinedButton(onClick = { vm.selectContact(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Verify against any family voice (unknown caller)")
            }
            // Live enrollment meter (P1): animate the RMS level + elapsed seconds
            // while the mic records, so enrollment no longer looks frozen behind a
            // single static status line.
            if (enrollProgress.recording) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { enrollProgress.level.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        (if (enrollProgress.enoughAudio) "Got enough ✓ · " else "🎙️ Keep talking… ") +
                            "%.1fs".format(enrollProgress.seconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            enrollStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Enroll records ~3s on this phone and stores only the on-device voiceprint (no raw audio).",
                style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(onClick = { showFir = !showFir }, modifier = Modifier.fillMaxWidth()) {
            Text("Cyber complaint (FIR draft)")
        }
        if (showFir) {
            val draft = remember(live) { vm.buildFirDraft() }
            Text(draft, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.shareFir(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share draft (1930 / cybercrime.gov.in)")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
