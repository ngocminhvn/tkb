package com.sv.tnu.rust

import com.sv.tnu.data.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** TKB1 encoders paired with database/batch.rs; no JSON or string packing. */
internal object DatabaseBinaryEncoder {
    private fun DataOutputStream.writePeriod(r: PeriodEntity) {
        writeInt(r.tiet)
        utf8(r.gioBatDau)
        utf8(r.gioKetThuc)
    }
    private fun DataOutputStream.writeWeek(r: WeekEntity) {
        writeInt(r.hocKy)
        writeInt(r.tuanHocKy)
        utf8(r.ngayBatDau)
        utf8(r.ngayKetThuc)
        utf8(r.thongTinTuan)
    }
    private fun DataOutputStream.writeLesson(r: LessonEntity) {
        utf8(r.idTkb)
        writeInt(r.hocKy)
        writeInt(r.tuanHocKy)
        utf8(r.ngayHoc)
        writeInt(r.thuKieuSo)
        writeInt(r.tietBatDau)
        writeInt(r.soTiet)
        utf8(r.maMon)
        utf8(r.tenMon)
        utf8(r.soTinChi)
        utf8(r.maPhong)
        utf8(r.tenGiangVien)
        utf8(r.tenLop)
        utf8(r.maNhom)
    }
    private fun DataOutputStream.writeExam(r: ExamEntity) {
        writeLong(r.id)
        writeInt(r.hocKy)
        writeBoolean(r.isGiuaKy)
        writeInt(r.soThuTu)
        utf8(r.maMon)
        utf8(r.tenMon)
        utf8(r.ngayThi)
        utf8(r.gioBatDau)
        writeInt(r.soPhut)
        utf8(r.maPhong)
        utf8(r.diaDiemThi)
        utf8(r.hinhThucThi)
        utf8(r.soBaoDanh)
        utf8(r.dotThi)
        utf8(r.ghiChuSv)
        writeBoolean(r.isHoanThi)
    }
    private fun DataOutputStream.utf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 8_388_608)
        writeInt(bytes.size); write(bytes)
    }
    private fun <T> DataOutputStream.rows(rows: List<T>, writeRow: DataOutputStream.(T) -> Unit) {
        require(rows.size <= 100_000)
        writeInt(rows.size); rows.forEach { writeRow(it) }
    }
    fun timetable(periods: List<PeriodEntity> = emptyList(), weeks: List<WeekEntity> = emptyList(), lessons: List<LessonEntity> = emptyList()): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeBytes("TKB1T")
            out.rows(periods) { writePeriod(it) }; out.rows(weeks) { writeWeek(it) }; out.rows(lessons) { writeLesson(it) }
        }
        return buffer.toByteArray()
    }
    fun exams(rows: List<ExamEntity>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeBytes("TKB1E"); out.writeInt(0); out.writeInt(rows.size)
            out.rows(rows) { writeExam(it) }
        }
        return buffer.toByteArray()
    }
}
