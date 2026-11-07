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

    fun periodsFlow(): Flow<List<PeriodEntity>> = dao.periods()

    fun weeksFlow(hocKy: Int): Flow<List<WeekEntity>> = dao.weeks(hocKy)

    fun lessonsFlow(hocKy: Int, tuan: Int): Flow<List<LessonEntity>> = dao.lessons(hocKy, tuan)

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

    /** Tải lịch tuần của học kỳ từ API rồi ghi đè vào Room trong 1 transaction. */
    suspend fun refresh(hocKy: Int): Result<Unit> {
        return try {
            // Keep cached timetable, exams and personal notes untouched until the
            // portal returns valid data. Room replaces timetable rows atomically.
            // Notes are user-owned data and must never expire on a sync timer.
            val res = api.weeklyTimetable(WeeklyRequest(filter = WeeklyFilter(hocKy = hocKy)))
            if (res.code != 200 || res.data == null) {
                return Result.failure(
                    IllegalStateException(res.message ?: "Không tải được thời khóa biểu (${res.code})"),
                )
            }
            saveToDb(hocKy, res.data)
            authStore.hocKy = hocKy
            // Save successful timetable sync immediately; auxiliary exam/avatar
            // requests can be slow or fail without invalidating the timetable.
            authStore.lastSyncTime = System.currentTimeMillis()
            // Đồng thời tải lịch thi (cả giữa kỳ và cuối kỳ)
            try {
                fetchAndSaveExams(hocKy)
            } catch (_: Exception) {}
            // Đồng thời tải avatar nếu chưa có
            if (authStore.avatarPath.isNullOrBlank() && !authStore.username.isNullOrBlank()) {
                try {
                    fetchAndSaveAvatar(authStore.username!!)
                } catch (_: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val rawImage = res.data?.thongTinSinhVien?.image?.takeIf { it.isNotBlank() }
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

    suspend fun fetchAndSaveExams(hocKy: Int): Result<Unit> {
        return try {
            val finalExams = mutableListOf<ExamEntity>()
            var fetchedCuoiKy = false

            // Lịch thi cuối kỳ
            try {
                val resCuoiKy = api.exams(ExamRequest(filter = ExamFilter(hocKy = hocKy, isGiuaKy = false)))
                Log.d("TnutRepository", "fetch exams cuoiky result: code=${resCuoiKy.code}, items=${resCuoiKy.data?.dsLichThi?.size}")
                if ((resCuoiKy.code == 200 || resCuoiKy.result) && resCuoiKy.data != null) {
                    fetchedCuoiKy = true
                    resCuoiKy.data?.let { data ->
                        data.dsLichThi?.forEach {
                            finalExams.add(
                                ExamEntity(
                                    hocKy = hocKy,
                                    isGiuaKy = false,
                                    soThuTu = it.soThuTuInt,
                                    maMon = it.maMon?.trim() ?: "",
                                    tenMon = it.tenMon?.trim() ?: "",
                                    ngayThi = normalizeDateToYmd(it.ngayThi ?: ""),
                                    gioBatDau = it.gioBatDau?.trim() ?: "",
                                    soPhut = it.soPhutInt,
                                    maPhong = it.maPhong?.trim() ?: "",
                                    diaDiemThi = it.diaDiemThi?.trim() ?: "",
                                    hinhThucThi = (it.hinhThucThi ?: it.ghiChuHtt)?.trim() ?: "",
                                    soBaoDanh = (it.soBaoDanh ?: it.toThi)?.trim() ?: "",
                                    dotThi = it.dotThi?.trim() ?: "",
                                    ghiChuSv = it.ghiChuSv?.trim() ?: "",
                                    isHoanThi = false,
                                )
                            )
                        }
                        data.dsLichHoanThi?.forEach {
                            finalExams.add(
                                ExamEntity(
                                    hocKy = hocKy,
                                    isGiuaKy = false,
                                    soThuTu = it.soThuTuInt,
                                    maMon = it.maMon?.trim() ?: "",
                                    tenMon = it.tenMon?.trim() ?: "",
                                    ngayThi = normalizeDateToYmd(it.ngayThi ?: ""),
                                    gioBatDau = it.gioBatDau?.trim() ?: "",
                                    soPhut = it.soPhutInt,
                                    maPhong = it.maPhong?.trim() ?: "",
                                    diaDiemThi = it.diaDiemThi?.trim() ?: "",
                                    hinhThucThi = (it.hinhThucThi ?: it.ghiChuHtt)?.trim() ?: "",
                                    soBaoDanh = (it.soBaoDanh ?: it.toThi)?.trim() ?: "",
                                    dotThi = it.dotThi?.trim() ?: "",
                                    ghiChuSv = it.ghiChuSv?.trim() ?: "",
                                    isHoanThi = true,
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TnutRepository", "fetch exams cuoiky failed", e)
            }

            val midtermExams = mutableListOf<ExamEntity>()
            var fetchedGiuaKy = false

            // Lịch thi giữa kỳ
            try {
                val resGiuaKy = api.exams(ExamRequest(filter = ExamFilter(hocKy = hocKy, isGiuaKy = true)))
                Log.d("TnutRepository", "fetch exams giuaky result: code=${resGiuaKy.code}, items=${resGiuaKy.data?.dsLichThi?.size}")
                if ((resGiuaKy.code == 200 || resGiuaKy.result) && resGiuaKy.data != null) {
                    fetchedGiuaKy = true
                    resGiuaKy.data?.let { data ->
                        data.dsLichThi?.forEach {
                            midtermExams.add(
                                ExamEntity(
                                    hocKy = hocKy,
                                    isGiuaKy = true,
                                    soThuTu = it.soThuTuInt,
                                    maMon = it.maMon?.trim() ?: "",
                                    tenMon = it.tenMon?.trim() ?: "",
                                    ngayThi = normalizeDateToYmd(it.ngayThi ?: ""),
                                    gioBatDau = it.gioBatDau?.trim() ?: "",
                                    soPhut = it.soPhutInt,
                                    maPhong = it.maPhong?.trim() ?: "",
                                    diaDiemThi = it.diaDiemThi?.trim() ?: "",
                                    hinhThucThi = (it.hinhThucThi ?: it.ghiChuHtt)?.trim() ?: "",
                                    soBaoDanh = (it.soBaoDanh ?: it.toThi)?.trim() ?: "",
                                    dotThi = it.dotThi?.trim() ?: "",
                                    ghiChuSv = it.ghiChuSv?.trim() ?: "",
                                    isHoanThi = false,
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TnutRepository", "fetch exams giuaky failed", e)
            }

            // Trust successful portal responses (including an empty exam list).
            // Only replace the exam type whose endpoint succeeded; keep the
            // previous offline cache for any type whose request failed.
            if (fetchedCuoiKy) {
                dao.replaceExamsByType(hocKy, false, finalExams)
            }
            if (fetchedGiuaKy) {
                dao.replaceExamsByType(hocKy, true, midtermExams)
            }
            if (fetchedCuoiKy || fetchedGiuaKy) {
                TimetableWidgetProvider.updateAll(context)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TnutRepository", "fetchAndSaveExams error", e)
            Result.failure(e)
        }
    }

    fun clearAvatar() {
        _avatarPath.value = null
    }
}
