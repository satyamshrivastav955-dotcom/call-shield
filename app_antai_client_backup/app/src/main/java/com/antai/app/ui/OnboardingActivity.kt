package com.antai.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.antai.app.AppContainer
import com.antai.app.databinding.ActivityOnboardingBinding
import com.antai.app.realtime.RealtimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MVP onboarding (Truecaller-style): phone + name + consent -> one tap.
 * Dev server auto-verifies any 6-digit code (auto_verify_otp=true).
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val scope = CoroutineScope(Dispatchers.Main)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show the address the app will actually contact, so a misconfigured
        // server URL is obvious before the user even taps Continue.
        scope.launch(Dispatchers.IO) {
            val current = AppContainer.prefs.currentBaseUrl()
            withContext(Dispatchers.Main) { binding.tvServerUrl.text = "server: $current" }
        }

        binding.btnContinue.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            if (!phone.matches(Regex("^\\+?[0-9]{8,15}$"))) {
                binding.etPhone.error = "Enter your phone number"
                binding.etPhone.requestFocus()
                return@setOnClickListener
            }
            if (!binding.cbConsent.isChecked) {
                Toast.makeText(this, "Please accept the privacy note to continue",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val name = binding.etName.text.toString().trim().ifBlank { "User" }
            binding.btnContinue.isEnabled = false
            binding.btnContinue.text = "Setting up…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        AppContainer.api.verifyOtp(phone, "000000", name)
                    }
                }.onSuccess { res ->
                    val token = res.optString("token")
                    scope.launch(Dispatchers.IO) {
                        AppContainer.prefs.save(token, phone, name)
                        withContext(Dispatchers.Main) { proceed() }
                    }
                }.onFailure { e ->
                    binding.btnContinue.isEnabled = true
                    binding.btnContinue.text = "Continue"
                    Toast.makeText(this@OnboardingActivity,
                        "Cannot reach server: ${e.message}",
                        Toast.LENGTH_LONG).show()
                    askServerAddress()
                }
            }
        }
    }

    private fun askServerAddress() {
        val input = android.widget.EditText(this).apply {
            hint = "http://127.0.0.1:18765"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        scope.launch(Dispatchers.IO) {
            val current = AppContainer.prefs.currentBaseUrl()
            scope.launch(Dispatchers.Main) {
                input.setText(current)
                android.app.AlertDialog.Builder(this@OnboardingActivity)
                    .setTitle("Server address")
                    .setMessage("antAI runs on your computer. Over USB, keep the default " +
                        "http://127.0.0.1:18765 and run usb_setup.bat on the computer " +
                        "(it maps the phone's port to the server via 'adb reverse'). " +
                        "Only change this to a http://<computer-LAN-IP>:8765 address if you " +
                        "are connecting over Wi-Fi instead of USB.")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        var rawUrl = input.text.toString().trim()
                        if (rawUrl.isNotEmpty()) {
                            if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                                rawUrl = "http://$rawUrl"
                            }
                            val url = rawUrl.trimEnd('/')
                            binding.btnContinue.isEnabled = false
                            binding.btnContinue.text = "Saving…"
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppContainer.prefs.setBaseUrl(url)
                                }
                                binding.tvServerUrl.text = url
                                binding.btnContinue.isEnabled = true
                                binding.btnContinue.text = "Continue"
                                Toast.makeText(this@OnboardingActivity,
                                    "Server saved. Connecting…",
                                    Toast.LENGTH_SHORT).show()
                                binding.btnContinue.performClick()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun proceed() {
        requestPermissionsIfNeeded()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { AppContainer.ws.connect() } }
            RealtimeService.createChannels(this@OnboardingActivity)
            val i = Intent(this@OnboardingActivity, RealtimeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i)
            } else {
                startService(i)
            }
            startActivity(Intent(this@OnboardingActivity, HomeActivity::class.java))
            finish()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val need = listOfNotNull(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }
}