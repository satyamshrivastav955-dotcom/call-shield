package com.codewithkael.simplecall.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.viewmodel.VoiceprintViewModel

/**
 * "Register my voice" — records a short sample and stores it on the server as the
 * reference for the AI-voice cross-check.
 *
 * This is the missing half of impersonation defence: the synthetic-voice detectors
 * answer "was this generated?", but only a registered voiceprint can answer "is
 * this actually you?". Until a sample exists the cross-check has nothing to
 * compare against, so the screen states plainly what is stored and what is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceprintScreen(
    onBack: () -> Unit,
    vm: VoiceprintViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()
    val progress by vm.progress.collectAsState()

    // Granting the permission starts the recording immediately, so the user's tap
    // isn't swallowed by the permission dialog.
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.startRecording() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Register your voice") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            WhyCard()

            Spacer(Modifier.height(12.dp))
            StatusCard(
                loading = ui.loadingStatus,
                unreachable = ui.serverUnreachable,
                enrolled = ui.status.enrolled,
                count = ui.status.count,
                engineReady = ui.status.engineReady,
                onRetry = vm::refresh
            )

            Spacer(Modifier.height(20.dp))
            PhraseCard(minSeconds = ui.status.minSeconds.toInt())

            Spacer(Modifier.weight(1f))

            // ----- recorder -----
            val secs = progress.seconds
            Text(
                text = when {
                    progress.recording && progress.enoughAudio ->
                        "%.0f s — that's enough, tap to save".format(secs)
                    progress.recording -> "Recording… %.0f s".format(secs)
                    ui.uploading -> "Saving your voice…"
                    else -> "Tap the mic and read the sentence above"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(10.dp))
            LevelMeter(level = progress.level, active = progress.recording)
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (progress.recording) {
                    OutlinedButton(onClick = vm::cancelRecording) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                }
                MicButton(
                    recording = progress.recording,
                    enabled = !ui.uploading,
                    onClick = {
                        when {
                            progress.recording -> vm.stopAndUpload()
                            vm.hasMicPermission() -> vm.startRecording()
                            else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }

            if (ui.uploading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            ui.message?.let { msg ->
                Surface(
                    color = if (ui.messageIsError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(
                                if (ui.messageIsError) R.drawable.ic_warning
                                else R.drawable.ic_verified
                            ),
                            contentDescription = null,
                            tint = if (ui.messageIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ui.messageIsError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        TextButton(onClick = vm::dismissMessage) { Text("OK") }
                    }
                }
            }

            if (ui.status.enrolled) {
                TextButton(
                    onClick = vm::deleteAll,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Remove my stored samples",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
                    "Why register your voice?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "If someone clones your voice to call your family, antAI compares the " +
                        "caller against this sample and can tell them it isn't really you. " +
                        "The sample stays on your own antAI server as a numeric voiceprint — " +
                        "the recording itself isn't kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    loading: Boolean,
    unreachable: Boolean,
    enrolled: Boolean,
    count: Int,
    engineReady: Boolean,
    onRetry: () -> Unit
) {
    val (icon, tint, title, body) = when {
        loading -> Quad(
            R.drawable.ic_shield, MaterialTheme.colorScheme.outline,
            "Checking…", "Asking the server what's registered."
        )
        // "Unknown" is not the same as "none registered": saying the latter when the
        // server is simply unreachable would send the user re-recording for nothing.
        unreachable -> Quad(
            R.drawable.ic_warning, MaterialTheme.colorScheme.error,
            "Can't reach the server",
            "Check the server address on the Calls screen, then retry."
        )
        !engineReady -> Quad(
            R.drawable.ic_warning, MaterialTheme.colorScheme.error,
            "Voice matching is offline",
            "The server's speaker-matching model isn't loaded, so a sample can't be " +
                "processed yet. Start the antAI server with the speaker_verify model " +
                "downloaded, then retry."
        )
        enrolled -> Quad(
            R.drawable.ic_verified, MaterialTheme.colorScheme.primary,
            if (count == 1) "Your voice is registered" else "$count samples registered",
            "Cross-checking a suspicious caller against your voice is active. " +
                "Adding another sample in a different setting improves accuracy."
        )
        else -> Quad(
            R.drawable.ic_mic_off, MaterialTheme.colorScheme.error,
            "No voice registered yet",
            "Until you record a sample, antAI can't tell a cloned version of your " +
                "voice apart from the real one."
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(icon), contentDescription = null,
                tint = tint, modifier = Modifier.size(24.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    body,
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
private fun PhraseCard(minSeconds: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Read this out loud (about $minSeconds–8 seconds)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "\"This is my real voice. I am registering it with antAI so my family " +
                    "can tell if someone pretends to be me.\"",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LevelMeter(level: Float, active: Boolean) {
    // The recorder already smooths the RMS, so no animation is needed here — one
    // less moving part, and the bar reflects exactly what was measured.
    val shown = if (active) level.coerceIn(0f, 1f) else 0f
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
    if (active && shown <= 0.06f) {
        Text(
            "We can barely hear you — speak a little louder.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        recording -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = bg,
        shape = CircleShape,
        modifier = Modifier.size(76.dp),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (recording) R.drawable.ic_mic_off else R.drawable.ic_mic_on),
                contentDescription = if (recording) "Stop and save" else "Start recording",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** Tiny holder so [StatusCard] can pick all four pieces of copy in one `when`. */
private data class Quad(
    val icon: Int,
    val tint: Color,
    val title: String,
    val body: String
)
