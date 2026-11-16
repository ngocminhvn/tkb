package com.sv.tnu.schools.tnut

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
