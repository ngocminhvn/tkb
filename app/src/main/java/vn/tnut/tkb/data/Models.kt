package vn.tnut.tkb.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
