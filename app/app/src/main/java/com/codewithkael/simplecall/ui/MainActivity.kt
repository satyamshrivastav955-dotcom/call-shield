package com.codewithkael.simplecall.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.codewithkael.simplecall.notifications.RiskNotificationManager
import com.codewithkael.simplecall.ui.screens.HomeScreen
import com.codewithkael.simplecall.ui.theme.SimpleCallTheme
import com.codewithkael.simplecall.utils.SimpleCallApplication
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var riskNotificationManager: RiskNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // P1.7: Create the antai_risk_alerts notification channel (idempotent on re-creates).
        // Channel must exist before the app can post any notification on Android 8+.
        riskNotificationManager.createChannels()

        handleSendIntent(intent)

        enableEdgeToEdge()
        setContent {
            SimpleCallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // App came to foreground — dismiss any lingering risk notification (user is
        // already looking at the live call screen / home screen).
        riskNotificationManager.dismissRiskNotification()
        SimpleCallApplication.isForegrounded = true
    }

    override fun onPause() {
        super.onPause()
        SimpleCallApplication.isForegrounded = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            val audioUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.data
            audioUri?.let { SimpleCallApplication.sharedIncomingAudio.value = it }
        }
    }
}
