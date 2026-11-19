package com.sv.tnu.rust

import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.schools.SchoolSessionTokens
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
    private external fun mapLoginNative(payload: ByteArray): ByteArray
    private external fun mapAvatarNative(payload: ByteArray): ByteArray
    private external fun saveAvatarNative(payload: ByteArray, targetPath: String): ByteArray
    private external fun timetableRequestNative(semester: Int): ByteArray
    private external fun examRequestNative(semester: Int, midterm: Boolean, page: Int): ByteArray
    private external fun uniqueExamIndicesNative(keys: Array<String>): IntArray
    private external fun nextExamPageNative(page: Int, totalPages: Int, rowCount: Int): Int
    private external fun unpackUpdateNative(
        packagePath: String,
        destinationPath: String,
        sha256: String,
        compressed: Boolean,
    ): Boolean
    private external fun readReleaseUpdateNative(
        currentVersion: Long,
        allowedPrefix: String,
        payload: ByteArray,
    ): ByteArray
    private external fun hasNextExamPageNative(page: Int, totalPages: Int, rowCount: Int): Boolean
    private external fun normalizeNoteNative(value: String): String
    private external fun normalizeUsernameNative(value: String): String
    private external fun isLogicalUnauthorizedNative(responseBody: ByteArray): Boolean
    private external fun isOfficialPortalOriginNative(scheme: String, hostname: String): Boolean
    private external fun canReuseRotatedTokenNative(current: String, rejected: String): Boolean
    private external fun extractBearerTokenNative(authHeader: String): String
    private external fun shouldAuthenticateRetryNative(isOfficial: Boolean, path: String, responseCount: Int): Boolean
    private external fun shouldFallbackToLoginNative(httpStatus: Int): Boolean
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

    fun mapLogin(rawResponse: ByteArray): SchoolSessionTokens =
        PortalBinaryDecoder.decodeLogin(mapLoginNative(rawResponse))

    fun mapAvatar(rawResponse: ByteArray): PortalBinaryDecoder.Avatar =
        PortalBinaryDecoder.decodeAvatar(mapAvatarNative(rawResponse))

    fun saveAvatar(rawResponse: ByteArray, targetPath: String): PortalBinaryDecoder.SavedAvatar =
        PortalBinaryDecoder.decodeSavedAvatar(saveAvatarNative(rawResponse, targetPath))

    fun timetableRequest(semester: Int): ByteArray = timetableRequestNative(semester)

    fun examRequest(semester: Int, midterm: Boolean, page: Int): ByteArray =
        examRequestNative(semester, midterm, page)

    fun nextExamPage(page: Int, mapped: PortalBinaryDecoder.ExamPageRows): Int? =
        nextExamPageNative(page, mapped.totalPages ?: -1, mapped.rowCount).takeIf { it > 0 }

    data class EncodedExamPage(val bytes: ByteArray, val nextPage: Int?)

    fun encodeExamPage(semester: Int, midterm: Boolean, page: Int, payload: ByteArray): EncodedExamPage {
        val bytes = mapExamBytesNative(semester, midterm, payload)
        val (totalPages, rowCount) = PortalBinaryDecoder.examPageHeader(bytes)
        return EncodedExamPage(bytes, nextExamPageNative(page, totalPages, rowCount).takeIf { it > 0 })
    }

    fun unpackUpdate(packagePath: String, destinationPath: String, sha256: String, compressed: Boolean): Boolean =
        unpackUpdateNative(packagePath, destinationPath, sha256, compressed)

    fun readReleaseUpdate(
        currentVersion: Long,
        allowedPrefix: String,
        payload: ByteArray,
    ): PortalBinaryDecoder.ReleaseUpdate? =
        readReleaseUpdateNative(currentVersion, allowedPrefix, payload)
            .takeIf { it.isNotEmpty() }
            ?.let(PortalBinaryDecoder::decodeReleaseUpdate)

    fun hasNextExamPage(page: Int, totalPages: Int?, rowCount: Int): Boolean =
        hasNextExamPageNative(page, totalPages ?: -1, rowCount)

    fun normalizeNote(value: String): String = normalizeNoteNative(value)

    fun normalizeUsername(value: String): String = normalizeUsernameNative(value)

    fun isLogicalUnauthorized(rawPreview: ByteArray): Boolean =
        isLogicalUnauthorizedNative(rawPreview)

    fun isOfficialPortalOrigin(scheme: String, hostname: String): Boolean =
        isOfficialPortalOriginNative(scheme, hostname)

    fun canReuseRotatedToken(current: String?, rejected: String?): Boolean =
        canReuseRotatedTokenNative(current.orEmpty(), rejected.orEmpty())

    fun extractBearerToken(authHeader: String?): String? =
        extractBearerTokenNative(authHeader.orEmpty()).ifBlank { null }

    fun shouldAuthenticateRetry(isOfficial: Boolean, path: String, responseCount: Int): Boolean =
        shouldAuthenticateRetryNative(isOfficial, path, responseCount)

    private external fun accountChangedNative(previous: String?, username: String): Boolean
    private external fun rotationFieldsNative(refresh: String, idpc: String): Int
    private external fun renewalActionNative(httpStatus: Int, ioFailure: Boolean, access: String): Int

    fun accountChanged(previous: String?, username: String): Boolean =
        accountChangedNative(previous, username)

    fun rotationFields(refresh: String?, idpc: String?): Int =
        rotationFieldsNative(refresh.orEmpty(), idpc.orEmpty())

    enum class RenewalAction { STOP, USE_REFRESH, FALLBACK_LOGIN }

    fun renewalAction(httpStatus: Int, ioFailure: Boolean, access: String?): RenewalAction =
        RenewalAction.entries[renewalActionNative(httpStatus, ioFailure, access.orEmpty())]

    private external fun renewalStartNative(current: String, rejected: String, refresh: String): Int
    private external fun requestKindNative(scheme: String, host: String, path: String): Int

    enum class RenewalStart { REUSE_CURRENT, REFRESH, LOGIN }
    enum class RequestKind { OTHER_ORIGIN, LOGIN, SESSION }

    fun renewalStart(current: String?, rejected: String?, refresh: String?): RenewalStart =
        RenewalStart.entries[renewalStartNative(current.orEmpty(), rejected.orEmpty(), refresh.orEmpty())]

    fun requestKind(scheme: String, host: String, path: String): RequestKind =
        RequestKind.entries[requestKindNative(scheme, host, path)]

    fun shouldFallbackToLogin(httpStatus: Int): Boolean =
        shouldFallbackToLoginNative(httpStatus)

    fun verifyUpdateManifest(
        currentVersionCode: Int,
        allowedPrefix: String,
        rawManifest: ByteArray,
    ): PortalBinaryDecoder.UpdateManifest? {
        val bytes = verifyUpdateManifestNative(currentVersionCode, allowedPrefix, rawManifest)
        if (bytes.isEmpty()) return null
        return PortalBinaryDecoder.decodeUpdateManifest(bytes)
    }

    private external fun examSyncIssueNative(semester: Int, updated: Boolean, errors: Array<String>): String?

    fun examSyncIssue(semester: Int, updated: Boolean, errors: List<String>): String? =
        examSyncIssueNative(semester, updated, errors.toTypedArray())

    fun dedupeExams(rows: List<ExamEntity>): List<ExamEntity> {
        if (rows.size < 2) return rows
        val keys = ArrayList<String>(rows.size * 6)
        for (row in rows) {
            keys.add(if (row.isHoanThi) "1" else "0")
            keys.add(row.maMon)
            keys.add(row.ngayThi)
            keys.add(row.gioBatDau)
            keys.add(row.soBaoDanh)
            keys.add(row.maPhong)
        }
        return uniqueExamIndicesNative(keys.toTypedArray()).map(rows::get)
    }
}

/** Versioned native binary bridge, big-endian integers and length-prefixed UTF-8. */
object PortalBinaryDecoder {
    data class TimetableRows(
        val periods: List<PeriodEntity>,
        val weeks: List<WeekEntity>,
        val lessons: List<LessonEntity>,
    )

    data class ExamPageRows(
        val rows: List<ExamEntity>,
        val totalPages: Int?,
        val rowCount: Int,
    )

    data class Avatar(val name: String, val base64: String?)

    data class ReleaseUpdate(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val packageUrl: String,
        val compression: String,
        val sha256: String,
        val sizeBytes: Long?,
        val changelog: String,
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

        fun optionalString(): String? = if (bool()) string() else null

        fun finish() {
            require(data.available() == 0) { "Unexpected trailing Rust binary bytes" }
        }
    }

    /** Database-only extension: TKB1N, count, then UTF-8 id/note and i64 timestamp. */
    fun decodeDatabaseNotes(bytes: ByteArray): List<com.sv.tnu.data.LessonNoteEntity> {
        val reader = Reader(bytes, 'N'.code.toByte())
        val notes = List(reader.count()) {
            com.sv.tnu.data.LessonNoteEntity(reader.string(), reader.string(), reader.long())
        }
        reader.finish()
        return notes
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
        reader.finish()
        return TimetableRows(periods, weeks, lessons)
    }

    fun examPageHeader(bytes: ByteArray): Pair<Int, Int> {
        val reader = Reader(bytes, 'E'.code.toByte())
        val pages = reader.int()
        val rowCount = reader.count()
        require(reader.count() == rowCount) { "Rust exam page row count mismatch" }
        return pages to rowCount
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

    data class TimelineItem(val source: Int, val date: String, val timeLabel: String)

    fun decodeTimeline(bytes: ByteArray): List<TimelineItem> {
        val reader = Reader(bytes, 'Y'.code.toByte())
        val rows = List(reader.count(200_000)) { TimelineItem(reader.int(), reader.string(), reader.string()) }
        reader.finish()
        return rows
    }

    fun decodeLogin(bytes: ByteArray): SchoolSessionTokens {
        val reader = Reader(bytes, 'L'.code.toByte())
        val session = SchoolSessionTokens(reader.string(), reader.optionalString(), reader.optionalString())
        reader.finish()
        return session
    }

    fun decodeAvatar(bytes: ByteArray): Avatar {
        val reader = Reader(bytes, 'A'.code.toByte())
        val name = reader.string()
        val base64 = reader.optionalString()
        reader.finish()
        return Avatar(name, base64)
    }

    data class SavedAvatar(val name: String, val saved: Boolean)

    fun decodeSavedAvatar(bytes: ByteArray): SavedAvatar {
        val reader = Reader(bytes, 'A'.code.toByte())
        val name = reader.string()
        val saved = reader.bool()
        reader.finish()
        return SavedAvatar(name, saved)
    }

    fun decodeReleaseUpdate(bytes: ByteArray): ReleaseUpdate {
        val reader = Reader(bytes, 'P'.code.toByte())
        val update = ReleaseUpdate(
            versionCode = reader.long(),
            versionName = reader.string(),
            apkUrl = reader.string(),
            packageUrl = reader.string(),
            compression = reader.string(),
            sha256 = reader.string(),
            sizeBytes = reader.long().takeIf { it > 0 },
            changelog = reader.string(),
        )
        reader.finish()
        return update
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
