package com.codewithkael.simplecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R

/**
 * Home-screen card shown before connecting. Lets the user enter the LAN IP of the
 * laptop running the Node signaling backend, then connect. The value is persisted by
 * the ViewModel, so on a home Wi-Fi you typically set it once.
 *
 * Restyle only: the connect flow (onConnect + trimming) is unchanged.
 */
@Composable
fun ServerConfig(
    initialHost: String,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val host = remember { mutableStateOf(initialHost) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp, 12.dp, 16.dp, 0.dp),
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Connect your device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Enter the server address to enable call protection. Your administrator will provide this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = host.value,
                onValueChange = { host.value = it },
                label = { Text("Server address") },
                placeholder = { Text("192.168.1.10") },
                supportingText = { Text("Found in the server console on port 3007") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { if (host.value.isNotBlank()) onConnect(host.value.trim()) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Connect", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
