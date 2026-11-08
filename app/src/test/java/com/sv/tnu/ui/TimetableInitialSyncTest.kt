package com.sv.tnu.ui

import com.sv.tnu.data.CachedTimetable
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableInitialSyncTest {
    private val weeks = listOf(WeekEntity(20261, 10, "02/11/2026", "08/11/2026", "Tuần 10"))
    private val lessons = listOf(
        LessonEntity(
            idTkb = "lesson-1",
            hocKy = 20261,
            tuanHocKy = 10,
            ngayHoc = "03/11/2026",
            thuKieuSo = 3,
            tietBatDau = 1,
            soTiet = 2,
            maMon = "MH1",
            tenMon = "Môn học",
            soTinChi = "2",
            maPhong = "A101",
            tenGiangVien = "Giảng viên",
            tenLop = "L1",
            maNhom = "N1",
        ),
    )

    @Test
    fun firstLoginDisplaysCommittedRowsWhileRoomStillEmitsEmptyLists() {
        val saved = CachedTimetable(weeks, lessons)
        assertEquals(saved, preferJustSyncedCache(emptyList(), emptyList(), saved))
        assertEquals(saved, preferJustSyncedCache(weeks, emptyList(), saved))
    }

    @Test
    fun subsequentAppLaunchUsesRoomCacheWithoutRequiringSync() {
        assertEquals(
            CachedTimetable(weeks, lessons),
            preferJustSyncedCache(weeks, lessons, null),
        )
    }

    @Test
    fun settledRoomDataRemainsAuthoritativeAfterSync() {
        val different = listOf(weeks.single().copy(tuanHocKy = 11))
        assertEquals(
            CachedTimetable(different, lessons),
            preferJustSyncedCache(different, lessons, CachedTimetable(weeks, lessons)),
        )
    }

    @Test
    fun emptyOrFailedFirstSyncDoesNotInventAStoredSchedule() {
        val empty = CachedTimetable(emptyList(), emptyList())
        assertEquals(empty, preferJustSyncedCache(emptyList(), emptyList(), null))
        assertEquals(empty, preferJustSyncedCache(emptyList(), emptyList(), empty))
    }
}
