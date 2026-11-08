package com.sv.tnu.ui

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VietnamAutoSyncScheduleTest {
    private fun vn(ymd: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }.parse(ymd)!!.time

    @Test
    fun dailyRefreshWaitsUntilVietnamSixThirty() {
        val before = vn("2026-11-08 06:29")
        val cutoff = vn("2026-11-08 06:30")
        assertFalse(isVietnamDailyAutoSyncDue(before, 0L))
        assertEquals(60_000L, nextVietnamAutoSyncDelayMillis(before, 0L))
        assertTrue(isVietnamDailyAutoSyncDue(cutoff, 0L))
        assertTrue(isVietnamDailyAutoSyncDue(cutoff, vn("2026-11-08 06:00")))
    }

    @Test
    fun onlyOneRefreshAfterCutoffEachVietnamDay() {
        val attempted = vn("2026-11-08 06:31")
        val late = vn("2026-11-08 22:00")
        assertFalse(isVietnamDailyAutoSyncDue(late, attempted))
        assertEquals(vn("2026-11-09 06:30") - late, nextVietnamAutoSyncDelayMillis(late, attempted))
        assertTrue(isVietnamDailyAutoSyncDue(vn("2026-11-09 06:30"), attempted))
    }

    @Test
    fun vietnamTimeZoneDoesNotDependOnPhoneTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(vn("2026-11-08 06:30"), vietnamAutoSyncCutoffMillis(vn("2026-11-08 12:00")))
            assertEquals(0L, nextVietnamAutoSyncDelayMillis(vn("2026-11-08 06:30"), 0L))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
