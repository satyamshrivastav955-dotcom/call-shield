package com.antai.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.databinding.ActivityChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app messaging with real-time protection: messages are scanned at send
 * time; high-risk messages get the verdict card + freeze holds on the exact
 * action (OTP share, money transfer, link, etc.) (MD 3.2 / 4.6).
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessagesAdapter
    private var peerPhone = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peerPhone = intent.getStringExtra("peer_phone") ?: return finish()
        binding.tvTitle.text = peerPhone

        adapter = MessagesAdapter()
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter

        lifecycleScope.launch {
            AppContainer.db.messages().observeThread(peerPhone).collect { msgs ->
                adapter.submit(msgs)
                binding.rvMessages.scrollToPosition(msgs.size - 1)
            }
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            binding.etInput.setText("")
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    AppRepository.sendMessage(peerPhone, text)
                }
                // Show what the server found on THIS message immediately - don't
                // rely solely on the WS verdict push, which targets the recipient.
                result.verdict?.let { v ->
                    if (v.optString("verdict").isNotBlank()) {
                        binding.cardVerdict.visibility = View.VISIBLE
                        binding.tvVerdict.visibility = View.VISIBLE
                        VerdictCard.bind(binding.tvVerdict, v)
                    }
                }
                result.freeze?.let { f -> FreezeDialogs.show(this@ChatActivity, f, lifecycleScope) }
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnCall.setOnClickListener {
            val i = Intent(this, CallActivity::class.java)
            i.putExtra("callee_phone", peerPhone)
            i.putExtra("kind", "voice")
            i.putExtra("is_caller", true)
            startActivity(i)
        }
        binding.btnVideoCall.setOnClickListener {
            val i = Intent(this, CallActivity::class.java)
            i.putExtra("callee_phone", peerPhone)
            i.putExtra("kind", "video")
            i.putExtra("is_caller", true)
            startActivity(i)
        }

        wireRealtime()
    }

    override fun onResume() {
        super.onResume()
        // The realtime router is a single-owner dispatcher shared with Home/Call
        // and the background service. Re-claim our handlers whenever this screen
        // comes back to the foreground so a previously-opened screen can't keep
        // swallowing this chat's verdict/guidance/message events.
        com.antai.app.realtime.RealtimeService.onActivityResumed()
        wireRealtime()
    }

    override fun onPause() {
        super.onPause()
        // Release the alerting callbacks back to the background notification path
        // when no realtime screen remains visible; onResume re-claims them.
        com.antai.app.realtime.RealtimeService.onActivityPaused(this)
    }

    private fun wireRealtime() {
        val router = AppContainer.router
        router.onVerdict = { v ->
            runOnUiThread {
                if (v.optString("session_key").contains(peerPhone) ||
                    v.optString("kind") == "message"
                ) {
                    binding.cardVerdict.visibility = View.VISIBLE
                    binding.tvVerdict.visibility = View.VISIBLE
                    VerdictCard.bind(binding.tvVerdict, v)
                }
            }
        }
        router.onGuidance = { g ->
            runOnUiThread {
                val txt = g.optString("guidance")
                if (txt.isNotBlank()) {
                    binding.cardVerdict.visibility = View.VISIBLE
                    binding.tvVerdict.text = "⚠️ $txt"
                    binding.tvVerdict.setBackgroundColor(0xFFD97706.toInt())
                }
            }
        }
        router.onFreeze = { f ->
            runOnUiThread {
                FreezeDialogs.show(this, f, lifecycleScope)
            }
        }
        router.onChatRecv = { msg ->
            // File under the ACTUAL sender, not whichever chat happens to be open.
            // Previously every inbound message was saved to peerPhone, so a message
            // from someone else arriving while this chat was open got misfiled into
            // this thread. observeThread(peerPhone) still surfaces this peer's own
            // messages (sender == peerPhone); others now route to their own thread.
            val sender = msg.optString("sender_phone")
                .ifBlank { "contact:${msg.optInt("sender_id")}" }
            lifecycleScope.launch(Dispatchers.IO) {
                AppRepository.receiveMessage(sender, msg.optString("body"), msg.optInt("sender_id"))
            }
        }
    }
}