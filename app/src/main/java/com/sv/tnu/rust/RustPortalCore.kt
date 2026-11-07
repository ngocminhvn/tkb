package com.sv.tnu.rust

import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.tnut.ExamDataApi
import com.sv.tnu.tnut.TkbData
import com.sv.tnu.tnut.TnutApiClient
import kotlinx.serialization.Serializable

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
