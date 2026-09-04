package com.antai.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.data.local.ContactEntity
import com.antai.app.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home: WhatsApp-style chat list + new-chat FAB. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var convAdapter: ConversationsAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsIfNeeded()

        convAdapter = ConversationsAdapter(onTap = { openChat(it.peerPhone) })
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = convAdapter

        binding.fabNewChat.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        binding.ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.ivHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        lifecycleScope.launch {
            AppContainer.db.messages().observeConversations().collect { convs ->
                convAdapter.submit(convs)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AppContainer.ws.connect() }
            runCatching { AppRepository.syncContacts() }
            runCatching { AppRepository.flushQueue() }
        }
        startRealtimeService()

        wireRealtime()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AppContainer.ws.connect() }
        }
        // Claim the single-owner router while this screen is visible so inbound
        // calls/verdicts/messages are handled in-app (and saved to history)
        // rather than by the background notification handlers.
        com.antai.app.realtime.RealtimeService.onActivityResumed()
        wireRealtime()
    }

    override fun onPause() {
        super.onPause()
        // When no realtime screen remains visible this hands the alerting
        // callbacks back to the background notification path (full-screen intent
        // for calls, etc.). onResume re-claims them when we come back.
        com.antai.app.realtime.RealtimeService.onActivityPaused(this)
    }

    private fun startRealtimeService() {
        try {
            val i = Intent(this, com.antai.app.realtime.RealtimeService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        } catch (e: Exception) {
            // already running or permissions not granted yet
        }
    }

    private fun openChats() {
        startActivity(Intent(this, ContactsActivity::class.java))
    }

    private fun openChat(peerPhone: String) {
        val i = Intent(this, ChatActivity::class.java)
        i.putExtra("peer_phone", peerPhone)
        startActivity(i)
    }

    private fun wireRealtime() {
        val router = AppContainer.router
        router.onCallIncoming = { msg ->
            runOnUiThread {
                val i = Intent(this, IncomingCallActivity::class.java)
                i.putExtra("session_key", msg.optString("session_key"))
                i.putExtra("kind", msg.optString("kind", "voice"))
                i.putExtra("offer", msg.optJSONObject("offer")?.toString().orEmpty())
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(i)
            }
        }
        router.onChatRecv = { msg ->
            lifecycleScope.launch(Dispatchers.IO) {
                val peer = msg.optString("sender_phone")
                    .ifBlank { "contact:${msg.optInt("sender_id")}" }
                AppRepository.receiveMessage(peer, msg.optString("body"), msg.optInt("sender_id"))
            }
        }
        router.onVerdict = { v ->
            lifecycleScope.launch(Dispatchers.IO) {
                AppRepository.saveVerdict(v, v.optString("kind", "live"), v.optString("session_key"))
            }
        }
        router.onVerifyPrompt = { m ->
            runOnUiThread { VerifyDialogs.show(this, m, lifecycleScope) }
        }
        router.onFreeze = { f ->
            runOnUiThread { FreezeDialogs.show(this, f, lifecycleScope) }
        }
        router.onReportReady = { r ->
            runOnUiThread {
                Toast.makeText(this,
                    "Your report is ready: ${r.optString("title")}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}