package com.codewithkael.simplecall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.ui.viewmodel.LoginPhase
import com.codewithkael.simplecall.ui.viewmodel.MessagesViewModel

/**
 * Phone-OTP login for the messaging identity. Pure UI over MessagesViewModel;
 * the calling stack is unaffected (it uses a separate random USER_ID).
 */
@Composable
fun PhoneLoginScreen(vm: MessagesViewModel, onClose: () -> Unit = {}) {
    val state by vm.login.collectAsState()
    val phone = remember { mutableStateOf("") }
    val otp = remember { mutableStateOf("") }
    val name = remember { mutableStateOf("") }

    // Success: leave the login sub-screen back to the conversation list.
    LaunchedEffect(state.phase) {
        if (state.phase == LoginPhase.DONE) onClose()
    }
    // A previously-saved session can be logged out from here; leave on that too.
    val loggedIn by vm.loggedIn.collectAsState()
    LaunchedEffect(loggedIn) {
        if (loggedIn) onClose()
    }

    // prefill dev OTP when the server returns it (auto-verify mode)
    LaunchedEffect(state.devOtp) {
        if (!state.devOtp.isNullOrBlank() && otp.value.isBlank()) otp.value = state.devOtp!!
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Always-available escape hatch: sign-in is optional, never a dead end.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back to messages"
                )
            }
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_message),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Secure messages",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Optional: sign in with your phone number for in-app chat. SMS scam scanning already works on-device without this.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(28.dp))

        if (state.phase == LoginPhase.ENTER_PHONE) {
            OutlinedTextField(
                value = phone.value,
                onValueChange = { phone.value = it },
                label = { Text("Phone number") },
                placeholder = { Text("+1 555 010 0000") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.requestOtp(phone.value) },
                enabled = !state.loading,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.loading) LoadingDot() else Text("Send code", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            OutlinedTextField(
                value = otp.value,
                onValueChange = { otp.value = it },
                label = { Text("Verification code") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { Text("Your name (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.debugOffline) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Offline debug sign-in: code ${state.devOtp} creates a LOCAL debug " +
                        "identity only — it is not verified by any server.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!state.devOtp.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Dev mode: code ${state.devOtp} prefilled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.verifyOtp(otp.value, name.value) },
                enabled = !state.loading,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.loading) LoadingDot() else Text("Verify & continue", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.backToPhone() }) { Text("Use a different number") }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoadingDot() {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.onPrimary,
        strokeWidth = 2.dp,
        modifier = Modifier.size(20.dp)
    )
}
