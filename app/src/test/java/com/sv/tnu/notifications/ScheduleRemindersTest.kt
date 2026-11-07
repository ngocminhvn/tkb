package com.sv.tnu.notifications

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRemindersTest {
    @Test
    fun remindersUseNextLocalTimeAndRollOverWhenHourAlreadyPassed() {
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        try {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.NOVEMBER, 7, 6, 15, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sameDay = Calendar.getInstance().apply {
                timeInMillis = ScheduleReminders.nextAlarmMillis(now, 6 * 60 + 30)
            }
            assertEquals(7, sameDay.get(Calendar.DAY_OF_MONTH))
            assertEquals(6, sameDay.get(Calendar.HOUR_OF_DAY))
            assertEquals(30, sameDay.get(Calendar.MINUTE))

            val nextDay = Calendar.getInstance().apply {
                timeInMillis = ScheduleReminders.nextAlarmMillis(now, 5 * 60)
            }
            assertEquals(8, nextDay.get(Calendar.DAY_OF_MONTH))
            assertEquals(5, nextDay.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, nextDay.get(Calendar.MINUTE))
            assertTrue(nextDay.timeInMillis > now)
        } finally {
            TimeZone.setDefault(before)
        }
    }
}
