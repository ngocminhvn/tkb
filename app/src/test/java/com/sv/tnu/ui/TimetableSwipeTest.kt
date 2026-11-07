package com.sv.tnu.ui

import com.sv.tnu.data.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableSwipeTest {
    private val weeks = listOf(
        WeekEntity(20261, 9, "26/10/2026", "01/11/2026", "Tuần 9"),
        WeekEntity(20261, 10, "02/11/2026", "08/11/2026", "Tuần 10"),
        WeekEntity(20261, 11, "09/11/2026", "15/11/2026", "Tuần 11"),
    )

    @Test
    fun swipeLeftAdvancesOneCalendarDayNotOneWeek() {
        assertEquals("2026-11-08" to 10, adjacentTimetableDay("2026-11-07", 1, weeks))
        assertEquals("2026-11-09" to 11, adjacentTimetableDay("2026-11-08", 1, weeks))
    }

    @Test
    fun swipeRightReturnsToPreviousDayAcrossWeekBoundary() {
        assertEquals("2026-11-07" to 10, adjacentTimetableDay("2026-11-08", -1, weeks))
        assertEquals("2026-11-01" to 9, adjacentTimetableDay("2026-11-02", -1, weeks))
    }

    @Test
    fun boundsNeverNavigateOutsideAvailableSemesterWeeks() {
        assertNull(adjacentTimetableDay("2026-10-26", -1, weeks))
        assertNull(adjacentTimetableDay("2026-11-15", 1, weeks))
        assertNull(adjacentTimetableDay("bad-date", 1, weeks))
        assertNull(adjacentTimetableDay("2026-11-07", 0, weeks))
    }
}
