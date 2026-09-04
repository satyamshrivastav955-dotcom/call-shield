package com.antai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.databinding.ActivityEnrollVoiceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Voiceprint enrollment (README 3.3): record 5s of speech for the protected
 * user OR for a trusted contact (so calls from that person can be verified).
 */
class EnrollVoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnrollVoiceBinding
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var recording = false
    private var enrollForPhone: String? = null
    private var enrollForName = "you"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollVoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enrollForPhone = intent.getStringExtra("enroll_for_phone")
        enrollForName = intent.getStringExtra("enroll_for_name") ?: "you"

        val title = if (enrollForPhone == null) "Your voiceprint"
        else "${enrollForName}'s voiceprint"
        binding.tvTitle.text = title
        binding.tvHint.text = if (enrollForPhone == null)
            "This voiceprint lets antAI know it is really you. " +
            "Record a short clip speaking naturally."
        else
            "Record ${enrollForName}'s voice so antAI can verify that calls " +
            "from them are really them. Ask them to speak naturally."

        binding.btnRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                return@setOnClickListener
            }
            toggleRecording()
        }
        binding.btnSave.setOnClickListener { upload() }
    }

    private fun toggleRecording() {
        if (recording) {
            stopRecording()
            binding.btnRecord.text = "● Record again"
            binding.tvHint.text = "Recording saved. Tap 'Use this recording'."
            binding.btnSave.visibility = android.view.View.VISIBLE
            return
        }
        file = File(cacheDir, "enroll_${System.currentTimeMillis()}.m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file!!.absolutePath)
            prepare()
            start()
        }
        recording = true
        binding.btnRecord.text = "■ Stop"
        binding.tvHint.text = "Speak naturally for 5 seconds"
        binding.btnSave.visibility = android.view.View.GONE
        lifecycleScope.launch {
            delay(5000)
            if (recording) {
                stopRecording()
                binding.btnRecord.text = "● Record again"
                binding.btnSave.visibility = android.view.View.VISIBLE
                binding.tvHint.text = "Recording saved. Tap 'Use this recording'."
            }
        }
    }

    private fun stopRecording() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        recording = false
    }

    override fun onPause() {
        super.onPause()
        stopRecording()
    }

    private fun upload() {
        val f = file ?: return
        binding.tvHint.text = "Saving voiceprint…"
        binding.btnSave.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val bytes = f.readBytes()
                val res = AppContainer.api.enrollVoiceprint(bytes)
                if (res.optBoolean("enrolled", false)) {
                    // register the enrollment locally for the contact
                    enrollForPhone?.let { phone ->
                        AppContainer.db.contacts().byPhone(phone)?.let { c ->
                            AppContainer.db.contacts().upsert(c.copy(isTrusted = true))
                        }
                    }
                    true
                } else false
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                binding.btnSave.isEnabled = true
                binding.tvHint.text = if (ok)
                    "Voiceprint saved. antAI can now recognize ${if (enrollForPhone == null) "your voice" else "$enrollForName's voice"}."
                else "Could not save. Please try again."
                Toast.makeText(this@EnrollVoiceActivity,
                    if (ok) "Voiceprint saved" else "Save failed",
                    Toast.LENGTH_LONG).show()
                if (ok) finish()
            }
        }
    }
}