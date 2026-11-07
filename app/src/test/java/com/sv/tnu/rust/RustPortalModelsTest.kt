package com.sv.tnu.rust

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host JVM verifies the Rust-to-Room JSON contract without loading a .so.
 * Native Rust tests validate the source portal payloads separately.
 */
class RustPortalModelsTest {
    private val codec = Json { ignoreUnknownKeys = true }

    @Test
    fun timetableRowsUseExactRoomFieldNames() {
        val payload = """
            {"periods":[{"tiet":1,"gioBatDau":"07:15","gioKetThuc":"08:05"}],
             "weeks":[{"hocKy":20261,"tuanHocKy":2,"ngayBatDau":"02/11/2026",
                       "ngayKetThuc":"08/11/2026","thongTinTuan":"Tuần 2"}],
             "lessons":[{"idTkb":"lesson1","hocKy":20261,"tuanHocKy":2,
                         "ngayHoc":"2026-11-07","thuKieuSo":7,"tietBatDau":1,
                         "soTiet":2,"maMon":"MAT01","tenMon":"Toán","soTinChi":"2",
                         "maPhong":"101","tenGiangVien":"GV","tenLop":"A","maNhom":"01"}]}
        """.trimIndent()
        val mapped = codec.decodeFromString(RustPortalCore.TimetableRows.serializer(), payload)
        assertEquals(1, mapped.periods.size)
        assertEquals("07:15", mapped.periods[0].gioBatDau)
        assertEquals(20261, mapped.weeks[0].hocKy)
        assertEquals("lesson1", mapped.lessons[0].idTkb)
        assertEquals("Toán", mapped.lessons[0].tenMon)
    }

    @Test
    fun examRowsCanBeWrittenThroughExistingRoomEntity() {
        val payload = """
            {"rows":[{"id":0,"hocKy":20261,"isGiuaKy":true,"soThuTu":1,
                      "maMon":"MAT01","tenMon":"Toán","ngayThi":"2026-11-07",
                      "gioBatDau":"07:00","soPhut":90,"maPhong":"P101",
                      "diaDiemThi":"","hinhThucThi":"","soBaoDanh":"01",
                      "dotThi":"","ghiChuSv":"","isHoanThi":false}],
             "totalPages":1,"rowCount":1}
        """.trimIndent()
        val mapped = codec.decodeFromString(RustPortalCore.ExamPageRows.serializer(), payload)
        assertEquals(1, mapped.rows.size)
        assertTrue(mapped.rows.single().isGiuaKy)
        assertEquals(90, mapped.rows.single().soPhut)
        assertEquals(1, mapped.totalPages)
    }
}
