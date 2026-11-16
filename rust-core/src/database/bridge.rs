//! Checked handles avoid exposing native pointers to Kotlin. All access and
//! close operations share the same lock; close cannot race a query/use-after-free.
use super::*;
use std::{
    collections::HashMap,
    sync::{Mutex, OnceLock},
};
#[derive(Default)]
struct Handles {
    next: i64,
    databases: HashMap<i64, Database>,
}
fn handles() -> &'static Mutex<Handles> {
    static HANDLES: OnceLock<Mutex<Handles>> = OnceLock::new();
    HANDLES.get_or_init(|| Mutex::new(Handles::default()))
}
pub(crate) fn open(path: &str) -> Result<i64> {
    let mut handles = handles()
        .lock()
        .map_err(|_| rusqlite::Error::InvalidQuery)?;
    let next = handles
        .next
        .checked_add(1)
        .ok_or(rusqlite::Error::InvalidQuery)?;
    let db = Database::open(path)?;
    handles.next = next;
    handles.databases.insert(next, db);
    Ok(next)
}
pub(crate) fn close(handle: i64) -> Result<()> {
    handles()
        .lock()
        .map_err(|_| rusqlite::Error::InvalidQuery)?
        .databases
        .remove(&handle)
        .ok_or(rusqlite::Error::InvalidQuery)?;
    Ok(())
}
pub(crate) fn cache(handle: i64, semester: i32) -> Result<Vec<u8>> {
    with_database(handle, |db| super::wire::cache(db, semester))
}
pub(crate) fn notes(handle: i64) -> Result<Vec<u8>> {
    with_database(handle, |db| super::wire::notes(db))
}
pub(crate) fn edit_note(
    handle: i64,
    operation: i32,
    id: &str,
    text: &str,
    timestamp: i64,
) -> Result<()> {
    with_database(handle, |db| match operation {
        0 => db.upsert_lesson_note(&LessonNote {
            lesson_id: id.into(),
            note: text.into(),
            updated_at: timestamp,
        }),
        1 => db.delete_lesson_note(id),
        2 => db.clear_old_lesson_notes(timestamp),
        3 => db.clear_all_lesson_notes(),
        _ => Err(rusqlite::Error::InvalidQuery),
    })
}
fn with_database<T>(handle: i64, f: impl FnOnce(&mut Database) -> Result<T>) -> Result<T> {
    let mut handles = handles()
        .lock()
        .map_err(|_| rusqlite::Error::InvalidQuery)?;
    f(handles
        .databases
        .get_mut(&handle)
        .ok_or(rusqlite::Error::InvalidQuery)?)
}
pub(crate) fn query(handle: i64, op: i32, semester: i32, week: i32, date: &str) -> Result<Vec<u8>> {
    with_database(handle, |db| {
        use super::batch;
        match op {
            1 => batch::timetable(&db.periods()?, &[], &[]),
            2 => batch::timetable(&[], &db.weeks(semester)?, &[]),
            3 => batch::timetable(&[], &[], &db.semester_lessons(semester)?),
            4 => batch::timetable(&[], &[], &db.lessons(semester, week)?),
            5 => batch::timetable(&[], &[], &db.lessons_on(date)?),
            6 => batch::exams(&db.exams_on(date)?),
            7 => batch::timetable(&[], &[], &db.reminder_lessons_on(semester, date)?),
            8 => batch::exams(&db.reminder_exams_on(semester, date)?),
            9 => batch::exams(&db.exams(semester)?),
            10 => super::wire::cache(db, semester),
            11 | 12 => {
                let n = if op == 11 {
                    db.week_count(semester)?
                } else {
                    db.exam_count(semester)?
                };
                let n = i32::try_from(n).map_err(|_| rusqlite::Error::InvalidQuery)?;
                let mut out = b"TKB1C".to_vec();
                out.extend_from_slice(&n.to_be_bytes());
                Ok(out)
            }
            _ => Err(rusqlite::Error::InvalidQuery),
        }
    })
}
pub(crate) fn execute(
    handle: i64,
    op: i32,
    semester: i32,
    midterm: bool,
    payload: &[u8],
) -> Result<i64> {
    with_database(handle, |db| {
        use super::batch;
        match op {
            1..=3 | 5 => {
                let (p, w, l) = batch::read_timetable(payload)?;
                match op {
                    1 => db.insert_periods(&p)?,
                    2 => db.insert_weeks(&w)?,
                    3 => db.insert_lessons(&l)?,
                    _ => db.replace_timetable(semester, &p, &w, &l)?,
                }
            }
            4 | 6 | 7 => {
                let rows = batch::read_exams(payload)?;
                match op {
                    4 => db.insert_exams(&rows)?,
                    6 => db.replace_exams(semester, &rows)?,
                    _ => db.replace_exams_by_type(semester, midterm, &rows)?,
                }
            }
            8 => db.delete_periods()?,
            9 => db.delete_weeks(semester)?,
            10 => db.delete_lessons(semester)?,
            11 => db.delete_exams(semester)?,
            12 => db.delete_exams_by_type(semester, midterm)?,
            13 => {
                return db
                    .remove_legacy_sample_exams_if_exact(semester)
                    .map(i64::from)
            }
            _ => return Err(rusqlite::Error::InvalidQuery),
        }
        Ok(0)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn closed_handles_cannot_be_reused_and_failed_operations_keep_notes() {
        let first = open(":memory:").unwrap();
        edit_note(first, 0, "lesson", "Tiếng Việt", 123).unwrap();
        let before = notes(first).unwrap();
        assert!(before.starts_with(b"TKB1N\0\0\0\x01"));
        assert!(edit_note(first, 99, "", "", 0).is_err());
        assert_eq!(notes(first).unwrap(), before);
        assert_eq!(cache(first, 1).unwrap(), b"TKB1T\0\0\0\0\0\0\0\0\0\0\0\0");
        close(first).unwrap();
        assert!(cache(first, 1).is_err());
        assert!(close(first).is_err());
        let second = open(":memory:").unwrap();
        assert_ne!(first, second);
        close(second).unwrap();
    }
}
