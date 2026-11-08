package com.sv.tnu.rust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host JVM uses the Kotlin test fallback; Rust's cargo tests exercise the
 * matching native implementations. The APK itself must package libtkb_core.
 */
class RustScheduleCoreTest {
    @Test
    fun dateNormalizerMatchesPortalInputFormats() {
        assertEquals("2026-11-07", RustScheduleCore.normalizeDate("7/11/2026"))
        assertEquals("2026-11-07", RustScheduleCore.normalizeDate("2026-11-7T07:00:00"))
        assertEquals("2026-11-07", RustScheduleCore.normalizeDate("07-11-2026 00:00:00"))
    }

    @Test
    fun examDurationAndMidnight() {
        assertEquals("08:30", RustScheduleCore.examEndTime("07:00", 90))
        assertEquals("00:15", RustScheduleCore.examEndTime("23:45", 30))
        assertEquals("", RustScheduleCore.examEndTime("25:00", 30))
        assertEquals("", RustScheduleCore.examEndTime("07:00", 0))
    }

    @Test
    fun navigatesAcrossWeeksAndYears() {
        assertEquals("2026-11-09", RustScheduleCore.moveCalendarDay("2026-11-08", 1))
        assertEquals("2026-11-08", RustScheduleCore.moveCalendarDay("2026-11-09", -1))
        assertNull(RustScheduleCore.moveCalendarDay("2026-11-09", 0))
        val dates = RustScheduleCore.weekDates("2026-12-28")
        assertEquals(7, dates.size)
        assertEquals("2026-12-28", dates.first())
        assertEquals("2027-01-03", dates.last())
        assertTrue(RustScheduleCore.weekDates("bad-date").isEmpty())
    }

    @Test
    fun monthCalendarAndDisplayFormattersMatchRust() {
        assertEquals("07/11/2026", RustScheduleCore.formatDmy("2026-11-07"))
        assertEquals("07/11", RustScheduleCore.formatDm("7/11/2026"))
        assertEquals("2026-11-02", RustScheduleCore.mondayOfWeek("2026-11-08"))
        assertEquals("2026-11-15", RustScheduleCore.addCalendarDays("2026-11-08", 7))
        assertEquals(450, RustScheduleCore.parseHhMmMinutes("07:30"))
        assertEquals("3.0 MB", RustScheduleCore.formatBytes(3L * 1024L * 1024L))
        val grid = RustScheduleCore.monthDates("2026-11-08", 0)
        assertEquals(11, grid?.first)
        assertEquals(2026, grid?.second)
        assertEquals("2026-11-01", grid?.third?.get(6))
    }
}
