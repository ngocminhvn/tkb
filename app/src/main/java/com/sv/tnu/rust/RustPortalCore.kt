package com.sv.tnu.rust

import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets

/**
 * Android adapter. Kotlin only passes raw portal HTTP bytes and converts the
 * one compact Rust binary result into existing Room entities. No intermediate
 * Kotlin JSON decoding, encoding, or JSON JNI round-trip occurs.
 */
object RustPortalCore {
    init {
        System.loadLibrary("tkb_core")
    }

    private external fun mapTimetableBytesNative(
        semester: Int,
        fromYmd: String,
        responseBody: ByteArray,
    ): ByteArray
    private external fun mapExamBytesNative(
        semester: Int,
        isMidterm: Boolean,
        responseBody: ByteArray,
    ): ByteArray
    private external fun hasNextExamPageNative(page: Int, totalPages: Int, rowCount: Int): Boolean
    private external fun normalizeNoteNative(value: String): String
    private external fun normalizeUsernameNative(value: String): String
    private external fun isLogicalUnauthorizedNative(responseBody: ByteArray): Boolean
    private external fun verifyUpdateManifestNative(
        currentVersionCode: Int,
        allowedPrefix: String,
        responseBody: ByteArray,
    ): ByteArray

    /** Only upcoming weeks/lessons are sent through TKB1 and decoded into Room. */
    fun mapTimetable(
        semester: Int,
        fromYmd: String,
        rawResponse: ByteArray,
    ): PortalBinaryDecoder.TimetableRows =
        PortalBinaryDecoder.decodeTimetable(mapTimetableBytesNative(semester, fromYmd, rawResponse))

    fun mapExamPage(
        semester: Int,
        isMidterm: Boolean,
        rawResponse: ByteArray,
    ): PortalBinaryDecoder.ExamPageRows =
        PortalBinaryDecoder.decodeExams(mapExamBytesNative(semester, isMidterm, rawResponse))

    fun hasNextExamPage(page: Int, totalPages: Int?, rowCount: Int): Boolean =
        hasNextExamPageNative(page, totalPages ?: -1, rowCount)

    fun normalizeNote(value: String): String = normalizeNoteNative(value)

    fun normalizeUsername(value: String): String = normalizeUsernameNative(value)

    fun isLogicalUnauthorized(rawPreview: ByteArray): Boolean =
        isLogicalUnauthorizedNative(rawPreview)

    fun verifyUpdateManifest(
        currentVersionCode: Int,
        allowedPrefix: String,
        rawManifest: ByteArray,
    ): PortalBinaryDecoder.UpdateManifest? {
        val bytes = verifyUpdateManifestNative(currentVersionCode, allowedPrefix, rawManifest)
        if (bytes.isEmpty()) return null
        return PortalBinaryDecoder.decodeUpdateManifest(bytes)
    }

    // Cross-page dedup is linear and tiny. Do it once on already decoded Room
    // rows instead of turning all records back into JSON for another JNI call.
    private data class ExamKey(
        val postponed: Boolean,
        val course: String,
        val date: String,
        val time: String,
        val candidate: String,
        val room: String,
    )

    fun dedupeExams(rows: List<ExamEntity>): List<ExamEntity> =
        rows.distinctBy {
            ExamKey(it.isHoanThi, it.maMon, it.ngayThi, it.gioBatDau, it.soBaoDanh, it.maPhong)
        }
}

/** Versioned native binary bridge, big-endian integers and length-prefixed UTF-8. */
object PortalBinaryDecoder {
    data class TimetableRows(
        val periods: List<PeriodEntity>,
        val weeks: List<WeekEntity>,
        val lessons: List<LessonEntity>,
        val weeklySubjects: Int,
        val weeklyPeriods: Int,
    )

    data class ExamPageRows(
        val rows: List<ExamEntity>,
        val totalPages: Int?,
        val rowCount: Int,
    )

    data class UpdateManifest(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val apkSha256: String,
        val apkSize: Long,
        val compressedApkUrl: String,
        val compressedApkSha256: String,
        val compressedApkSize: Long,
        val notes: String,
        val publishedAt: String,
    )

    private class Reader(bytes: ByteArray, type: Byte) {
        private val data = DataInputStream(ByteArrayInputStream(bytes))

        init {
            val signature = ByteArray(5)
            data.readFully(signature)
            require(signature[0] == 'T'.code.toByte() &&
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

        fun count(): Int = int().also {
            require(it in 0..100_000) { "Invalid Rust record count: $it" }
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

    fun decodeTimetable(bytes: ByteArray): TimetableRows {
        val reader = Reader(bytes, 'T'.code.toByte())
        val periods = List(reader.count()) {
            PeriodEntity(
                tiet = reader.int(),
                gioBatDau = reader.string(),
                gioKetThuc = reader.string(),
            )
        }
        val weeks = List(reader.count()) {
            WeekEntity(
                hocKy = reader.int(),
                tuanHocKy = reader.int(),
                ngayBatDau = reader.string(),
                ngayKetThuc = reader.string(),
                thongTinTuan = reader.string(),
            )
        }
        val lessons = List(reader.count()) {
            LessonEntity(
                idTkb = reader.string(),
                hocKy = reader.int(),
                tuanHocKy = reader.int(),
                ngayHoc = reader.string(),
                thuKieuSo = reader.int(),
                tietBatDau = reader.int(),
                soTiet = reader.int(),
                maMon = reader.string(),
                tenMon = reader.string(),
                soTinChi = reader.string(),
                maPhong = reader.string(),
                tenGiangVien = reader.string(),
                tenLop = reader.string(),
                maNhom = reader.string(),
            )
        }
        // Footer is calculated before old lesson rows are excluded by Rust.
        val weeklySubjects = reader.int()
        val weeklyPeriods = reader.int()
        require(weeklySubjects >= 0 && weeklyPeriods >= 0) { "Invalid weekly lesson totals" }
        reader.finish()
        return TimetableRows(periods, weeks, lessons, weeklySubjects, weeklyPeriods)
    }

    fun decodeExams(bytes: ByteArray): ExamPageRows {
        val reader = Reader(bytes, 'E'.code.toByte())
        val rawPages = reader.int()
        val totalPages = rawPages.takeIf { it > 0 }
        val rowCount = reader.count()
        val rows = List(reader.count()) {
            ExamEntity(
                id = reader.long(),
                hocKy = reader.int(),
                isGiuaKy = reader.bool(),
                soThuTu = reader.int(),
                maMon = reader.string(),
                tenMon = reader.string(),
                ngayThi = reader.string(),
                gioBatDau = reader.string(),
                soPhut = reader.int(),
                maPhong = reader.string(),
                diaDiemThi = reader.string(),
                hinhThucThi = reader.string(),
                soBaoDanh = reader.string(),
                dotThi = reader.string(),
                ghiChuSv = reader.string(),
                isHoanThi = reader.bool(),
            )
        }
        reader.finish()
        require(rowCount == rows.size) { "Rust exam page row count mismatch" }
        return ExamPageRows(rows, totalPages, rowCount)
    }

    fun decodeUpdateManifest(bytes: ByteArray): UpdateManifest {
        val reader = Reader(bytes, 'U'.code.toByte())
        val manifest = UpdateManifest(
            versionCode = reader.int(),
            versionName = reader.string(),
            apkUrl = reader.string(),
            apkSha256 = reader.string(),
            apkSize = reader.long(),
            compressedApkUrl = reader.string(),
            compressedApkSha256 = reader.string(),
            compressedApkSize = reader.long(),
            notes = reader.string(),
            publishedAt = reader.string(),
        )
        reader.finish()
        return manifest
    }
}
