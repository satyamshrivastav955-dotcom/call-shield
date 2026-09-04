package com.antai.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.comms.CallEngine
import com.antai.app.databinding.ActivityCallBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * VoIP / video-call screen (MD 3.1, 5): WebRTC against the server SFU,
 * live verdict card, live guidance banner, freeze holds and verify prompts
 * pushed over the WS control channel.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var engine: CallEngine? = null
    private var callLogId: Long? = null
    private var isCaller = false
    private var calleePhone = ""
    private var kind = "voice"
    private var muted = false
    private var speaker = false
    private var connected = false
    // P1-F FIX: record wall-clock time at ICE CONNECTED, not at screen-open.
    private var connectedAt: Long = 0L
    // Latest advisory state. Verdict and live guidance are rendered into ONE card
    // (combined) instead of overwriting the same TextView, which used to make the
    // banner flicker/clobber between the two messages.
    private var lastVerdict: JSONObject? = null
    private var lastGuidance: String? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
            val cameraGranted = results[Manifest.permission.CAMERA] == true
            Log.d("CallActivity", "Permissions: audio=$audioGranted, camera=$cameraGranted")
            if (audioGranted || (kind == "video" && cameraGranted)) {
                proceedWithEngine()
            } else {
                Toast.makeText(this, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        calleePhone = intent.getStringExtra("callee_phone") ?: ""
        kind = intent.getStringExtra("kind") ?: "voice"
        isCaller = intent.getBooleanExtra("is_caller", false)

        val peerLabel = runCatching {
            kotlinx.coroutines.runBlocking {
                AppContainer.db.contacts().byPhone(calleePhone)?.label
            }
        }.getOrNull() ?: calleePhone
        binding.tvPeerName.text = peerLabel.ifBlank { calleePhone }
        binding.tvAvatar.text = peerLabel.firstOrNull()?.uppercase() ?: "?"

        if (kind == "video") {
            // Full-bleed video: give the video frame the whole area. Previously only
            // the avatar/name/status were hidden while their parent panel (weight=1)
            // stayed visible, so the video was squeezed into the top half of the
            // screen with a large empty gap below it. Hiding the panel lets the
            // videoFrame (also weight=1) expand to fill the space above the controls.
            binding.videoFrame.visibility = View.VISIBLE
            binding.localVideo.visibility = View.VISIBLE
            binding.remoteVideo.visibility = View.VISIBLE
            binding.identityPanel.visibility = View.GONE
        } else {
            binding.videoFrame.visibility = View.GONE
        }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (kind == "video" && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            proceedWithEngine()
        }
    }

    private fun proceedWithEngine() {
        engine = CallEngine(this, AppContainer.ws, kind == "video")
        engine?.initialize()
        engine?.attachLocalVideo(binding.localVideo)
        engine?.attachRemoteVideo(binding.remoteVideo)
        engine?.onConnected = {
            // P1-F FIX: record connected time here (ICE CONNECTED / media flows),
            // not at screen open. The timer ticks from this moment.
            connectedAt = System.currentTimeMillis()
            connected = true
            runOnUiThread {
                binding.tvStatus.text = "Connected  0:00"
                binding.tvStatus.setBackgroundColor(0xFF059669.toInt())
            }
            // Tick the call duration on the UI every second
            lifecycleScope.launch {
                while (connected) {
                    delay(1000)
                    val elapsed = (System.currentTimeMillis() - connectedAt) / 1000
                    val mm = elapsed / 60
                    val ss = elapsed % 60
                    runOnUiThread {
                        binding.tvStatus.text = "Connected  $mm:${ss.toString().padStart(2, '0')}"
                    }
                }
            }
        }
        engine?.onEnded = {
            runOnUiThread { finish() }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            callLogId = AppRepository.logCallStarted(calleePhone, calleePhone, kind)
        }

        if (isCaller) {
            binding.tvStatus.text = "Calling $calleePhone…"
            binding.btnAccept.visibility = View.GONE
            binding.btnDecline.visibility = View.GONE
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { AppContainer.ws.connect() }
                delay(300)
                engine?.startCall(calleePhone, kind) { }
            }
        } else if (intent.getBooleanExtra("auto_accept", false)) {
            // Launched from IncomingCallActivity's own Accept tap - answer immediately,
            // don't make the user confirm a second time.
            binding.btnAccept.visibility = View.GONE
            binding.btnDecline.visibility = View.GONE
            acceptIncoming()
        } else {
            binding.tvStatus.text = "Incoming call…"
            binding.btnAccept.visibility = View.VISIBLE
            binding.btnDecline.visibility = View.VISIBLE
        }

        binding.btnMute.setOnClickListener {
            muted = engine?.toggleMute() ?: true
            binding.btnMute.text = if (muted) "🔇" else "🔊"
        }
        binding.btnSpeaker.setOnClickListener {
            speaker = !speaker
            engine?.toggleSpeaker(speaker)
            binding.btnSpeaker.text = if (speaker) "🔉" else "🔈"
        }
        binding.btnCamera.setOnClickListener {
            if (engine?.switchCamera() == true) Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
        }
        binding.btnHangup.setOnClickListener { hangup() }
        binding.btnAccept.setOnClickListener { acceptIncoming() }
        binding.btnDecline.setOnClickListener { declineIncoming() }
        binding.btnVerify.setOnClickListener {
            // "Verify Caller" (MD 4.7): pick a linked trusted contact and ping them
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppContainer.db
                val trusted = db.contacts().observeTrusted().first()
                withContext(Dispatchers.Main) {
                    if (trusted.isEmpty()) {
                        Toast.makeText(this@CallActivity,
                            "No trusted-circle contact linked yet. Add one in Contacts.",
                            Toast.LENGTH_LONG).show()
                        return@withContext
                    }
                    val names = trusted.map { it.label.ifBlank { it.displayName } }.toTypedArray()
                    android.app.AlertDialog.Builder(this@CallActivity)
                        .setTitle("Verify caller with")
                        .setItems(names) { _, i ->
                            val contact = trusted[i]
                            val sk = engine?.sessionKey ?: intent.getStringExtra("session_key")
                            if (sk == null) {
                                Toast.makeText(this@CallActivity, "Call not active yet",
                                    Toast.LENGTH_SHORT).show()
                                return@setItems
                            }
                            lifecycleScope.launch(Dispatchers.IO) {
                                val ok = AppRepository.requestVerify(sk, contact.phone)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@CallActivity,
                                        if (ok) "Verification prompt sent to ${contact.label}"
                                        else "Could not reach ${contact.label}",
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        wireRealtime()
    }

    private fun acceptIncoming() {
        val sessionKey = intent.getStringExtra("session_key") ?: return
        val offer = intent.getStringExtra("offer") ?: return
        engine?.acceptIncoming(sessionKey, JSONObject(offer))
        engine?.startCapturing()
        binding.tvStatus.text = "Connecting…"
    }

    private fun declineIncoming() {
        engine?.decline()
        finish()
    }

    private fun hangup() {
        engine?.hangup()
        endCall("ended")
        finish()
    }

    private fun endCall(status: String) {
        connected = false  // P1-F FIX: stops the ticker coroutine
        callLogId?.let { id ->
            // Compute actual connected duration in seconds (0 if call never connected)
            val durationSec = if (connectedAt > 0L)
                (System.currentTimeMillis() - connectedAt) / 1000.0
            else 0.0
            lifecycleScope.launch(Dispatchers.IO) {
                AppRepository.logCallEnded(id, status, durationSec)
            }
        }
    }

    private fun wireRealtime() {
        val router = AppContainer.router
        router.onCallAnswer = { msg ->
            engine?.handleAnswer(msg.optString("session_key"), msg.optJSONObject("answer") ?: JSONObject())
        }
        router.onCallState = { msg ->
            runOnUiThread {
                val state = msg.optString("state")
                when (state) {
                    "connected" -> {
                        connected = true
                        binding.tvStatus.text = "Connected"
                        binding.tvStatus.setBackgroundColor(0xFF059669.toInt())
                    }
                    "declined" -> {
                        Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show()
                        hangup()
                    }
                    "ended" -> finish()
                }
            }
        }
        router.onCallIce = { msg ->
            engine?.handleIce(msg)
        }
        router.onVerdict = { v ->
            runOnUiThread {
                lastVerdict = v
                renderAdvisory()
            }
        }
        router.onGuidance = { g ->
            runOnUiThread {
                lastGuidance = g.optString("guidance")
                renderAdvisory()
            }
        }
        router.onFreeze = { f ->
            runOnUiThread { FreezeDialogs.show(this, f, lifecycleScope) }
        }
        router.onVerifyPrompt = { m ->
            runOnUiThread { VerifyDialogs.show(this, m, lifecycleScope) }
        }
        router.onReportReady = { r ->
            runOnUiThread {
                Toast.makeText(this, "Report ready: ${r.optString("title")}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Render the verdict + live guidance as a single coherent card. Colour is
     * driven by the strongest signal: critical (red) > verify/any-guidance
     * (amber) > all-clear (green). Combining them in one place stops the two
     * realtime messages from overwriting each other on the shared TextView.
     */
    private fun renderAdvisory() {
        val v = lastVerdict
        val g = lastGuidance
        if (v == null && g.isNullOrBlank()) return
        binding.cardVerdict.visibility = View.VISIBLE
        VerdictCard.bind(binding.tvVerdict, v ?: JSONObject(), g)
        val band = v?.optString("band", "passive") ?: "passive"
        // If we only have guidance (no actionable verdict yet) keep a cautionary
        // amber rather than the all-clear green VerdictCard would pick for passive.
        if (band == "passive" && !g.isNullOrBlank()) {
            binding.tvVerdict.setBackgroundColor(0xFFD97706.toInt())
        }
    }

    override fun onResume() {
        super.onResume()
        // Claim the shared router while the call screen is visible so verdicts /
        // guidance render on the in-call card. Signaling callbacks (answer / ICE /
        // state) are not part of the notification set, so they stay owned by this
        // activity for the whole call regardless of foreground state.
        com.antai.app.realtime.RealtimeService.onActivityResumed()
        wireRealtime()
    }

    override fun onPause() {
        super.onPause()
        // If the user leaves mid-call the call keeps running (WebRTC + signaling
        // handlers persist); only the alerting callbacks fall back to notifications.
        com.antai.app.realtime.RealtimeService.onActivityPaused(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.release()
    }
}