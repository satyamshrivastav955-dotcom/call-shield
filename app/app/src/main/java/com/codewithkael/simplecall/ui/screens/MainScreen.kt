package com.codewithkael.simplecall.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.IncidentItem
import com.codewithkael.simplecall.ui.components.CallComponent
import com.codewithkael.simplecall.ui.components.DeepfakeAlertDialog
import com.codewithkael.simplecall.ui.components.IncomingCallSnackBar
import com.codewithkael.simplecall.ui.components.ProtectionMode
import com.codewithkael.simplecall.ui.components.RiskResultCard
import com.codewithkael.simplecall.ui.components.RiskSignalsData
import com.codewithkael.simplecall.ui.components.RiskStatusChip
import com.codewithkael.simplecall.ui.components.WhoToCall
import com.codewithkael.simplecall.ui.components.YourIdCard
import com.codewithkael.simplecall.ui.theme.*
import com.codewithkael.simplecall.ui.viewmodel.MainViewModel
import com.codewithkael.simplecall.ui.viewmodel.SettingsViewModel
import com.codewithkael.simplecall.utils.ConnectionState
import com.codewithkael.simplecall.utils.SimpleCallApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen() {
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(
                context, "Camera and Microphone permission is required", Toast.LENGTH_SHORT
            ).show()
        }
    }

    val connectionStatus = viewModel.connectionState.collectAsState()
    val protectionMode by viewModel.protectionMode.collectAsState()
    val lastIncident by viewModel.lastIncident.collectAsState()
    val fileAnalysis by viewModel.fileAnalysisState.collectAsState()

    // File picker launcher for recording check (Task A2)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.analyzeAudioUri(context, uri)
        }
    }

    // Share target handler: incoming audio forwarded via ACTION_SEND
    LaunchedEffect(Unit) {
        SimpleCallApplication.sharedIncomingAudio.collect { uri ->
            if (uri != null) {
                viewModel.analyzeAudioUri(context, uri)
                SimpleCallApplication.sharedIncomingAudio.value = null
            }
        }
    }

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.CAMERA
            )
        )
        viewModel.loadLastIncident()
        viewModel.refreshProtectionMode()
    }

    LaunchedEffect(Unit) {
        viewModel.eventState.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val isOnCall = connectionStatus.value is ConnectionState.CallingTarget ||
            connectionStatus.value is ConnectionState.OnCall

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = if (isOnCall) 0.dp else 12.dp)
    ) {
        if (!isOnCall) {
            // ── IDLE PRODUCT HOME (Tasks A1, A2, A4) ─────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Protection Status Hero + Mode & Connection Chips
                ProtectionHero(
                    mode = protectionMode,
                    connectionState = connectionStatus.value
                )

                // 2. Who to Call (Primary CTA dialer)
                WhoToCall(onCallClick = { targetId, isVoice ->
                    if (targetId.isEmpty()) {
                        Toast.makeText(context, "Enter User ID to call", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.findUser(targetId, isVoice)
                    }
                })

                // 3. Your ID Card
                YourIdCard(userId = SimpleCallApplication.USER_ID) {
                    Toast.makeText(context, "User ID Copied to clipboard", Toast.LENGTH_SHORT).show()
                }

                // 4. Check a recording card (Task A2)
                CheckRecordingCard(
                    fileAnalysis = fileAnalysis,
                    onPickFile = { audioPickerLauncher.launch("audio/*") },
                    onClearResult = { viewModel.clearFileAnalysis() },
                    onShareResult = { norm -> shareFileResult(context, norm) }
                )

                // 5. Recent Protection Activity / Empty state (Task A1)
                RecentIncidentSection(incident = lastIncident)

                Spacer(Modifier.height(16.dp))
            }
        }

        // ── IN-CALL VIEW (CallComponent) ─────────────────────────────────────
        if (isOnCall) {
            val aiInsight = viewModel.aiInsightState.collectAsState()
            val transcript = viewModel.transcriptState.collectAsState()
            val isVoice = viewModel.callKind.collectAsState().value == "voice"
            val callerVerify = viewModel.callerVerifyState.collectAsState()
            val peerId = when (val s = connectionStatus.value) {
                is ConnectionState.OnCall -> s.target
                is ConnectionState.CallingTarget -> s.target.orEmpty()
                else -> ""
            }
            CallComponent(
                onSurfaceRemoteReady = { remoteRenderer ->
                    viewModel.onSurfaceRemoteReady(remoteRenderer)
                },
                onSurfaceLocalReady = { localRenderer ->
                    viewModel.onSurfaceLocalReady(localRenderer)
                },
                onSwitchCamera = {
                    viewModel.switchCamera()
                },
                onEndCall = {
                    viewModel.endCall()
                },
                onToggleMic = { enabled ->
                    viewModel.toggleMic(enabled)
                },
                onToggleCamera = { enabled ->
                    viewModel.toggleCamera(enabled)
                },
                onToggleSpeaker = { isSpeaker ->
                    viewModel.toggleSpeaker(isSpeaker)
                },
                isVoiceCall = isVoice,
                aiInsight = aiInsight.value,
                transcript = transcript.value,
                myUsername = SimpleCallApplication.USER_ID,
                callerVerification = callerVerify.value,
                verifyDefaultPhone = peerId,
                onVerifyCaller = { claimedPhone ->
                    viewModel.requestCallerVerify(claimedPhone)
                },
                onDismissVerify = {
                    viewModel.dismissCallerVerify()
                }
            )
        }

        // Incoming call banner
        if (connectionStatus.value is ConnectionState.ReceivedCall) {
            val callerId = (connectionStatus.value as ConnectionState.ReceivedCall).sender
            IncomingCallSnackBar(
                callerId = callerId!!,
                onTimeout = {
                    viewModel.incomingCallDismissed()
                    Toast.makeText(context, "Call Timed out", Toast.LENGTH_SHORT).show()
                },
                onAccept = { target ->
                    viewModel.acceptIncomingCall(target)
                },
                onReject = { target ->
                    viewModel.rejectIncomingCall(target)
                }
            )
        }

        // Deepfake pause-and-alert modal
        val deepfakeAlert = viewModel.deepfakeAlertState.collectAsState()
        val voiceprint = viewModel.voiceprintState.collectAsState()
        deepfakeAlert.value?.let { alert ->
            DeepfakeAlertDialog(
                alert = alert,
                voiceprint = voiceprint.value,
                onCrossVerify = { viewModel.crossVerifyVoiceprint() },
                onResume = { viewModel.resumeAfterDeepfakeAlert() },
                onEndCall = { viewModel.endCallFromDeepfakeAlert() }
            )
        }
    }
}

/**
 * Hero component replacing dev-centric headers with a proud guardian product statement.
 */
@Composable
private fun ProtectionHero(
    mode: ProtectionMode,
    connectionState: ConnectionState
) {
    val context = LocalContext.current
    val activeScenario = remember { SettingsViewModel.readScenario(context) }
    val scenarioLabel = when (activeScenario) {
        "privileged_access" -> "Privileged Access"
        "routine_call"      -> "Routine Call"
        else                -> "High-Value Txn"
    }

    val (connLabel, connFg, connBg) = when (connectionState) {
        is ConnectionState.WaitingForCall -> Triple(
            stringResource(R.string.mode_connected),
            RiskSafe,
            RiskSafeBg
        )
        is ConnectionState.UserOffline -> Triple(
            stringResource(R.string.mode_offline),
            RiskCaution,
            RiskCautionBg
        )
        else -> Triple(
            stringResource(R.string.mode_local_only),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.hero_protecting_calls),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = stringResource(R.string.hero_protection_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // Badges row: Protection Mode Chip (A4) + Connection Chip + Scenario Chip
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Persistent Mode Chip (tap opens explanation bottom sheet)
                RiskStatusChip(mode = mode)

                // Connection state chip
                Surface(
                    color = connBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = connLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = connFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                // Scenario label
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = scenarioLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Task A2: Check a recording flow card.
 */
@Composable
private fun CheckRecordingCard(
    fileAnalysis: MainViewModel.FileAnalysisUiState,
    onPickFile: () -> Unit,
    onClearResult: () -> Unit,
    onShareResult: (com.codewithkael.simplecall.remote.antai.NormalizedResult) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ear),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.card_check_recording_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (fileAnalysis.result != null || fileAnalysis.error != null) {
                    IconButton(onClick = onClearResult, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.card_check_recording_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            when {
                fileAnalysis.isAnalyzing -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.recording_analyzing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                fileAnalysis.result != null -> {
                    val r = fileAnalysis.result
                    val signals = RiskSignalsData(
                        voiceDeepfake = r.voiceDeepfake,
                        scamProb = r.scamProb,
                        scamType = r.scamType,
                        urgency = r.urgency,
                        voiceprintSimilarity = r.voiceprintSimilarity,
                        identityMismatch = r.identityMismatch
                    )
                    RiskResultCard(
                        riskScore = r.risk,
                        band = r.band,
                        headline = fileAnalysis.fileName ?: stringResource(R.string.recording_success),
                        verdict = r.recommendation.ifBlank { "Analysis complete" },
                        recommendation = r.recommendation,
                        why = r.reasons.joinToString("\n"),
                        signals = signals,
                        reasons = r.reasons,
                        initiallyExpanded = true,
                        footerActionContent = {
                            OutlinedButton(
                                onClick = { onShareResult(r) },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_share_result), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    )
                }

                fileAnalysis.error != null -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fileAnalysis.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                else -> {
                    Button(
                        onClick = onPickFile,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_pick_audio), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Task A1: Recent incident or empty state card.
 */
@Composable
private fun RecentIncidentSection(incident: IncidentItem?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Recent Incident / हालिया चेतावनी",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        if (incident != null) {
            val formattedTime = formatRecentTime(incident.createdAt)
            val signals = RiskSignalsData(
                scamType = incident.scamType,
                scamProb = if (incident.scamType != null) (incident.riskScore / 100.0) else null
            )
            RiskResultCard(
                riskScore = incident.riskScore,
                band = incident.band,
                headline = "${incident.kind.replaceFirstChar { it.uppercase() }} session · $formattedTime",
                verdict = incident.verdict.ifBlank { incident.scamType?.let { "Suspected $it scam" } ?: "" },
                recommendation = incident.action,
                why = incident.why.ifBlank { "No explanation available from this session." },
                signals = signals
            )
        } else {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(RiskSafeBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shield_check),
                            contentDescription = null,
                            tint = RiskSafe,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.incidents_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.incidents_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun shareFileResult(
    context: Context,
    norm: com.codewithkael.simplecall.remote.antai.NormalizedResult
) {
    val text = """
antAI Audio Scan Verdict / ऑडियो स्कैन परिणाम
---------------------------------------------
Risk Score: ${norm.risk.toInt()}/100
Risk Level: ${norm.band.uppercase()}
Recommendation: ${norm.recommendation}
Reasons:
${norm.reasons.joinToString("\n") { "• $it" }}

Verified by antAI Guardian
    """.trimIndent()
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "antAI Audio Analysis Result")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Audio Analysis"))
}

private val _recentFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatRecentTime(millis: Long): String =
    try { _recentFmt.format(Date(millis)) } catch (_: Exception) { "—" }
