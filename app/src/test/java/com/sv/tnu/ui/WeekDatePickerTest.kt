package com.sv.tnu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekDatePickerTest {
    @Test
    fun novemberSecondToEighthUsesCorrectMondaySundayOrder() {
        assertEquals(
            listOf(
                "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05",
                "2026-11-06", "2026-11-07", "2026-11-08",
            ),
            weekDates("2026-11-02"),
        )
    }

    @Test
    fun monthViewAlignsWeekdaysAndAccountsForLeapYear() {
        val (november, dates) = monthDates("2026-11-07", 0)
        assertEquals("Tháng 11/2026", november)
        assertEquals(35, dates.size)
        assertNull(dates[0]) // November 1 2026 is a Sunday.
        assertEquals("2026-11-01", dates[6])
        assertEquals("2026-11-07", dates[12])

        val (_, february) = monthDates("2024-02-12", 0)
        assertTrue(february.contains("2024-02-29"))
        assertEquals(35, february.size)
    }

    @Test
    fun portalWeekDatesNormalizeWithoutDeviceLocaleDependencies() {
        assertEquals("2026-11-02", normalizePortalDay("02/11/2026"))
        assertEquals("2026-11-07", normalizePortalDay("2026-11-07"))
        assertEquals("2026-11-08", weekDates("2026-11-02").last())
    }
}
