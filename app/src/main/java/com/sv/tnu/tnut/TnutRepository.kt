package com.sv.tnu.tnut

import android.content.Context
import android.util.Base64
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.ExamEntity
import com.sv.tnu.data.LessonEntity
import com.sv.tnu.data.LessonNoteEntity
import com.sv.tnu.data.PeriodEntity
import com.sv.tnu.data.TkbDao
import com.sv.tnu.data.WeekEntity
import com.sv.tnu.widget.TimetableWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

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
            val res = api.weeklyTimetable(WeeklyRequest(filter = WeeklyFilter(hocKy = hocKy)))
            if (res.code != 200 || res.data == null) {
                return Result.failure(
                    IllegalStateException(res.message ?: "Không tải được thời khóa biểu (${res.code})"),
                )
            }
            saveToDb(hocKy, res.data)
            authStore.hocKy = hocKy
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
            authStore.lastSyncTime = System.currentTimeMillis()
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

    /** Lần đầu mở app chưa có dữ liệu: seed Room từ file JSON mẫu trong assets. */
    suspend fun seedFromAssetsIfEmpty(hocKy: Int) {
        if (dao.weekCount(hocKy) > 0) return
        try {
            val text = context.assets.open("sample_tkb_tuan_20261.json")
                .bufferedReader().use { it.readText() }
            val wrapper = json.decodeFromString<ApiWrapper<TkbData>>(text)
            val data = wrapper.data ?: return
            saveToDb(hocKy, data)
        } catch (_: Exception) {
            // Không có file mẫu thì thôi, app vẫn chạy, user đăng nhập để tải lịch thật.
        }
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

    private fun normalizeDateToYmd(dateStr: String): String {
        val clean = dateStr.trim()
        val parts = clean.split("/")
        return if (parts.size == 3) {
            val day = parts[0].padStart(2, '0')
            val month = parts[1].padStart(2, '0')
            val year = parts[2]
            "$year-$month-$day"
        } else {
            clean
        }
    }

    suspend fun fetchAndSaveExams(hocKy: Int): Result<Unit> {
        return try {
            val list = mutableListOf<ExamEntity>()
            // Lịch thi cuối kỳ
            try {
                val resCuoiKy = api.exams(ExamRequest(filter = ExamFilter(hocKy = hocKy, isGiuaKy = false)))
                resCuoiKy.data?.let { data ->
                    data.dsLichThi.forEach {
                        list.add(
                            ExamEntity(
                                hocKy = hocKy,
                                isGiuaKy = false,
                                soThuTu = it.soThuTu,
                                maMon = it.maMon,
                                tenMon = it.tenMon,
                                ngayThi = normalizeDateToYmd(it.ngayThi),
                                gioBatDau = it.gioBatDau,
                                soPhut = it.soPhut,
                                maPhong = it.maPhong,
                                diaDiemThi = it.diaDiemThi ?: "",
                                hinhThucThi = it.hinhThucThi ?: "",
                                soBaoDanh = it.soBaoDanh ?: "",
                                dotThi = it.dotThi ?: "",
                                ghiChuSv = it.ghiChuSv ?: "",
                                isHoanThi = false,
                            )
                        )
                    }
                    data.dsLichHoanThi.forEach {
                        list.add(
                            ExamEntity(
                                hocKy = hocKy,
                                isGiuaKy = false,
                                soThuTu = it.soThuTu,
                                maMon = it.maMon,
                                tenMon = it.tenMon,
                                ngayThi = normalizeDateToYmd(it.ngayThi),
                                gioBatDau = it.gioBatDau,
                                soPhut = it.soPhut,
                                maPhong = it.maPhong,
                                diaDiemThi = it.diaDiemThi ?: "",
                                hinhThucThi = it.hinhThucThi ?: "",
                                soBaoDanh = it.soBaoDanh ?: "",
                                dotThi = it.dotThi ?: "",
                                ghiChuSv = it.ghiChuSv ?: "",
                                isHoanThi = true,
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            // Lịch thi giữa kỳ
            try {
                val resGiuaKy = api.exams(ExamRequest(filter = ExamFilter(hocKy = hocKy, isGiuaKy = true)))
                resGiuaKy.data?.let { data ->
                    data.dsLichThi.forEach {
                        list.add(
                            ExamEntity(
                                hocKy = hocKy,
                                isGiuaKy = true,
                                soThuTu = it.soThuTu,
                                maMon = it.maMon,
                                tenMon = it.tenMon,
                                ngayThi = normalizeDateToYmd(it.ngayThi),
                                gioBatDau = it.gioBatDau,
                                soPhut = it.soPhut,
                                maPhong = it.maPhong,
                                diaDiemThi = it.diaDiemThi ?: "",
                                hinhThucThi = it.hinhThucThi ?: "",
                                soBaoDanh = it.soBaoDanh ?: "",
                                dotThi = it.dotThi ?: "",
                                ghiChuSv = it.ghiChuSv ?: "",
                                isHoanThi = false,
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            dao.replaceExams(hocKy, list)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearAvatar() {
        _avatarPath.value = null
    }
}
