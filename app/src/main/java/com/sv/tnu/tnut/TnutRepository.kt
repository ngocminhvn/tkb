package com.sv.tnu.tnut

import android.content.Context
import android.util.Base64
import android.util.Log
import com.sv.tnu.data.AuthStore
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

/**
 * Nguồn dữ liệu thời khóa biểu: API portal -> Room, UI đọc Flow từ Room nên
 * mất mạng vẫn xem được lịch đã tải.
 */
class TnutRepository(
    private val context: Context,
    private val api: TnutApi,
    private val dao: TkbDao,
    private val authStore: AuthStore,
) {
    /** Học kỳ hiện tại của TNUT, dùng khi chưa lưu học kỳ nào. */
    val defaultHocKy: Int = 20261

    private val _avatarPath = MutableStateFlow<String?>(authStore.avatarPath)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()
    private val _studentName = MutableStateFlow<String?>(authStore.studentName)
    val studentName: StateFlow<String?> = _studentName.asStateFlow()

    fun periodsFlow(): Flow<List<PeriodEntity>> = dao.periods()

    fun weeksFlow(hocKy: Int): Flow<List<WeekEntity>> = dao.weeks(hocKy)

    fun lessonsFlow(hocKy: Int, tuan: Int): Flow<List<LessonEntity>> = dao.lessons(hocKy, tuan)

    fun semesterLessonsFlow(hocKy: Int): Flow<List<LessonEntity>> = dao.semesterLessons(hocKy)

    fun examsFlow(hocKy: Int): Flow<List<ExamEntity>> = dao.exams(hocKy)

    fun lessonNotesFlow(): Flow<List<LessonNoteEntity>> = dao.lessonNotes()

    suspend fun saveLessonNote(lessonId: String, note: String) {
        val normalized = note.trim()
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
    suspend fun refresh(hocKy: Int): Result<Unit> = coroutineScope {
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
            val res = api.weeklyTimetable(WeeklyRequest(filter = WeeklyFilter(hocKy = hocKy)))
            val data = res.data
            when {
                res.code != 200 || data == null ->
                    Result.failure(IllegalStateException(
                        res.message ?: "Không tải được thời khóa biểu (${res.code})."
                    ))
                data.dsTuanTkb.isEmpty() ||
                    data.dsTuanTkb.sumOf { it.dsThoiKhoaBieu.size } == 0 ->
                    Result.failure(IllegalStateException(
                        "Portal trả lịch học rỗng (học kỳ $hocKy). Dữ liệu đã lưu được giữ nguyên."
                    ))
                else -> {
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
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        examSync.await().onFailure { error ->
            Log.w("TnutRepository", "Exam sync failed independently of timetable", error)
        }
        timetableResult
    }

    private suspend fun saveToDb(hocKy: Int, data: TkbData) {
        val periods = data.dsTietTrongNgay.map {
            PeriodEntity(tiet = it.tiet, gioBatDau = it.gioBatDau, gioKetThuc = it.gioKetThuc)
        }
        val weeks = data.dsTuanTkb.map {
            WeekEntity(
                hocKy = hocKy,
                tuanHocKy = it.tuanHocKy,
                ngayBatDau = it.ngayBatDau,
                ngayKetThuc = it.ngayKetThuc,
                thongTinTuan = it.thongTinTuan,
            )
        }
        val lessons = data.dsTuanTkb.flatMap { week ->
            week.dsThoiKhoaBieu.map { lesson ->
                LessonEntity(
                    idTkb = lesson.idTkb,
                    hocKy = hocKy,
                    tuanHocKy = week.tuanHocKy,
                    ngayHoc = lesson.ngayHoc,
                    thuKieuSo = lesson.thuKieuSo,
                    tietBatDau = lesson.tietBatDau,
                    soTiet = lesson.soTiet,
                    maMon = lesson.maMon,
                    tenMon = lesson.tenMon,
                    soTinChi = lesson.soTinChi,
                    maPhong = lesson.maPhong,
                    tenGiangVien = lesson.tenGiangVien,
                    tenLop = lesson.tenLop,
                    maNhom = lesson.maNhom,
                )
            }
        }
        dao.replaceTimetable(hocKy, periods, weeks, lessons)
        TimetableWidgetProvider.updateAll(context)
    }

    /**
     * Retain only live portal cache on startup. Older releases inserted one
     * user's demo exam list into all accounts; remove only the exact known
     * legacy fixture. Never replace genuine saved lessons, exams or notes.
     */
    suspend fun prepareOfflineCache(hocKy: Int) = withContext(Dispatchers.IO) {
        if (dao.removeLegacySampleExamsIfExact(hocKy)) {
            TimetableWidgetProvider.updateAll(context)
        }
    }

    private fun normalizeDateToYmd(dateStr: String): String {
        val clean = dateStr.trim().substringBefore(" ").substringBefore("T")
        val partsSlash = clean.split("/")
        if (partsSlash.size == 3) {
            val day = partsSlash[0].padStart(2, '0')
            val month = partsSlash[1].padStart(2, '0')
            val year = partsSlash[2]
            return "$year-$month-$day"
        }
        val partsDash = clean.split("-")
        if (partsDash.size == 3) {
            return if (partsDash[0].length == 4) {
                val year = partsDash[0]
                val month = partsDash[1].padStart(2, '0')
                val day = partsDash[2].padStart(2, '0')
                "$year-$month-$day"
            } else {
                val day = partsDash[0].padStart(2, '0')
                val month = partsDash[1].padStart(2, '0')
                val year = partsDash[2]
                "$year-$month-$day"
            }
        }
        return clean
    }

    suspend fun fetchAndSaveAvatar(maSv: String): Result<String?> {
        return try {
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
            val response = api.exams(ExamRequest.forSemester(hocKy, isGiuaKy, page))
            if (response.code != 200 || response.data == null) {
                throw IllegalStateException(
                    "Portal không trả lịch thi ${if (isGiuaKy) "giữa kỳ" else "cuối kỳ"} " +
                        "(HTTP/API ${response.code}): ${response.message.orEmpty()}"
                )
            }
            val data = response.data
            val regular = data.dsLichThi.orEmpty()
            val postponed = data.dsLichHoanThi.orEmpty()
            for ((items, hoanThi) in listOf(regular to false, postponed to true)) {
                items.forEach { item ->
                    val date = normalizeDateToYmd(item.ngayThi.orEmpty())
                    if (!hoanThi && (item.maMon.isNullOrBlank() ||
                            !Regex("""\d{4}-\d{2}-\d{2}""").matches(date))) {
                        throw IllegalStateException(
                            "Portal trả buổi thi thiếu mã môn/ngày thi; giữ lịch đã lưu."
                        )
                    }
                    all.add(
                        ExamEntity(
                            hocKy = hocKy,
                            isGiuaKy = isGiuaKy,
                            soThuTu = item.soThuTuInt,
                            maMon = item.maMon?.trim().orEmpty(),
                            tenMon = (item.tenMon ?: item.tenMonEg)?.trim().orEmpty(),
                            ngayThi = date,
                            gioBatDau = item.gioBatDau?.trim().orEmpty(),
                            soPhut = item.soPhutInt,
                            maPhong = item.maPhong?.trim().orEmpty(),
                            diaDiemThi = item.diaDiemThi?.trim().orEmpty(),
                            hinhThucThi = (item.hinhThucThi ?: item.ghiChuHtt)?.trim().orEmpty(),
                            soBaoDanh = (item.soBaoDanh ?: item.toThi)?.trim().orEmpty(),
                            dotThi = item.dotThi?.trim().orEmpty(),
                            ghiChuSv = item.ghiChuSv?.trim().orEmpty(),
                            isHoanThi = hoanThi,
                        )
                    )
                }
            }
            // Portal may omit total_pages. In that case a short page means end.
            // Never commit a partially fetched multi-page schedule.
            val totalPages = data.totalPages?.takeIf { it > 0 }
            val rowsOnPage = regular.size + postponed.size
            val hasNext = if (totalPages != null) page < totalPages
                else rowsOnPage >= ExamRequest.PAGE_SIZE
            if (!hasNext) break
            check(page < maxPages) { "Lịch thi vượt quá $maxPages trang; giữ dữ liệu cũ." }
            page++
        }
        return all.distinctBy { item ->
            listOf(item.isHoanThi.toString(), item.maMon, item.ngayThi,
                item.gioBatDau, item.soBaoDanh, item.maPhong).joinToString("|")
        }
    }

    /**
     * Each source is independent; update a semester/type only after its entire
     * fetch succeeds with actual rows. A valid empty result must not delete
     * manually imported exams or genuine offline cache.
     */
    suspend fun fetchAndSaveExams(hocKy: Int): Result<Unit> {
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

    fun clearAvatar() {
        _avatarPath.value = null
    }
}
