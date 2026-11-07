#!/usr/bin/env python3
"""Data safety, Room migration, school-provider and release checks."""
from pathlib import Path
import re
import sqlite3

root = Path("app/src/main/java/com/sv/tnu")
repo = (root / "schools/tnut/TnutRepository.kt").read_text(encoding="utf-8")
db_source = (root / "data/AppDatabase.kt").read_text(encoding="utf-8")
ui = (root / "ui/TimetableScreen.kt").read_text(encoding="utf-8")
client = (root / "schools/tnut/TnutApiClient.kt").read_text(encoding="utf-8")
auth = (root / "data/AuthStore.kt").read_text(encoding="utf-8")
adapter = (root / "schools/tnut/TnutSchoolProvider.kt").read_text(encoding="utf-8")
contract = (root / "schools/SchoolProvider.kt").read_text(encoding="utf-8")
registry = (root / "schools/SchoolRegistry.kt").read_text(encoding="utf-8")
activity = (root / "MainActivity.kt").read_text(encoding="utf-8")
workflow = Path(".github/workflows/build.yml").read_text(encoding="utf-8")
build = Path("app/build.gradle.kts").read_text(encoding="utf-8")
rules = Path("app/proguard-rules.pro").read_text(encoding="utf-8")

# Portal data must be validated before the Room transaction; failed requests
# must not purge previously saved lessons, exams or personal notes.
refresh = repo.split("suspend fun refresh(hocKy: Int)", 1)[1].split("private suspend fun saveToDb", 1)[0]
assert refresh.index("api.weeklyTimetable(") < refresh.index("saveToDb(hocKy, data)")
assert refresh.index("saveToDb(hocKy, data)") < refresh.index("authStore.lastSyncTime =")
for destructive in ("dao.deleteLessons(", "dao.deleteWeeks(", "dao.deleteExams(", "dao.clearAllLessonNotes("):
    assert destructive not in refresh
assert "dao.replaceTimetable(hocKy, data.periods, data.weeks, data.lessons)" in repo
assert "dao.replaceExamsByType(hocKy, isGiuaKy, exams)" in repo
assert "if (exams.isNotEmpty())" in repo
assert "dao.removeLegacySampleExamsIfExact(hocKy)" in repo
assert "exams.size != fixture.size || keys != fixture" in db_source

# School transport and credentials are decoupled from the shared UI.
assert "interface SchoolRepository" in contract
assert "interface SchoolSession" in contract
assert "interface SchoolProvider" in contract
assert "TnutSchoolProvider" in registry and "SchoolIds.TNUT" in adapter
assert "SchoolRegistry.forId(authStore.schoolId)" in activity
assert "schoolSession.login(username, password)" in (root / "ui/LoginScreen.kt").read_text(encoding="utf-8")
assert "SchoolSessionTokens(" in adapter and "SchoolSessionTokens(" in client
assert "SchoolIds.TNUT" in auth
assert 'KEY_SCHOOL_ID = "school_id"' in auth
assert "private val initialSnapshot: TimetableSnapshot? = runCatching" in ui
assert "repository.cachedTimetable(hocKy)" in ui
assert "pendingWeek = weekNumber" in ui
for name in ("LoginScreen.kt", "MainScreen.kt", "SettingsScreen.kt", "TimetableScreen.kt"):
    assert "com.sv.tnu.tnut" not in (root / "ui" / name).read_text(encoding="utf-8")

# Never destructively migrate v1–v4 Room DBs or lose personal lesson notes.
assert "fallbackToDestructiveMigration" not in db_source
assert "version = 4" in db_source

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


# The published APK must be R8 Release, signed with the existing key.
assert 'applicationId = "com.tkb.sv"' in build
assert "testDebugUnitTest assembleRelease" in workflow
assert "Signer #1 certificate SHA-256 digest" in workflow
assert "outputs/apk/release/app-release.apk" in workflow
assert "-keep class com.sv.tnu.schools.tnut.** { *; }" in rules
assert "-keep class com.sv.tnu.rust.RustScheduleCore { *; }" in rules
assert "-keep class com.sv.tnu.rust.RustPortalCore { *; }" in rules
assert "bash scripts/build-rust-android.sh" in workflow
assert 'System.loadLibrary("tkb_core")' in (root / "rust/RustScheduleCore.kt").read_text(encoding="utf-8")
print("OK: school provider boundaries, Room migrations, offline cache and Release JNI")
