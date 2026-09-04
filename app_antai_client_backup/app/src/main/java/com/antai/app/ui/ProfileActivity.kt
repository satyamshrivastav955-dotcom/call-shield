package com.antai.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.antai.app.AppContainer
import com.antai.app.databinding.ActivityProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Profile: display name, voiceprint enrollment entry, logout (MD 3.3). */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val name = AppContainer.prefs.displayName
            val phone = AppContainer.prefs.phone
            name.collect { binding.etName.setText(it ?: "") }
            phone.collect { binding.tvPhone.text = "Account: ${it ?: ""}" }
        }

        binding.btnEnrollVoice.setOnClickListener {
            startActivity(Intent(this, EnrollVoiceActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { AppContainer.api.updateProfile(name = name) }
                Toast.makeText(this@ProfileActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                AppContainer.prefs.clear()
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@ProfileActivity, OnboardingActivity::class.java))
                    finishAffinity()
                }
            }
        }
    }
}