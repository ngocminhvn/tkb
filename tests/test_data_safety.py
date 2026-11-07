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
native_portal = Path("rust-core/src/portal.rs").read_text(encoding="utf-8")
native_bridge = (root / "rust/RustPortalCore.kt").read_text(encoding="utf-8")
assert "private suspend fun loadPortalExams(" in repo
assert "RustPortalCore.hasNextExamPage(page, mapped.totalPages, mapped.rowCount)" in repo
assert "RustPortalCore.dedupeExams(all)" in repo
assert "RustPortalCore.normalizeNote(note)" in repo
assert "pub fn exam_has_next(" in native_portal
assert "pub fn dedupe_exam_rows(" in native_portal
assert "pub fn normalize_note(" in native_portal
assert "RustPortalCore.mapExamPage(hocKy, isGiuaKy, response.data)" in repo
assert "RustPortalCore.mapTimetable(hocKy, data)" in repo
assert "dao.replaceTimetable(hocKy, mapped.periods, mapped.weeks, mapped.lessons)" in repo
assert "@Serializable" in db_source
assert "pub fn map_timetable(" in native_portal
assert "pub fn map_exam_page(" in native_portal
assert "System.loadLibrary(\"tkb_core\")" in native_bridge
assert "mapTimetableNative(semester, source)" in native_bridge
assert "mapExamPageNative(semester, isMidterm, source)" in native_bridge
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
):
    assert removed not in settings, f"Manual import action still present: {removed}"
# Permission request launcher is required for Android 13+ notifications.
# It must not be mistaken for the deleted JSON/CSV file pickers.
assert "ActivityResultContracts.RequestPermission()" in settings
assert "ActivityResultContracts.OpenDocument()" not in settings
assert "ActivityResultContracts.GetContent()" not in settings
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
assert "weekDates(startDayYmd).associateWith { date -> allGrouped[date].orEmpty() }" in ui
assert "val listState = rememberLazyListState()" in ui
assert "listState.animateScrollToItem(target)" in ui
assert "listState.scrollToItem(target)" in ui
assert "scrollRequest++" in ui
assert 'item(key = "day-$date", contentType = "day")' in ui
assert 'DayRail(date = date, isToday = date == todayStr)' in ui
assert 'dayItems.forEach { item ->' in ui
assert 'text = "Không có lịch học hoặc lịch thi."' in ui
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
assert "val weeks by viewModel.weeks.collectAsState()" in ui
assert "combine(weeks, currentWeekNumber, _visibleWeekCount, _selectedWeek)" in ui
assert "val list = weeks.value" in ui
assert "list[(currentIndex + delta).coerceIn(0, list.lastIndex)].tuanHocKy" in ui
assert "val semesterLessons by viewModel.semesterLessons.collectAsState()" in ui
assert "if (note.isNotBlank()) {" in ui
assert 'text = if (showPastDays)' not in ui
assert 'text = if (selectedWeek != null) "Tuần $selectedWeek"' not in ui
assert "fun WeekDatePicker(" in calendar
assert "internal fun monthDates(" in calendar
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

# Today and tomorrow reminders are opt-in, independently timed, and
# display portal-derived Room data (no network or permanent foreground service).
reminders = (root / "notifications/ScheduleReminders.kt").read_text(encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
settings = (root / "ui/SettingsScreen.kt").read_text(encoding="utf-8")
auth_store = (root / "data/AuthStore.kt").read_text(encoding="utf-8")
main_activity = (root / "MainActivity.kt").read_text(encoding="utf-8")
for kind in ("ACTION_TODAY", "ACTION_TOMORROW"):
    assert kind in reminders
assert "alarm.setAndAllowWhileIdle(" in reminders
assert "nextAlarmMillis(System.currentTimeMillis(), minute)" in reminders
assert "Intent.ACTION_BOOT_COMPLETED" in reminders
assert "Intent.ACTION_MY_PACKAGE_REPLACED" in reminders
assert "reminderLessonsOn(semester, target)" in reminders
assert "reminderExamsOn(semester, target)" in reminders
assert "store.hocKy.takeIf { it != 0 } ?: return" in reminders
assert "POST_NOTIFICATIONS" in manifest
assert "RECEIVE_BOOT_COMPLETED" in manifest
assert "ScheduleReminderReceiver" in manifest
assert "ReminderRescheduleReceiver" in manifest
assert "weekend = index >= 5" in calendar
assert "weekend = dayIndex >= 5" in calendar
assert "if (ymd == selectedYmd && showMonth) showMonth = false" in calendar
assert "scrollRequest++" in ui
assert "bottom = contentPadding.calculateBottomPadding() + maxHeight" in ui
assert "displayedGrouped.forEach { (date, dayItems) ->" in ui
assert "if (dayItems.isEmpty())" in ui
assert "if (date == selectedDay)" not in ui
assert (Path("app/src/test/java/com/sv/tnu/notifications/ScheduleRemindersTest.kt")).exists()
assert "TimePickerDialog(" in settings
assert "Thông báo lịch hôm nay" in settings
assert "Thông báo lịch ngày mai" in settings
assert "RequestPermission()" in settings
assert "getBoolean(KEY_REMIND_TODAY, false)" in auth_store
assert "getBoolean(KEY_REMIND_TOMORROW, false)" in auth_store
assert "ScheduleReminders.syncAll(this)" in main_activity
assert "ScheduleReminders.cancelAll(this@MainActivity)" in main_activity

# Swipe gestures navigate by one DATE even across a week boundary.
# The profile header uses the logged-in account, never a hardcoded ID/name.
profile_models = (root / "tnut/TnutModels.kt").read_text(encoding="utf-8")
auth_store = (root / "data/AuthStore.kt").read_text(encoding="utf-8")
assert "internal fun adjacentTimetableDay(" in ui
assert "detectHorizontalDragGestures(" in ui
assert "onMoveDay = { direction -> changeSelectedDay(direction) }" in ui
assert "onMoveDay(if (dx < 0f) 1 else -1)" in calendar
assert "onMoveWeek(" not in calendar
assert "changeSelectedDay(if (totalDrag < 0f) 1 else -1)" in ui
assert "val next = adjacentTimetableDay(selectedDay, direction, weeks)" in ui
assert "if (selectedWeek != next.second) viewModel.selectWeek(next.second)" in ui
assert "UserAvatar(" in ui
assert "val avatarPath by viewModel.avatarPath.collectAsState()" in ui
assert "val studentName by viewModel.studentName.collectAsState()" in ui
assert "viewModel.studentId.ifBlank" in ui
# App branding and Android install ID must match the renamed release.
build_config = Path("app/build.gradle.kts").read_text(encoding="utf-8")
strings = Path("app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
manifest_xml = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow = Path(".github/workflows/build.yml").read_text(encoding="utf-8")
assert 'applicationId = "com.tkb.sv"' in build_config
assert '<string name="app_name">TKB SV</string>' in strings
assert 'android:name="com.sv.tnu.MainActivity"' in manifest_xml
assert 'android:name="com.sv.tnu.notifications.ScheduleReminderReceiver"' in manifest_xml
assert 'android:name="com.sv.tnu.widget.TimetableWidgetProvider"' in manifest_xml
assert "TKB-SV.apk" in workflow
assert '"Sinh viên TNUT"' not in ui
assert 'if (!studentName.isNullOrBlank()) {' in ui
assert '.height(64.dp)' in ui
assert ".background(MaterialTheme.colorScheme.background)" in ui
assert "modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)" in ui
assert '"• T${lesson.tietBatDau} · ${lesson.soTiet} tiết"' in ui
assert 'text = "Học kỳ ${viewModel.hocKy}"' in ui
assert "studentName: StateFlow<String?> = repository.studentName" in ui
assert "val studentName: StateFlow<String?>" in repo
assert "authStore.studentName = name" in repo
assert '@SerialName("ho_ten") val hoTen: String? = null' in profile_models
assert "var studentName: String?" in auth_store
assert "lastProfileCheck" in auth_store
assert (Path("app/src/test/java/com/sv/tnu/ui/TimetableSwipeTest.kt")).exists()
assert (Path("app/src/test/java/com/sv/tnu/tnut/StudentProfileTest.kt")).exists()

# Note editor remains opt-in, but the extra Liquid Glass button migration
# is completely reverted. Saved notes still live in Room.
assert "if (note.isNotBlank()) {" in ui
# Earlier dates use a short, bounded list animation after an immediate nearby
# jump, avoiding a costly animation through every item in the prior days.
assert "kotlin.math.abs(target - firstVisible) > 5" in ui
assert "listState.scrollToItem(nearTarget)" in ui
assert "listState.animateScrollToItem(target)" in ui
assert "color = MaterialTheme.colorScheme.onSurface," in ui
assert "if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface" not in ui
assert "MaterialTheme.colorScheme.primary" in calendar
assert "Color(0xFF009F9C)" not in calendar
assert "Color(0xFF22A74C)" not in calendar
assert "val selectedDayColor = MaterialTheme.colorScheme.primary" in calendar

# Compact reminder cards: switch retains its own action; tapping the rest of
# the card opens the time picker. Duplicate update-check card is removed.
assert 'title = "Thông báo lịch hôm nay"' in settings
assert 'title = "Thông báo lịch ngày mai"' in settings
assert "private fun ReminderSettingCard(" in settings
assert ".clickable(onClick = onPickTime)" in settings
assert "onCheckedChange = onToggle" in settings
assert '"Giờ nhắc: %02d:%02d".format(' in settings
assert 'title = "Kiểm tra cập nhật"' not in settings
assert 'title = "Thông tin ứng dụng"' in settings
assert "appUpdateViewModel.checkForUpdate(manual = true)" in settings
assert "Giờ nhắc theo giờ điện thoại;" not in settings
# One-line titles must shrink to their actual available width instead of
# wrapping beside the note pencil or the THI badge.
assert "private fun AutoFitSubjectTitle(" in ui
assert "measurer.measure(" in ui
assert "maxWidth.roundToPx()" in ui
assert "softWrap = false" in ui
assert "maxLines = 1" in ui
assert "overflow = TextOverflow.Ellipsis" in ui
assert "title = lesson.tenMon" in ui
assert "title = exam.tenMon" in ui
assert "fontSize = if (weekend) 17.sp else 19.sp" in calendar
assert "fontSize = if (weekend) 10.sp else 11.sp" in calendar
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

# Android builds now execute native Rust schedule logic and must ship every ABI.
rust_bridge = (root / "rust/RustScheduleCore.kt").read_text(encoding="utf-8")
rust_source = Path("rust-core/src/lib.rs").read_text(encoding="utf-8")
rust_jni = Path("rust-core/src/android.rs").read_text(encoding="utf-8")
build_script = Path("scripts/build-rust-android.sh").read_text(encoding="utf-8")
assert "System.loadLibrary(\"tkb_core\")" in rust_bridge
assert "private external fun normalizeDateNative" in rust_bridge
assert "private external fun examEndTimeNative" in rust_bridge
assert "RustScheduleCore.normalizeDate(dateStr)" in ui
assert "RustScheduleCore.examEndTime(gioBatDau, soPhut)" in ui
assert "RustScheduleCore.moveCalendarDay(selectedYmd, direction)" in ui
assert "RustScheduleCore.weekDates(start)" in calendar
assert "RustScheduleCore.normalizeDate(dateStr)" in repo
assert "pub fn move_calendar_day(" in rust_source
assert "pub fn calendar_week_dates(" in rust_source
assert "Java_com_sv_tnu_rust_RustScheduleCore_weekDatesNative" in rust_jni
assert "cargo ndk -p 24" in build_script
assert "dependsOn(verifyRustNativeLibraries)" in build_config
assert "cargo test --manifest-path rust-core/Cargo.toml" in workflow
assert "bash scripts/build-rust-android.sh" in workflow
assert (Path("app/src/test/java/com/sv/tnu/rust/RustScheduleCoreTest.kt")).exists()

print("OK: timetable/notes survive sync, demo exams are excluded, past exams hide, v1-v4 migration preserves data; Rust JNI bridge and Android ABIs configured")
