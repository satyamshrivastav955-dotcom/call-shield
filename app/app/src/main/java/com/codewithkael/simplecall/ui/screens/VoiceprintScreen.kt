package com.codewithkael.simplecall.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.theme.*
import com.codewithkael.simplecall.ui.viewmodel.ENROLL_PHRASES
import com.codewithkael.simplecall.ui.viewmodel.FamilyMember
import com.codewithkael.simplecall.ui.viewmodel.VoiceprintViewModel

/**
 * Task A5: Multi-profile family voiceprint enrollment and protection.
 *
 * Protects family members against AI-voice cloning and impersonation scams.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceprintScreen(
    onBack: () -> Unit,
    vm: VoiceprintViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()
    val progress by vm.progress.collectAsState()

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startRecording() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Voiceprints / आवाज़ पहचान") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // Add member in progress (modal/stepper view)
                ui.addFlow.active -> {
                    AddMemberFlowScreen(
                        flow = ui.addFlow,
                        progress = progress,
                        uploading = ui.uploading,
                        minSeconds = ui.status.minSeconds.toInt(),
                        hasMicPermission = vm.hasMicPermission(),
                        onCancel = vm::cancelAddMember,
                        onDetailsSubmit = { name, rel, phone, isSelf -> vm.setMemberDetails(name, rel, phone, isSelf) },
                        onStartRecording = {
                            if (vm.hasMicPermission()) vm.startRecording()
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onStopRecording = vm::stopAndUploadCurrentPhrase,
                        onSkipVoice = vm::skipFamilyVoice,
                        onDone = vm::cancelAddMember
                    )
                }

                // Main family members overview list
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        item {
                            WhyCard()
                        }

                        item {
                            StatusOverviewCard(
                                engineReady = ui.status.engineReady,
                                unreachable = ui.serverUnreachable,
                                memberCount = ui.members.size,
                                onRetry = vm::refresh
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enrolled Members (${ui.members.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Button(
                                    onClick = vm::startAddMember,
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Member", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (ui.members.isEmpty()) {
                            item {
                                EmptyFamilyMembersCard(onAdd = vm::startAddMember)
                            }
                        } else {
                            items(ui.members, key = { it.id }) { member ->
                                FamilyMemberCard(
                                    member = member,
                                    onEnroll = { vm.startReenroll(member) },
                                    onDelete = { vm.removeMember(member.id) }
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Global transient toast/error banner
            ui.message?.let { msg ->
                Surface(
                    color = if (ui.messageIsError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                if (ui.messageIsError) R.drawable.ic_warning else R.drawable.ic_verified
                            ),
                            contentDescription = null,
                            tint = if (ui.messageIsError) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ui.messageIsError) MaterialTheme.colorScheme.onErrorContainer
                                   else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = vm::dismissMessage) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhyCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                painterResource(R.drawable.ic_shield_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    "Why enroll your voiceprint?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "When a scammer uses an AI voice clone of you in a call, antAI cross-checks the audio " +
                    "against your enrolled voiceprint and flags the identity mismatch. Add family members " +
                    "with their own on-device voiceprints — the offline Shield uses them to catch voice " +
                    "mismatches in live calls. आवाज़ की तुलना इसी फ़ोन पर होती है।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusOverviewCard(
    engineReady: Boolean,
    unreachable: Boolean,
    memberCount: Int,
    onRetry: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (engineReady) R.drawable.ic_verified else R.drawable.ic_warning),
                contentDescription = null,
                tint = if (engineReady) MaterialTheme.colorScheme.primary else RiskCaution,
                modifier = Modifier.size(24.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = when {
                        unreachable -> "Server unreachable"
                        engineReady -> "Voice Matching Engine Ready"
                        else -> "Engine initializing…"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        unreachable -> "Check server host IP in Settings."
                        engineReady -> "$memberCount profiles protected on this device."
                        else -> "Speaker verification model is loading on the server."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (unreachable || !engineReady) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun FamilyMemberCard(
    member: FamilyMember,
    onEnroll: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (member.isPrimary) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Primary",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (member.isPrimary) "${member.relation} · ${member.samplesCount} phrase samples (server)"
                           else if (member.hasVoiceprint) "${member.relation} · voice ✓ (on-device)"
                           else "${member.relation} · no voiceprint yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Status: ${member.lastVerified}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (member.isPrimary || member.hasVoiceprint) RiskSafe else RiskCaution
                )
            }

            Row {
                if (!member.isPrimary) {
                    IconButton(onClick = onEnroll) {
                        Icon(
                            painterResource(R.drawable.ic_mic_on),
                            contentDescription = if (member.hasVoiceprint) "Re-enroll voice" else "Enroll voice",
                            tint = if (member.hasVoiceprint) MaterialTheme.colorScheme.primary else RiskCaution
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove member",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFamilyMembersCard(onAdd: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No family members enrolled yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Enroll your own voice on the antAI server so analyzed calls can flag clones of you. " +
                "Add parents, children or partners with an on-device voiceprint — it works offline and " +
                "never leaves this phone. पहले अपनी आवाज़ दर्ज करें।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd, shape = MaterialTheme.shapes.small) {
                Text("Enroll Your Voice")
            }
        }
    }
}

/**
 * Multi-step Add Family Member flow (Task A5: details -> 3 phrases recording -> confirmation).
 */
@Composable
private fun AddMemberFlowScreen(
    flow: com.codewithkael.simplecall.ui.viewmodel.AddMemberFlow,
    progress: com.codewithkael.simplecall.voice.VoiceprintRecorder.Progress,
    uploading: Boolean,
    minSeconds: Int,
    hasMicPermission: Boolean,
    onCancel: () -> Unit,
    onDetailsSubmit: (String, String, String, Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSkipVoice: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (flow.step) {
            // Step 0: Input Name & Relationship
            0 -> {
                var name by remember { mutableStateOf("") }
                var relation by remember { mutableStateOf("") }
                var phone by remember { mutableStateOf("") }
                var isSelf by remember { mutableStateOf(flow.isSelf) }

                Text(
                    text = "Add Member / नया सदस्य जोड़ें",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Who are you registering?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = isSelf,
                        onClick = { isSelf = true },
                        label = { Text("Myself (voiceprint)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isSelf,
                        onClick = { isSelf = false },
                        label = { Text("Family member (on-device)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isSelf)
                        "Record 3 phrases of your own voice — the server cross-checks callers against it."
                    else
                        "Their voiceprint is computed and stored on this phone (offline) — the Shield uses " +
                        "it to flag voice mismatches in live calls. Only a protected hash of the number is stored. " +
                        "आवाज़ इसी फ़ोन पर सुरक्षित रहती है।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isSelf) "Your Name" else "Member Name (e.g. Mom, Rahul)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text(if (isSelf) "Label (e.g. Me, Owner)" else "Relationship (e.g. Mother, Son, Spouse)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isSelf) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone number (stored as a protected hash only)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) { Text("Cancel") }

                    Button(
                        onClick = { onDetailsSubmit(name.trim(), relation.trim(), phone.trim(), isSelf) },
                        enabled = name.isNotBlank() && (isSelf || phone.isNotBlank()),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) { Text(if (isSelf) "Continue to Voice" else "Add & Record Voice") }
                }
            }

            // Step 1: Record — self: 3 phrases (server); family: one ~3s free-talk
            // sample embedded on-device.
            1 -> {
                val currentPhrase = ENROLL_PHRASES.getOrElse(flow.phraseIndex) { "" }

                Text(
                    text = "Recording for ${flow.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (flow.isSelf) "Phrase ${flow.phraseIndex + 1} of 3" else "On-device voice sample",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = if (flow.isSelf) "Please read this sentence aloud:" else "Talk naturally for a few seconds:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (flow.isSelf) "\"$currentPhrase\""
                                   else "\"Hi, this is my voice — I'm registering it so antAI can protect our family from voice clones.\"",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                val secs = progress.seconds
                val needSecs = if (flow.isSelf) minSeconds else 3
                Text(
                    text = when {
                        progress.recording && progress.enoughAudio ->
                            "%.0f s — enough audio, tap to save".format(secs)
                        progress.recording -> "Recording… %.0f s (need $needSecs s)".format(secs)
                        uploading -> "Processing sample…"
                        else -> "Tap the mic and read the phrase"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                // Level meter
                val shown = if (progress.recording) progress.level.coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                ) {
                    if (shown > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(shown)
                                .background(
                                    if (shown > 0.06f) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Mic button
                val bg = when {
                    uploading -> MaterialTheme.colorScheme.surfaceVariant
                    progress.recording -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = bg,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(76.dp)
                        .clickable(enabled = !uploading) {
                            if (progress.recording) onStopRecording()
                            else onStartRecording()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (progress.recording) R.drawable.ic_mic_off else R.drawable.ic_mic_on
                            ),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (uploading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                if (!flow.isSelf && !progress.recording) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onSkipVoice) {
                        Text("Skip for now — add without a voiceprint")
                    }
                }
            }

            // Step 2: Confirmation
            2 -> {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(RiskSafeBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = RiskSafe,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (flow.isSelf) "Voiceprint Enrolled!" else "Family Member Added!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (flow.isSelf)
                        "Your voice is registered on the antAI server with 3 samples. During analyzed calls, " +
                        "antAI cross-checks the caller's voice against it and flags a mismatch."
                    else
                        "${flow.name}'s voiceprint is stored on this phone and shared with the on-device Shield — " +
                        "it flags voice mismatches in live calls, fully offline. Only a protected hash of " +
                        "their number was saved. आवाज़ इसी फ़ोन पर सुरक्षित है।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text("Done / पूर्ण")
                }
            }
        }
    }
}
