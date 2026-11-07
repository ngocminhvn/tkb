#!/usr/bin/env python3
"""Fast data-safety regression tests; stdlib-only for GitHub Actions."""
from pathlib import Path
import re
import sqlite3

root = Path("app/src/main/java/com/sv/tnu")
repo = (root / "tnut/TnutRepository.kt").read_text(encoding="utf-8")
db_source = (root / "data/AppDatabase.kt").read_text(encoding="utf-8")
ui = (root / "ui/TimetableScreen.kt").read_text(encoding="utf-8")

# Never purge a student's data before fetching replacement data.
refresh = repo.split("suspend fun refresh(hocKy: Int)", 1)[1].split("private suspend fun saveToDb", 1)[0]
for destructive in ("dao.clearAllLessonNotes(", "dao.clearOldLessonNotes(",
                    "dao.deleteLessons(", "dao.deleteWeeks(", "dao.deleteExams("):
    assert destructive not in refresh, f"Unsafe pre-fetch cleanup: {destructive}"
assert refresh.index("api.weeklyTimetable(") < refresh.index("saveToDb(hocKy, res.data)")
assert refresh.index("saveToDb(hocKy, res.data)") < refresh.index("authStore.lastSyncTime =")

# Offline cache cleanup is serialized before refresh and cannot repopulate
# legacy demo exams/timetable belonging to a different student.
assert "private val cacheReadyJob = viewModelScope.launch" in ui
assert ui.count("cacheReadyJob.join()") >= 2
initializer = ui.split("private val cacheReadyJob =", 1)[1].split("fun setShowExamsOnHome", 1)[0]
assert "fetchExamsOnly()" not in initializer
assert "if (_refreshing.value) return" in ui
assert "if (_isFetchingExams.value) return" in ui
assert "withContext(Dispatchers.IO)" in repo
assert "prepareOfflineCache(hocKy)" in ui
assert "suspend fun prepareOfflineCache(hocKy: Int)" in repo
assert "seedSampleExamsIfEmpty" not in repo
assert "sample_tkb_tuan_20261.json" not in repo
assert "dao.removeLegacySampleExamsIfExact(hocKy)" in repo
assert "suspend fun removeLegacySampleExamsIfExact(hocKy: Int)" in db_source

# Empty portal payloads must not wipe real or imported offline exams.
assert "if (fetchedCuoiKy && finalExams.isNotEmpty())" in repo
assert "if (fetchedGiuaKy && midtermExams.isNotEmpty())" in repo
assert "incomingLessons == 0" in repo
assert "Dữ liệu đã lưu được giữ nguyên" in repo
assert "return Result.failure(" in repo
assert "dao.replaceExamsByType(hocKy, false, finalExams)" in repo
assert "dao.replaceExamsByType(hocKy, true, midtermExams)" in repo
assert "suspend fun deleteExamsByType(" in db_source
assert "suspend fun replaceExamsByType(" in db_source
assert "resCuoiKy.data != null" in repo
assert "resGiuaKy.data != null" in repo
assert "suspend fun importPersonalTimetable(uri: Uri, hocKy: Int)" in repo
assert "suspend fun importPersonalExams(uri: Uri, hocKy: Int)" in repo
assert "dao.replaceExamsByType(hocKy, false, exams)" in repo
assert "saveToDb(hocKy, data)" in repo
assert "PersonalExamImporter.parse(" in repo
parser = (root / "tnut/PersonalExamImporter.kt").read_text(encoding="utf-8")
assert "class PersonalExamImporter" in parser or "object PersonalExamImporter" in parser
assert "val normalizedDate" in parser
assert "require(maMon.isNotBlank() && tenMon.isNotBlank())" in parser
settings = (root / "ui/SettingsScreen.kt").read_text(encoding="utf-8")
assert "Khôi phục thời khóa biểu từ JSON" in settings
assert "Nhập lịch thi từ Excel (CSV/TSV)" in settings

# Past days are filtered uniformly across lessons AND exam cards, including
# when app resumes from the background after a local calendar-day change.
assert "(lessonItems + examItems)" in ui
assert "allGrouped.filterKeys { date -> date >= todayStr }" in ui
assert "if (showPastDays)" in ui
assert "event == Lifecycle.Event.ON_RESUME" in ui
assert "todayStr = localTodayYmd()" in ui

# Guard exact legacy demo fingerprint: never delete arbitrary valid exams.
assert "exams.size != fixture.size || keys != fixture" in db_source
assert "exams.any { it.isGiuaKy || it.isHoanThi }" in db_source
assert "}.toSet()" in db_source
assert "B103BC1|2026-11-08|14:25|THECHAT|002" in db_source
assert "deleteExams(hocKy)" in db_source

# Every historical on-disk database must have a non-destructive migration.
assert "fallbackToDestructiveMigration" not in db_source
assert "version = 4" in db_source

def migrate_sql(name, next_marker):
    chunk = db_source.split(f"private val {name} =", 1)[1].split(next_marker, 1)[0]
    triple = re.findall(r'db\.execSQL\(\s*"""(.*?)"""\.trimIndent\(\),?\s*\)', chunk, re.S)
    single = re.findall(r'db\.execSQL\("([^"]+)"\)', chunk)
    statements = triple + single
    assert statements, f"No SQL in {name}"
    return statements

migrations = [
    migrate_sql("migration1To2", "private val migration2To3"),
    migrate_sql("migration2To3", "private val migration3To4"),
    migrate_sql("migration3To4", "@Volatile"),
]
assert ".addMigrations(migration1To2, migration2To3, migration3To4)" in db_source

# Simulate legacy version-1 database and upgrade all the way to version 4.
conn = sqlite3.connect(":memory:")
conn.executescript("""
CREATE TABLE periods (tiet INTEGER PRIMARY KEY, gioBatDau TEXT NOT NULL, gioKetThuc TEXT NOT NULL);
CREATE TABLE weeks (hocKy INTEGER NOT NULL, tuanHocKy INTEGER NOT NULL, ngayBatDau TEXT NOT NULL,
                    ngayKetThuc TEXT NOT NULL, thongTinTuan TEXT NOT NULL, PRIMARY KEY(hocKy,tuanHocKy));
CREATE TABLE lessons (idTkb TEXT PRIMARY KEY, hocKy INTEGER NOT NULL, tuanHocKy INTEGER NOT NULL,
                      ngayHoc TEXT NOT NULL);
INSERT INTO periods VALUES (1,'07:00','07:50');
INSERT INTO weeks VALUES (20261,1,'2026-09-01','2026-09-07','week 1');
INSERT INTO lessons VALUES ('lesson-1',20261,1,'2026-09-02');
""")
for statement in migrations[0]:
    conn.execute(statement)
assert len(conn.execute("PRAGMA table_info(exams)").fetchall()) == 16
for statement in migrations[1]:
    conn.execute(statement)
conn.execute("INSERT INTO lesson_notes (lessonId,note,updatedAt) VALUES ('lesson-1','Important note',123)")
for statement in migrations[2]:
    conn.execute(statement)
assert conn.execute("SELECT note FROM lesson_notes WHERE lessonId='lesson-1'").fetchone()[0] == "Important note"
assert conn.execute("SELECT COUNT(*) FROM lessons").fetchone()[0] == 1
assert conn.execute("SELECT COUNT(*) FROM weeks").fetchone()[0] == 1
for index in ("index_exams_hocKy_ngayThi", "index_exams_ngayThi", "index_lessons_ngayHoc"):
    assert conn.execute("SELECT 1 FROM sqlite_master WHERE type='index' AND name=?", (index,)).fetchone(), index
conn.close()
print("OK: timetable/notes survive sync, demo exams are excluded, past exams hide, v1-v4 migration preserves data")
