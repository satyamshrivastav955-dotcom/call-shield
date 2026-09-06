package com.codewithkael.simplecall.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.IncidentItem
import com.codewithkael.simplecall.ui.components.RiskResultCard
import com.codewithkael.simplecall.ui.components.RiskSignalsData
import com.codewithkael.simplecall.ui.theme.RiskCaution
import com.codewithkael.simplecall.ui.viewmodel.IncidentHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Incident History — shows past verdicts from GET /api/verdicts.
 *
 * Uses the shared [RiskResultCard] component (§0 rule 3) and surfaces the
 * FIR / cyber-crime complaint drafting action (§1 task A7).
 */
@Composable
fun IncidentHistoryScreen(
    viewModel: IncidentHistoryViewModel = hiltViewModel()
) {
    val incidents by viewModel.incidents
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    text = "Incident History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Past risk assessments from your protected sessions",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RiskCaution,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = error ?: "Could not load incidents",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            incidents.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.incidents_empty_title),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.incidents_empty_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(incidents, key = { it.id }) { item ->
                        IncidentRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentRow(item: IncidentItem) {
    val context = LocalContext.current
    val formattedTime = formatTime(item.createdAt)
    val signals = RiskSignalsData(
        scamType = item.scamType,
        scamProb = if (item.scamType != null) (item.riskScore / 100.0) else null
    )

    RiskResultCard(
        riskScore = item.riskScore,
        band = item.band,
        headline = "${item.kind.replaceFirstChar { it.uppercase() }} session · $formattedTime",
        verdict = item.verdict.ifBlank { item.scamType?.let { "Suspected $it scam" } ?: "" },
        recommendation = item.action,
        why = item.why.ifBlank { "No explanation available from this session." },
        signals = signals,
        modifier = Modifier.fillMaxWidth(),
        footerActionContent = {
            OutlinedButton(
                onClick = { shareFirDraft(context, item) },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_draft_fir),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    )
}

private fun shareFirDraft(context: Context, item: IncidentItem) {
    val formattedTime = formatTime(item.createdAt)
    val text = """
antAI Cyber Fraud Incident Report / साइबर अपराध शिकायत विवरण
-----------------------------------------------------------
Date & Time / दिनांक: $formattedTime
Session Type / प्रकार: ${item.kind.replaceFirstChar { it.uppercase() }}
Risk Assessment / जोखिम स्तर: ${item.band.uppercase()} (${item.riskScore.toInt()}/100)
Scam Category / श्रेणी: ${item.scamType ?: "Suspected Impersonation / Fraud"}
Verdict / निष्कर्ष: ${item.verdict.ifBlank { "Flagged by real-time detection pipeline" }}
Technical Reasoning / तकनीकी कारण: ${item.why.ifBlank { "Signals crossed verification margin" }}
Recommended Action / सुझाई गई कार्रवाई: ${item.action.ifBlank { "Block caller and preserve evidence" }}

Generated by antAI Guardian (National Cyber Crime Reporting Portal prefill)
    """.trimIndent()

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "Cyber Fraud Report - ${item.kind}")
        type = "text/plain"
    }
    val chooser = Intent.createChooser(sendIntent, "Draft FIR / Share Incident Report")
    context.startActivity(chooser)
}

private val _timeFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatTime(millis: Long): String =
    try { _timeFmt.format(Date(millis)) } catch (_: Exception) { "—" }
