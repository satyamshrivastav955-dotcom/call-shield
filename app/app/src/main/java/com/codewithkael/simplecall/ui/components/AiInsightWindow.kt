package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.remote.antai.AiInsight
import com.codewithkael.simplecall.remote.antai.TranscriptEntry

/**
 * In-call protection card: renders using the unified [RiskResultCard].
 *
 * All values flow through the server's analysis pushes. Missing/unscored models
 * render as "— / Unavailable" states, never 0.
 */
@Composable
fun AiInsightWindow(
    insight: AiInsight?,
    transcript: List<TranscriptEntry>,
    myUsername: String,
    modifier: Modifier = Modifier
) {
    val band = insight?.band ?: "passive"
    val risk = insight?.riskScore ?: 0.0

    val flags = buildList {
        insight?.let { ins ->
            if (ins.identityMismatch) add("Caller voice does not match the saved voiceprint")
            if (ins.requestDetected && ins.requestType.isNotBlank())
                add("Caller requested: ${ins.requestType}")
            else if (ins.requestDetected) add("Caller made a sensitive request")
            if (ins.lipsyncMismatch) add("Audio and video appear out of sync")
        }
    }

    val signals = RiskSignalsData(
        voiceDeepfake = insight?.voiceDeepfake,
        scamProb = insight?.scamProb,
        scamType = insight?.scamType,
        urgency = insight?.urgency,
        voiceprintSimilarity = null,
        identityMismatch = insight?.identityMismatch,
        audioSegments = insight?.audioSegments ?: 0,
        isAnalyzing = insight == null || (insight.audioSegments == 0 && transcript.isEmpty())
    )

    RiskResultCard(
        riskScore = risk,
        band = band,
        verdict = insight?.verdict.orEmpty(),
        recommendation = insight?.action.orEmpty(),
        why = insight?.why.orEmpty(),
        signals = signals,
        reasons = flags,
        modifier = modifier,
        extraExpandedContent = {
            // Technical engine health for diagnostics
            insight?.enginesReady?.let { ready ->
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
    )
}

private fun label(speaker: String, myUsername: String): String =
    if (speaker == myUsername) "You"
    else speaker.replaceFirstChar { it.uppercase() }
