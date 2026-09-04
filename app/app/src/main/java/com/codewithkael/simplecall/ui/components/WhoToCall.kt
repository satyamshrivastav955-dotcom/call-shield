package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R

/**
 * Home card for placing a call. Restyle only — the call action
 * (onCallClick with the entered id + voice/video flag) is unchanged.
 */
@Composable
fun WhoToCall(
    onCallClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetUsername = remember { mutableStateOf("") }
    val isVoiceCall = remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp, 5.dp, 16.dp, 0.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "New call",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = targetUsername.value,
                onValueChange = { targetUsername.value = it },
                label = { Text("Enter an ID to call") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // call-mode toggle: video / voice (audio only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isVoiceCall.value,
                    onClick = { isVoiceCall.value = false },
                    label = { Text("Video") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isVoiceCall.value,
                    onClick = { isVoiceCall.value = true },
                    label = { Text("Voice") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (targetUsername.value.isNotEmpty()) {
                        onCallClick(targetUsername.value, isVoiceCall.value)
                    }
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (isVoiceCall.value) "Start voice call" else "Start video call",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
