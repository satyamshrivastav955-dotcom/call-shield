package com.codewithkael.simplecall.ui.components

import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.theme.AntaiGreen
import com.codewithkael.simplecall.ui.theme.RiskCritical
import com.codewithkael.simplecall.utils.Constants.TIME_OUT_DURATION_MS
import kotlinx.coroutines.delay

/**
 * Incoming-call card. Restyle only — the ringtone playback, timeout delay,
 * and accept/reject/timeout callbacks are unchanged.
 */
@Composable
fun IncomingCallSnackBar(
    callerId: String,
    onTimeout: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSnackBar by remember { mutableStateOf(true) }
    val ringtone = remember {
        RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
    }

    if (showSnackBar) {
        // Play ringtone when the snackbar is shown
        LaunchedEffect(Unit) {
            ringtone.play()
            delay(TIME_OUT_DURATION_MS) // Wait for 5 seconds
            if (showSnackBar) {
                showSnackBar = false
                ringtone.stop()
                onTimeout() // Trigger the timeout callback
            }
        }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Incoming call",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Caller avatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = callerId,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reject
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                ringtone.stop()
                                showSnackBar = false
                                onReject(callerId) // Trigger the reject callback
                            },
                            modifier = Modifier
                                .size(60.dp)
                                .background(RiskCritical, CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_end_call),
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Decline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Accept
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                ringtone.stop()
                                showSnackBar = false
                                onAccept(callerId) // Trigger the accept callback
                            },
                            modifier = Modifier
                                .size(60.dp)
                                .background(AntaiGreen, CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_call),
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Accept",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
