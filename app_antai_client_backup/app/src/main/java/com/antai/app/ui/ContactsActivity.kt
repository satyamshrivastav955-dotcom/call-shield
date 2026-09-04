package com.antai.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.databinding.ActivityContactsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contacts: tap a contact for Call / Video call / Message / Enroll voice;
 * long-press to report the number as a scam (collective DB).
 */
class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var adapter: ContactsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ContactsAdapter(
            onTap = { c -> showContactActions(c) },
            onFlag = { c ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppRepository.flagNumber(c.phone)
                }
                Toast.makeText(this,
                    "Reported ${c.label.ifBlank { c.phone }} as a scam. Thank you for helping others.",
                    Toast.LENGTH_LONG).show()
            },
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        lifecycleScope.launch {
            AppContainer.db.contacts().observeAll().collect { adapter.submit(it) }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AppRepository.syncContacts() }
        }
    }

    private fun showContactActions(c: com.antai.app.data.local.ContactEntity) {
        val name = c.label.ifBlank { c.displayName }
        val options = arrayOf("📞  Call", "📹  Video call", "💬  Send message", "🎤  Enroll this person's voice")
        android.app.AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCall(c.phone, "voice")
                    1 -> startCall(c.phone, "video")
                    2 -> openChat(c.phone)
                    3 -> enrollVoice(c)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun startCall(phone: String, kind: String) {
        val i = Intent(this, CallActivity::class.java)
        i.putExtra("callee_phone", phone)
        i.putExtra("kind", kind)
        i.putExtra("is_caller", true)
        startActivity(i)
    }

    private fun openChat(phone: String) {
        val i = Intent(this, ChatActivity::class.java)
        i.putExtra("peer_phone", phone)
        startActivity(i)
    }

    private fun enrollVoice(c: com.antai.app.data.local.ContactEntity) {
        val i = Intent(this, EnrollVoiceActivity::class.java)
        i.putExtra("enroll_for_phone", c.phone)
        i.putExtra("enroll_for_name", c.label.ifBlank { c.displayName })
        startActivity(i)
    }
}