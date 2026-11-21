//! Database binary batches. Existing TKB1T layout; TKB1N adds notes.
use super::*;
fn int(out: &mut Vec<u8>, value: i32) {
    out.extend_from_slice(&value.to_be_bytes());
}
fn string(out: &mut Vec<u8>, value: &str) -> Result<()> {
    let length = i32::try_from(value.len()).map_err(|_| rusqlite::Error::InvalidQuery)?;
    int(out, length);
    out.extend_from_slice(value.as_bytes());
    Ok(())
}
pub(super) fn encode_week(out: &mut Vec<u8>, row: &Week) -> Result<()> {
    int(out, row.hoc_ky);
    int(out, row.tuan_hoc_ky);
    string(out, &row.ngay_bat_dau)?;
    string(out, &row.ngay_ket_thuc)?;
    string(out, &row.thong_tin_tuan)?;
    Ok(())
}
pub(super) fn encode_lesson(out: &mut Vec<u8>, row: &Lesson) -> Result<()> {
    string(out, &row.id_tkb)?;
    int(out, row.hoc_ky);
    int(out, row.tuan_hoc_ky);
    string(out, &row.ngay_hoc)?;
    int(out, row.thu_kieu_so);
    int(out, row.tiet_bat_dau);
    int(out, row.so_tiet);
    string(out, &row.ma_mon)?;
    string(out, &row.ten_mon)?;
    string(out, &row.so_tin_chi)?;
    string(out, &row.ma_phong)?;
    string(out, &row.ten_giang_vien)?;
    string(out, &row.ten_lop)?;
    string(out, &row.ma_nhom)?;
    Ok(())
}
fn encode_lessonnote(out: &mut Vec<u8>, row: &LessonNote) -> Result<()> {
    string(out, &row.lesson_id)?;
    string(out, &row.note)?;
    out.extend_from_slice(&row.updated_at.to_be_bytes());
    Ok(())
}
pub(super) fn cache(db: &mut Database, semester: i32) -> Result<Vec<u8>> {
    let snapshot = db.cached_timetable(semester)?;
    let mut out = b"TKB1T".to_vec();
    int(&mut out, 0); // cache contract contains weeks and lessons, no period query
    int(
        &mut out,
        i32::try_from(snapshot.weeks.len()).map_err(|_| rusqlite::Error::InvalidQuery)?,
    );
    for row in &snapshot.weeks {
        encode_week(&mut out, row)?;
    }
    int(
        &mut out,
        i32::try_from(snapshot.lessons.len()).map_err(|_| rusqlite::Error::InvalidQuery)?,
    );
    for row in &snapshot.lessons {
        encode_lesson(&mut out, row)?;
    }
    Ok(out)
}
pub(super) fn notes(db: &Database) -> Result<Vec<u8>> {
    let rows = db.lesson_notes()?;
    let mut out = b"TKB1N".to_vec();
    int(
        &mut out,
        i32::try_from(rows.len()).map_err(|_| rusqlite::Error::InvalidQuery)?,
    );
    for row in &rows {
        encode_lessonnote(&mut out, row)?;
    }
    Ok(out)
}
