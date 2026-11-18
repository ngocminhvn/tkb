package com.sv.tnu.grades

import org.json.JSONArray
import org.json.JSONObject

object GradeParser {

    private val defaultSymbols = mapOf(
        "CC" to "Chuyên cần",
        "KT" to "Kiểm tra",
        "GK" to "Giữa kỳ",
        "TH" to "Thực hành",
        "BT" to "Bài tập",
        "K1" to "Kiểm tra",
        "T1" to "Điểm thi",
        "T2" to "Thi lại",
    )

    fun parseSymbols(jsonString: String): Map<String, String> {
        val map = defaultSymbols.toMutableMap()
        try {
            val root = JSONObject(jsonString)
            val data = root.optJSONObject("data") ?: return map
            val list = data.optJSONArray("ds_ky_hieu_diem") ?: return map
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val kyHieu = item.optString("ky_hieu").trim()
                val ten = item.optString("ten").trim()
                if (kyHieu.isNotEmpty() && ten.isNotEmpty()) {
                    map[kyHieu] = ten
                }
            }
        } catch (_: Exception) {}
        return map
    }

    fun parseTranscript(
        jsonString: String,
        studentId: String = "",
        studentName: String = "",
        symbols: Map<String, String> = defaultSymbols,
    ): GradeTranscript {
        val root = JSONObject(jsonString)
        val data = root.optJSONObject("data") ?: JSONObject()
        val dsHocKy = data.optJSONArray("ds_diem_hocky") ?: JSONArray()

        val semesterList = mutableListOf<GradeSemester>()
        var totalCourseCount = 0

        for (i in 0 until dsHocKy.length()) {
            val semObj = dsHocKy.optJSONObject(i) ?: continue
            val hocKy = semObj.optString("hoc_ky").trim()
            var tenHocKy = semObj.optString("ten_hoc_ky").trim()
            if (tenHocKy.isBlank() && hocKy.isNotBlank()) {
                tenHocKy = formatHocKyName(hocKy)
            }

            val dsMon = semObj.optJSONArray("ds_diem_mon_hoc") ?: JSONArray()
            val courses = mutableListOf<GradeCourse>()

            for (j in 0 until dsMon.length()) {
                val monObj = dsMon.optJSONObject(j) ?: continue
                val maMon = monObj.optString("ma_mon").trim()
                val tenMon = monObj.optString("ten_mon").trim()
                val nhomTo = monObj.optString("nhom_to").trim()
                val soTinChi = monObj.optString("so_tin_chi").trim()
                val diemThi = monObj.optString("diem_thi").trim()
                val diemGiuaKy = monObj.optString("diem_giua_ky").trim()
                val diemTk10 = monObj.optString("diem_tk").trim()
                val diemTk4 = monObj.optString("diem_tk_so").trim()
                val diemChu = monObj.optString("diem_tk_chu").trim().uppercase()
                val ketQua = monObj.optString("ket_qua").trim()

                val isDat = when {
                    ketQua.contains("đạt", ignoreCase = true) -> true
                    ketQua.contains("không", ignoreCase = true) -> false
                    diemChu == "F" -> false
                    diemChu in setOf("A", "A+", "B", "B+", "C", "C+", "D", "D+", "M") -> true
                    diemTk10.toDoubleOrNull()?.let { it >= 4.0 } == true -> true
                    else -> true
                }

                val dsTp = monObj.optJSONArray("ds_diem_thanh_phan") ?: JSONArray()
                val components = mutableListOf<GradeComponent>()
                for (k in 0 until dsTp.length()) {
                    val tpObj = dsTp.optJSONObject(k) ?: continue
                    val kyHieu = tpObj.optString("ky_hieu").trim()
                    val diemTp = tpObj.optString("diem_thanh_phan").trim()
                    if (kyHieu.isEmpty() && diemTp.isEmpty()) continue
                    val tenTp = symbols[kyHieu]
                        ?: defaultSymbols[kyHieu]
                        ?: tpObj.optString("ten_thanh_phan").trim().ifBlank { kyHieu }
                    components.add(
                        GradeComponent(
                            kyHieu = kyHieu,
                            tenThanhPhan = tenTp,
                            diem = diemTp,
                        )
                    )
                }

                courses.add(
                    GradeCourse(
                        maMon = maMon,
                        tenMon = tenMon,
                        nhomTo = nhomTo,
                        soTinChi = soTinChi,
                        diemThi = diemThi,
                        diemGiuaKy = diemGiuaKy,
                        diemTk10 = diemTk10,
                        diemTk4 = diemTk4,
                        diemChu = diemChu,
                        ketQua = ketQua.ifBlank { if (isDat) "Đạt" else "Không đạt" },
                        isDat = isDat,
                        thanhPhan = components,
                    )
                )
            }

            totalCourseCount += courses.size

            semesterList.add(
                GradeSemester(
                    hocKy = hocKy,
                    tenHocKy = tenHocKy,
                    dtbHk10 = semObj.optString("dtb_hk_he10").trim(),
                    dtbHk4 = semObj.optString("dtb_hk_he4").trim(),
                    soTinChiHk = semObj.optString("so_tin_chi_dat_hk").trim(),
                    diemRlHk = semObj.optString("diemrl_hk").trim(),
                    phanLoaiRlHk = semObj.optString("phan_loai_rl_hk").trim(),
                    dtbTichLuy10 = semObj.optString("dtb_tich_luy_he_10").trim(),
                    dtbTichLuy4 = semObj.optString("dtb_tich_luy_he_4").trim(),
                    soTinChiTichLuy = semObj.optString("so_tin_chi_dat_tich_luy").trim(),
                    xepLoaiHocTap = semObj.optString("xep_loai_tkb_hk").trim(),
                    diemRlTichLuy = semObj.optString("diemrl_hk_tl").trim(),
                    courses = courses,
                )
            )
        }

        // Build overall overview from the latest semester that has cumulative stats
        val latestCumulativeSem = semesterList.lastOrNull {
            it.dtbTichLuy4.isNotBlank() && it.dtbTichLuy4 != "-" && it.dtbTichLuy4 != "0"
        } ?: semesterList.lastOrNull()

        val gpa4 = latestCumulativeSem?.dtbTichLuy4?.takeIf { it.isNotBlank() && it != "-" } ?: ""
        val gpa10 = latestCumulativeSem?.dtbTichLuy10?.takeIf { it.isNotBlank() && it != "-" } ?: ""
        val tcTichLuy = latestCumulativeSem?.soTinChiTichLuy?.takeIf { it.isNotBlank() && it != "-" } ?: ""
        val xepLoai = latestCumulativeSem?.xepLoaiHocTap?.takeIf { it.isNotBlank() && it != "-" }
            ?: classifyGpa(gpa4)

        val overview = GradeOverview(
            dtbTichLuy4 = gpa4,
            dtbTichLuy10 = gpa10,
            soTinChiTichLuy = tcTichLuy,
            xepLoaiHocTap = xepLoai,
            totalCourses = totalCourseCount,
            totalPassedCredits = tcTichLuy,
        )

        return GradeTranscript(
            studentId = studentId,
            studentName = studentName,
            semesters = semesterList,
            overview = overview,
            updatedAt = System.currentTimeMillis(),
            symbols = symbols,
        )
    }

    private fun formatHocKyName(code: String): String {
        if (code.length == 5 && code.all { it.isDigit() }) {
            val year = code.substring(0, 4).toIntOrNull()
            val term = code.substring(4)
            if (year != null) {
                return "Học kỳ $term (${year}-${year + 1})"
            }
        }
        return "Học kỳ $code"
    }

    private fun classifyGpa(gpa4Str: String): String {
        val gpa = gpa4Str.toDoubleOrNull() ?: return ""
        return when {
            gpa >= 3.6 -> "Xuất sắc"
            gpa >= 3.2 -> "Giỏi"
            gpa >= 2.5 -> "Khá"
            gpa >= 2.0 -> "Trung bình"
            else -> "Yếu"
        }
    }

    fun toJson(transcript: GradeTranscript): String {
        val root = JSONObject()
        root.put("studentId", transcript.studentId)
        root.put("studentName", transcript.studentName)
        root.put("updatedAt", transcript.updatedAt)

        val overviewObj = JSONObject()
        overviewObj.put("dtbTichLuy4", transcript.overview.dtbTichLuy4)
        overviewObj.put("dtbTichLuy10", transcript.overview.dtbTichLuy10)
        overviewObj.put("soTinChiTichLuy", transcript.overview.soTinChiTichLuy)
        overviewObj.put("xepLoaiHocTap", transcript.overview.xepLoaiHocTap)
        overviewObj.put("totalCourses", transcript.overview.totalCourses)
        overviewObj.put("totalPassedCredits", transcript.overview.totalPassedCredits)
        root.put("overview", overviewObj)

        val symbolsObj = JSONObject()
        transcript.symbols.forEach { (k, v) -> symbolsObj.put(k, v) }
        root.put("symbols", symbolsObj)

        val semArray = JSONArray()
        for (sem in transcript.semesters) {
            val semObj = JSONObject()
            semObj.put("hocKy", sem.hocKy)
            semObj.put("tenHocKy", sem.tenHocKy)
            semObj.put("dtbHk10", sem.dtbHk10)
            semObj.put("dtbHk4", sem.dtbHk4)
            semObj.put("soTinChiHk", sem.soTinChiHk)
            semObj.put("diemRlHk", sem.diemRlHk)
            semObj.put("phanLoaiRlHk", sem.phanLoaiRlHk)
            semObj.put("dtbTichLuy10", sem.dtbTichLuy10)
            semObj.put("dtbTichLuy4", sem.dtbTichLuy4)
            semObj.put("soTinChiTichLuy", sem.soTinChiTichLuy)
            semObj.put("xepLoaiHocTap", sem.xepLoaiHocTap)
            semObj.put("diemRlTichLuy", sem.diemRlTichLuy)

            val coursesArray = JSONArray()
            for (c in sem.courses) {
                val cObj = JSONObject()
                cObj.put("maMon", c.maMon)
                cObj.put("tenMon", c.tenMon)
                cObj.put("nhomTo", c.nhomTo)
                cObj.put("soTinChi", c.soTinChi)
                cObj.put("diemThi", c.diemThi)
                cObj.put("diemGiuaKy", c.diemGiuaKy)
                cObj.put("diemTk10", c.diemTk10)
                cObj.put("diemTk4", c.diemTk4)
                cObj.put("diemChu", c.diemChu)
                cObj.put("ketQua", c.ketQua)
                cObj.put("isDat", c.isDat)

                val tpArray = JSONArray()
                for (tp in c.thanhPhan) {
                    val tpObj = JSONObject()
                    tpObj.put("kyHieu", tp.kyHieu)
                    tpObj.put("tenThanhPhan", tp.tenThanhPhan)
                    tpObj.put("diem", tp.diem)
                    tpArray.put(tpObj)
                }
                cObj.put("thanhPhan", tpArray)
                coursesArray.put(cObj)
            }
            semObj.put("courses", coursesArray)
            semArray.put(semObj)
        }
        root.put("semesters", semArray)

        return root.toString()
    }

    fun fromJson(jsonString: String): GradeTranscript? {
        return try {
            val root = JSONObject(jsonString)
            val studentId = root.optString("studentId")
            val studentName = root.optString("studentName")
            val updatedAt = root.optLong("updatedAt")

            val overviewObj = root.optJSONObject("overview") ?: JSONObject()
            val overview = GradeOverview(
                dtbTichLuy4 = overviewObj.optString("dtbTichLuy4"),
                dtbTichLuy10 = overviewObj.optString("dtbTichLuy10"),
                soTinChiTichLuy = overviewObj.optString("soTinChiTichLuy"),
                xepLoaiHocTap = overviewObj.optString("xepLoaiHocTap"),
                totalCourses = overviewObj.optInt("totalCourses"),
                totalPassedCredits = overviewObj.optString("totalPassedCredits"),
            )

            val symbolsObj = root.optJSONObject("symbols")
            val symbols = mutableMapOf<String, String>()
            if (symbolsObj != null) {
                val keys = symbolsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    symbols[key] = symbolsObj.optString(key)
                }
            }

            val semArray = root.optJSONArray("semesters") ?: JSONArray()
            val semesters = mutableListOf<GradeSemester>()
            for (i in 0 until semArray.length()) {
                val semObj = semArray.optJSONObject(i) ?: continue
                val coursesArray = semObj.optJSONArray("courses") ?: JSONArray()
                val courses = mutableListOf<GradeCourse>()
                for (j in 0 until coursesArray.length()) {
                    val cObj = coursesArray.optJSONObject(j) ?: continue
                    val tpArray = cObj.optJSONArray("thanhPhan") ?: JSONArray()
                    val tpList = mutableListOf<GradeComponent>()
                    for (k in 0 until tpArray.length()) {
                        val tpObj = tpArray.optJSONObject(k) ?: continue
                        tpList.add(
                            GradeComponent(
                                kyHieu = tpObj.optString("kyHieu"),
                                tenThanhPhan = tpObj.optString("tenThanhPhan"),
                                diem = tpObj.optString("diem"),
                            )
                        )
                    }
                    courses.add(
                        GradeCourse(
                            maMon = cObj.optString("maMon"),
                            tenMon = cObj.optString("tenMon"),
                            nhomTo = cObj.optString("nhomTo"),
                            soTinChi = cObj.optString("soTinChi"),
                            diemThi = cObj.optString("diemThi"),
                            diemGiuaKy = cObj.optString("diemGiuaKy"),
                            diemTk10 = cObj.optString("diemTk10"),
                            diemTk4 = cObj.optString("diemTk4"),
                            diemChu = cObj.optString("diemChu"),
                            ketQua = cObj.optString("ketQua"),
                            isDat = cObj.optBoolean("isDat"),
                            thanhPhan = tpList,
                        )
                    )
                }
                semesters.add(
                    GradeSemester(
                        hocKy = semObj.optString("hocKy"),
                        tenHocKy = semObj.optString("tenHocKy"),
                        dtbHk10 = semObj.optString("dtbHk10"),
                        dtbHk4 = semObj.optString("dtbHk4"),
                        soTinChiHk = semObj.optString("soTinChiHk"),
                        diemRlHk = semObj.optString("diemRlHk"),
                        phanLoaiRlHk = semObj.optString("phanLoaiRlHk"),
                        dtbTichLuy10 = semObj.optString("dtbTichLuy10"),
                        dtbTichLuy4 = semObj.optString("dtbTichLuy4"),
                        soTinChiTichLuy = semObj.optString("soTinChiTichLuy"),
                        xepLoaiHocTap = semObj.optString("xepLoaiHocTap"),
                        diemRlTichLuy = semObj.optString("diemRlTichLuy"),
                        courses = courses,
                    )
                )
            }

            GradeTranscript(
                studentId = studentId,
                studentName = studentName,
                semesters = semesters,
                overview = overview,
                updatedAt = updatedAt,
                symbols = symbols,
            )
        } catch (_: Exception) {
            null
        }
    }
}
