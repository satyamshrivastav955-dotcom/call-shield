package com.codewithkael.simplecall.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.RiskBand
import com.codewithkael.simplecall.ui.theme.RiskCaution
import com.codewithkael.simplecall.ui.theme.RiskCautionBg
import com.codewithkael.simplecall.ui.theme.RiskCritical
import com.codewithkael.simplecall.ui.theme.RiskCriticalBg
import com.codewithkael.simplecall.ui.theme.RiskSafe
import com.codewithkael.simplecall.ui.theme.RiskSafeBg

/**
 * Normalized signal data passed into [RiskResultCard].
 * Null values represent unavailable/unscored models and MUST render as "— / Unavailable",
 * never as 0 or a fake green (Ground Rule §0.2).
 */
data class RiskSignalsData(
    val voiceDeepfake: Double? = null,
    val scamProb: Double? = null,
    val scamType: String? = null,
    val urgency: Double? = null,
    val voiceprintSimilarity: Double? = null,
    val identityMismatch: Boolean? = null,
    val audioSegments: Int = 0,
    val isAnalyzing: Boolean = false
)

private data class CardBandSpec(
    val fg: Color,
    val bg: Color,
    val icon: Int,
    val label: String
)

@Composable
private fun resolveBandSpec(band: String): CardBandSpec = when (band.lowercase()) {
    "critical" -> CardBandSpec(
        fg = RiskCritical,
        bg = RiskCriticalBg,
        icon = R.drawable.ic_warning,
        label = stringResource(R.string.risk_band_critical)
    )
    "verify", "caution" -> CardBandSpec(
        fg = RiskCaution,
        bg = RiskCautionBg,
        icon = R.drawable.ic_warning,
        label = stringResource(R.string.risk_band_caution)
    )
    "safe", "passive" -> CardBandSpec(
        fg = RiskSafe,
        bg = RiskSafeBg,
        icon = R.drawable.ic_verified,
        label = stringResource(R.string.risk_band_safe)
    )
    else -> CardBandSpec(
        fg = MaterialTheme.colorScheme.onSurfaceVariant,
        bg = MaterialTheme.colorScheme.surfaceVariant,
        icon = R.drawable.ic_shield,
        label = stringResource(R.string.risk_band_checking)
    )
}

/**
 * One unified risk card composable used across:
 * 1. Live call (AiInsightWindow / CallComponent)
 * 2. File / forwarded voice note analysis (MainScreen / Check Recording)
 * 3. Incident history rows (IncidentHistoryScreen)
 *
 * Meets Ground Rules §0.1 (theme tokens), §0.2 (no 0 for missing signals),
 * §0.3 (one card everywhere), and §0.6 (zero raw hex literals).
 */
@Composable
fun RiskResultCard(
    riskScore: Double,
    band: String,
    modifier: Modifier = Modifier,
    headline: String = "",
    verdict: String = "",
    recommendation: String = "",
    why: String = "",
    signals: RiskSignalsData? = null,
    reasons: List<String> = emptyList(),
    initiallyExpanded: Boolean = false,
    headerTrailingContent: (@Composable () -> Unit)? = null,
    footerActionContent: (@Composable () -> Unit)? = null,
    extraExpandedContent: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val spec = resolveBandSpec(band)
    val pillBg by animateColorAsState(spec.bg, label = "pillBg")
    val pillFg by animateColorAsState(spec.fg, label = "pillFg")

    val displayHeadline = headline.ifBlank {
        when (band.lowercase()) {
            "critical" -> stringResource(R.string.risk_headline_critical)
            "verify", "caution" -> stringResource(R.string.risk_headline_caution)
            "safe", "passive" -> stringResource(R.string.risk_headline_safe)
            else -> stringResource(R.string.risk_headline_default)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            // ── Header: Icon + Title + Status Badge ──────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = spec.icon),
                    contentDescription = null,
                    tint = spec.fg,
                    modifier = Modifier.size(24.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.risk_headline_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayHeadline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = pillBg,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(pillFg)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = spec.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = pillFg
                        )
                    }
                }
                headerTrailingContent?.invoke()
            }

            // ── Risk Score Gauge (Honest: only when risk > 0) ───────────────────
            if (riskScore > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (riskScore / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        color = spec.fg,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.risk_score_format, riskScore.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Summary / Verdict (collapsed: 2 lines) ──────────────────────────
            val finding = verdict.ifBlank { recommendation }
            if (finding.isNotBlank()) {
                Text(
                    text = finding,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ── Expanded Body: 3 Signals + Why + Recommendation ─────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(Modifier.padding(top = 10.dp)) {
                    // Recommendation box
                    if (recommendation.isNotBlank() && recommendation != finding) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_recommended_action, recommendation),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Why flagged
                    if (why.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_why_flagged),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = why,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Active reasons list
                    if (reasons.isNotEmpty()) {
                        Column(Modifier.padding(top = 6.dp)) {
                            reasons.forEach { r ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_warning),
                                        contentDescription = null,
                                        tint = spec.fg,
                                        modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                    )
                                    Text(
                                        text = r,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // ── 3 Signals: Grouped, Honest unavailable state ──────────
                    Text(
                        text = stringResource(R.string.label_call_analysis),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    // 1. Voice authenticity
                    SignalRowItem(
                        label = stringResource(R.string.signal_voice_auth),
                        value = resolveVoiceAuth(signals)
                    )

                    // 2. Message patterns
                    SignalRowItem(
                        label = stringResource(R.string.signal_message_patterns),
                        value = resolveMessagePatterns(signals)
                    )

                    // 3. Caller identity / Voiceprint
                    SignalRowItem(
                        label = stringResource(R.string.signal_voiceprint),
                        value = resolveVoiceprint(signals)
                    )

                    // Optional: Pressure tactics (when urgency is reported)
                    signals?.urgency?.let { urg ->
                        SignalRowItem(
                            label = stringResource(R.string.signal_pressure),
                            value = when {
                                urg >= 70 -> stringResource(R.string.signal_pressure_high, urg.toInt())
                                urg >= 40 -> stringResource(R.string.signal_pressure_some, urg.toInt())
                                else -> stringResource(R.string.signal_pressure_normal)
                            }
                        )
                    }

                    extraExpandedContent?.invoke()
                }
            }

            // ── Footer: Expand Toggle + Actions ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) stringResource(R.string.btn_show_less)
                           else stringResource(R.string.btn_view_details),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                footerActionContent?.invoke()
            }
        }
    }
}

@Composable
private fun SignalRowItem(label: String, value: String) {
    val isUnavailable = value.contains("Unavailable") || value.contains("अनुपलब्ध")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (isUnavailable) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun resolveVoiceAuth(signals: RiskSignalsData?): String {
    if (signals == null || (signals.voiceDeepfake == null && !signals.isAnalyzing && signals.audioSegments == 0)) {
        return stringResource(R.string.signal_unavailable)
    }
    if (signals.isAnalyzing || (signals.audioSegments == 0 && signals.voiceDeepfake == null)) {
        return stringResource(R.string.signal_analyzing)
    }
    val v = signals.voiceDeepfake ?: return stringResource(R.string.signal_unavailable)
    val pct = (v * 100).toInt()
    return when {
        v >= 0.85 -> stringResource(R.string.signal_voice_likely_synthetic, pct)
        v >= 0.50 -> stringResource(R.string.signal_voice_uncertain, pct)
        else -> stringResource(R.string.signal_voice_natural, pct)
    }
}

@Composable
private fun resolveMessagePatterns(signals: RiskSignalsData?): String {
    if (signals == null || (signals.scamProb == null && signals.scamType.isNullOrBlank())) {
        return stringResource(R.string.signal_unavailable)
    }
    val type = signals.scamType.orEmpty()
    val prob = signals.scamProb ?: 0.0
    return when {
        type.isNotBlank() && type != "unknown" ->
            stringResource(R.string.signal_scam_detected, type, (prob * 100).toInt())
        prob > 0.5 ->
            stringResource(R.string.signal_scam_suspicious, (prob * 100).toInt())
        else ->
            stringResource(R.string.signal_scam_none)
    }
}

@Composable
private fun resolveVoiceprint(signals: RiskSignalsData?): String {
    if (signals == null || signals.voiceprintSimilarity == null) {
        return stringResource(R.string.signal_unavailable)
    }
    val sim = signals.voiceprintSimilarity
    val pct = (sim * 100).toInt()
    return if (signals.identityMismatch == true) {
        stringResource(R.string.signal_voiceprint_mismatch, pct)
    } else {
        stringResource(R.string.signal_voiceprint_match, pct)
    }
}
