package com.codewithkael.simplecall.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lightweight, allocation-cheap timestamp formatting for chat UI. */
object TimeFormat {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

    /** Short stamp for list rows: time if today, weekday if this week, else date. */
    fun shortStamp(millis: Long): String {
        if (millis <= 0) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return timeFmt.format(Date(millis))
        val daysAgo = (now.timeInMillis - millis) / (24L * 60 * 60 * 1000)
        return if (daysAgo in 1..6) dayFmt.format(Date(millis)) else dateFmt.format(Date(millis))
    }

    /** Clock time for individual message bubbles. */
    fun clock(millis: Long): String = if (millis <= 0) "" else timeFmt.format(Date(millis))
}
