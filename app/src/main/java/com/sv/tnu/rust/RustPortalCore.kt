package com.sv.tnu.rust

import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.tnut.ExamDataApi
import com.sv.tnu.tnut.TkbData
import com.sv.tnu.tnut.TnutApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Android-side transport adapter for business rules in Rust.
 * Rust validates and constructs the Room-shaped records; Android's existing
 * Room DAO remains the storage adapter to preserve historical migrations and
 * note flows. Do not call these methods on the main/UI thread.
 */
object RustPortalCore {
    init {
        System.loadLibrary("tkb_core")
    }

    @Serializable
    data class TimetableRows(
        val periods: List<PeriodEntity>,
        val weeks: List<WeekEntity>,
        val lessons: List<LessonEntity>,
    )

    @Serializable
    data class ExamPageRows(
        val rows: List<ExamEntity>,
        val totalPages: Int? = null,
        val rowCount: Int,
    )

    private external fun mapTimetableNative(semester: Int, payload: String): String
    private external fun mapExamPageNative(
        semester: Int,
        isMidterm: Boolean,
        payload: String,
    ): String

    private external fun dedupeExamsNative(payload: String): String
    private external fun hasNextExamPageNative(page: Int, totalPages: Int, rowCount: Int): Boolean
    private external fun normalizeNoteNative(value: String): String
    private external fun normalizeUsernameNative(value: String): String

    fun normalizeNote(value: String): String = normalizeNoteNative(value)

    fun normalizeUsername(value: String): String = normalizeUsernameNative(value)

    fun hasNextExamPage(page: Int, totalPages: Int?, rowCount: Int): Boolean =
        hasNextExamPageNative(page, totalPages ?: -1, rowCount)

    fun dedupeExams(rows: List<ExamEntity>): List<ExamEntity> {
        val codec = TnutApiClient.json()
        val serializer = ListSerializer(ExamEntity.serializer())
        val payload = codec.encodeToString(serializer, rows)
        return codec.decodeFromString(serializer, dedupeExamsNative(payload))
    }

    fun mapTimetable(semester: Int, data: TkbData): TimetableRows {
        val codec = TnutApiClient.json()
        val source = codec.encodeToString(TkbData.serializer(), data)
        return codec.decodeFromString(
            TimetableRows.serializer(),
            mapTimetableNative(semester, source),
        )
    }

    fun mapExamPage(semester: Int, isMidterm: Boolean, data: ExamDataApi): ExamPageRows {
        val codec = TnutApiClient.json()
        val source = codec.encodeToString(ExamDataApi.serializer(), data)
        return codec.decodeFromString(
            ExamPageRows.serializer(),
            mapExamPageNative(semester, isMidterm, source),
        )
    }
}
