//! Bounded binary database batches, compatible with TKB1T and TKB1E.
use super::*;
use std::io::{Cursor, Read};
fn invalid() -> rusqlite::Error {
    rusqlite::Error::InvalidQuery
}
struct Reader<'a>(Cursor<&'a [u8]>);
impl Reader<'_> {
    fn take<const N: usize>(&mut self) -> Result<[u8; N]> {
        let mut b = [0; N];
        self.0.read_exact(&mut b).map_err(|_| invalid())?;
        Ok(b)
    }
    fn int(&mut self) -> Result<i32> {
        Ok(i32::from_be_bytes(self.take()?))
    }
    fn long(&mut self) -> Result<i64> {
        Ok(i64::from_be_bytes(self.take()?))
    }
    fn boolean(&mut self) -> Result<bool> {
        match self.take::<1>()?[0] {
            0 => Ok(false),
            1 => Ok(true),
            _ => Err(invalid()),
        }
    }
    fn count(&mut self) -> Result<usize> {
        let n = self.int()?;
        if !(0..=100_000).contains(&n) {
            return Err(invalid());
        }
        Ok(n as usize)
    }
    fn string(&mut self) -> Result<String> {
        let n = self.int()?;
        let pos = self.0.position() as usize;
        if !(0..=8_388_608).contains(&n) || n as usize > self.0.get_ref().len() - pos {
            return Err(invalid());
        }
        let mut b = vec![0; n as usize];
        self.0.read_exact(&mut b).map_err(|_| invalid())?;
        String::from_utf8(b).map_err(|_| invalid())
    }
    fn done(&self) -> Result<()> {
        if self.0.position() as usize == self.0.get_ref().len() {
            Ok(())
        } else {
            Err(invalid())
        }
    }
}
fn int(out: &mut Vec<u8>, n: i32) {
    out.extend_from_slice(&n.to_be_bytes());
}
fn count(out: &mut Vec<u8>, n: usize) -> Result<()> {
    if n > 100_000 {
        return Err(invalid());
    }
    int(out, n as i32);
    Ok(())
}
fn string(out: &mut Vec<u8>, s: &str) -> Result<()> {
    if s.len() > 8_388_608 {
        return Err(invalid());
    }
    int(out, s.len() as i32);
    out.extend_from_slice(s.as_bytes());
    Ok(())
}
fn read_period(r: &mut Reader<'_>) -> Result<Period> {
    Ok(Period {
        tiet: r.int()?,
        gio_bat_dau: r.string()?,
        gio_ket_thuc: r.string()?,
    })
}
fn write_period(out: &mut Vec<u8>, r: &Period) -> Result<()> {
    int(out, r.tiet);
    string(out, &r.gio_bat_dau)?;
    string(out, &r.gio_ket_thuc)?;
    Ok(())
}
fn read_week(r: &mut Reader<'_>) -> Result<Week> {
    Ok(Week {
        hoc_ky: r.int()?,
        tuan_hoc_ky: r.int()?,
        ngay_bat_dau: r.string()?,
        ngay_ket_thuc: r.string()?,
        thong_tin_tuan: r.string()?,
    })
}
fn write_week(out: &mut Vec<u8>, r: &Week) -> Result<()> {
    int(out, r.hoc_ky);
    int(out, r.tuan_hoc_ky);
    string(out, &r.ngay_bat_dau)?;
    string(out, &r.ngay_ket_thuc)?;
    string(out, &r.thong_tin_tuan)?;
    Ok(())
}
fn read_lesson(r: &mut Reader<'_>) -> Result<Lesson> {
    Ok(Lesson {
        id_tkb: r.string()?,
        hoc_ky: r.int()?,
        tuan_hoc_ky: r.int()?,
        ngay_hoc: r.string()?,
        thu_kieu_so: r.int()?,
        tiet_bat_dau: r.int()?,
        so_tiet: r.int()?,
        ma_mon: r.string()?,
        ten_mon: r.string()?,
        so_tin_chi: r.string()?,
        ma_phong: r.string()?,
        ten_giang_vien: r.string()?,
        ten_lop: r.string()?,
        ma_nhom: r.string()?,
    })
}
fn write_lesson(out: &mut Vec<u8>, r: &Lesson) -> Result<()> {
    string(out, &r.id_tkb)?;
    int(out, r.hoc_ky);
    int(out, r.tuan_hoc_ky);
    string(out, &r.ngay_hoc)?;
    int(out, r.thu_kieu_so);
    int(out, r.tiet_bat_dau);
    int(out, r.so_tiet);
    string(out, &r.ma_mon)?;
    string(out, &r.ten_mon)?;
    string(out, &r.so_tin_chi)?;
    string(out, &r.ma_phong)?;
    string(out, &r.ten_giang_vien)?;
    string(out, &r.ten_lop)?;
    string(out, &r.ma_nhom)?;
    Ok(())
}
fn read_exam(r: &mut Reader<'_>) -> Result<Exam> {
    Ok(Exam {
        id: r.long()?,
        hoc_ky: r.int()?,
        is_giua_ky: r.boolean()?,
        so_thu_tu: r.int()?,
        ma_mon: r.string()?,
        ten_mon: r.string()?,
        ngay_thi: r.string()?,
        gio_bat_dau: r.string()?,
        so_phut: r.int()?,
        ma_phong: r.string()?,
        dia_diem_thi: r.string()?,
        hinh_thuc_thi: r.string()?,
        so_bao_danh: r.string()?,
        dot_thi: r.string()?,
        ghi_chu_sv: r.string()?,
        is_hoan_thi: r.boolean()?,
    })
}
fn write_exam(out: &mut Vec<u8>, r: &Exam) -> Result<()> {
    out.extend_from_slice(&r.id.to_be_bytes());
    int(out, r.hoc_ky);
    out.push(u8::from(r.is_giua_ky));
    int(out, r.so_thu_tu);
    string(out, &r.ma_mon)?;
    string(out, &r.ten_mon)?;
    string(out, &r.ngay_thi)?;
    string(out, &r.gio_bat_dau)?;
    int(out, r.so_phut);
    string(out, &r.ma_phong)?;
    string(out, &r.dia_diem_thi)?;
    string(out, &r.hinh_thuc_thi)?;
    string(out, &r.so_bao_danh)?;
    string(out, &r.dot_thi)?;
    string(out, &r.ghi_chu_sv)?;
    out.push(u8::from(r.is_hoan_thi));
    Ok(())
}
pub(super) type Timetable = (Vec<Period>, Vec<Week>, Vec<Lesson>);
pub(super) fn read_timetable(bytes: &[u8]) -> Result<Timetable> {
    let mut r = Reader(Cursor::new(bytes));
    if r.take::<5>()? != *b"TKB1T" {
        return Err(invalid());
    }
    let p = (0..r.count()?)
        .map(|_| read_period(&mut r))
        .collect::<Result<Vec<_>>>()?;
    let w = (0..r.count()?)
        .map(|_| read_week(&mut r))
        .collect::<Result<Vec<_>>>()?;
    let l = (0..r.count()?)
        .map(|_| read_lesson(&mut r))
        .collect::<Result<Vec<_>>>()?;
    r.done()?;
    Ok((p, w, l))
}
pub(super) fn read_exams(bytes: &[u8]) -> Result<Vec<Exam>> {
    let mut r = Reader(Cursor::new(bytes));
    if r.take::<5>()? != *b"TKB1E" {
        return Err(invalid());
    }
    let _pages = r.int()?;
    let rows = r.count()?;
    let n = r.count()?;
    if rows != n {
        return Err(invalid());
    }
    let result = (0..n)
        .map(|_| read_exam(&mut r))
        .collect::<Result<Vec<_>>>()?;
    r.done()?;
    Ok(result)
}
pub(super) fn timetable(p: &[Period], w: &[Week], l: &[Lesson]) -> Result<Vec<u8>> {
    let mut out = b"TKB1T".to_vec();
    count(&mut out, p.len())?;
    for r in p {
        write_period(&mut out, r)?
    }
    count(&mut out, w.len())?;
    for r in w {
        write_week(&mut out, r)?
    }
    count(&mut out, l.len())?;
    for r in l {
        write_lesson(&mut out, r)?
    }
    Ok(out)
}
pub(super) fn exams(rows: &[Exam]) -> Result<Vec<u8>> {
    let mut out = b"TKB1E".to_vec();
    int(&mut out, 0);
    count(&mut out, rows.len())?;
    count(&mut out, rows.len())?;
    for r in rows {
        write_exam(&mut out, r)?
    }
    Ok(out)
}
