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
