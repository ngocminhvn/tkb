package com.sv.tnu.tnut

import com.sv.tnu.data.ExamEntity
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Offline CSV/TSV import of the student's *own* final-exam timetable.
 * No student-specific data is bundled in the public APK.
 * Supports Excel CSV (comma/semicolon), TSV and quoted fields.
 */
internal object PersonalExamImporter {
    fun parse(content: String, hocKy: Int): List<ExamEntity> {
        require(content.length <= 2_000_000) { "Tệp lịch thi quá lớn." }
        val firstLine = content.trimStart('\uFEFF').lineSequence().firstOrNull().orEmpty()
        val delimiter = when {
            firstLine.count { it == '\t' } >= 3 -> '\t'
            firstLine.count { it == ';' } >= 3 -> ';'
            else -> ','
        }
        val rows = readRows(content.trimStart('\uFEFF'), delimiter)
            .filter { cells -> cells.any { it.isNotBlank() } }
        require(rows.size >= 2) { "CSV phải có tiêu đề và ít nhất một môn thi." }

        val headings = rows.first().map(::normalizeHeading)
        fun column(vararg aliases: String): Int =
            headings.indexOfFirst { name -> aliases.any { normalizeHeading(it) == name } }
        val code = column("Mã MH", "Mã môn", "ma_mon", "Ma MH")
        val title = column("Tên môn học", "Tên môn", "ten_mon")
        val date = column("Ngày thi", "ngay_thi")
        val time = column("Giờ bắt đầu", "gio_bat_dau")
        val group = column("Nhóm thi", "nhom_thi")
        val team = column("Tổ thi", "to_thi")
        val room = column("Phòng thi", "ma_phong")
        val location = column("Địa điểm", "dia_diem_thi")
        val duration = column("Số phút", "so_phut")
        require(code >= 0 && title >= 0 && date >= 0 && time >= 0) {
            "Thiếu cột Mã MH, Tên môn học, Ngày thi hoặc Giờ bắt đầu."
        }

        fun cell(row: List<String>, index: Int) =
            if (index < 0) "" else row.getOrNull(index)?.trim().orEmpty()

        return rows.drop(1).mapIndexed { position, row ->
            val maMon = cell(row, code)
            val tenMon = cell(row, title)
            val rawDate = cell(row, date)
            val rawTime = cell(row, time)
            require(maMon.isNotBlank() && tenMon.isNotBlank()) {
                "Dòng ${position + 2}: thiếu mã môn hoặc tên môn."
            }
            val format = when {
                Regex("""\d{4}-\d{2}-\d{2}""").matches(rawDate) -> "yyyy-MM-dd"
                Regex("""\d{1,2}/\d{1,2}/\d{4}""").matches(rawDate) -> "d/M/yyyy"
                else -> throw IllegalArgumentException("Dòng ${position + 2}: ngày thi không hợp lệ.")
            }
            val parsed = SimpleDateFormat(format, Locale.ROOT).apply {
                isLenient = false
            }.parse(rawDate) ?: error("Ngày thi không hợp lệ.")
            val normalizedDate = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(parsed)
            val timeMatch = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(rawTime)
                ?: throw IllegalArgumentException("Dòng ${position + 2}: giờ thi không hợp lệ.")
            val hour = timeMatch.groupValues[1].toInt()
            val minute = timeMatch.groupValues[2].toInt()
            require(hour in 0..23 && minute in 0..59) {
                "Dòng ${position + 2}: giờ thi vượt phạm vi."
            }
            val note = listOfNotNull(
                cell(row, group).takeIf { it.isNotBlank() }?.let { "Nhóm thi: $it" },
                cell(row, team).takeIf { it.isNotBlank() }?.let { "Tổ thi: $it" },
                "Lịch nhập từ CSV",
            ).joinToString(" • ")

            ExamEntity(
                hocKy = hocKy,
                isGiuaKy = false,
                soThuTu = position + 1,
                maMon = maMon,
                tenMon = tenMon,
                ngayThi = normalizedDate,
                gioBatDau = "%02d:%02d".format(Locale.ROOT, hour, minute),
                soPhut = cell(row, duration).toIntOrNull()?.coerceIn(0, 600) ?: 0,
                maPhong = cell(row, room),
                diaDiemThi = cell(row, location),
                ghiChuSv = note,
                isHoanThi = false,
            )
        }.also { items ->
            require(items.isNotEmpty()) { "Không có môn thi nào để nhập." }
            require(items.size == items.distinctBy { it.maMon to it.ngayThi }.size) {
                "Tệp có môn thi trùng mã và ngày; hãy kiểm tra trước khi nhập."
            }
        }
    }

    private fun normalizeHeading(label: String): String =
        Normalizer.normalize(label.lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("đ", "d")
            .replace(Regex("[ _-]+"), " ")

    private fun readRows(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val current = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                c == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                c == '"' -> quoted = !quoted
                c == separator && !quoted -> {
                    current.add(cell.toString()); cell.setLength(0)
                }
                (c == '\n' || c == '\r') && !quoted -> {
                    current.add(cell.toString()); cell.setLength(0)
                    rows.add(current.toList()); current.clear()
                    if (c == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> cell.append(c)
            }
            index++
        }
        require(!quoted) { "Tệp CSV có dấu ngoặc kép chưa đóng." }
        if (current.isNotEmpty() || cell.isNotEmpty()) {
            current.add(cell.toString())
            rows.add(current.toList())
        }
        return rows
    }
}
