package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.theme.*

enum class ProtectionMode {
    SERVER_BACKED,
    ON_DEVICE,
    OFFLINE
}

/**
 * Persistent status chip indicating "On-device" / "Server-backed" / "Offline".
 * Tapping opens an explanation bottom sheet explaining local vs server analysis in Hinglish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskStatusChip(
    mode: ProtectionMode,
    modifier: Modifier = Modifier,
    showDetailsSheetOnTap: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }

    val (iconRes, fgColor, bgColor, label) = when (mode) {
        ProtectionMode.SERVER_BACKED -> Quad(
            R.drawable.ic_shield_check,
            AntaiTeal,
            AntaiTealContainer,
            stringResource(R.string.mode_server_backed)
        )
        ProtectionMode.ON_DEVICE -> Quad(
            R.drawable.ic_shield,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.surfaceVariant,
            stringResource(R.string.mode_on_device)
        )
        ProtectionMode.OFFLINE -> Quad(
            R.drawable.ic_warning,
            RiskCritical,
            RiskCriticalBg,
            stringResource(R.string.mode_offline)
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = showDetailsSheetOnTap) { showSheet = true },
        color = bgColor,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fgColor
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Protection Mode / सुरक्षा स्थिति",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "antAI analyzes calls and media to protect against voice clones and scams.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                ModeDetailCard(
                    title = "Server-backed (सर्वर आधारित)",
                    desc = "Full AI pipeline active on server: live deepfake detection (AASIST), Whisper speech-to-text transcript, LLM scam pattern analysis & voiceprint match.",
                    isActive = mode == ProtectionMode.SERVER_BACKED,
                    color = AntaiTeal
                )

                Spacer(Modifier.height(10.dp))

                ModeDetailCard(
                    title = "On-device (लोकल मोड)",
                    desc = "Fallback mode: local voice activity detection (VAD) & SMS guardian triage. Calls remain private and peer-to-peer.",
                    isActive = mode == ProtectionMode.ON_DEVICE,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(10.dp))

                ModeDetailCard(
                    title = "Offline (ऑफलाइन)",
                    desc = "Signaling or analysis server unreachable. Calls connect peer-to-peer without real-time AI scam inspection. Check host settings in Settings.",
                    isActive = mode == ProtectionMode.OFFLINE,
                    color = RiskCritical
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Got it / समझ गए")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModeDetailCard(
    title: String,
    desc: String,
    isActive: Boolean,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
