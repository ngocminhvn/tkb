package com.sv.tnu.rust

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validate the Rust -> Room binary contract without loading JNI on host JVM.
 * Rust cargo tests independently produce the corresponding wire payloads.
 */
class RustPortalModelsTest {
    private fun DataOutputStream.utf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    @Test
    fun timetableRowsReadFromCompactBinaryWithoutJson() {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.write("TKB1T".toByteArray(Charsets.US_ASCII))
                out.writeInt(1) // periods
                out.writeInt(1)
                out.utf8("07:15")
                out.utf8("08:05")
                out.writeInt(1) // weeks
                out.writeInt(20261)
                out.writeInt(2)
                out.utf8("02/11/2026")
                out.utf8("08/11/2026")
                out.utf8("Tuần 2")
                out.writeInt(1) // lessons
                out.utf8("lesson1")
                out.writeInt(20261)
                out.writeInt(2)
                out.utf8("2026-11-07")
                out.writeInt(7)
                out.writeInt(1)
                out.writeInt(2)
                listOf("MAT01", "Toán", "2", "101", "GV", "A", "01")
                    .forEach(out::utf8)
            }
        }.toByteArray()

        val mapped = PortalBinaryDecoder.decodeTimetable(bytes)
        assertEquals(1, mapped.periods.size)
        assertEquals("07:15", mapped.periods.single().gioBatDau)
        assertEquals(20261, mapped.weeks.single().hocKy)
        assertEquals("lesson1", mapped.lessons.single().idTkb)
        assertEquals("Toán", mapped.lessons.single().tenMon)
    }

    @Test
    fun examRowsReadWithOriginalRoomColumnsAndMidtermFlags() {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.write("TKB1E".toByteArray(Charsets.US_ASCII))
                out.writeInt(1) // total pages
                out.writeInt(1) // row count
                out.writeInt(1) // records
                out.writeLong(0L)
                out.writeInt(20261)
                out.writeByte(1) // isGiuaKy
                out.writeInt(1)
                listOf("MAT01", "Toán", "2026-11-07", "07:00")
                    .forEach(out::utf8)
                out.writeInt(90)
                listOf("P101", "", "", "01", "", "")
                    .forEach(out::utf8)
                out.writeByte(0) // isHoanThi
            }
        }.toByteArray()

        val page = PortalBinaryDecoder.decodeExams(bytes)
        assertEquals(1, page.totalPages)
        assertEquals(1, page.rowCount)
        val exam = page.rows.single()
        assertEquals("MAT01", exam.maMon)
        assertTrue(exam.isGiuaKy)
        assertFalse(exam.isHoanThi)
        assertEquals(90, exam.soPhut)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesWrongBinaryProtocolBeforeUsingRoom() {
        PortalBinaryDecoder.decodeExams("JSON instead of native records".toByteArray())
    }
}
