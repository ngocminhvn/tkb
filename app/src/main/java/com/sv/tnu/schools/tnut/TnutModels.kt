package com.sv.tnu.schools.tnut

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("idpc") val idpc: String? = null,
)

@Serializable
data class ApiWrapper<T>(
    val result: Boolean = false,
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
data class Semester(
    @SerialName("hoc_ky") val hocKy: Int,
    @SerialName("ten_hoc_ky") val tenHocKy: String = "",
    @SerialName("ngay_bat_dau_hk") val ngayBatDau: String = "",
    @SerialName("ngay_ket_thuc_hk") val ngayKetThuc: String = "",
)

@Serializable
data class SemesterListData(
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("hoc_ky") val hocKy: Int = 0,
    @SerialName("hoc_ky_theo_ngay_hien_tai") val hocKyHienTai: Int = 0,
    @SerialName("ds_hoc_ky") val dsHocKy: List<Semester> = emptyList(),
)

@Serializable
data class Period(
    val tiet: Int,
    @SerialName("gio_bat_dau") val gioBatDau: String,
    @SerialName("gio_ket_thuc") val gioKetThuc: String,
)

@Serializable
data class Lesson(
    @SerialName("id_tkb") val idTkb: String,
    @SerialName("thu_kieu_so") val thuKieuSo: Int,
    @SerialName("tiet_bat_dau") val tietBatDau: Int,
    @SerialName("so_tiet") val soTiet: Int,
    @SerialName("ma_mon") val maMon: String = "",
    @SerialName("ten_mon") val tenMon: String = "",
    @SerialName("so_tin_chi") val soTinChi: String = "",
    @SerialName("ma_phong") val maPhong: String = "",
    @SerialName("ten_giang_vien") val tenGiangVien: String = "",
    @SerialName("ten_lop") val tenLop: String = "",
    @SerialName("ma_nhom") val maNhom: String = "",
    @SerialName("ngay_hoc") val ngayHoc: String = "",
)

@Serializable
data class Week(
    @SerialName("tuan_hoc_ky") val tuanHocKy: Int,
    @SerialName("thong_tin_tuan") val thongTinTuan: String = "",
    @SerialName("ngay_bat_dau") val ngayBatDau: String = "",
    @SerialName("ngay_ket_thuc") val ngayKetThuc: String = "",
    @SerialName("ds_thoi_khoa_bieu") val dsThoiKhoaBieu: List<Lesson> = emptyList(),
)

@Serializable
data class TkbData(
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("ds_tiet_trong_ngay") val dsTietTrongNgay: List<Period> = emptyList(),
    @SerialName("ds_tuan_tkb") val dsTuanTkb: List<Week> = emptyList(),
)

@Serializable
data class WeeklyFilter(
    @SerialName("hoc_ky") val hocKy: Int,
    @SerialName("ten_hoc_ky") val tenHocKy: String = "",
)

@Serializable
data class Paging(
    val limit: Int = 100,
    val page: Int = 1,
)

@Serializable
data class Ordering(
    val name: String? = null,
    @SerialName("order_type") val orderType: String? = null,
)

@Serializable
data class WeeklyAdditional(
    val paging: Paging = Paging(),
    val ordering: List<Ordering> = listOf(Ordering()),
)

@Serializable
data class WeeklyRequest(
    val filter: WeeklyFilter,
    val additional: WeeklyAdditional = WeeklyAdditional(),
)

@Serializable
data class StudentImage(
    val image: String? = null,
    // Optional profile fields: older portal versions may return only image.
    @SerialName("ho_ten") val hoTen: String? = null,
    @SerialName("ten_sinh_vien") val tenSinhVien: String? = null,
    @SerialName("ten_sv") val tenSv: String? = null,
)

@Serializable
data class AvatarData(
    @SerialName("thong_tin_sinh_vien") val thongTinSinhVien: StudentImage? = null,
)

@Serializable
data class AvatarResponse(
    val code: Int = 0,
    val data: AvatarData? = null,
    val message: String? = null,
)

@Serializable
data class ExamItemApi(
    @SerialName("so_thu_tu") val soThuTu: JsonElement? = null,
    @SerialName("ma_mon") val maMon: String? = null,
    @SerialName("ten_mon") val tenMon: String? = null,
    @SerialName("ten_mon_eg") val tenMonEg: String? = null,
    @SerialName("ngay_thi") val ngayThi: String? = null,
    @SerialName("gio_bat_dau") val gioBatDau: String? = null,
    @SerialName("so_phut") val soPhut: JsonElement? = null,
    @SerialName("tiet_bat_dau") val tietBatDau: JsonElement? = null,
    @SerialName("so_tiet") val soTiet: JsonElement? = null,
    @SerialName("ma_phong") val maPhong: String? = null,
    @SerialName("dia_diem_thi") val diaDiemThi: String? = null,
    @SerialName("hinh_thuc_thi") val hinhThucThi: String? = null,
    @SerialName("so_bao_danh") val soBaoDanh: String? = null,
    @SerialName("dot_thi") val dotThi: String? = null,
    @SerialName("ghi_chu_sv") val ghiChuSv: String? = null,
    @SerialName("ghi_chu_htt") val ghiChuHtt: String? = null,
    @SerialName("to_thi") val toThi: String? = null,
    @SerialName("nhom_thi") val nhomThi: String? = null,
    @SerialName("ghep_thi") val ghepThi: String? = null,
) {
    val soPhutInt: Int
        get() = when (val el = soPhut) {
            is JsonPrimitive -> el.intOrNull ?: el.contentOrNull?.toIntOrNull() ?: 0
            else -> 0
        }

    val soThuTuInt: Int
        get() = when (val el = soThuTu) {
            is JsonPrimitive -> el.intOrNull ?: el.contentOrNull?.toIntOrNull() ?: 0
            else -> 0
        }
}

@Serializable
data class ExamDataApi(
    @SerialName("total_items") val totalItems: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("is_DHMO") val isDhmo: Boolean? = null,
    @SerialName("ds_lich_thi") val dsLichThi: List<ExamItemApi>? = emptyList(),
    @SerialName("ds_lich_hoan_thi") val dsLichHoanThi: List<ExamItemApi>? = emptyList(),
    @SerialName("title_lich_hoan_thi") val titleLichHoanThi: String? = null,
    @SerialName("thong_bao_no_hoc_phi") val thongBaoNoHocPhi: String? = null,
    @SerialName("is_show_in_sv_du_thi") val isShowInSvDuThi: Boolean? = null,
    @SerialName("thong_bao_ghi_chu") val thongBaoGhiChu: String? = null,
)

@Serializable
data class ExamResponse(
    val code: Int = 0,
    val data: ExamDataApi? = null,
    val message: String? = null,
    val result: Boolean = false,
)

/**
 * Exam portal request fields must be REQUIRED (no Kotlin defaults).
 * Json.encodeDefaults is false, so using WeeklyAdditional() and
 * isGiuaKy = false silently omits required fields from the HTTP body.
 * This model matches the request used by TNUT's /#/lichthi page.
 */
@Serializable
data class ExamFilter(
    @SerialName("hoc_ky") val hocKy: Int,
    @SerialName("is_giua_ky") val isGiuaKy: Boolean,
)

@Serializable
data class ExamPaging(
    val limit: Int,
    val page: Int,
)

@Serializable
data class ExamOrdering(
    val name: String?,
    @SerialName("order_type") val orderType: String?,
)

@Serializable
data class ExamAdditional(
    val paging: ExamPaging,
    val ordering: List<ExamOrdering>,
)

@Serializable
data class ExamRequest(
    val filter: ExamFilter,
    val additional: ExamAdditional,
) {
    companion object {
        const val PAGE_SIZE: Int = 100

        fun forSemester(hocKy: Int, isGiuaKy: Boolean, page: Int = 1): ExamRequest {
            require(page >= 1) { "Trang lịch thi phải >= 1" }
            return ExamRequest(
                filter = ExamFilter(hocKy = hocKy, isGiuaKy = isGiuaKy),
                additional = ExamAdditional(
                    paging = ExamPaging(limit = PAGE_SIZE, page = page),
                    ordering = listOf(ExamOrdering(name = null, orderType = null)),
                ),
            )
        }
    }
}
