package com.liquorbee.mobile.picklists

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Server dates are UTC ("2026-11-20T14:30:05.123"); shown in the phone's local time. */
internal object PickListFormat {
    fun parse(value: String?): Date? {
        if (value.isNullOrBlank() || value.length < 19) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value.take(19))
        }.getOrNull()
    }

    /** "Today 2:30 PM", "Yesterday 9:05 AM", "Tue, Nov 18 · 2:30 PM", or with the year when it isn't this year. */
    fun dateTime(value: String?): String {
        val date = parse(value) ?: return ""
        val then = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        val time = SimpleDateFormat("h:mm a", Locale.US).format(date)
        val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        val dayDiff = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) else Int.MAX_VALUE
        return when (dayDiff) {
            0 -> "Today $time"
            1 -> "Yesterday $time"
            else -> SimpleDateFormat(if (sameYear) "EEE, MMM d" else "MMM d, yyyy", Locale.US).format(date) + " · " + time
        }
    }
}
