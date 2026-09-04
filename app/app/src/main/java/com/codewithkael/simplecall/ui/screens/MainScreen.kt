package com.codewithkael.simplecall.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.components.CallComponent
import com.codewithkael.simplecall.ui.components.DeepfakeAlertDialog
import com.codewithkael.simplecall.ui.components.IncomingCallSnackBar
import com.codewithkael.simplecall.ui.components.ServerConfig
import com.codewithkael.simplecall.ui.components.WhoToCall
import com.codewithkael.simplecall.ui.components.YourIdCard
import com.codewithkael.simplecall.ui.viewmodel.MainViewModel
import com.codewithkael.simplecall.ui.viewmodel.SettingsViewModel
import com.codewithkael.simplecall.utils.ConnectionState
import com.codewithkael.simplecall.utils.SimpleCallApplication

@Composable
fun MainScreen() {
    val viewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.all { it.value }) {
            Toast.makeText(
                context, "Camera And Microphone permission is required", Toast.LENGTH_SHORT
            ).show()
        }
        // Connecting is started from the "Connect" button (ServerConfig) so the user
        // can set/confirm the server IP first, instead of auto-connecting to a default.
    }

    val connectionStatus = viewModel.connectionState.collectAsState()


    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.eventState.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(4.dp, 22.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (connectionStatus.value !is ConnectionState.OnCall) {
                    AntaiCallHeader()

                    YourIdCard(userId = SimpleCallApplication.USER_ID) {
                        Toast.makeText(context, "User ID Copied to clipboard", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            if (connectionStatus.value is ConnectionState.New) {
                val currentHost = viewModel.serverHost.collectAsState()
                ServerConfig(
                    initialHost = currentHost.value,
                    onConnect = { enteredHost ->
                        viewModel.updateServerHost(enteredHost)
                        viewModel.connectSocket()
                    }
                )
            }
            if (connectionStatus.value is ConnectionState.WaitingForCall || connectionStatus.value is ConnectionState.UserOffline) {
                WhoToCall(onCallClick = { targetId, isVoice ->
                    if (targetId.isEmpty()) {
                        Toast.makeText(context, "Enter User ID to call", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.findUser(targetId, isVoice)
                    }
                })
            }
        }

        if (connectionStatus.value is ConnectionState.CallingTarget ||
            connectionStatus.value is ConnectionState.OnCall){
            val aiInsight = viewModel.aiInsightState.collectAsState()
            val transcript = viewModel.transcriptState.collectAsState()
            val isVoice = viewModel.callKind.collectAsState().value == "voice"
            val callerVerify = viewModel.callerVerifyState.collectAsState()
            // Pre-fill with the other side's own id purely as a convenience; the
            // dialog lets the user change it, because the whole point is that the
            // caller may be claiming to be someone else entirely.
            val peerId = when (val s = connectionStatus.value) {
                is ConnectionState.OnCall -> s.target
                is ConnectionState.CallingTarget -> s.target.orEmpty()
                else -> ""
            }
            CallComponent(
                onSurfaceRemoteReady = {remoteRenderer ->
                    viewModel.onSurfaceRemoteReady(remoteRenderer)
                },
                onSurfaceLocalReady = {localRenderer ->
                    viewModel.onSurfaceLocalReady(localRenderer)
                },
                onSwitchCamera = {
                    viewModel.switchCamera()
                },
                onEndCall = {
                    viewModel.endCall()
                },
                onToggleMic = {enabled ->
                    viewModel.toggleMic(enabled)
                },
                onToggleCamera = {enabled->
                    viewModel.toggleCamera(enabled)
                },
                onToggleSpeaker = {isSpeaker->
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

        if (connectionStatus.value is ConnectionState.ReceivedCall){
            val callerId = (connectionStatus.value as ConnectionState.ReceivedCall).sender
            IncomingCallSnackBar(
                callerId = callerId!!,
                onTimeout = {
                    viewModel.incomingCallDismissed()
                    Toast.makeText(context, "Call Timed out", Toast.LENGTH_SHORT).show()
                },
                onAccept = {target->
                    viewModel.acceptIncomingCall(target)
                },
                onReject = {target->
                    viewModel.rejectIncomingCall(target)
                }
            )
        }

        // Deepfake pause-and-alert: blocking modal over the whole screen while
        // the call media is paused.
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

@Composable
private fun AntaiCallHeader() {
    val context = LocalContext.current
    val activeScenario = remember { SettingsViewModel.readScenario(context) }
    val scenarioLabel = when (activeScenario) {
        "privileged_access" -> "Privileged Access"
        "routine_call"      -> "Routine Call"
        else                -> "High-Value Txn"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = "antAI",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Autonomous Deepfake & Scam Interception",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Real-time Protection Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF2E7D32), CircleShape)
            )
            Text(
                text = "PROTECTION ARMED",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2E7D32)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Threat Scenario & Active Model Health Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            HeaderChip(label = scenarioLabel, icon = R.drawable.ic_shield)
            HeaderChip(label = "4 Models Active", icon = R.drawable.ic_shield_check)
        }
    }
}

@Composable
private fun HeaderChip(label: String, icon: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}