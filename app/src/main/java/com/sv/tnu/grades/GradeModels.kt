package com.sv.tnu.grades

data class GradeComponent(
    val kyHieu: String,
    val tenThanhPhan: String,
    val diem: String,
)

data class GradeCourse(
    val maMon: String,
    val tenMon: String,
    val nhomTo: String,
    val soTinChi: String,
    val diemThi: String,
    val diemGiuaKy: String,
    val diemTk10: String,
    val diemTk4: String,
    val diemChu: String,
    val ketQua: String,
    val isDat: Boolean,
    val thanhPhan: List<GradeComponent>,
)

data class GradeSemester(
    val hocKy: String,
    val tenHocKy: String,
    val dtbHk10: String,
    val dtbHk4: String,
    val soTinChiHk: String,
    val diemRlHk: String,
    val phanLoaiRlHk: String,
    val dtbTichLuy10: String,
    val dtbTichLuy4: String,
    val soTinChiTichLuy: String,
    val xepLoaiHocTap: String,
    val diemRlTichLuy: String,
    val courses: List<GradeCourse>,
)

data class GradeOverview(
    val dtbTichLuy4: String,
    val dtbTichLuy10: String,
    val soTinChiTichLuy: String,
    val xepLoaiHocTap: String,
    val totalCourses: Int,
    val totalPassedCredits: String,
)

data class GradeTranscript(
    val studentId: String = "",
    val studentName: String = "",
    val semesters: List<GradeSemester> = emptyList(),
    val overview: GradeOverview = GradeOverview("", "", "", "", 0, ""),
    val updatedAt: Long = 0L,
    val symbols: Map<String, String> = emptyMap(),
)
