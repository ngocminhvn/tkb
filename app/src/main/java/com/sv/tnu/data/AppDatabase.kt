package com.sv.tnu.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

data class PeriodEntity(
    val tiet: Int,
    val gioBatDau: String,
    val gioKetThuc: String,
)

data class WeekEntity(
    val hocKy: Int,
    val tuanHocKy: Int,
    val ngayBatDau: String,
    val ngayKetThuc: String,
    val thongTinTuan: String,
)

data class LessonEntity(
    val idTkb: String,
    val hocKy: Int,
    val tuanHocKy: Int,
    val ngayHoc: String,
    val thuKieuSo: Int,
    val tietBatDau: Int,
    val soTiet: Int,
    val maMon: String,
    val tenMon: String,
    val soTinChi: String,
    val maPhong: String,
    val tenGiangVien: String,
    val tenLop: String,
    val maNhom: String,
)

data class LessonNoteEntity(
    val lessonId: String,
    val note: String,
    val updatedAt: Long,
)

data class ExamEntity(
    val id: Long = 0,
    val hocKy: Int,
    val isGiuaKy: Boolean = false,
    val soThuTu: Int = 0,
    val maMon: String = "",
    val tenMon: String = "",
    val ngayThi: String = "", // yyyy-MM-dd
    val gioBatDau: String = "", // HH:mm
    val soPhut: Int = 0,
    val maPhong: String = "",
    val diaDiemThi: String = "",
    val hinhThucThi: String = "",
    val soBaoDanh: String = "",
    val dotThi: String = "",
    val ghiChuSv: String = "",
    val isHoanThi: Boolean = false,
)

/** Coherent week and lesson snapshot from the same SQLite transaction. */
data class CachedTimetable(
    val weeks: List<WeekEntity>,
    val lessons: List<LessonEntity>,
)

interface TkbDao {
    fun periods(): Flow<List<PeriodEntity>>

    suspend fun periodsOnce(): List<PeriodEntity>

    fun weeks(hocKy: Int): Flow<List<WeekEntity>>

    suspend fun weeksOnce(hocKy: Int): List<WeekEntity>

    suspend fun semesterLessonsOnce(hocKy: Int): List<LessonEntity>

    /** Both lists must describe the same committed timetable. */
    suspend fun cachedTimetable(hocKy: Int): CachedTimetable

    fun lessons(hocKy: Int, tuan: Int): Flow<List<LessonEntity>>

    fun semesterLessons(hocKy: Int): Flow<List<LessonEntity>>

    suspend fun lessonsOn(date: String): List<LessonEntity>

    suspend fun examsOn(date: String): List<ExamEntity>

    suspend fun reminderLessonsOn(hocKy: Int, date: String): List<LessonEntity>

    suspend fun reminderExamsOn(hocKy: Int, date: String): List<ExamEntity>

    fun exams(hocKy: Int): Flow<List<ExamEntity>>

    suspend fun examsOnce(hocKy: Int): List<ExamEntity>

    fun lessonNotes(): Flow<List<LessonNoteEntity>>

    suspend fun weekCount(hocKy: Int): Int

    suspend fun examCount(hocKy: Int): Int

    suspend fun insertPeriods(list: List<PeriodEntity>)

    suspend fun insertWeeks(list: List<WeekEntity>)

    suspend fun insertLessons(list: List<LessonEntity>)

    suspend fun insertExams(list: List<ExamEntity>)

    suspend fun upsertLessonNote(note: LessonNoteEntity)

    suspend fun deleteLessonNote(lessonId: String)

    suspend fun clearAllLessonNotes()

    suspend fun clearOldLessonNotes(cutoffTimestamp: Long)

    suspend fun deleteLessons(hocKy: Int)

    suspend fun deleteExams(hocKy: Int)

    suspend fun deleteExamsByType(hocKy: Int, isGiuaKy: Boolean)

    suspend fun deleteWeeks(hocKy: Int)

    suspend fun deletePeriods()

    suspend fun replaceTimetable(
        hocKy: Int,
        periods: List<PeriodEntity>,
        weeks: List<WeekEntity>,
        lessons: List<LessonEntity>,
    )

    suspend fun replaceExams(hocKy: Int, exams: List<ExamEntity>)

    // Each TNUT exam endpoint can fail independently.
    suspend fun replaceExamsByType(hocKy: Int, isGiuaKy: Boolean, exams: List<ExamEntity>)

    /** Remove only the complete, exact fixture inserted by older APKs. */
    suspend fun removeLegacySampleExamsIfExact(hocKy: Int): Boolean
}

/** One native DAO shared by UI, widgets and reminders. */
class AppDatabase private constructor(context: Context) {
    private val dao: TkbDao = com.sv.tnu.rust.RustCacheStore.create(
        context.applicationContext.getDatabasePath("tkb.db"),
    )
    fun tkbDao(): TkbDao = dao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: AppDatabase(context.applicationContext).also { instance = it }
        }
    }
}
