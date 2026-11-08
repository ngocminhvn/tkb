package com.sv.tnu.schools

import android.content.Context
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.CachedTimetable
import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.LessonNoteEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.TkbDao
import com.sv.tnu.data.WeekEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

object SchoolIds { const val TNUT: String = "tnut" }

data class SchoolInfo(val id: String, val displayName: String, val defaultTerm: Int)

data class SchoolSessionTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idpc: String?,
)

/** UI only reads generic Room entities. School adapters map their own APIs. */
interface SchoolRepository {
    val schoolName: String
    val defaultHocKy: Int
    val avatarPath: StateFlow<String?>
    val studentName: StateFlow<String?>
    /** Report independent exam-sync warnings without losing a successful timetable save. */
    val lastSyncWarning: StateFlow<String?>
    fun periodsFlow(): Flow<List<PeriodEntity>>
    fun weeksFlow(hocKy: Int): Flow<List<WeekEntity>>
    fun semesterLessonsFlow(hocKy: Int): Flow<List<LessonEntity>>
    fun examsFlow(hocKy: Int): Flow<List<ExamEntity>>
    fun lessonNotesFlow(): Flow<List<LessonNoteEntity>>
    suspend fun cachedTimetable(hocKy: Int): CachedTimetable
    suspend fun saveLessonNote(lessonId: String, note: String)
    suspend fun refresh(hocKy: Int): Result<Unit>
    suspend fun prepareOfflineCache(hocKy: Int)
    suspend fun fetchAndSaveAvatar(studentId: String): Result<String?>
    suspend fun fetchAndSaveExams(hocKy: Int): Result<Unit>
    fun clearAvatar()
}

interface SchoolSession {
    val repository: SchoolRepository
    suspend fun login(username: String, password: String)
}

interface SchoolProvider {
    val info: SchoolInfo
    fun open(context: Context, authStore: AuthStore, dao: TkbDao): SchoolSession
}
