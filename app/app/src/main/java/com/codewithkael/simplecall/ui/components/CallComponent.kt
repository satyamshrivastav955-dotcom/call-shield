package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.AiInsight
import com.codewithkael.simplecall.remote.antai.CallerVerification
import com.codewithkael.simplecall.remote.antai.TranscriptEntry
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallComponent(
    onSurfaceRemoteReady: (SurfaceViewRenderer) -> Unit,
    onSurfaceLocalReady: (SurfaceViewRenderer) -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    isVoiceCall: Boolean = false,
    aiInsight: AiInsight? = null,
    transcript: List<TranscriptEntry> = emptyList(),
    myUsername: String = "",
    // Identity verification — optional and purely additive, so existing call
    // behaviour is unchanged when a caller omits these.
    callerVerification: CallerVerification? = null,
    verifyDefaultPhone: String = "",
    onVerifyCaller: (String) -> Unit = {},
    onDismissVerify: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMicEnabled = remember {mutableStateOf(true)}
    val isCameraEnabled = remember {mutableStateOf(true)}
    val isSpeakerEnabled = remember {mutableStateOf(true)}
    Box(modifier = modifier.fillMaxSize().padding(0.dp,16.dp)) {
        if (!isVoiceCall) {
            // Remote video fullscreen
            SurfaceViewRendererComposable(
                modifier = Modifier.fillMaxSize(), onSurfaceReady = onSurfaceRemoteReady
            )

            // Local video on the bottom right
            SurfaceViewRendererComposable(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp, 80.dp)
                    .size(110.dp, 150.dp), onSurfaceReady = onSurfaceLocalReady
            )
        } else {
            // Voice-only: professional caller card instead of a bare label.
            // No duration timer here (the ViewModel owns call state); the card
            // shows who the protection is watching and that audio is live.
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Text(
                    text = "Voice call",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(2.dp))
                androidx.compose.material3.Text(
                    text = "Protected by antAI",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // antAI live-scam-analysis window at the top of the call screen
        AiInsightWindow(
            insight = aiInsight,
            transcript = transcript,
            myUsername = myUsername,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp)
        )

        // Control buttons at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // "Is this really who they say they are?" — sits directly above the
            // call controls so it's reachable during the call, which is the only
            // moment it's useful.
            CallerVerifyBar(
                state = callerVerification,
                defaultPhone = verifyDefaultPhone,
                onVerify = onVerifyCaller,
                onDismiss = onDismissVerify
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.66f), shape = MaterialTheme.shapes.medium)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isVoiceCall) {
                    // Switch Camera Button
                    IconButton(onClick = onSwitchCamera) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_switch_camera),
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }

                // End Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .background(Color.Red, shape = CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_end_call),
                        contentDescription = "End Call",
                        tint = Color.White
                    )
                }

                // Mute/UnMute Mic Button
                IconButton(onClick = {
                    isMicEnabled.value = !isMicEnabled.value
                    onToggleMic.invoke(isMicEnabled.value)
                }) {
                    Icon(
                        painter = if (isMicEnabled.value) painterResource(id = R.drawable.ic_mic_on)
                        else painterResource(id = R.drawable.ic_mic_off),
                        contentDescription = if (isMicEnabled.value) "Mute Mic" else "UnMute Mic",
                        tint = Color.White
                    )
                }

                if (!isVoiceCall) {
                    // Disable/Enable Camera Button
                    IconButton(onClick = {
                        isCameraEnabled.value = !isCameraEnabled.value
                        onToggleCamera.invoke(isCameraEnabled.value)
                    }) {
                        Icon(
                            painter = if (isCameraEnabled.value) painterResource(id = R.drawable.ic_camera_on)
                            else painterResource(id = R.drawable.ic_camera_off),
                            contentDescription = if (isCameraEnabled.value) "Disable Camera" else "Enable Camera",
                            tint = Color.White
                        )
                    }
                }

                // Disable/Enable Speaker
                IconButton(onClick = {
                    isSpeakerEnabled.value = !isSpeakerEnabled.value
                    onToggleSpeaker.invoke(isSpeakerEnabled.value)
                }) {
                    Icon(
                        painter = if (isSpeakerEnabled.value) painterResource(id = R.drawable.ic_speaker)
                        else painterResource(id = R.drawable.ic_ear),
                        contentDescription = if (isSpeakerEnabled.value) "Switch to earpiece" else "Switch to speaker",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
