package com.codewithkael.simplecall.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/** One raw SMS row read from the device Telephony provider. */
data class SmsRecord(
    val address: String,   // sender/recipient phone number
    val body: String,
    val date: Long,        // epoch millis
    val incoming: Boolean  // true = received, false = sent
)

/**
 * Reads the device SMS inbox + sent box via the Telephony content provider.
 * Read-only; requires READ_SMS. The app is NOT the default SMS app — it only
 * observes existing SMS so they can be shown in the unified thread list and
 * scored by the antAI server.
 */
class SmsReader(private val context: Context) {

    /**
     * Load recent SMS (inbox + sent), newest first. `limit` caps the total rows
     * to keep the initial hydrate cheap.
     */
    fun loadRecent(limit: Int = 500): List<SmsRecord> {
        val out = ArrayList<SmsRecord>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { c -> readRows(c, out, limit) }
        } catch (e: Exception) {
            Log.w(TAG, "loadRecent failed (permission not granted?)", e)
        }
        return out
    }

    private fun readRows(c: Cursor, out: MutableList<SmsRecord>, limit: Int) {
        val iAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
        val iBody = c.getColumnIndex(Telephony.Sms.BODY)
        val iDate = c.getColumnIndex(Telephony.Sms.DATE)
        val iType = c.getColumnIndex(Telephony.Sms.TYPE)
        while (c.moveToNext() && out.size < limit) {
            val addr = if (iAddr >= 0) c.getString(iAddr) ?: "" else ""
            val body = if (iBody >= 0) c.getString(iBody) ?: "" else ""
            val date = if (iDate >= 0) c.getLong(iDate) else System.currentTimeMillis()
            val type = if (iType >= 0) c.getInt(iType) else Telephony.Sms.MESSAGE_TYPE_INBOX
            if (addr.isBlank() && body.isBlank()) continue
            out.add(
                SmsRecord(
                    address = addr,
                    body = body,
                    date = date,
                    incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                )
            )
        }
    }

    companion object {
        private const val TAG = "SmsReader"
    }
}
