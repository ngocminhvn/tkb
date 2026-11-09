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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
    // irrespective of a different device timezone.
    private fun todayInVietnam(): String {
        val clock = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            clock.get(Calendar.YEAR),
            clock.get(Calendar.MONTH) + 1,
            clock.get(Calendar.DAY_OF_MONTH),
        )
    }

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

    suspend fun loadSemesters(): Result<List<Semester>> {
        return try {
            val res = api.semesters()
            if (res.code == 200 && res.data != null) {
                Result.success(res.data.dsHocKy)
            } else {
                Result.failure(IllegalStateException(res.message ?: "Không tải được danh sách học kỳ (${res.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synchronize the timetable and exams on every requested portal sync.
     * These API sources are independent: failed/empty timetable requests must
     * never suppress the exam request, and neither may wipe the other's Room
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
            // This TNUT endpoint exposes a semester filter, not a verified
            // server-side from-date parameter. Request every available week,
            // then Rust discards past weeks and past lessons BEFORE creating
            // the binary rows for Room; no Kotlin JSON round-trip.
            val data = withContext(Dispatchers.IO) {
                val todayYmd = todayInVietnam()
                val raw = api.weeklyTimetable(
                    WeeklyRequest(filter = WeeklyFilter(hocKy = hocKy))
                ).use { it.bytes() }
                RustPortalCore.mapTimetable(hocKy, todayYmd, raw)
            }
            saveToDb(hocKy, data)
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

    private suspend fun saveToDb(
        hocKy: Int,
        data: com.sv.tnu.rust.PortalBinaryDecoder.TimetableRows,
    ) {
        // Native decoder has already checked the response; only then commit.
        // Keep the original Room transaction and all existing notes.
        dao.replaceTimetable(hocKy, data.periods, data.weeks, data.lessons)
        TimetableWidgetProvider.updateAll(context)
    }

    /**
     * Retain only live portal cache on startup. Older releases inserted one
     * user's demo exam list into all accounts; remove only the exact known
     * legacy fixture. Never replace genuine saved lessons, exams or notes.
     */
    override suspend fun prepareOfflineCache(hocKy: Int) = withContext(Dispatchers.IO) {
        if (dao.removeLegacySampleExamsIfExact(hocKy)) {
            TimetableWidgetProvider.updateAll(context)
        }
    }

    override suspend fun fetchAndSaveAvatar(maSv: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val res = api.avatar(maSv)
            val profile = res.data?.thongTinSinhVien
            val name = listOf(profile?.hoTen, profile?.tenSinhVien, profile?.tenSv)
                .firstOrNull { !it.isNullOrBlank() }?.trim()
            if (!name.isNullOrBlank()) {
                authStore.studentName = name
                _studentName.value = name
            }
            val rawImage = profile?.image?.takeIf { it.isNotBlank() }
            if (rawImage != null) {
                val base64Str = if (rawImage.contains(",")) rawImage.substringAfter(",") else rawImage
                val bytes = Base64.decode(base64Str, Base64.DEFAULT)
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
     * A failed page invalidates this batch; caller then keeps the Room cache.
     */
    private suspend fun loadPortalExams(hocKy: Int, isGiuaKy: Boolean): List<ExamEntity> {
        val all = mutableListOf<ExamEntity>()
        val maxPages = 50
        var page = 1
        while (true) {
            val mapped = withContext(Dispatchers.IO) {
                val raw = api.exams(
                    ExamRequest.forSemester(hocKy, isGiuaKy, page)
                ).use { it.bytes() }
                // Rust rejects invalid portal pages before any Room replacement.
                RustPortalCore.mapExamPage(hocKy, isGiuaKy, raw)
            }
            all.addAll(mapped.rows)
            // Portal may omit total_pages. In that case a short page means end.
            // Never commit a partially fetched multi-page schedule.
            val hasNext = RustPortalCore.hasNextExamPage(page, mapped.totalPages, mapped.rowCount)
            if (!hasNext) break
            check(page < maxPages) { "Lịch thi vượt quá $maxPages trang; giữ dữ liệu cũ." }
            page++
        }
        return RustPortalCore.dedupeExams(all)
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
                val exams = loadPortalExams(hocKy, isGiuaKy)
                if (exams.isNotEmpty()) {
                    dao.replaceExamsByType(hocKy, isGiuaKy, exams)
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
        if (failures.isNotEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "Đồng bộ lịch thi chưa hoàn tất: ${failures.joinToString("; ")}. " +
                        "Dữ liệu hợp lệ đã tải được lưu, dữ liệu còn lại được giữ nguyên."
                )
            )
        }
        if (updated) return Result.success(Unit)
        return Result.failure(
            IllegalStateException(
                "Portal trả danh sách thi giữa kỳ và cuối kỳ rỗng cho học kỳ $hocKy. " +
                    "Không có dữ liệu mới để đồng bộ; lịch đã lưu được giữ nguyên."
            )
        )
    }

    override fun clearAvatar() {
        _avatarPath.value = null
        _studentName.value = null
    }
}
