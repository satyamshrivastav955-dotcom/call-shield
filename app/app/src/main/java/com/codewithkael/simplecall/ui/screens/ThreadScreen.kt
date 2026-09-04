package com.codewithkael.simplecall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.ChatMessage
import com.codewithkael.simplecall.remote.antai.MessageChannel
import com.codewithkael.simplecall.remote.antai.RiskBand
import com.codewithkael.simplecall.ui.components.RiskBadge
import com.codewithkael.simplecall.ui.components.isNoteworthy
import com.codewithkael.simplecall.ui.theme.BubbleOutgoing
import com.codewithkael.simplecall.ui.viewmodel.MessagesViewModel
import com.codewithkael.simplecall.utils.TimeFormat

/**
 * One conversation. WhatsApp-style bubbles; each incoming message carries the
 * antAI server verdict (risk badge + expandable "why / what to do"). Composer
 * can send a real SMS or an analyzed antAI in-app message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    vm: MessagesViewModel,
    peerPhone: String,
    onBack: () -> Unit
) {
    val conversations by vm.conversations.collectAsState()
    val convo = remember(conversations, peerPhone) {
        conversations.firstOrNull { it.peerPhone == peerPhone || it.peerPhone == peerPhone.trim() }
    }
    val messages = convo?.messages ?: emptyList()
    val draft = remember { mutableStateOf("") }
    val viaSms = remember { mutableStateOf(true) }
    val expanded = remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState()

    LaunchedEffect(peerPhone) { vm.openThread(peerPhone) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            convo?.displayName ?: peerPhone,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Protected by antAI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        isExpanded = msg.id in expanded.value,
                        onToggle = {
                            expanded.value = expanded.value.toMutableSet().apply {
                                if (contains(msg.id)) remove(msg.id) else add(msg.id)
                            }
                        }
                    )
                }
            }

            Composer(
                draft = draft.value,
                onDraftChange = { draft.value = it },
                viaSms = viaSms.value,
                onToggleChannel = { viaSms.value = !viaSms.value },
                onSend = {
                    val body = draft.value.trim()
                    if (body.isNotEmpty()) {
                        vm.sendMessage(peerPhone, body, viaSms.value)
                        draft.value = ""
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isExpanded: Boolean, onToggle: () -> Unit) {
    val alignment = if (msg.outgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.outgoing) BubbleOutgoing else MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (msg.outgoing) 16.dp else 4.dp,
        bottomEnd = if (msg.outgoing) 4.dp else 16.dp
    )
    val riskyIncoming = !msg.outgoing && msg.band.isNoteworthy()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = if (msg.outgoing) 0.dp else 1.dp,
            border = if (msg.outgoing) null
            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = msg.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (msg.channel == MessageChannel.SMS) {
                        Text(
                            "SMS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        TimeFormat.clock(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // verdict area under risky incoming messages
        if (msg.band == RiskBand.PENDING && !msg.outgoing) {
            Text(
                "Checking for scams…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
        } else if (riskyIncoming) {
            Column(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clickable(onClick = onToggle),
                horizontalAlignment = Alignment.Start
            ) {
                RiskBadge(band = msg.band)
                if (isExpanded) {
                    msg.verdict?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    msg.why?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    msg.action?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "Do now: $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } else {
                    Text(
                        "Tap for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    viaSms: Boolean,
    onToggleChannel: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // channel toggle: real SMS vs analyzed antAI message
            AssistChip(
                onClick = onToggleChannel,
                label = { Text(if (viaSms) "SMS" else "antAI") },
                leadingIcon = {
                    Icon(
                        painterResource(if (viaSms) R.drawable.ic_sms else R.drawable.ic_shield_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.padding(end = 6.dp)
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(if (viaSms) "Text message" else "Analyzed message") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(24.dp))
            ) {
                Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
