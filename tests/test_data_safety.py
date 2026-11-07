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
assert refresh.index("api.weeklyTimetable(") < refresh.index("saveToDb(hocKy, data)")
assert refresh.index("saveToDb(hocKy, data)") < refresh.index("authStore.lastSyncTime =")
assert "val examSync = async {" in refresh
assert "fetchAndSaveExams(hocKy)" in refresh
assert "examSync.await()" in refresh
assert refresh.index("val examSync = async {") < refresh.index("api.weeklyTimetable(")
assert "res.code != 200 || data == null" in refresh
assert "data.dsTuanTkb.isEmpty()" in refresh

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

# The exam endpoint must use a fully specified, non-default request body.
models = (root / "tnut/TnutModels.kt").read_text(encoding="utf-8")
assert "ExamRequest.forSemester(hocKy, isGiuaKy, page)" in repo
assert "val isGiuaKy: Boolean," in models
assert "data class ExamPaging(" in models
assert "val limit: Int," in models and "val page: Int," in models
assert "data class ExamAdditional(" in models
assert "ExamOrdering(name = null, orderType = null)" in models
assert 'SerialName("is_giua_ky")' in models
assert "val additional: ExamAdditional," in models

# No missing/partial page is allowed to wipe genuine cached or imported exams.
assert "private suspend fun loadPortalExams(" in repo
assert "data.totalPages?.takeIf { it > 0 }" in repo
assert "page < maxPages" in repo
assert "if (exams.isNotEmpty())" in repo
assert "dao.replaceExamsByType(hocKy, isGiuaKy, exams)" in repo
assert "Dữ liệu đã lưu được giữ nguyên" in repo
assert "suspend fun deleteExamsByType(" in db_source
assert "suspend fun replaceExamsByType(" in db_source
assert "saveToDb(hocKy, data)" in repo

# Only the TNUT portal is allowed to supply timetable/exam data. Remove
# manual import UI, file pickers, parser, and their repository entry points.
settings = (root / "ui/SettingsScreen.kt").read_text(encoding="utf-8")
for removed in (
    "Khôi phục thời khóa biểu từ JSON",
    "Nhập lịch thi từ Excel (CSV/TSV)",
    "importPersonalTimetable",
    "importPersonalExams",
    "timetableJsonPicker",
    "examCsvPicker",
    "rememberLauncherForActivityResult",
):
    assert removed not in settings, f"Manual import action still present: {removed}"
for removed in ("importPersonalTimetable", "importPersonalExams", "readPersonalFile", "PersonalExamImporter"):
    assert removed not in repo, f"Manual import repository path still present: {removed}"
assert not (root / "tnut/PersonalExamImporter.kt").exists()

# A successful login must trigger a fresh portal sync even when a prior
# session has already refreshed today. Otherwise stored exams go stale.
auth_store = (root / "data/AuthStore.kt").read_text(encoding="utf-8")
assert ".putLong(KEY_LAST_AUTO_SYNC_ATTEMPT, 0L)" in auth_store
assert "val lastAutomaticCheck = authStore.lastAutoSyncAttempt" in ui
assert "val lastAutomaticCheck = maxOf(" not in ui
main_screen = (root / "ui/MainScreen.kt").read_text(encoding="utf-8")
assert "LaunchedEffect(timetableViewModel)" in main_screen
assert "timetableViewModel.refreshIfNeeded()" in main_screen
assert "viewModel.refreshIfNeeded()" not in ui

# Real phone-local today is highlighted, not just the start of week.
# Both lesson and exam cards are now scoped to the explicitly selected day.
calendar = (root / "ui/WeekDatePicker.kt").read_text(encoding="utf-8")
glass = (root / "ui/glass/LiquidGlass.kt").read_text(encoding="utf-8")
assert "(lessonItems + examItems)" in ui
assert "allGrouped.filterKeys { date -> date == selectedDay }" in ui
assert "var selectedDay by remember { mutableStateOf(todayStr) }" in ui
assert "if (actualToday != todayStr)" in ui
assert "viewModel.updateToday()" in ui
assert "event == Lifecycle.Event.ON_RESUME" in ui
assert "nextMidnight - System.currentTimeMillis()" in ui
assert "private val _todayYmd = MutableStateFlow(localTodayYmd())" in ui
assert "currentWeekNumber(weekList, today)" in ui
assert "todayYmd >= normalizeDateToYmd(week.ngayBatDau)" in ui
assert "WeekDatePicker(" in ui
assert "eventDays = eventDays" in ui
assert "val semesterLessons by viewModel.semesterLessons.collectAsState()" in ui
assert "if (note.isNotBlank()) {" in ui
assert 'text = if (showPastDays)' not in ui
assert 'text = if (selectedWeek != null) "Tuần $selectedWeek"' not in ui
assert "fun WeekDatePicker(" in calendar
assert "private fun monthDates(" in calendar
assert "private fun dateInWeek(" in calendar
assert "detectDragGestures(" in calendar
assert "selectedYmd = selectedYmd" in calendar
assert "ymd == todayYmd" in calendar
assert "hasSchedule = ymd in eventDays" in calendar
assert "dateInWeek(ymd, it)" in calendar
# Remove the harsh white rim, keep the sliding lens and native tab selection.
nav_bar = glass.split("fun GlassTabBar(", 1)[1]
assert "containerBorderBrush" not in nav_bar
assert ".border(" not in nav_bar
assert "lensOffsetAnim.animateTo(" in nav_bar
assert "currentOnSelected(finalTab)" in nav_bar

# Note editor remains opt-in, but the extra Liquid Glass button migration
# is completely reverted. Saved notes still live in Room.
assert "if (note.isNotBlank()) {" in ui
assert "IconButton(" in ui
assert 'contentDescription = if (note.isBlank()) "Thêm ghi chú" else "Sửa ghi chú"' in ui
assert 'text = note.ifBlank { "Thêm ghi chú" }' not in ui
assert 'text = "${lesson.soTinChi} TC"' not in ui
assert "note = lessonNotes[item.lesson.idTkb].orEmpty()" in ui
assert "onEditNote = { noteLesson = item.lesson }" in ui
assert "viewModel.saveLessonNote(lesson.idTkb, note)" in ui
assert "TextButton(onClick = { onSave(note.trim()) })" in ui
assert "GlassIconButton(" not in ui
assert "GlassTextButton(" not in ui
assert not (root / "ui/glass/GlassButtons.kt").exists()
glass = (root / "ui/glass/LiquidGlass.kt").read_text(encoding="utf-8")
assert "interactionSource: MutableInteractionSource? = null" not in glass

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
