package com.antai.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.antai.app.databinding.ActivityIncomingCallBinding

/** Incoming call screen: accept (answer via CallActivity) or decline (MD 5). */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var sessionKey = ""
    private var kind = "voice"
    private var offer = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionKey = intent.getStringExtra("session_key") ?: return finish()
        kind = intent.getStringExtra("kind") ?: "voice"
        offer = intent.getStringExtra("offer") ?: ""

        binding.tvTitle.text = if (kind == "video") "Incoming video call" else "Incoming voice call"

        binding.btnAccept.setOnClickListener {
            val i = Intent(this, CallActivity::class.java)
            i.putExtra("session_key", sessionKey)
            i.putExtra("offer", offer)
            i.putExtra("kind", kind)
            i.putExtra("is_caller", false)
            i.putExtra("auto_accept", true)
            i.putExtra("callee_phone", "")
            startActivity(i)
            finish()
        }
        binding.btnDecline.setOnClickListener {
            com.antai.app.AppContainer.ws.send(
                "call.decline",
                org.json.JSONObject().put("session_key", sessionKey))
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}