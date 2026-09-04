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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codewithkael.simplecall.remote.antai.VerifyPrompt

/**
 * Prompt shown to a trusted contact: someone is on a call claiming to be them.
 * The answer is time-sensitive — a plain two-choice dialog with no distraction.
 */
@Composable
fun VerifyPromptDialog(
    prompt: VerifyPrompt,
    onAnswer: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val answered = prompt.answered

    Dialog(onDismissRequest = { if (!prompt.answering) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (answered) {
                    null -> {
                        Text(
                            "Verification request",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            prompt.question.ifBlank {
                                "Someone is on a call claiming to be you. Are you on that call right now?"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "One of your contacts needs to know whether to trust this caller. Your answer is sent immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (prompt.error.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    prompt.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Button(
                                onClick = onDismiss,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close")
                            }
                        } else if (prompt.answering) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Sending…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onAnswer(false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    prompt.options.getOrNull(1) ?: "No, that's not me",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(
                                onClick = { onAnswer(true) },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(prompt.options.getOrNull(0) ?: "Yes, it's me")
                            }
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }

                    false -> AnswerReceipt(
                        headline = "You declined — contact warned",
                        body = "Your contact has been told the caller is not you. If this was unexpected, someone may be impersonating you.",
                        onDismiss = onDismiss
                    )

                    true -> AnswerReceipt(
                        headline = "Confirmed — it's you",
                        body = "Your contact can continue the call.",
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerReceipt(
    headline: String,
    body: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Button(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Close")
    }
}
