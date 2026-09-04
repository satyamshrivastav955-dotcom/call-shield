package com.codewithkael.simplecall.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.codewithkael.simplecall.R
import com.codewithkael.simplecall.remote.antai.RiskBand
import com.codewithkael.simplecall.ui.components.VerifyPromptDialog
import com.codewithkael.simplecall.ui.viewmodel.MainViewModel
import com.codewithkael.simplecall.ui.viewmodel.MessagesViewModel
import com.codewithkael.simplecall.utils.ConnectionState

private enum class HomeTab { CALLS, MESSAGES, GUARD, INCIDENTS, SETTINGS }

/**
 * Top-level shell hosting three tabs via simple state switching (no NavHost, to
 * keep the change surface minimal and well away from the call stack):
 *
 *  - Calls    -> the existing [MainScreen] (WebRTC calling, completely untouched)
 *  - Messages -> unified SMS + antAI chat (login-gated)
 *  - Guard    -> notification scanning feed (login-gated)
 *
 * Calling always takes over the whole screen exactly like the original single-
 * screen app: when a call is incoming/active we force the Calls tab and hide the
 * bottom bar. This only *reads* the call state — it never drives call logic.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val messagesVm: MessagesViewModel = hiltViewModel()

    // Same activity-scoped MainViewModel instance MainScreen uses; read-only here.
    val mainVm: MainViewModel = hiltViewModel()
    val callState by mainVm.connectionState.collectAsState()
    val onCall = callState is ConnectionState.CallingTarget ||
        callState is ConnectionState.OnCall ||
        callState is ConnectionState.ReceivedCall

    val loggedIn by messagesVm.loggedIn.collectAsState()
    val conversations by messagesVm.conversations.collectAsState()
    val notifications by messagesVm.notifications.collectAsState()
    val verifyPrompt by messagesVm.verifyPrompt.collectAsState()

    var tab by rememberSaveable { mutableStateOf(HomeTab.CALLS) }
    var openThread by rememberSaveable { mutableStateOf<String?>(null) }
    // Voice registration is a sub-screen of Guard rather than a fourth tab: it is
    // set up once and then only revisited occasionally, so it doesn't earn
    // permanent space in the bottom bar.
    var showVoiceprint by rememberSaveable { mutableStateOf(false) }

    // A call must always be visible: pull the user to the Calls tab (which hosts
    // the incoming-call UI and the live call surface) whenever one is happening.
    LaunchedEffect(onCall) {
        if (onCall) {
            tab = HomeTab.CALLS
            openThread = null
            // Leaving the recorder open behind a call screen would hold the mic.
            showVoiceprint = false
        }
    }

    // ----- SMS / notification permission onboarding (messaging only) -----
    val smsPermissions = remember {
        buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_SMS] == true) messagesVm.onSmsPermissionGranted()
    }
    var askedSms by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(loggedIn, tab) {
        val messaging = tab == HomeTab.MESSAGES || tab == HomeTab.GUARD
        if (loggedIn && messaging && !askedSms) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) messagesVm.onSmsPermissionGranted() else permLauncher.launch(smsPermissions)
            askedSms = true
        }
    }

    // System back inside a thread returns to the conversation list.
    BackHandler(enabled = tab == HomeTab.MESSAGES && openThread != null) {
        openThread = null
    }
    BackHandler(enabled = tab == HomeTab.GUARD && showVoiceprint) {
        showVoiceprint = false
    }

    val riskyConvos = conversations.count {
        it.worstBand == RiskBand.CRITICAL || it.worstBand == RiskBand.CAUTION
    }
    val riskyNotifs = notifications.count {
        it.band == RiskBand.CRITICAL || it.band == RiskBand.CAUTION
    }

    Scaffold(
        bottomBar = {
            if (!onCall) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == HomeTab.CALLS,
                        onClick = { tab = HomeTab.CALLS },
                        icon = { Icon(painterResource(R.drawable.ic_call), contentDescription = null) },
                        label = { Text("Calls") },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.MESSAGES,
                        onClick = { tab = HomeTab.MESSAGES },
                        icon = { BadgedIcon(R.drawable.ic_message, riskyConvos) },
                        label = { Text("Messages") },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.GUARD,
                        // Re-tapping the tab returns to the feed, so the voice
                        // sub-screen can't feel like a dead end.
                        onClick = {
                            if (tab == HomeTab.GUARD) showVoiceprint = false
                            tab = HomeTab.GUARD
                        },
                        icon = { BadgedIcon(R.drawable.ic_shield_check, riskyNotifs) },
                        label = { Text("Guard") },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.INCIDENTS,
                        onClick = { tab = HomeTab.INCIDENTS },
                        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
                        label = { Text("Incidents") },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == HomeTab.SETTINGS,
                        onClick = { tab = HomeTab.SETTINGS },
                        icon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null) },
                        label = { Text("Settings") },
                        colors = navItemColors()
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                HomeTab.CALLS -> MainScreen()

                HomeTab.MESSAGES -> {
                    if (!loggedIn) {
                        PhoneLoginScreen(messagesVm)
                    } else {
                        val peer = openThread
                        if (peer == null) {
                            ConversationListScreen(
                                vm = messagesVm,
                                onOpenThread = { openThread = it }
                            )
                        } else {
                            ThreadScreen(
                                vm = messagesVm,
                                peerPhone = peer,
                                onBack = { openThread = null }
                            )
                        }
                    }
                }

                HomeTab.GUARD -> {
                    if (!loggedIn) PhoneLoginScreen(messagesVm)
                    else if (showVoiceprint) VoiceprintScreen(onBack = { showVoiceprint = false })
                    else NotificationGuardScreen(
                        vm = messagesVm,
                        onOpenVoiceprint = { showVoiceprint = true }
                    )
                }

                HomeTab.INCIDENTS -> IncidentHistoryScreen()

                HomeTab.SETTINGS -> SettingsScreen()
            }

            // Someone is impersonating this user on a call with one of their
            // contacts. It must interrupt whatever tab they're on — the person on
            // the other call is waiting on this answer, so it sits at shell level
            // rather than inside any one screen.
            verifyPrompt?.let { prompt ->
                VerifyPromptDialog(
                    prompt = prompt,
                    onAnswer = { messagesVm.answerVerifyPrompt(it) },
                    onDismiss = { messagesVm.dismissVerifyPrompt() }
                )
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun BadgedIcon(iconRes: Int, count: Int) {
    if (count > 0) {
        androidx.compose.material3.BadgedBox(
            badge = {
                androidx.compose.material3.Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) { Text(if (count > 9) "9+" else count.toString()) }
            }
        ) {
            Icon(painterResource(iconRes), contentDescription = null)
        }
    } else {
        Icon(painterResource(iconRes), contentDescription = null)
    }
}
