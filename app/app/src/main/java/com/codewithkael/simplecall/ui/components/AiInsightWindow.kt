package com.codewithkael.simplecall.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.AiInsight
import com.codewithkael.simplecall.remote.antai.TranscriptEntry
import com.codewithkael.simplecall.ui.theme.RiskCaution
import com.codewithkael.simplecall.ui.theme.RiskCautionBg
import com.codewithkael.simplecall.ui.theme.RiskCritical
import com.codewithkael.simplecall.ui.theme.RiskCriticalBg
import com.codewithkael.simplecall.ui.theme.RiskSafe
import com.codewithkael.simplecall.ui.theme.RiskSafeBg

/**
 * In-call protection card: a calm, professional summary of what the server's
 * analysis has found so far on this call.
 *
 * Design intent is a banking / dialer trust surface (Truecaller, Google Phone
 * spam warnings), not an AI-demo console: one status, one plain-language
 * finding, one recommended action. Engineering telemetry (per-model scores,
 * engine health) lives behind "View details" so support staff can still see it
 * without alarming the person on the call.
 *
 * Every value comes from the server's pushes; nothing is hardcoded here. A
 * check that has not produced a result reads as "Checking…", never as 0%.
 */
@Composable
fun AiInsightWindow(
    insight: AiInsight?,
    transcript: List<TranscriptEntry>,
    myUsername: String,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }
    val band = insight?.band ?: "passive"
    val status = statusFor(band)

    val pillBg by animateColorAsState(status.bg, label = "statusBg")
    val pillFg by animateColorAsState(status.fg, label = "statusFg")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded.value = !expanded.value },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header: shield + title + status pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(
                        when (band) {
                            "critical" -> R.drawable.ic_warning
                            "verify" -> R.drawable.ic_shield
                            else -> R.drawable.ic_shield_check
                        }
                    ),
                    contentDescription = null,
                    tint = status.fg,
                    modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Call protection",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = status.headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = pillBg,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = status.pill,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = pillFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Risk meter (honest: only when the server has scored something)
            if (insight != null && insight.riskScore > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (insight.riskScore / 100).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        color = status.fg,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "Risk ${insight.riskScore.toInt()}/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Plain-language finding (collapsed: max 2 lines)
            val finding = insight?.verdict?.takeIf { it.isNotBlank() }
                ?: insight?.guidance?.takeIf { it.isNotBlank() }
            finding?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded.value) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (!expanded.value) {
                transcript.lastOrNull()?.let {
                    Text(
                        text = "${label(it.speaker, myUsername)}: ${it.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                ExpandedBody(insight = insight, transcript = transcript, myUsername = myUsername)
            }

            Text(
                text = if (expanded.value) "Show less" else "View details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private data class StatusSpec(
    val pill: String,
    val headline: String,
    val fg: androidx.compose.ui.graphics.Color,
    val bg: androidx.compose.ui.graphics.Color
)

private fun statusFor(band: String): StatusSpec = when (band) {
    "critical" -> StatusSpec(
        pill = "High risk", headline = "This call needs attention",
        fg = RiskCritical, bg = RiskCriticalBg
    )
    "verify" -> StatusSpec(
        pill = "Review", headline = "Something looks unusual",
        fg = RiskCaution, bg = RiskCautionBg
    )
    else -> StatusSpec(
        pill = "Monitoring", headline = "No threat detected so far",
        fg = RiskSafe, bg = RiskSafeBg
    )
}

@Composable
private fun ExpandedBody(
    insight: AiInsight?,
    transcript: List<TranscriptEntry>,
    myUsername: String
) {
    if (insight == null && transcript.isEmpty()) {
        Text(
            text = "Listening. Findings will appear here as the call progresses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    insight?.let { ins ->
        if (ins.why.isNotBlank()) {
            Text(
                text = ins.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (ins.action.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Recommended: ${ins.action}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        val flags = buildList {
            if (ins.identityMismatch) add("Caller voice does not match the saved voiceprint")
            if (ins.requestDetected && ins.requestType.isNotBlank())
                add("Caller requested: ${ins.requestType}")
            else if (ins.requestDetected) add("Caller made a sensitive request")
            if (ins.lipsyncMismatch) add("Audio and video appear out of sync")
        }
        if (flags.isNotEmpty()) {
            Column(Modifier.padding(top = 8.dp)) {
                flags.forEach { flag ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = RiskCaution,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = flag,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Professional signal summary: grouped, labeled, honest nulls.
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        Text(
            text = "Call analysis",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SignalRow(
            label = "Voice authenticity",
            value = ins.voiceDeepfake?.let { v ->
                when {
                    v >= 0.85 -> "Likely synthetic (${(v * 100).toInt()}%)"
                    v >= 0.5 -> "Uncertain (${(v * 100).toInt()}%)"
                    else -> "Appears natural (${(v * 100).toInt()}% synthetic)"
                }
            } ?: if (ins.audioSegments == 0) "Listening…" else "Checking…"
        )
        SignalRow(
            label = "Message patterns",
            value = when {
                ins.scamType.isNotBlank() && ins.scamType != "unknown" ->
                    "Matches ${ins.scamType} patterns (${(ins.scamProb * 100).toInt()}%)"
                ins.scamProb > 0.5 -> "Suspicious patterns (${(ins.scamProb * 100).toInt()}%)"
                ins.audioSegments == 0 && transcript.isEmpty() -> "Listening…"
                else -> "No scam patterns detected"
            }
        )
        SignalRow(
            label = "Pressure tactics",
            value = when {
                ins.urgency >= 70 -> "High pressure (${ins.urgency.toInt()}/100)"
                ins.urgency >= 40 -> "Some pressure (${ins.urgency.toInt()}/100)"
                ins.audioSegments == 0 && transcript.isEmpty() -> "Listening…"
                else -> "Normal tone"
            }
        )

        // Technical details for support — collapsed language, no alarm.
        val ready = ins.enginesReady
        if (ready.isNotEmpty()) {
            val up = ready.count { it.value }
            Text(
                text = "System: $up of ${ready.size} checks running" +
                    if (up < ready.size) {
                        val down = ready.filterValues { !it }.keys
                            .map { it.replace('_', ' ') }.sorted()
                        " (${down.joinToString(", ")} unavailable)"
                    } else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (ins.voiceDeepfake == null && ins.audioSegments > 0) {
            Text(
                text = "Voice analysis is temporarily unavailable. Treat unexpected requests with extra care.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (transcript.isNotEmpty()) {
        Text(
            text = "Transcript",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )
        val listState = rememberLazyListState()
        LaunchedEffect(transcript.size) {
            if (transcript.isNotEmpty()) {
                listState.animateScrollToItem(transcript.size - 1)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
        ) {
            items(transcript) { entry ->
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = label(entry.speaker, myUsername),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(64.dp)
                    )
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private fun label(speaker: String, myUsername: String): String =
    if (speaker == myUsername) "You"
    else speaker.replaceFirstChar { it.uppercase() }
