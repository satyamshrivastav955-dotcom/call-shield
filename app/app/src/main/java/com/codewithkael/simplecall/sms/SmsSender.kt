package com.codewithkael.simplecall.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * Sends SMS through the system SmsManager. Requires SEND_SMS. The app is not the
 * default SMS app; it simply asks the platform to send a text. Long messages are
 * split into multipart automatically.
 */
class SmsSender(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun send(phone: String, body: String): Boolean {
        if (phone.isBlank() || body.isBlank()) return false
        return try {
            val manager = smsManager()
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                manager.sendTextMessage(phone, null, body, null, null)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "SMS send failed", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    companion object {
        private const val TAG = "SmsSender"
    }
}
