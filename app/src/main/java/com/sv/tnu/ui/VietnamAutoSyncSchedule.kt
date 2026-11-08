package com.sv.tnu.ui

import java.util.Calendar
import java.util.TimeZone

/** Fixed Vietnam timezone, independent of the user's system timezone. */
private val vietnamZone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

/** Daily portal refresh becomes eligible at 06:30 Vietnam time. */
internal fun vietnamAutoSyncCutoffMillis(nowMillis: Long): Long =
    Calendar.getInstance(vietnamZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 6)
        set(Calendar.MINUTE, 30)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Once per Vietnam day AFTER 06:30; earlier attempts do not count. */
internal fun isVietnamDailyAutoSyncDue(nowMillis: Long, lastAttemptMillis: Long): Boolean {
    val cutoff = vietnamAutoSyncCutoffMillis(nowMillis)
    return nowMillis >= cutoff && lastAttemptMillis < cutoff
}

/** Exact next 06:30 boundary; callers wait instead of polling every minute. */
internal fun nextVietnamAutoSyncDelayMillis(nowMillis: Long, lastAttemptMillis: Long): Long {
    val cutoff = vietnamAutoSyncCutoffMillis(nowMillis)
    if (nowMillis < cutoff) return cutoff - nowMillis
    if (lastAttemptMillis < cutoff) return 0L
    val tomorrow = Calendar.getInstance(vietnamZone).apply {
        timeInMillis = cutoff
        add(Calendar.DAY_OF_YEAR, 1)
    }
    return (tomorrow.timeInMillis - nowMillis).coerceAtLeast(0L)
}
