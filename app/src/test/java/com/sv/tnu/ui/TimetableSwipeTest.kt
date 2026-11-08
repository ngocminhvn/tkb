package com.sv.tnu.ui

import com.sv.tnu.data.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSwipeTest {
    @Test
    fun edgeNavigationIsOnlyConfirmedAfterFullPullAndValidNeighbor() {
        assertEquals(null, confirmedEdgeWeekDirection(143f, 144f, 1, true))
        assertEquals(1, confirmedEdgeWeekDirection(144f, 144f, 1, true))
        assertEquals(-1, confirmedEdgeWeekDirection(185f, 144f, -1, true))
        assertEquals(null, confirmedEdgeWeekDirection(220f, 144f, 1, false))
        assertEquals(null, confirmedEdgeWeekDirection(220f, 144f, 0, true))
        assertEquals(null, confirmedEdgeWeekDirection(220f, 144f, 2, true))
    }

    @Test
    fun elasticPullMovesListWithoutProgressOverlay() {
        assertEquals(0f, elasticEdgeOffsetPx(0f, 144f, 1), 0.001f)
        assertEquals(0f, elasticEdgeOffsetPx(220f, 144f, 0), 0.001f)
        val down = elasticEdgeOffsetPx(144f, 144f, -1)
        val up = elasticEdgeOffsetPx(144f, 144f, 1)
        assertTrue(down > 0f)
        assertEquals(-down, up, 0.001f)
        assertTrue(elasticEdgeOffsetPx(360f, 144f, -1) > down)
        assertTrue(elasticEdgeOffsetPx(720f, 144f, -1) < 144f)
    }

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
