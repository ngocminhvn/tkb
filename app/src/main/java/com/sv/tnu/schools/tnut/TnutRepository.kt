package com.sv.tnu.schools.tnut

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sv.tnu.rust.RustPortalCore
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.CachedTimetable
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.LessonNoteEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.TkbDao
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.widget.TimetableWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Nguồn dữ liệu thời khóa biểu: API portal -> Room, UI đọc Flow từ Room nên
 * mất mạng vẫn xem được lịch đã tải.
 */
class TnutRepository(
    private val context: Context,
    private val api: TnutApi,
    private val dao: TkbDao,
    private val authStore: AuthStore,
) : SchoolRepository {
    override val schoolName: String = TnutSchoolProvider.info.displayName
    /** Học kỳ hiện tại của TNUT, dùng khi chưa lưu học kỳ nào. */
    override val defaultHocKy: Int = 20261

    // Use Vietnam calendar day as the cut-off for every portal sync,
    // computed directly by Rust native core.
    private fun todayInVietnam(): String =
        com.sv.tnu.rust.RustScheduleCore.todayVietnamYmd()

    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    private val _avatarPath = MutableStateFlow<String?>(authStore.avatarPath)
    override val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()
    private val _studentName = MutableStateFlow<String?>(authStore.studentName)
    override val studentName: StateFlow<String?> = _studentName.asStateFlow()
    private val _lastSyncWarning = MutableStateFlow<String?>(null)
    override val lastSyncWarning: StateFlow<String?> = _lastSyncWarning.asStateFlow()

    override fun periodsFlow(): Flow<List<PeriodEntity>> = dao.periods()

    override fun weeksFlow(hocKy: Int): Flow<List<WeekEntity>> = dao.weeks(hocKy)

    /** One-shot, transactionally consistent cache read for the first frame. */
    override suspend fun cachedTimetable(hocKy: Int): CachedTimetable = dao.cachedTimetable(hocKy)

    override fun cachedTimetableFlow(hocKy: Int): Flow<CachedTimetable> =
        (dao as? com.sv.tnu.rust.RustCacheStore)?.cachedTimetableFlow(hocKy)
            ?: kotlinx.coroutines.flow.combine(dao.weeks(hocKy), dao.semesterLessons(hocKy)) { weeks, lessons ->
                CachedTimetable(weeks, lessons)
            }

    fun lessonsFlow(hocKy: Int, tuan: Int): Flow<List<LessonEntity>> = dao.lessons(hocKy, tuan)

    override fun semesterLessonsFlow(hocKy: Int): Flow<List<LessonEntity>> = dao.semesterLessons(hocKy)

    override fun examsFlow(hocKy: Int): Flow<List<ExamEntity>> = dao.exams(hocKy)

    override fun lessonNotesFlow(): Flow<List<LessonNoteEntity>> = dao.lessonNotes()

    override suspend fun saveLessonNote(lessonId: String, note: String) {
        val normalized = RustPortalCore.normalizeNote(note)
        if (normalized.isEmpty()) {
            dao.deleteLessonNote(lessonId)
        } else {
            dao.upsertLessonNote(
                LessonNoteEntity(
                    lessonId = lessonId,
                    note = normalized,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Synchronize the timetable and exams on every requested portal sync.
     * These API sources are independent: failed/empty timetable requests must
     * never suppress the exam request, and neither may wipe the other's SQLite
     * cache. Both run concurrently, but each uses its own transactional write.
     */
    override suspend fun refresh(hocKy: Int): Result<Unit> = coroutineScope {
        _lastSyncWarning.value = null
        val examSync = async {
            try {
                fetchAndSaveExams(hocKy)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val timetableResult = try {
            withContext(Dispatchers.IO) {
                val todayYmd = todayInVietnam()
                val raw = api.weeklyTimetable(
                    RustPortalCore.timetableRequest(hocKy).toRequestBody(jsonMediaType)
                ).use { it.bytes() }
                dao.commitPortalTimetable(hocKy, todayYmd, raw)
            }
            TimetableWidgetProvider.updateAll(context)
            authStore.hocKy = hocKy
            authStore.lastSyncTime = System.currentTimeMillis()
            if (authStore.avatarPath.isNullOrBlank() &&
                !authStore.username.isNullOrBlank()) {
                try {
                    fetchAndSaveAvatar(authStore.username!!)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("TnutRepository", "Could not refresh avatar", e)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        examSync.await().onFailure { error ->
            Log.w("TnutRepository", "Exam sync failed independently of timetable", error)
            _lastSyncWarning.value = error.message ?: error.javaClass.simpleName
        }
        timetableResult
    }

    /**
     * Retain only live portal cache on startup. Older releases inserted one
     * user's demo exam list into all accounts; remove only the exact known
     * legacy fixture. Never replace genuine saved lessons, exams or notes.
     */
    override suspend fun prepareOfflineCache(hocKy: Int) = withContext(Dispatchers.IO) {
        // Old demo rows share their identity with genuine portal rows.
        if (authStore.lastSyncTime == 0L && !authStore.hasCommittedFinalExams(hocKy) &&
            dao.removeLegacySampleExamsIfExact(hocKy)
        ) {
            TimetableWidgetProvider.updateAll(context)
        }
    }

    override suspend fun fetchAndSaveAvatar(maSv: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val profile = api.avatar(maSv, "{}".toByteArray().toRequestBody(jsonMediaType)).use { RustPortalCore.mapAvatar(it.bytes()) }
            if (profile.name.isNotEmpty()) {
                authStore.studentName = profile.name
                _studentName.value = profile.name
            }
            if (profile.base64 != null) {
                val bytes = Base64.decode(profile.base64, Base64.DEFAULT)
                val file = File(context.filesDir, "avatar_${maSv}.jpg")
                file.writeBytes(bytes)
                val path = file.absolutePath
                authStore.avatarPath = path
                _avatarPath.value = path
                Result.success(path)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read every published exam page for one semester/type. The portal expects
     * filter.is_giua_ky AND additional.paging/ordering even for default values.
     * A failed page invalidates this batch; caller then keeps the SQLite cache.
     */
    private suspend fun loadPortalExamPages(hocKy: Int, isGiuaKy: Boolean): List<ByteArray> {
        val pages = mutableListOf<ByteArray>()
        var page = 1
        while (true) {
            val mapped = withContext(Dispatchers.IO) {
                val raw = api.exams(
                    RustPortalCore.examRequest(hocKy, isGiuaKy, page).toRequestBody(jsonMediaType)
                ).use { it.bytes() }
                // Rust rejects invalid portal pages before any SQLite replacement.
                RustPortalCore.encodeExamPage(hocKy, isGiuaKy, page, raw)
            }
            pages.add(mapped.bytes)
            // Portal may omit total_pages. In that case a short page means end.
            // Never commit a partially fetched multi-page schedule.
            page = mapped.nextPage ?: break
        }
        return pages
    }

    /**
     * Each source is independent; update a semester/type only after its entire
     * fetch succeeds with actual rows. A valid empty result must not delete
     * manually imported exams or genuine offline cache.
     */
    override suspend fun fetchAndSaveExams(hocKy: Int): Result<Unit> {
        var updated = false
        val failures = mutableListOf<String>()
        for (isGiuaKy in listOf(false, true)) {
            try {
                val pages = loadPortalExamPages(hocKy, isGiuaKy)
                if (dao.commitPortalExamPages(hocKy, isGiuaKy, pages)) {
                    if (!isGiuaKy) authStore.markFinalExamsCommitted(hocKy)
                    updated = true
                } else {
                    Log.i("TnutRepository", "Portal returned zero ${if (isGiuaKy) "midterm" else "final"} exams for semester $hocKy")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("TnutRepository", "Could not fetch exam type isGiuaKy=$isGiuaKy", e)
                failures.add(e.message ?: e.javaClass.simpleName)
            }
        }
        if (updated) {
            TimetableWidgetProvider.updateAll(context)
        }
        val issue = RustPortalCore.examSyncIssue(hocKy, updated, failures)
        return if (issue == null) Result.success(Unit) else Result.failure(IllegalStateException(issue))
    }

    override fun clearAvatar() {
        _avatarPath.value = null
        _studentName.value = null
    }
}
