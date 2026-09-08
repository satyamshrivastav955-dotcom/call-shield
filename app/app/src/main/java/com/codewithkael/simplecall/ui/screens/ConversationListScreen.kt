package com.codewithkael.simplecall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ai.TextEngines
import com.codewithkael.simplecall.remote.antai.Conversation
import com.codewithkael.simplecall.ui.components.RiskBadge
import com.codewithkael.simplecall.ui.components.isNoteworthy
import com.codewithkael.simplecall.ui.viewmodel.MessagesViewModel
import com.codewithkael.simplecall.utils.TimeFormat

/**
 * Unified conversation list (SMS + antAI in-app chat merged per contact).
 * WhatsApp-style rows; Truecaller-style risk badge on any risky thread.
 */
@Composable
fun ConversationListScreen(
    vm: MessagesViewModel,
    onOpenThread: (String) -> Unit,
    onSignIn: () -> Unit = {}
) {
    val conversations by vm.conversations.collectAsState()
    val loggedIn by vm.loggedIn.collectAsState()
    var showNew by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Optional server features banner. Offline scanning is already active
        // below — this is an upgrade path, never a blocker.
        if (!loggedIn) {
            SignInBanner(onSignIn = onSignIn)
        }

        Box(Modifier.weight(1f)) {
            if (conversations.isEmpty()) {
                EmptyMessages()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(conversations, key = { it.peerPhone }) { convo ->
                        ConversationRow(convo) { onOpenThread(convo.peerPhone) }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 84.dp)
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { showNew = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(painterResource(R.drawable.ic_message), contentDescription = "New message")
            }
        }
    }

    if (showNew) {
        NewConversationDialog(
            onDismiss = { showNew = false },
            onStart = { phone ->
                showNew = false
                if (phone.isNotBlank()) onOpenThread(phone.trim())
            }
        )
    }
}

@Composable
private fun SignInBanner(onSignIn: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "SMS scam scan is ON (on-device). Sign in for in-app chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSignIn, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Sign in")
            }
        }
    }
}

@Composable
private fun ConversationRow(convo: Conversation, onClick: () -> Unit) {
    // Offline scan (Bug 1): even without sign-in/server, the last message is
    // scored on-device so a risky thread still shows a badge. Labeled "local"
    // so provenance stays honest (server verdicts use the normal RiskBadge).
    val offlineVerdict = remember(convo.lastMessage?.body) {
        TextEngines.scamHeuristic(convo.lastMessage?.body ?: "")
    }
    val offlineRisky = offlineVerdict.prob >= 0.55f
    // Provenance for the server-band badge: only label "on-device" when the
    // worst band is driven SOLELY by locally-scored messages (Bug 1 fallback).
    // A server verdict at that band leaves it unlabeled.
    val worstOnDevice = remember(convo.messages) {
        val wb = convo.worstBand
        val atWorst = convo.messages.filter { it.band == wb }
        atWorst.isNotEmpty() && atWorst.all { it.onDevice }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = convo.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (convo.worstBand.isNoteworthy()) {
                    Spacer(Modifier.width(6.dp))
                    RiskBadge(band = convo.worstBand, compact = true)
                    if (worstOnDevice) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "· on-device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else if (offlineRisky) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "⚠ ${offlineVerdict.type ?: "scam"}? · on-device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = convo.lastMessage?.body ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = TimeFormat.shortStamp(convo.lastTimestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyMessages() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_message),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No conversations yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Grant SMS access so your texts appear here and get scanned for scams on-device. " +
                "No account needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun NewConversationDialog(onDismiss: () -> Unit, onStart: (String) -> Unit) {
    val phone = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            OutlinedTextField(
                value = phone.value,
                onValueChange = { phone.value = it },
                label = { Text("Phone number") },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
        },
        confirmButton = { TextButton(onClick = { onStart(phone.value) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
