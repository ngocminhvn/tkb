use super::*;
use std::sync::atomic::{AtomicU64, Ordering};

fn period(n: i32) -> Period {
    Period {
        tiet: n,
        gio_bat_dau: "07:00".into(),
        gio_ket_thuc: "07:50".into(),
    }
}
fn week(semester: i32, n: i32) -> Week {
    Week {
        hoc_ky: semester,
        tuan_hoc_ky: n,
        ngay_bat_dau: "2026-11-16".into(),
        ngay_ket_thuc: "2026-11-22".into(),
        thong_tin_tuan: "Tuần học".into(),
    }
}
fn lesson(semester: i32, id: &str, start: i32) -> Lesson {
    Lesson {
        id_tkb: id.into(),
        hoc_ky: semester,
        tuan_hoc_ky: 1,
        ngay_hoc: "2026-11-16T00:00:00".into(),
        thu_kieu_so: 2,
        tiet_bat_dau: start,
        so_tiet: 3,
        ma_mon: "M1".into(),
        ten_mon: "Toán — tiếng Việt ' ?".into(),
        so_tin_chi: "3".into(),
        ma_phong: "A".into(),
        ten_giang_vien: "GV".into(),
        ten_lop: "L".into(),
        ma_nhom: "N".into(),
    }
}
fn exam(semester: i32, midterm: bool) -> Exam {
    Exam {
        id: 0,
        hoc_ky: semester,
        is_giua_ky: midterm,
        so_thu_tu: 1,
        ma_mon: "M1".into(),
        ten_mon: "Toán".into(),
        ngay_thi: "2026-11-16".into(),
        gio_bat_dau: "07:00".into(),
        so_phut: 90,
        ma_phong: "A".into(),
        dia_diem_thi: "TNUT".into(),
        hinh_thuc_thi: "Viết".into(),
        so_bao_danh: "001".into(),
        dot_thi: "D1".into(),
        ghi_chu_sv: "Ghi chú".into(),
        is_hoan_thi: false,
    }
}
fn note(id: &str, time: i64) -> LessonNote {
    LessonNote {
        lesson_id: id.into(),
        note: "Nội dung riêng tư '); DROP TABLE lessons;--".into(),
        updated_at: time,
    }
}
struct FileDb(std::path::PathBuf);
impl FileDb {
    fn new() -> Self {
        static NEXT: AtomicU64 = AtomicU64::new(0);
        Self(std::env::temp_dir().join(format!(
            "tkb-test-{}-{}.db",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        )))
    }
}
impl Drop for FileDb {
    fn drop(&mut self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
        }
    }
}

#[test]
fn fresh_schema_and_all_record_round_trips() {
    let mut db = Database::in_memory().unwrap();
    assert_eq!(
        db.connection
            .pragma_query_value(None, "user_version", |r| r.get::<_, i32>(0))
            .unwrap(),
        4
    );
    db.insert_periods(&[period(2), period(1)]).unwrap();
    assert_eq!(db.periods().unwrap(), vec![period(1), period(2)]);
    db.insert_weeks(&[week(1, 2), week(1, 1), week(2, 1)])
        .unwrap();
    assert_eq!(db.weeks(1).unwrap(), vec![week(1, 1), week(1, 2)]);
    assert_eq!(db.week_count(1).unwrap(), 2);
    db.insert_lessons(&[lesson(1, "l1", 4), lesson(1, "l2", 1), lesson(2, "l3", 2)])
        .unwrap();
    assert_eq!(
        db.lessons(1, 1).unwrap(),
        vec![lesson(1, "l2", 1), lesson(1, "l1", 4)]
    );
    assert_eq!(db.semester_lessons(1).unwrap().len(), 2);
    assert_eq!(db.lessons_on("2026-11-16").unwrap().len(), 3);
    assert_eq!(db.reminder_lessons_on(1, "2026-11-16").unwrap().len(), 2);
    assert!(db.lessons_on("' OR 1=1 --").unwrap().is_empty());
    let mut later = exam(1, false);
    later.gio_bat_dau = "09:00".into();
    let mut deferred = exam(1, true);
    deferred.is_hoan_thi = true;
    db.insert_exams(&[later, exam(1, false), exam(2, true), deferred])
        .unwrap();
    let rows = db.exams(1).unwrap();
    assert_eq!(rows.len(), 3);
    assert!(rows.iter().all(|e| e.id > 0));
    assert_eq!(rows[0].ten_mon, "Toán");
    assert_eq!(db.exams_on("2026-11-16").unwrap().len(), 3);
    assert_eq!(db.reminder_exams_on(1, "2026-11-16").unwrap().len(), 2);
    assert_eq!(db.exams(1).unwrap().last().unwrap().gio_bat_dau, "09:00");
    let mut updated = rows[0].clone();
    updated.ten_mon = "Môn mới".into();
    db.insert_exams(&[updated.clone()]).unwrap();
    assert_eq!(db.exam_count(1).unwrap(), 3);
    assert!(db.exams(1).unwrap().contains(&updated));
    db.upsert_lesson_note(&note("l1", 10)).unwrap();
    db.upsert_lesson_note(&note("l2", 20)).unwrap();
    db.upsert_lesson_note(&note("l1", 30)).unwrap();
    assert_eq!(
        db.lesson_notes().unwrap(),
        vec![note("l1", 30), note("l2", 20)]
    );
    db.clear_old_lesson_notes(30).unwrap();
    assert_eq!(db.lesson_notes().unwrap(), vec![note("l1", 30)]);
    db.delete_lesson_note("l1").unwrap();
    assert!(db.lesson_notes().unwrap().is_empty());
    db.upsert_lesson_note(&note("l1", 40)).unwrap();
    db.clear_all_lesson_notes().unwrap();
    assert!(db.lesson_notes().unwrap().is_empty());
}

#[test]
fn replacement_preserves_notes_other_semesters_and_other_exam_type() {
    let mut db = Database::in_memory().unwrap();
    for semester in [1, 2] {
        db.insert_weeks(&[week(semester, 1)]).unwrap();
        db.insert_lessons(&[lesson(semester, &format!("l{semester}"), 1)])
            .unwrap();
        db.insert_exams(&[exam(semester, false), exam(semester, true)])
            .unwrap();
    }
    db.upsert_lesson_note(&note("l1", 10)).unwrap();
    db.replace_timetable(1, &[period(3)], &[week(1, 2)], &[lesson(1, "new", 5)])
        .unwrap();
    let snapshot = db.cached_timetable(1).unwrap();
    assert_eq!(snapshot.weeks, vec![week(1, 2)]);
    assert_eq!(snapshot.lessons, vec![lesson(1, "new", 5)]);
    assert_eq!(db.week_count(2).unwrap(), 1);
    assert_eq!(db.semester_lessons(2).unwrap().len(), 1);
    assert_eq!(db.lesson_notes().unwrap(), vec![note("l1", 10)]);
    db.replace_exams_by_type(1, false, &[exam(1, false)])
        .unwrap();
    assert_eq!(db.exam_count(1).unwrap(), 2);
    db.replace_exams_by_type(1, true, &[]).unwrap();
    assert_eq!(db.exam_count(1).unwrap(), 1);
    assert_eq!(db.exam_count(2).unwrap(), 2);
    db.replace_exams(1, &[]).unwrap();
    assert_eq!(db.exam_count(1).unwrap(), 0);
    db.delete_lessons(1).unwrap();
    db.delete_weeks(1).unwrap();
    db.delete_periods().unwrap();
    db.delete_exams_by_type(2, true).unwrap();
    assert_eq!(db.exam_count(2).unwrap(), 1);
    db.delete_exams(2).unwrap();
    assert!(db.periods().unwrap().is_empty());
    assert_eq!(db.week_count(1).unwrap(), 0);
    assert!(db.semester_lessons(1).unwrap().is_empty());
    assert_eq!(db.exam_count(2).unwrap(), 0);
}

#[test]
fn sql_error_rolls_back_every_delete_and_partial_insert() {
    let mut db = Database::in_memory().unwrap();
    db.replace_timetable(1, &[period(1)], &[week(1, 1)], &[lesson(1, "old", 1)])
        .unwrap();
    db.connection.execute_batch("CREATE TRIGGER fail_lesson BEFORE INSERT ON lessons WHEN NEW.idTkb='fail' BEGIN SELECT RAISE(ABORT, 'injected failure'); END;").unwrap();
    assert!(db
        .replace_timetable(
            1,
            &[period(2)],
            &[week(1, 2)],
            &[lesson(1, "new", 1), lesson(1, "fail", 1)]
        )
        .is_err());
    assert_eq!(db.periods().unwrap(), vec![period(1)]);
    assert_eq!(db.weeks(1).unwrap(), vec![week(1, 1)]);
    assert_eq!(db.semester_lessons(1).unwrap(), vec![lesson(1, "old", 1)]);
    db.insert_exams(&[exam(1, false), exam(1, true)]).unwrap();
    let before = db.exams(1).unwrap();
    db.connection.execute_batch("CREATE TRIGGER fail_exam BEFORE INSERT ON exams WHEN NEW.maMon='fail' BEGIN SELECT RAISE(ABORT, 'injected failure'); END;").unwrap();
    let mut fail = exam(1, false);
    fail.ma_mon = "fail".into();
    assert!(db
        .replace_exams_by_type(1, false, &[exam(1, false), fail.clone()])
        .is_err());
    assert_eq!(db.exams(1).unwrap(), before);
    assert!(db.replace_exams(1, &[fail]).is_err());
    assert_eq!(db.exams(1).unwrap(), before);
    assert!(db
        .insert_lessons(&[lesson(1, "partial", 1), lesson(1, "fail", 1)])
        .is_err());
    assert_eq!(db.semester_lessons(1).unwrap(), vec![lesson(1, "old", 1)]);
}

#[test]
fn invalid_scope_rejected_before_any_deletion() {
    let mut db = Database::in_memory().unwrap();
    db.insert_exams(&[exam(1, false)]).unwrap();
    assert!(db
        .replace_exams_by_type(1, false, &[exam(1, true)])
        .is_err());
    assert!(db.replace_exams(1, &[exam(2, false)]).is_err());
    assert_eq!(db.exam_count(1).unwrap(), 1);
    db.insert_weeks(&[week(1, 1)]).unwrap();
    assert!(db.replace_timetable(1, &[], &[week(2, 2)], &[]).is_err());
    assert_eq!(db.week_count(1).unwrap(), 1);
}

#[test]
fn upgrades_each_supported_version_and_handles_room_identity_without_losing_data() {
    for version in 1..=4 {
        let file = FileDb::new();
        let old = Connection::open(&file.0).unwrap();
        for sql in [V1, V2, V3, V4].iter().take(version) {
            old.execute_batch(sql).unwrap();
        }
        old.pragma_update(None, "user_version", version as i32)
            .unwrap();
        old.execute_batch("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT); INSERT INTO room_master_table VALUES(42,'existing-room-v4-identity');").unwrap();
        insert(&old, &[week(1, 1)]).unwrap();
        insert(&old, &[lesson(1, "old", 1)]).unwrap();
        if version >= 2 {
            insert(&old, &[exam(1, false)]).unwrap();
        }
        if version >= 3 {
            insert(&old, &[note("old", 123)]).unwrap();
        }
        drop(old);
        let mut db = Database::open(&file.0).unwrap();
        assert_eq!(
            db.cached_timetable(1).unwrap().lessons,
            vec![lesson(1, "old", 1)]
        );
        assert_eq!(db.exam_count(1).unwrap(), i64::from(version >= 2));
        assert_eq!(db.lesson_notes().unwrap().len(), usize::from(version >= 3));
        if version == 4 {
            let identity: String = db
                .connection
                .query_row(
                    "SELECT identity_hash FROM room_master_table WHERE id=42",
                    [],
                    |r| r.get(0),
                )
                .unwrap();
            assert_eq!(identity, "existing-room-v4-identity");
        } else {
            let masters: i32 = db
                .connection
                .query_row(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name='room_master_table'",
                    [],
                    |r| r.get(0),
                )
                .unwrap();
            assert_eq!(
                masters, 0,
                "Room must revalidate migrated schema instead of reading a stale v1-v3 identity"
            );
        }
        drop(db);
        assert_eq!(Database::open(&file.0).unwrap().week_count(1).unwrap(), 1);
    }
}

#[test]
fn future_unversioned_and_incompatible_databases_remain_untouched() {
    for version in [0i32, 3, 4, 5] {
        let file = FileDb::new();
        let old = Connection::open(&file.0).unwrap();
        old.execute_batch(
            "CREATE TABLE sentinel(value TEXT); INSERT INTO sentinel VALUES('keep');",
        )
        .unwrap();
        old.pragma_update(None, "user_version", version).unwrap();
        drop(old);
        assert!(Database::open(&file.0).is_err());
        let old = Connection::open(&file.0).unwrap();
        assert_eq!(
            old.pragma_query_value(None, "user_version", |r| r.get::<_, i32>(0))
                .unwrap(),
            version
        );
        assert_eq!(
            old.query_row("SELECT value FROM sentinel", [], |r| r.get::<_, String>(0))
                .unwrap(),
            "keep"
        );
        assert_eq!(
            old.query_row(
                "SELECT COUNT(*) FROM sqlite_master WHERE name='lesson_notes'",
                [],
                |r| r.get::<_, i32>(0)
            )
            .unwrap(),
            0
        );
    }
}

#[test]
fn wal_commits_survive_open_and_reads_keep_one_snapshot() {
    let file = FileDb::new();
    let mut writer = Database::open(&file.0).unwrap();
    writer
        .connection
        .pragma_update(None, "journal_mode", "WAL")
        .unwrap();
    writer
        .connection
        .pragma_update(None, "wal_autocheckpoint", 0)
        .unwrap();
    writer
        .replace_timetable(1, &[], &[week(1, 1)], &[lesson(1, "old", 1)])
        .unwrap();
    // Keep writer alive so committed rows remain in the WAL, as with Room.
    let mut reader = Database::open(&file.0).unwrap();
    assert_eq!(
        reader.cached_timetable(1).unwrap().lessons,
        vec![lesson(1, "old", 1)]
    );
    let tx = reader.connection.transaction().unwrap();
    assert_eq!(
        query::<Week>(&tx, "WHERE hocKy=?1", [1]).unwrap(),
        vec![week(1, 1)]
    );
    writer
        .replace_timetable(1, &[], &[week(1, 2)], &[lesson(1, "new", 1)])
        .unwrap();
    assert_eq!(
        query::<Lesson>(&tx, "WHERE hocKy=?1", [1]).unwrap(),
        vec![lesson(1, "old", 1)]
    );
    tx.commit().unwrap();
    assert_eq!(
        reader.cached_timetable(1).unwrap().lessons,
        vec![lesson(1, "new", 1)]
    );
}

#[test]
fn exact_legacy_cleanup_leaves_modified_or_real_exams_alone() {
    let mut db = Database::in_memory().unwrap();
    let fixture = [
        ("B103BC1", "2026-11-08", "14:25", "THECHAT", "002"),
        (
            "MEC0347",
            "2026-11-12",
            "07:00",
            "DOT1",
            "MEC0347_61TDH8_02",
        ),
        ("BAS203", "2026-11-15", "07:00", "DOT1", "BAS203_61TDH8_01"),
        (
            "TEE0220",
            "2026-11-18",
            "12:45",
            "DOT1",
            "TEE0220_61TDH8_02",
        ),
        ("ELE309", "2026-11-25", "10:45", "DOT1", "ELE309_61TDH8_02"),
        ("ELE201", "2026-11-30", "10:45", "DOT1", "ELE201_61TDH8_02"),
    ];
    let rows: Vec<_> = fixture
        .iter()
        .map(|(code, date, time, batch, number)| {
            let mut e = exam(1, false);
            e.ma_mon = (*code).into();
            e.ngay_thi = (*date).into();
            e.gio_bat_dau = (*time).into();
            e.dot_thi = (*batch).into();
            e.so_bao_danh = (*number).into();
            e
        })
        .collect();
    for variant in 0..5 {
        let mut changed = rows.clone();
        match variant {
            0 => {
                changed.pop();
            }
            1 => changed[0].is_giua_ky = true,
            2 => changed[0].is_hoan_thi = true,
            3 => changed[0].so_bao_danh = "real".into(),
            _ => {
                changed[1] = changed[0].clone();
            }
        }
        db.replace_exams(1, &changed).unwrap();
        assert!(!db.remove_legacy_sample_exams_if_exact(1).unwrap());
        assert_eq!(db.exam_count(1).unwrap(), changed.len() as i64);
    }
    db.replace_exams(1, &rows).unwrap();
    db.insert_exams(&[exam(2, false)]).unwrap();
    assert!(db.remove_legacy_sample_exams_if_exact(1).unwrap());
    assert_eq!(db.exam_count(1).unwrap(), 0);
    assert_eq!(db.exam_count(2).unwrap(), 1);
}

#[test]
fn native_schema_matches_the_existing_kotlin_room_entity_contract() {
    // Independent Android source is the authority; do not just round-trip our
    // own generated schema and assume it is compatible with installed Room.
    let kotlin = include_str!("../../../app/src/main/java/com/sv/tnu/data/AppDatabase.kt");
    let db = Database::in_memory().unwrap();
    for (entity, table) in [
        ("PeriodEntity", "periods"),
        ("WeekEntity", "weeks"),
        ("LessonEntity", "lessons"),
        ("ExamEntity", "exams"),
        ("LessonNoteEntity", "lesson_notes"),
    ] {
        let declaration = kotlin
            .split(&format!("data class {entity}("))
            .nth(1)
            .unwrap()
            .split("\n)")
            .next()
            .unwrap();
        let expected: Vec<(String, String)> = declaration
            .lines()
            .filter_map(|line| {
                let field = line.split("val ").nth(1)?;
                let (name, rest) = field.split_once(':')?;
                let ty = rest.trim().split([',', ' ', '=']).next().unwrap();
                let affinity = match ty {
                    "String" => "TEXT",
                    "Int" | "Long" | "Boolean" => "INTEGER",
                    _ => panic!("Unmapped Room type: {ty}"),
                };
                Some((name.into(), affinity.into()))
            })
            .collect();
        let mut statement = db
            .connection
            .prepare(&format!("PRAGMA table_info({table})"))
            .unwrap();
        let actual: Vec<(String, String)> = statement
            .query_map([], |row| Ok((row.get(1)?, row.get(2)?)))
            .unwrap()
            .collect::<Result<_>>()
            .unwrap();
        assert_eq!(actual, expected, "Room/native column mismatch in {table}");
    }
}
