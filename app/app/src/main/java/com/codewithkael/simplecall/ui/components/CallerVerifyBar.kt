package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.CallerVerification

/**
 * In-call control for verifying who the caller claims to be.
 *
 * The expected scam is an unknown caller claiming to be someone trusted,
 * so the number challenged is the claimed identity (editable), not necessarily
 * the caller ID. Pure presentation — renders state and reports taps.
 */
@Composable
fun CallerVerifyBar(
    state: CallerVerification?,
    defaultPhone: String,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var askOpen by remember { mutableStateOf(false) }

    if (askOpen) {
        ClaimedContactDialog(
            defaultPhone = defaultPhone,
            onCancel = { askOpen = false },
            onConfirm = { phone ->
                askOpen = false
                onVerify(phone)
            }
        )
    }

    when {
        state == null -> OutlinedButton(
            onClick = { askOpen = true },
            shape = MaterialTheme.shapes.small,
            modifier = modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Verify caller identity",
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        else -> Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val headline = when {
                    state.sending -> "Sending verification request…"
                    state.error.isNotBlank() -> "Could not send request"
                    state.state == "confirmed" -> "Confirmed — this is them"
                    state.state == "denied" -> "Not them — caller denied"
                    state.state == "timeout" -> "No answer — still unverified"
                    state.state == "pending" -> "Waiting for ${state.peerPhone}"
                    else -> "Verification requested"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                when (state.state) {
                                    "confirmed" -> R.drawable.ic_verified
                                    "denied" -> R.drawable.ic_warning
                                    else -> R.drawable.ic_shield
                                }
                            ),
                            contentDescription = null,
                            tint = when (state.state) {
                                "confirmed" -> MaterialTheme.colorScheme.primary
                                "denied" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.state == "denied") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                val detail = state.error.ifBlank { state.message }
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.state == "timeout") {
                    Text(
                        "Treat this call as unverified. Hang up and call them back on their saved number.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.sending && state.state != "pending") {
                        TextButton(onClick = { askOpen = true }) { Text("Ask again") }
                    }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun ClaimedContactDialog(
    defaultPhone: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var phone by remember { mutableStateOf(defaultPhone) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Verify caller identity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "We'll ask that contact directly to confirm they are on this call. " +
                        "Enter the number of the person the caller claims to be, not the number calling you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    singleLine = true,
                    label = { Text("Contact number") },
                    placeholder = { Text("e.g. +91 98765 43210") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { if (phone.isNotBlank()) onConfirm(phone.trim()) },
                    enabled = phone.isNotBlank(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send verification request")
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
