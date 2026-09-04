package com.antai.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth")

class AuthPrefs(private val context: Context) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val PHONE = stringPreferencesKey("phone")
        val NAME = stringPreferencesKey("display_name")
        val BASE_URL = stringPreferencesKey("base_url")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }
    val phone: Flow<String?> = context.dataStore.data.map { it[Keys.PHONE] }
    val displayName: Flow<String?> = context.dataStore.data.map { it[Keys.NAME] }
    val baseUrl: Flow<String> = context.dataStore.data.map {
        val raw = it[Keys.BASE_URL] ?: DEFAULT_BASE
        if (raw.trim().trimEnd('/') == "http://127.0.0.1:8765" || raw.trim().trimEnd('/') == "http://localhost:8765") {
            "http://127.0.0.1:18765"
        } else {
            raw
        }
    }

    suspend fun currentToken(): String? = token.first()
    suspend fun currentPhone(): String? = phone.first()
    suspend fun currentBaseUrl(): String = baseUrl.first()

    suspend fun save(token: String, phone: String, name: String) {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.PHONE] = phone
            it[Keys.NAME] = name
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        // 10.0.2.2 only resolves on the Android *emulator*. For a real device
        // over USB, forward the port instead: adb reverse tcp:18765 tcp:8765
        const val DEFAULT_BASE = "http://127.0.0.1:18765"
    }
}