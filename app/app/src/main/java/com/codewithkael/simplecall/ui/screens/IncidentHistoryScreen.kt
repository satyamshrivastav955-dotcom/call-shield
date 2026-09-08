package com.codewithkael.simplecall.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val formattedTime = formatTime(item.createdAt)
    var showInspect by remember { mutableStateOf(false) }
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
        // Phase 3.2: the card (and its "View details" link) opens a full inspection
        // dialog — technical reasoning, forensic signals, transcript excerpt, and the
        // 1-tap national cyber-complaint — instead of the inline expand.
        onClick = { showInspect = true }
    )

    if (showInspect) {
        IncidentInspectionDialog(item = item, onDismiss = { showInspect = false })
    }
}

/**
 * Full incident inspection (§ Phase 3.2). Surfaces the forensic detail that the
 * collapsed card can't: the technical reasoning, the on-device AI-voice score, the
 * claimed identity, the scam category, and a transcript excerpt — every one of
 * which renders an HONEST "unavailable / not measured" when the value is null,
 * never a fabricated 0. Audio playback is intentionally surfaced as unavailable:
 * antAI never persists raw call audio (only features/embeddings), so there is
 * nothing to play back — stating that is the honest answer, not a missing feature.
 * The primary action files a bilingual (English + Hindi) national cyber-crime
 * complaint prefilled from this incident.
 */
@Composable
private fun IncidentInspectionDialog(item: IncidentItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    text = "Incident details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "घटना का विवरण",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                InspectLine("Assessment / आकलन", "${item.band.uppercase()} · ${item.riskScore.toInt()}/100")
                InspectLine("When / समय", formatTime(item.createdAt))
                InspectLine("Session / सत्र", item.kind.replaceFirstChar { it.uppercase() })

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // ── Technical reasoning ─────────────────────────────────────
                SectionLabel("Why this was flagged / इसे क्यों चिह्नित किया गया")
                Text(
                    text = item.why.ifBlank { "No detailed reasoning was recorded for this session." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.verdict.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Verdict / निष्कर्ष: ${item.verdict}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(12.dp))
                // ── Forensic signals (honest null) ──────────────────────────
                SectionLabel("Forensic signals / फोरेंसिक संकेत")
                InspectLine(
                    "AI-voice score / एआई-आवाज़ स्कोर",
                    item.spoofProb?.let { "${(it * 100).toInt()}%" } ?: "Not measured / मापा नहीं गया"
                )
                InspectLine(
                    "Claimed identity / दावा की गई पहचान",
                    item.contact ?: "Unknown caller / अज्ञात कॉलर"
                )
                InspectLine(
                    "Scam category / धोखाधड़ी श्रेणी",
                    item.scamType ?: "None identified / कोई नहीं"
                )

                Spacer(Modifier.height(12.dp))
                // ── Transcript excerpt ──────────────────────────────────────
                SectionLabel("Transcript excerpt / प्रतिलेख अंश")
                Text(
                    text = item.transcript?.takeIf { it.isNotBlank() }
                        ?: "No transcript was captured for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))
                // ── Audio playback — HONESTLY unavailable ───────────────────
                SectionLabel("Audio playback / ऑडियो प्लेबैक")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Unavailable — antAI never stores raw call audio. Only on-device " +
                            "features/embeddings are kept, for your privacy.\n" +
                            "अनुपलब्ध — antAI कभी भी कॉल ऑडियो सहेजता नहीं है।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { fileCyberComplaint(context, item) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_file_complaint))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 3.dp)
    )
}

@Composable
private fun InspectLine(label: String, value: String) {
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
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Build a bilingual (English + Hindi) National Cyber Crime complaint, prefilled
 * from the incident's forensic fields. Absent values render as an honest "Not
 * measured / Unknown" — never a fabricated 0 (honest-null rule). Reporting
 * channels are the real Indian ones: portal cybercrime.gov.in + helpline 1930.
 */
private fun buildComplaintText(item: IncidentItem): String {
    val time = formatTime(item.createdAt)
    val category = item.scamType ?: "Suspected impersonation / fraud"
    val aiVoice = item.spoofProb?.let { "${(it * 100).toInt()}% (on-device AI-voice detector)" }
        ?: "Not measured"
    val who = item.contact ?: "Unknown caller"
    val reasoning = item.why.ifBlank { "Signals crossed antAI's verification margin." }
    val verdict = item.verdict.ifBlank { "Flagged by antAI real-time detection" }
    val transcriptBlock = item.transcript?.takeIf { it.isNotBlank() }
        ?.let { "Transcript excerpt (auto-captured on device):\n\"${it.take(500)}\"\n\n" } ?: ""
    val divider = "=".repeat(56)

    return buildString {
        appendLine("NATIONAL CYBER CRIME COMPLAINT / राष्ट्रीय साइबर अपराध शिकायत")
        appendLine("Report online: https://cybercrime.gov.in  |  Helpline: 1930")
        appendLine("पोर्टल: https://cybercrime.gov.in  |  हेल्पलाइन: 1930")
        appendLine(divider)
        appendLine()
        // ---- English ----
        appendLine("[ENGLISH]")
        appendLine("Nature of complaint: Suspected voice-cloning / phone scam.")
        appendLine("Date & time of incident: $time")
        appendLine("Detected category: $category")
        appendLine("antAI risk assessment: ${item.band.uppercase()} (${item.riskScore.toInt()}/100)")
        appendLine("AI-generated-voice likelihood: $aiVoice")
        appendLine("Caller / claimed identity: $who")
        appendLine("Why flagged: $reasoning")
        appendLine("Verdict: $verdict")
        appendLine()
        append(transcriptBlock)
        appendLine("Complainant details (please fill): name, mobile, address.")
        appendLine("Suspect's number & time of call (please fill).")
        appendLine("Financial loss, if any (amount, UPI / transaction ref).")
        appendLine()
        appendLine(divider)
        appendLine()
        // ---- Hindi ----
        appendLine("[हिन्दी]")
        appendLine("शिकायत का प्रकार: संदिग्ध वॉइस-क्लोनिंग / फ़ोन धोखाधड़ी।")
        appendLine("घटना की तिथि व समय: $time")
        appendLine("पहचानी गई श्रेणी: $category")
        appendLine("antAI जोखिम आकलन: ${item.band.uppercase()} (${item.riskScore.toInt()}/100)")
        appendLine("एआई-निर्मित आवाज़ की संभावना: $aiVoice")
        appendLine("कॉलर / दावा की गई पहचान: $who")
        appendLine("शिकायतकर्ता विवरण (कृपया भरें): नाम, मोबाइल, पता।")
        appendLine("संदिग्ध का नंबर व कॉल का समय (कृपया भरें)।")
        appendLine("आर्थिक हानि, यदि कोई हो (राशि, UPI / लेनदेन संदर्भ)।")
        appendLine()
        appendLine("Generated on-device by antAI Guardian. Raw audio is not stored.")
        appendLine("antAI गार्जियन द्वारा डिवाइस पर तैयार। ऑडियो सहेजा नहीं जाता।")
    }
}

private fun fileCyberComplaint(context: Context, item: IncidentItem) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, buildComplaintText(item))
        putExtra(Intent.EXTRA_SUBJECT, "Cyber Fraud Complaint — ${item.kind} (${item.band})")
        type = "text/plain"
    }
    val chooser = Intent.createChooser(sendIntent, "File Cyber Complaint (1930 / cybercrime.gov.in)")
    context.startActivity(chooser)
}

private val _timeFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatTime(millis: Long): String =
    try { _timeFmt.format(Date(millis)) } catch (_: Exception) { "—" }
