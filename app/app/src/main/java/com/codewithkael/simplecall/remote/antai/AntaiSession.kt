package com.codewithkael.simplecall.remote.antai

import android.content.Context
import android.content.SharedPreferences
import com.codewithkael.simplecall.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Messaging identity for the antAI server: the user's phone number and the
 * bearer token issued by /api/auth/verify. Persisted so login survives restarts.
 *
 * Completely independent of the calling identity (SimpleCallApplication.USER_ID,
 * used by the Node signaling server) — this touches only the messaging path.
 */
@Singleton
class AntaiSession @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.MSG_PREFS, Context.MODE_PRIVATE)

    var phone: String
        get() = prefs.getString(Constants.KEY_PHONE, "") ?: ""
        private set(v) { prefs.edit().putString(Constants.KEY_PHONE, v).apply() }

    var token: String
        get() = prefs.getString(Constants.KEY_TOKEN, "") ?: ""
        private set(v) { prefs.edit().putString(Constants.KEY_TOKEN, v).apply() }

    var displayName: String
        get() = prefs.getString(Constants.KEY_DISPLAY_NAME, "") ?: ""
        private set(v) { prefs.edit().putString(Constants.KEY_DISPLAY_NAME, v).apply() }

    val isLoggedIn: Boolean get() = token.isNotBlank() && phone.isNotBlank()

    fun save(phone: String, token: String, displayName: String) {
        this.phone = normalize(phone)
        this.token = token
        this.displayName = displayName
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        /**
         * Normalize a phone number to a stable thread key: keep a leading '+',
         * strip spaces / dashes / parentheses. Server accepts `^\+?[0-9]{8,15}$`.
         * This is a display/keying convenience only — the server is the source
         * of truth for matching users.
         */
        fun normalize(raw: String): String {
            if (raw.isBlank()) return raw
            val trimmed = raw.trim()
            val hasPlus = trimmed.startsWith("+")
            val digits = trimmed.filter { it.isDigit() }
            return if (hasPlus) "+$digits" else digits
        }
    }
}
