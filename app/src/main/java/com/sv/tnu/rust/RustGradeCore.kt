package com.sv.tnu.rust

import com.sv.tnu.grades.GradeComponent
import com.sv.tnu.grades.GradeCourse
import com.sv.tnu.grades.GradeOverview
import com.sv.tnu.grades.GradeSemester
import com.sv.tnu.grades.GradeTranscript
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets

object RustGradeCore {
    init {
        System.loadLibrary("tkb_core")
    }

    private external fun mapGradesBytesNative(
        studentId: String,
        studentName: String,
        gradesBody: ByteArray,
        symbolsBody: ByteArray,
        updatedAt: Long,
    ): ByteArray

    private external fun saveCachedGradesNative(path: String, wireBytes: ByteArray): Boolean
    private external fun loadCachedGradesNative(path: String): ByteArray?

    fun saveCachedGrades(path: String, wireBytes: ByteArray): Boolean =
        saveCachedGradesNative(path, wireBytes)

    fun loadCachedGrades(path: String): ByteArray? =
        loadCachedGradesNative(path)

    fun mapGradesBytes(
        studentId: String,
        studentName: String,
        gradesRaw: ByteArray,
        symbolsRaw: ByteArray = ByteArray(0),
        updatedAt: Long = System.currentTimeMillis(),
    ): ByteArray =
        mapGradesBytesNative(studentId, studentName, gradesRaw, symbolsRaw, updatedAt)

    fun mapGrades(
        studentId: String,
        studentName: String,
        gradesRaw: ByteArray,
        symbolsRaw: ByteArray = ByteArray(0),
        updatedAt: Long = System.currentTimeMillis(),
    ): GradeTranscript =
        decodeGrades(mapGradesBytes(studentId, studentName, gradesRaw, symbolsRaw, updatedAt))

    private class Reader(bytes: ByteArray, type: Byte) {
        private val data = DataInputStream(ByteArrayInputStream(bytes))

        init {
            val signature = ByteArray(5)
            data.readFully(signature)
            require(
                signature[0] == 'T'.code.toByte() &&
                    signature[1] == 'K'.code.toByte() &&
                    signature[2] == 'B'.code.toByte() &&
                    signature[3] == '1'.code.toByte() &&
                    signature[4] == type
            ) { "Rust/Android binary protocol mismatch" }
        }

        fun int(): Int = data.readInt()
        fun long(): Long = data.readLong()
        fun bool(): Boolean = when (data.readUnsignedByte()) {
            0 -> false
            1 -> true
            else -> throw IllegalArgumentException("Invalid Rust boolean")
        }

        fun count(max: Int = 100_000): Int = int().also {
            require(it in 0..max) { "Invalid Rust record count: $it" }
        }

        fun string(): String {
            val length = int()
            require(length in 0..8_388_608 && length <= data.available()) {
                "Invalid Rust string length: $length"
            }
            val raw = ByteArray(length)
            data.readFully(raw)
            return String(raw, StandardCharsets.UTF_8)
        }

        fun finish() {
            require(data.available() == 0) { "Unexpected trailing Rust binary bytes" }
        }
    }

    fun decodeGrades(bytes: ByteArray): GradeTranscript {
        val reader = Reader(bytes, 'G'.code.toByte())
        val studentId = reader.string()
        val studentName = reader.string()
        val updatedAt = reader.long()
        val overview = GradeOverview(
            dtbTichLuy4 = reader.string(),
            dtbTichLuy10 = reader.string(),
            soTinChiTichLuy = reader.string(),
            xepLoaiHocTap = reader.string(),
            totalCourses = reader.int(),
            totalPassedCredits = reader.string(),
        )
        val semesterCount = reader.count()
        val semesters = ArrayList<GradeSemester>(semesterCount)
        for (i in 0 until semesterCount) {
            val hocKy = reader.string()
            val tenHocKy = reader.string()
            val dtbHk10 = reader.string()
            val dtbHk4 = reader.string()
            val soTinChiHk = reader.string()
            val diemRlHk = reader.string()
            val phanLoaiRlHk = reader.string()
            val dtbTichLuy10 = reader.string()
            val dtbTichLuy4 = reader.string()
            val soTinChiTichLuy = reader.string()
            val xepLoaiHocTap = reader.string()
            val diemRlTichLuy = reader.string()
            val courseCount = reader.count()
            val courses = ArrayList<GradeCourse>(courseCount)
            for (j in 0 until courseCount) {
                val maMon = reader.string()
                val tenMon = reader.string()
                val nhomTo = reader.string()
                val soTinChi = reader.string()
                val diemThi = reader.string()
                val diemGiuaKy = reader.string()
                val diemTk10 = reader.string()
                val diemTk4 = reader.string()
                val diemChu = reader.string()
                val ketQua = reader.string()
                val isDat = reader.bool()
                val componentCount = reader.count()
                val components = ArrayList<GradeComponent>(componentCount)
                for (k in 0 until componentCount) {
                    components.add(
                        GradeComponent(
                            kyHieu = reader.string(),
                            tenThanhPhan = reader.string(),
                            diem = reader.string(),
                        )
                    )
                }
                courses.add(
                    GradeCourse(
                        maMon = maMon,
                        tenMon = tenMon,
                        nhomTo = nhomTo,
                        soTinChi = soTinChi,
                        diemThi = diemThi,
                        diemGiuaKy = diemGiuaKy,
                        diemTk10 = diemTk10,
                        diemTk4 = diemTk4,
                        diemChu = diemChu,
                        ketQua = ketQua,
                        isDat = isDat,
                        thanhPhan = components,
                    )
                )
            }
            semesters.add(
                GradeSemester(
                    hocKy = hocKy,
                    tenHocKy = tenHocKy,
                    dtbHk10 = dtbHk10,
                    dtbHk4 = dtbHk4,
                    soTinChiHk = soTinChiHk,
                    diemRlHk = diemRlHk,
                    phanLoaiRlHk = phanLoaiRlHk,
                    dtbTichLuy10 = dtbTichLuy10,
                    dtbTichLuy4 = dtbTichLuy4,
                    soTinChiTichLuy = soTinChiTichLuy,
                    xepLoaiHocTap = xepLoaiHocTap,
                    diemRlTichLuy = diemRlTichLuy,
                    courses = courses,
                )
            )
        }
        val symbolCount = reader.count()
        val symbols = HashMap<String, String>(symbolCount)
        for (i in 0 until symbolCount) {
            val k = reader.string()
            val v = reader.string()
            symbols[k] = v
        }
        reader.finish()
        return GradeTranscript(
            studentId = studentId,
            studentName = studentName,
            semesters = semesters,
            overview = overview,
            updatedAt = updatedAt,
            symbols = symbols,
        )
    }
}
