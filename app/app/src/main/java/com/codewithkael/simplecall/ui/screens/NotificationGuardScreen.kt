package com.codewithkael.simplecall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.notifications.NotificationAccess
import com.codewithkael.simplecall.remote.antai.NotificationEvent
import com.codewithkael.simplecall.remote.antai.RiskBand
import com.codewithkael.simplecall.ui.components.RiskBadge
import com.codewithkael.simplecall.ui.viewmodel.MessagesViewModel
import com.codewithkael.simplecall.ui.viewmodel.VoiceprintViewModel
import com.codewithkael.simplecall.utils.TimeFormat

/**
 * Notification Guard — feed of notifications from other apps (WhatsApp, Telegram,
 * bank apps, …) captured by the listener service and scored by the antAI server.
 * Shows a permission banner until notification access is granted.
 *
 * Also the home for the protections that need setting up rather than watching:
 * registering the user's own voice, which is what lets an AI-voice alert be
 * cross-checked against the real person.
 */
@Composable
fun NotificationGuardScreen(
    vm: MessagesViewModel,
    onOpenVoiceprint: () -> Unit = {},
    onOpenShield: () -> Unit = {},
) {
    val context = LocalContext.current
    val notifications by vm.notifications.collectAsState()

    // Re-check access whenever this screen recomposes after returning from settings.
    var accessGranted by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }
    LaunchedEffect(Unit) { accessGranted = NotificationAccess.isEnabled(context) }

    Column(Modifier.fillMaxSize()) {
        if (!accessGranted) {
            AccessBanner(
                onEnable = {
                    NotificationAccess.openSettings(context)
                }
            )
        }

        VoiceprintEntryCard(onOpen = onOpenVoiceprint)

        com.codewithkael.simplecall.shield.ShieldEntryCard(onOpen = onOpenShield)

        if (notifications.isEmpty()) {
            EmptyGuard(accessGranted)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(notifications, key = { it.id }) { event ->
                    NotificationCard(event)
                }
            }
        }
    }
}

/**
 * Entry point to voice registration, showing live enrolment state so the gap is
 * visible instead of hidden behind a tap: an unregistered voice means the AI-voice
 * cross-check has nothing to compare against, and the user should know that here.
 *
 * Uses the same activity-scoped [VoiceprintViewModel] the screen itself uses, so
 * the state shown on this card and on that screen can never disagree.
 */
@Composable
private fun VoiceprintEntryCard(onOpen: () -> Unit) {
    val vpVm: VoiceprintViewModel = hiltViewModel()
    val ui by vpVm.ui.collectAsState()
    val enrolled = ui.status.enrolled

    Surface(
        color = if (enrolled) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enrolled) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onOpen() }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(
                    if (enrolled) R.drawable.ic_verified else R.drawable.ic_mic_on
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    if (enrolled) "Your voice is registered" else "Register your voice",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enrolled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (enrolled)
                        "${ui.status.count} sample${if (ui.status.count == 1) "" else "s"} stored — " +
                            "suspicious callers can be checked against your real voice."
                    else
                        "Record a few seconds so antAI can tell a cloned version of your " +
                            "voice apart from the real you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enrolled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (!enrolled) {
                Button(onClick = onOpen, shape = MaterialTheme.shapes.small) { Text("Record") }
            }
        }
    }
}

@Composable
private fun AccessBanner(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    "Turn on notification scanning",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Let antAI read notifications from your other apps to flag scam messages as they arrive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Button(onClick = onEnable, shape = MaterialTheme.shapes.small) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun NotificationCard(event: NotificationEvent) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_message),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = event.sender.ifBlank { event.source },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = event.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = TimeFormat.shortStamp(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = event.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (event.band != RiskBand.SAFE) {
                Spacer(Modifier.height(8.dp))
                RiskBadge(band = event.band)
                if (expanded) {
                    event.verdict?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    event.why?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGuard(accessGranted: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Notification Guard is watching",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (accessGranted)
                "Scanned messages from your other apps will appear here when something looks risky."
            else
                "Enable notification access above so antAI can scan messages from your other apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
