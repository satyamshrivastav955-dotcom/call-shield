package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.DeepfakeAlert
import com.codewithkael.simplecall.remote.antai.VoiceprintResult
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Blocking "call paused" prompt shown when the detectors flag the ongoing call.
 * The media is already paused; the user must consciously end or resume.
 *
 * Styled as a serious banking verification prompt (light surface, single clear
 * headline, explicit safe action first), not a security-theatre alert. Everything
 * shown comes off the wire — headline, explanation, confidence and which checks
 * produced it. Nothing is invented client-side.
 *
 * Phase 2.3 — AUTO-TERMINATION: when [autoHangupSeconds] is non-null (the caller
 * sets it only for a CRITICAL / high-risk verdict on the in-app WebRTC call we
 * fully own and can end reliably), the dialog widens into a prominent countdown
 * and auto-invokes [onEndCall] when it reaches zero. Any deliberate engagement
 * cancels the countdown: tapping the voiceprint cross-check pauses it (the user is
 * investigating), and "resume anyway" dismisses the whole dialog. A merely-
 * suspicious (verify) call passes [autoHangupSeconds] = null, so it shows the same
 * alert WITHOUT a countdown and never auto-hangs-up.
 */
@Composable
fun DeepfakeAlertDialog(
    alert: DeepfakeAlert,
    autoHangupSeconds: Int? = null,
    voiceprint: VoiceprintResult? = null,
    onCrossVerify: () -> Unit = {},
    onResume: () -> Unit,
    onEndCall: () -> Unit
) {
    val title = alert.title.ifBlank {
        when (alert.source) {
            "voice" -> "AI-generated voice detected"
            "video" -> "AI-generated video detected"
            "identity" -> "Voice does not match your contact"
            else -> "Possible scam detected"
        }
    }

    // Phase 2.3 auto-hangup countdown. `cancelled` is flipped by any deliberate
    // engagement (cross-check), which stops the timer but keeps the dialog so the
    // user can finish investigating; "resume anyway" dismisses the dialog entirely
    // (which also tears down the LaunchedEffect). The timer counts real seconds and
    // fires onEndCall exactly once at zero.
    var cancelled by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(autoHangupSeconds ?: 0) }
    val countdownActive = autoHangupSeconds != null && autoHangupSeconds > 0 && !cancelled

    LaunchedEffect(autoHangupSeconds, cancelled) {
        if (autoHangupSeconds == null || autoHangupSeconds <= 0 || cancelled) return@LaunchedEffect
        remaining = autoHangupSeconds
        while (remaining > 0) {
            delay(1000L)
            remaining -= 1
        }
        onEndCall()
    }

    // Deliberate engagement with the second-opinion check cancels the auto-hangup.
    val onCrossVerifyGuarded: () -> Unit = {
        cancelled = true
        onCrossVerify()
    }

    Dialog(
        onDismissRequest = { /* must choose explicitly */ },
        properties = DialogProperties(usePlatformDefaultWidth = !countdownActive)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = if (countdownActive) Modifier.fillMaxWidth(0.94f) else Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Phase 2.3 — prominent auto-hangup countdown (critical/high-risk only).
                if (countdownActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Ending call automatically in",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Text(
                                "${remaining}s",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Text(
                                "High risk of a scam or AI-cloned voice. " +
                                    "Tap “resume anyway” to stay on the line.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_warning),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = alert.message.ifBlank {
                        "This call shows signs of being a scam."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Provenance: which check fired, in plain words.
                detectorSummary(alert)?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "The call is paused. Do not share money, codes, or personal details until you verify.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (alert.canCrossVerify || voiceprint != null) {
                    Spacer(Modifier.height(2.dp))
                    VoiceprintSection(
                        result = voiceprint,
                        enabled = alert.canCrossVerify,
                        onCrossVerify = onCrossVerifyGuarded
                    )
                }

                Spacer(Modifier.height(4.dp))

                Column(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (countdownActive) "End call now" else "End call",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = onResume,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("I understand — resume anyway")
                    }
                }
            }
        }
    }
}

/**
 * The independent second opinion: compare the live call audio against the saved
 * voiceprint. Deliberately explicit about what it cannot prove — a match is not a
 * guarantee of authenticity, and "couldn't check" must never read as "not them".
 */
@Composable
private fun VoiceprintSection(
    result: VoiceprintResult?,
    enabled: Boolean,
    onCrossVerify: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                result?.checking == true -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        "Comparing this voice with the saved voiceprint…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                result != null && result.ok && result.matches == false -> {
                    Text(
                        "Does not match the saved voiceprint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Two independent checks now agree this is not the person who " +
                            "owns this number. Hang up and call them back yourself." +
                            similarityNote(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                result != null && result.ok && result.matches == true -> {
                    Text(
                        "Matches the saved voiceprint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "The voice matches the person who owns this number, which points " +
                            "away from impersonation. A very good clone can still match, " +
                            "so if anything else feels wrong, ask something only they " +
                            "would know." + similarityNote(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                result != null && !result.ok -> {
                    Text(
                        "Voiceprint check unavailable",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = result.hint.ifBlank {
                            "This says nothing about whether the caller is genuine — " +
                                "the comparison simply could not run."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (enabled) {
                        OutlinedButton(
                            onClick = onCrossVerify,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try again")
                        }
                    }
                }

                else -> {
                    Text(
                        "Confirm with a saved voiceprint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Compare this caller's voice with the voiceprint on file for " +
                            "this number — a second, independent check on this warning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onCrossVerify,
                        enabled = enabled,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cross-check this voice")
                    }
                }
            }
        }
    }
}

private fun similarityNote(r: VoiceprintResult): String {
    val sim = r.similarity ?: return ""
    val pct = (sim * 100).roundToInt()
    val thr = r.threshold?.let { " (match needs ${(it * 100).roundToInt()}%)" } ?: ""
    val secs = if (r.secondsOfAudio > 0) ", from ${r.secondsOfAudio}s of speech" else ""
    return "\n\nVoice similarity: $pct%$thr$secs."
}

/**
 * One line describing where the score came from. Agreement between two independent
 * detectors means something very different from one model on its own, and the user
 * deserves to see which they're looking at.
 */
private fun detectorSummary(alert: DeepfakeAlert): String? {
    if (alert.source == "video") {
        if (alert.videoDeepfake <= 0.0) return null
        val pct = (alert.videoDeepfake * 100).roundToInt()
        val votes = if (alert.videoVotes > 0) " across ${alert.videoVotes} frames" else ""
        return "Video model: $pct% synthetic$votes."
    }
    if (alert.voiceDeepfake <= 0.0) return null

    val pct = (alert.voiceDeepfake * 100).roundToInt()
    val who = when (alert.voiceAgreement) {
        "both-flag" -> "Both voice detectors agree"
        "one-flag-one-unsure" -> "One detector flagged it, the second was unsure"
        "single-source" -> when (alert.voiceBackend) {
            "velma" -> "Flagged by the streaming detector (the on-device model did not score this segment)"
            "local" -> "Flagged by the on-device model (the streaming detector is unavailable)"
            else -> "Flagged by one detector"
        }
        else -> if (alert.voiceSources >= 2) "Two detectors scored this" else "One detector scored this"
    }
    val breakdown = alert.voicePerModel
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(" · ") { "${it.key} ${(it.value * 100).roundToInt()}%" }
        ?.let { "  [$it]" }
        ?: ""
    return "$who — $pct% synthetic.$breakdown"
}
