package com.antai.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.antai.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher: if logged in -> Home, else onboarding. */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalScope.launch(Dispatchers.IO) {
            val token = AppContainer.prefs.currentToken()
            withContext(Dispatchers.Main) {
                val target = if (token != null)
                    Intent(this@SplashActivity, HomeActivity::class.java)
                else
                    Intent(this@SplashActivity, OnboardingActivity::class.java)
                startActivity(target)
                finish()
            }
        }
    }
}