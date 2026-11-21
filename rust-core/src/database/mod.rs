//! rusqlite backend for the existing tkb.db. Not enabled in Android until the
//! JNI/Flow adapter and installed-APK upgrade tests have been verified.
//! A connection is owned by one worker; do not run these blocking calls on UI.
pub(crate) mod bridge;
mod wire;
mod jni;
mod records;
mod batch;
use records::{insert, query};
pub use records::{Exam, Lesson, LessonNote, Period, Week};
use rusqlite::{params, Connection, OpenFlags, Result, TransactionBehavior};
use std::{path::Path, time::Duration};

const V1: &str = include_str!("v1.sql");
const V2: &str = include_str!("v2.sql");
const V3: &str = include_str!("v3.sql");
const V4: &str = include_str!("v4.sql");

#[derive(Debug, PartialEq, Eq)]
pub struct CachedTimetable {
    pub weeks: Vec<Week>,
    pub lessons: Vec<Lesson>,
}

pub struct Database {
    connection: Connection,
}

impl Database {
    /// Open exactly the path provided by Android's Context.getDatabasePath.
    /// SQLite reads committed WAL data from prior Room installations itself.
    /// Never copy just the main DB file or delete its WAL/SHM companions.
    pub fn open(path: impl AsRef<Path>) -> Result<Self> {
        let connection = Connection::open_with_flags(
            path,
            OpenFlags::SQLITE_OPEN_READ_WRITE | OpenFlags::SQLITE_OPEN_CREATE,
        )?;
        Self::initialize(connection)
    }

    pub fn in_memory() -> Result<Self> {
        Self::initialize(Connection::open_in_memory()?)
    }

    fn initialize(mut connection: Connection) -> Result<Self> {
        connection.busy_timeout(Duration::from_secs(5))?;
        // IMMEDIATE serializes version checks and migration against other opens.
        let tx = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
        let version: i32 = tx.pragma_query_value(None, "user_version", |row| row.get(0))?;
        if !(0..=4).contains(&version) {
            return Err(rusqlite::Error::InvalidQuery);
        }
        if version == 0 {
            // Refuse unversioned databases containing user tables. Treating one
            // as a fresh install could silently accept incompatible data.
            let tables: i64 = tx.query_row(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                [], |row| row.get(0),
            )?;
            if tables != 0 {
                return Err(rusqlite::Error::InvalidQuery);
            }
            tx.execute_batch(V1)?;
        }
        if version < 2 {
            tx.execute_batch(V2)?;
        }
        if version < 3 {
            tx.execute_batch(V3)?;
        }
        if version < 4 {
            tx.execute_batch(V4)?;
        }
        validate_schema(&tx)?;
        // Room v1-v3 identity hashes describe older schemas. Removing only
        // this generated metadata makes Room validate the v4 schema and write
        // its own current hash if an old Room-based v4 APK is installed later.
        // Preserve an existing v4 identity verbatim.
        if version < 4 {
            tx.execute_batch("DROP TABLE IF EXISTS room_master_table")?;
            tx.pragma_update(None, "user_version", 4)?;
        }
        tx.commit()?;
        Ok(Self { connection })
    }

    pub fn periods(&self) -> Result<Vec<Period>> {
        query(&self.connection, "ORDER BY tiet ASC", [])
    }
    pub fn weeks(&self, semester: i32) -> Result<Vec<Week>> {
        query(
            &self.connection,
            "WHERE hocKy=?1 ORDER BY tuanHocKy ASC",
            [semester],
        )
    }
    pub fn semester_lessons(&self, semester: i32) -> Result<Vec<Lesson>> {
        query(
            &self.connection,
            "WHERE hocKy=?1 ORDER BY ngayHoc ASC",
            [semester],
        )
    }
    pub fn lessons(&self, semester: i32, week: i32) -> Result<Vec<Lesson>> {
        query(
            &self.connection,
            "WHERE hocKy=?1 AND tuanHocKy=?2 ORDER BY ngayHoc ASC, tietBatDau ASC",
            [semester, week],
        )
    }
    pub fn cached_timetable(&mut self, semester: i32) -> Result<CachedTimetable> {
        let tx = self.connection.transaction()?;
        let weeks = query(&tx, "WHERE hocKy=?1 ORDER BY tuanHocKy ASC", [semester])?;
        let lessons = query(&tx, "WHERE hocKy=?1 ORDER BY ngayHoc ASC", [semester])?;
        tx.commit()?;
        Ok(CachedTimetable { weeks, lessons })
    }
    pub fn lessons_on(&self, date: &str) -> Result<Vec<Lesson>> {
        let start_date = crate::normalize_date(date);
        let next_date = crate::move_calendar_day(&start_date, 1)
            .unwrap_or_else(|| format!("{start_date}\u{ffff}"));
        query(
            &self.connection,
            "WHERE ngayHoc >= ?1 AND ngayHoc < ?2 ORDER BY tietBatDau ASC",
            params![start_date, next_date],
        )
    }
    pub fn exams_on(&self, date: &str) -> Result<Vec<Exam>> {
        let start_date = crate::normalize_date(date);
        let next_date = crate::move_calendar_day(&start_date, 1)
            .unwrap_or_else(|| format!("{start_date}\u{ffff}"));
        query(
            &self.connection,
            "WHERE ngayThi >= ?1 AND ngayThi < ?2 AND isHoanThi=0 ORDER BY gioBatDau ASC",
            params![start_date, next_date],
        )
    }
    pub fn reminder_lessons_on(&self, semester: i32, date: &str) -> Result<Vec<Lesson>> {
        let start_date = crate::normalize_date(date);
        let next_date = crate::move_calendar_day(&start_date, 1)
            .unwrap_or_else(|| format!("{start_date}\u{ffff}"));
        query(
            &self.connection,
            "WHERE hocKy=?1 AND ngayHoc >= ?2 AND ngayHoc < ?3 ORDER BY tietBatDau ASC",
            params![semester, start_date, next_date],
        )
    }
    pub fn reminder_exams_on(&self, semester: i32, date: &str) -> Result<Vec<Exam>> {
        let start_date = crate::normalize_date(date);
        let next_date = crate::move_calendar_day(&start_date, 1)
            .unwrap_or_else(|| format!("{start_date}\u{ffff}"));
        query(
            &self.connection,
            "WHERE hocKy=?1 AND ngayThi >= ?2 AND ngayThi < ?3 AND isHoanThi=0 ORDER BY gioBatDau ASC",
            params![semester, start_date, next_date],
        )
    }
    pub fn exams(&self, semester: i32) -> Result<Vec<Exam>> {
        query(
            &self.connection,
            "WHERE hocKy=?1 ORDER BY ngayThi ASC, gioBatDau ASC",
            [semester],
        )
    }
    pub fn lesson_notes(&self) -> Result<Vec<LessonNote>> {
        query(&self.connection, "ORDER BY updatedAt DESC", [])
    }
    pub fn week_count(&self, semester: i32) -> Result<i64> {
        self.connection.query_row(
            "SELECT COUNT(*) FROM weeks WHERE hocKy=?1",
            [semester],
            |row| row.get(0),
        )
    }
    pub fn exam_count(&self, semester: i32) -> Result<i64> {
        self.connection.query_row(
            "SELECT COUNT(*) FROM exams WHERE hocKy=?1",
            [semester],
            |row| row.get(0),
        )
    }
    pub fn insert_periods(&mut self, rows: &[Period]) -> Result<()> {
        self.insert_rows(rows)
    }
    pub fn insert_weeks(&mut self, rows: &[Week]) -> Result<()> {
        self.insert_rows(rows)
    }
    pub fn insert_lessons(&mut self, rows: &[Lesson]) -> Result<()> {
        self.insert_rows(rows)
    }
    pub fn insert_exams(&mut self, rows: &[Exam]) -> Result<()> {
        self.insert_rows(rows)
    }
    fn insert_rows<T: records::Record>(&mut self, rows: &[T]) -> Result<()> {
        let tx = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        insert(&tx, rows)?;
        tx.commit()
    }
    pub fn upsert_lesson_note(&mut self, row: &LessonNote) -> Result<()> {
        self.insert_rows(std::slice::from_ref(row))
    }
    pub fn delete_lesson_note(&self, lesson_id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM lesson_notes WHERE lessonId=?1", [lesson_id])?;
        Ok(())
    }
    pub fn clear_all_lesson_notes(&self) -> Result<()> {
        self.connection.execute("DELETE FROM lesson_notes", [])?;
        Ok(())
    }
    pub fn clear_old_lesson_notes(&self, cutoff: i64) -> Result<()> {
        self.connection
            .execute("DELETE FROM lesson_notes WHERE updatedAt < ?1", [cutoff])?;
        Ok(())
    }
    pub fn delete_lessons(&self, semester: i32) -> Result<()> {
        self.connection
            .execute("DELETE FROM lessons WHERE hocKy=?1", [semester])?;
        Ok(())
    }
    pub fn delete_weeks(&self, semester: i32) -> Result<()> {
        self.connection
            .execute("DELETE FROM weeks WHERE hocKy=?1", [semester])?;
        Ok(())
    }
    pub fn delete_periods(&self) -> Result<()> {
        self.connection.execute("DELETE FROM periods", [])?;
        Ok(())
    }
    pub fn delete_exams(&self, semester: i32) -> Result<()> {
        self.connection
            .execute("DELETE FROM exams WHERE hocKy=?1", [semester])?;
        Ok(())
    }
    pub fn delete_exams_by_type(&self, semester: i32, midterm: bool) -> Result<()> {
        self.connection.execute(
            "DELETE FROM exams WHERE hocKy=?1 AND isGiuaKy=?2",
            params![semester, midterm],
        )?;
        Ok(())
    }
    /// Caller must validate the portal response before entering this transaction.
    /// Notes intentionally remain independent of replaced timetable rows.
    pub fn replace_timetable(
        &mut self,
        semester: i32,
        periods: &[Period],
        weeks: &[Week],
        lessons: &[Lesson],
    ) -> Result<()> {
        if weeks.iter().any(|row| row.hoc_ky != semester)
            || lessons.iter().any(|row| row.hoc_ky != semester)
        {
            return Err(rusqlite::Error::InvalidQuery);
        }
        let tx = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        tx.execute("DELETE FROM lessons WHERE hocKy=?1", [semester])?;
        tx.execute("DELETE FROM weeks WHERE hocKy=?1", [semester])?;
        tx.execute("DELETE FROM periods", [])?;
        insert(&tx, periods)?;
        insert(&tx, weeks)?;
        insert(&tx, lessons)?;
        tx.commit()
    }
    pub fn replace_exams(&mut self, semester: i32, exams: &[Exam]) -> Result<()> {
        self.replace_exam_rows(semester, None, exams)
    }
    pub fn replace_exams_by_type(
        &mut self,
        semester: i32,
        midterm: bool,
        exams: &[Exam],
    ) -> Result<()> {
        self.replace_exam_rows(semester, Some(midterm), exams)
    }
    fn replace_exam_rows(
        &mut self,
        semester: i32,
        midterm: Option<bool>,
        exams: &[Exam],
    ) -> Result<()> {
        if exams
            .iter()
            .any(|row| row.hoc_ky != semester || midterm.is_some_and(|kind| row.is_giua_ky != kind))
        {
            return Err(rusqlite::Error::InvalidQuery);
        }
        let tx = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        if let Some(kind) = midterm {
            tx.execute(
                "DELETE FROM exams WHERE hocKy=?1 AND isGiuaKy=?2",
                params![semester, kind],
            )?;
        } else {
            tx.execute("DELETE FROM exams WHERE hocKy=?1", [semester])?;
        }
        insert(&tx, exams)?;
        tx.commit()
    }
    pub fn remove_legacy_sample_exams_if_exact(&mut self, semester: i32) -> Result<bool> {
        let tx = self
            .connection
            .transaction_with_behavior(TransactionBehavior::Immediate)?;
        let exams: Vec<Exam> = query(&tx, "WHERE hocKy=?1", [semester])?;
        let fixture = [
            "B103BC1|2026-11-08|14:25|THECHAT|002",
            "MEC0347|2026-11-12|07:00|DOT1|MEC0347_61TDH8_02",
            "BAS203|2026-11-15|07:00|DOT1|BAS203_61TDH8_01",
            "TEE0220|2026-11-18|12:45|DOT1|TEE0220_61TDH8_02",
            "ELE309|2026-11-25|10:45|DOT1|ELE309_61TDH8_02",
            "ELE201|2026-11-30|10:45|DOT1|ELE201_61TDH8_02",
        ];
        let keys: std::collections::HashSet<_> = exams
            .iter()
            .map(|e| {
                format!(
                    "{}|{}|{}|{}|{}",
                    e.ma_mon, e.ngay_thi, e.gio_bat_dau, e.dot_thi, e.so_bao_danh
                )
            })
            .collect();
        let exact = exams.len() == fixture.len()
            && !exams.iter().any(|e| e.is_giua_ky || e.is_hoan_thi)
            && fixture.iter().all(|key| keys.contains(*key));
        if exact {
            tx.execute("DELETE FROM exams WHERE hocKy=?1", [semester])?;
        }
        tx.commit()?;
        Ok(exact)
    }
}

/// Validate required columns, affinities, nullability, PK order and indexes.
/// Require the exact v4 columns so a Room-based v4 APK can validate on rollback.
fn validate_schema(connection: &Connection) -> Result<()> {
    let expected = Connection::open_in_memory()?;
    for sql in [V1, V2, V3, V4] {
        expected.execute_batch(sql)?;
    }
    for table in ["periods", "weeks", "lessons", "exams", "lesson_notes"] {
        let columns = |db: &Connection| -> Result<Vec<(String, String, bool, i32)>> {
            let mut statement = db.prepare(&format!("PRAGMA table_info({table})"))?;
            let rows = statement
                .query_map([], |row| {
                    Ok((row.get(1)?, row.get(2)?, row.get(3)?, row.get(5)?))
                })?
                .collect();
            rows
        };
        let actual = columns(connection)?;
        let required = columns(&expected)?;
        if actual.len() != required.len() || required.iter().any(|column| !actual.contains(column))
        {
            return Err(rusqlite::Error::InvalidQuery);
        }
    }
    for (name, columns) in [
        ("index_lessons_hocKy_tuanHocKy", vec!["hocKy", "tuanHocKy"]),
        ("index_lessons_ngayHoc", vec!["ngayHoc"]),
        ("index_exams_hocKy_ngayThi", vec!["hocKy", "ngayThi"]),
        ("index_exams_ngayThi", vec!["ngayThi"]),
    ] {
        let mut statement = connection.prepare(&format!("PRAGMA index_info({name})"))?;
        let actual: Vec<String> = statement
            .query_map([], |row| row.get(2))?
            .collect::<Result<_>>()?;
        if actual != columns {
            return Err(rusqlite::Error::InvalidQuery);
        }
    }
    Ok(())
}


