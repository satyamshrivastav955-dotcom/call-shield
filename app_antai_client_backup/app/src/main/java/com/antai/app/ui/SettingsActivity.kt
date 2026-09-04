package com.antai.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.antai.app.AppContainer
import com.antai.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings: server URL override (dev). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            AppContainer.prefs.baseUrl.collect { binding.etBaseUrl.setText(it) }
        }
        binding.btnSave.setOnClickListener {
            val url = binding.etBaseUrl.text.toString().trim()
            lifecycleScope.launch(Dispatchers.IO) {
                AppContainer.prefs.setBaseUrl(url.ifBlank { com.antai.app.data.local.AuthPrefs.DEFAULT_BASE })
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Saved. Reconnect on next launch.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnNotifications.setOnClickListener {
            // opens the system screen where the user grants notification access
            runCatching {
                startActivity(android.content.Intent(
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }.onFailure {
                Toast.makeText(this, "Open Settings -> Notifications -> Notification access",
                    Toast.LENGTH_LONG).show()
            }
        }
        binding.btnTestNotify.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    AppContainer.api.externalNotify(
                        "whatsapp", "+911234567890",
                        "Your bank account is frozen! Send 20000 to this account immediately, do not tell anyone!")
                }.onSuccess { r ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity,
                            "Test sent. Verdict: ${r.optJSONObject("verdict")?.optString("verdict") ?: "see chat"}",
                            Toast.LENGTH_LONG).show()
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity,
                            "Test failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}