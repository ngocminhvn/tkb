package com.sv.tnu.tnut

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    private val backupJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun readPersonalFile(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Không đọc được tệp đã chọn.")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buf = CharArray(8192)
            while (true) {
                val count = reader.read(buf)
                if (count == -1) break
                out.append(buf, 0, count)
                require(out.length <= 2_000_000) { "Tệp vượt giới hạn 2 MB." }
            }
            require(out.isNotEmpty()) { "Tệp đã chọn trống." }
            out.toString()
        }
    }

    /**
     * Restore a timetable backup explicitly selected by this student.
     * Never ship another student's historical JSON inside the public APK.
     */
    suspend fun importPersonalTimetable(uri: Uri, hocKy: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val wrapper = backupJson.decodeFromString<ApiWrapper<TkbData>>(readPersonalFile(uri))
                val data = wrapper.data
                    ?: throw IllegalArgumentException("Tệp không chứa dữ liệu thời khóa biểu.")
                val count = data.dsTuanTkb.sumOf { it.dsThoiKhoaBieu.size }
                require(data.dsTuanTkb.isNotEmpty() && count > 0) {
                    "Tệp sao lưu không có buổi học nào; không thay thế lịch hiện tại."
                }
                saveToDb(hocKy, data)
                authStore.hocKy = hocKy
                Result.success(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importPersonalExams(uri: Uri, hocKy: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val exams = PersonalExamImporter.parse(readPersonalFile(uri), hocKy)
                // Replace final exams only after ALL rows pass validation.
                // Any existing midterm exams and lesson notes are left intact.
                dao.replaceExamsByType(hocKy, false, exams)
                TimetableWidgetProvider.updateAll(context)
                Result.success(exams.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private val _avatarPath = MutableStateFlow<String?>(authStore.avatarPath)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

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
            // A portal response may be HTTP/code 200 but contain no lessons.
            // Never overwrite genuine offline lessons/weeks with that empty
            // response, nor label it a successful synchronization.
            val incomingLessons = res.data.dsTuanTkb.sumOf { it.dsThoiKhoaBieu.size }
            if (res.data.dsTuanTkb.isEmpty() || incomingLessons == 0) {
                // Exam endpoint may still return valid rows independently.
                fetchAndSaveExams(hocKy)
                return Result.failure(
                    IllegalStateException(
                        "Portal trả lịch học rỗng (học kỳ $hocKy). Dữ liệu đã lưu được giữ nguyên; có thể nhập bản sao lưu."
                    ),
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
            // A successful-but-empty portal response is NOT proof that the
            // student's manually imported/offline exam schedule was cancelled.
            if (fetchedCuoiKy && finalExams.isNotEmpty()) {
                dao.replaceExamsByType(hocKy, false, finalExams)
            }
            if (fetchedGiuaKy && midtermExams.isNotEmpty()) {
                dao.replaceExamsByType(hocKy, true, midtermExams)
            }
            if ((fetchedCuoiKy && finalExams.isNotEmpty()) ||
                (fetchedGiuaKy && midtermExams.isNotEmpty())) {
                TimetableWidgetProvider.updateAll(context)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Portal chưa cung cấp dữ liệu lịch thi. Giữ lịch đã lưu trên máy."))
            }
        } catch (e: Exception) {
            Log.e("TnutRepository", "fetchAndSaveExams error", e)
            Result.failure(e)
        }
    }

    fun clearAvatar() {
        _avatarPath.value = null
    }
}
