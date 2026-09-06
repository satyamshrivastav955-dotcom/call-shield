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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
                "Offline clone + scam detection. Tap to arm, check a recording, or manage family voices.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldScreen(vm: ShieldViewModel = hiltViewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val armed by vm.armed.collectAsState()
    val live by vm.liveResult.collectAsState()
    val models by vm.modelStatus.collectAsState()
    val busy by vm.busy.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val enrollStatus by vm.enrollStatus.collectAsState()
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Guard") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shield protection", style = MaterialTheme.typography.titleLarge)
            Switch(checked = armed, onCheckedChange = { onToggle(it) })
        }
        Text(
            if (armed) "Listening on mic — works in other apps, no internet."
            else "Off. Arm to detect clone/scam audio around you.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Models: $models", style = MaterialTheme.typography.bodySmall)

        // Overlay permission (#5): alerts float over other apps only with this grant.
        val canOverlay = remember(armed) { Settings.canDrawOverlays(context) }
        if (!canOverlay) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow floating alerts over other apps") }
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
                    r.speakerSim?.let { Text("Family match: ${(it * 100).toInt()}%") }
                    if (r.transcript.isNotBlank()) Text("“${r.transcript.take(200)}”")
                    Text(r.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(onClick = { pickFile.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Check a voice recording / call audio")
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
                    Text("${c.displayName} · ${if (c.hasVoiceprint) "voice ✓" else "no voice"}")
                    Row {
                        TextButton(onClick = { vm.enrollVoiceprint(c.phoneHash) }) {
                            Text(if (c.hasVoiceprint) "Re-enroll" else "Enroll voice")
                        }
                        TextButton(onClick = { vm.removeContact(c.phoneHash) }) { Text("Remove") }
                    }
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
