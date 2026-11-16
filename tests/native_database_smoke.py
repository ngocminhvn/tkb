"""Compile actual Kotlin DAO contracts and adapters and run JNI.
Requires KOTLIN_HOME, JAVA_HOME (or java on PATH), Rust, and coroutines bundled
with the Kotlin compiler. This host test does not replace Android upgrade tests.
"""
import os, pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[1]
kotlin_home = pathlib.Path(os.environ['KOTLIN_HOME'])
source = root / 'app/src/main/java/com/sv/tnu'
with tempfile.TemporaryDirectory(prefix='tkb-jni-') as tmp:
    tmp = pathlib.Path(tmp)
    (tmp / 'Context.kt').write_text("""
package android.content
import java.io.File
abstract class Context {
    abstract val applicationContext: Context
    abstract fun getDatabasePath(name: String): File
}
""")
    (tmp / 'AuthStore.kt').write_text('package com.sv.tnu.data\nclass AuthStore')
    (tmp / 'Smoke.kt').write_text('''
import com.sv.tnu.rust.RustCacheStore
import com.sv.tnu.rust.RustScheduleCore
import com.sv.tnu.rust.RustPortalCore
import com.sv.tnu.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
fun main(args: Array<String>) = runBlocking {
    val file = File(args[0])
    val db = RustCacheStore.create(file)
    val week = WeekEntity(1, 1, "2026-11-16", "2026-11-22", "Tuần")
    val period = PeriodEntity(1, "07:00", "07:50")
    val lesson = LessonEntity("id",1,1,"2026-11-16",2,1,3,"M","Toán","3","A","GV","L","N")
    val portal = """{"code":200,"data":{
      "ds_tiet_trong_ngay":[{"tiet":1,"gio_bat_dau":"07:00","gio_ket_thuc":"07:50"}],
      "ds_tuan_tkb":[{"tuan_hoc_ky":1,"ngay_bat_dau":"2026-11-16","ngay_ket_thuc":"2026-11-22","thong_tin_tuan":"Tuần",
      "ds_thoi_khoa_bieu":[{"id_tkb":"id","ngay_hoc":"2026-11-16","thu_kieu_so":2,"tiet_bat_dau":1,"so_tiet":3,
      "ma_mon":"M","ten_mon":"Toán","so_tin_chi":"3","ma_phong":"A","ten_giang_vien":"GV","ten_lop":"L","ma_nhom":"N"}]}]
    }}"""
    db.commitPortalTimetable(1, "2026-11-16", portal.toByteArray())
    check(runCatching { db.commitPortalTimetable(1, "2026-11-16", "{}".toByteArray()) }.isFailure)
    check(db.cachedTimetable(1) == CachedTimetable(listOf(week), listOf(lesson)))
    check(db.periodsOnce() == listOf(period))
    check(db.weeksOnce(1) == listOf(week))
    check(db.semesterLessonsOnce(1) == listOf(lesson))
    check(db.cachedTimetable(1) == CachedTimetable(listOf(week),listOf(lesson)))
    check(db.weekCount(1) == 1)
    check(db.lessonsOn("2026-11-16") == listOf(lesson))
    check(db.reminderLessonsOn(2,"2026-11-16").isEmpty())
    check(db.lessons(1,1).first() == listOf(lesson))
    val mid = ExamEntity(hocKy=1,isGiuaKy=true,ngayThi="2026-11-16",tenMon="Thi",gioBatDau="07:00")
    check(db.commitPortalExams(1, true, listOf(mid)))
    check(!db.commitPortalExams(1, true, emptyList()))
    val timeline = RustScheduleCore.timelineBatch(
        listOf(lesson, lesson.copy(idTkb = "missing-period", tietBatDau = 4)),
        listOf(mid.copy(gioBatDau = "07:00", soPhut = 60), mid.copy(isHoanThi = true)),
        listOf(period),
    )
    check(timeline.map { it.source } == listOf(0, 1, -1))
    check(timeline.map { it.timeLabel } == listOf("07:00", "Tiết 4", "08:00"))
    check(timeline.all { it.date == "2026-11-16" })
    db.replaceExamsByType(1,false,listOf(mid.copy(isGiuaKy=false)))
    check(db.examCount(1)==2)
    check(db.examsOn("2026-11-16").size==2)
    db.replaceExamsByType(1,false,emptyList())
    check(db.examsOnce(1).single().isGiuaKy)
    try { db.replaceExamsByType(1,false,listOf(mid)); error("Expected SQLite rejection") } catch (_: IllegalStateException) {}
    check(db.examCount(1)==1)
    val examJson = """{"code":200,"data":{"total_pages":2,"ds_lich_thi":[
      {"ma_mon":"A","ten_mon":"First","ngay_thi":"2026-11-16","gio_bat_dau":"08:00","so_bao_danh":"01","ma_phong":"P"}
    ]}}"""
    val firstPage = RustPortalCore.encodeExamPage(1, true, 1, examJson.toByteArray())
    val finalPage = RustPortalCore.encodeExamPage(1, true, 2, examJson.replace("First", "Duplicate").toByteArray())
    val brokenPage = finalPage.bytes.copyOf(finalPage.bytes.size - 1)
    check(firstPage.nextPage == 2 && finalPage.nextPage == null)
    check(runCatching { db.commitPortalExamPages(1, true, listOf(firstPage.bytes, brokenPage)) }.isFailure)
    check(db.examsOnce(1).single().tenMon == "Thi")
    check(db.commitPortalExamPages(1, true, listOf(firstPage.bytes, finalPage.bytes)))
    check(db.examsOnce(1).single().tenMon == "First")
    val empty = RustPortalCore.encodeExamPage(1, true, 1, """{"code":200,"data":{}}""".toByteArray())
    check(!db.commitPortalExamPages(1, true, listOf(empty.bytes)))
    check(db.examsOnce(1).single().tenMon == "First")
    check(RustPortalCore.renewalStart("rotated", "old", null) == RustPortalCore.RenewalStart.REUSE_CURRENT)
    check(RustPortalCore.renewalStart("old", "old", "refresh") == RustPortalCore.RenewalStart.REFRESH)
    check(RustPortalCore.renewalStart("old", "old", null) == RustPortalCore.RenewalStart.LOGIN)
    check(RustPortalCore.requestKind("https", "portal.tnut.edu.vn", "/api/auth/login") == RustPortalCore.RequestKind.LOGIN)
    check(RustPortalCore.requestKind("https", "example.test", "/api/tkb") == RustPortalCore.RequestKind.OTHER_ORIGIN)
    check(RustScheduleCore.shouldRecoverEmptyTimetable(false, 2000, 1000, false))
    check(!RustScheduleCore.shouldRecoverEmptyTimetable(false, 2000, 1000, true))
    check(!RustScheduleCore.shouldRecoverEmptyTimetable(true, 2000, 1000, false))
    check(RustScheduleCore.shouldAutoRefresh(1000, 0))
    check(!RustScheduleCore.canStartExamSync(true, true, true))
    check(RustScheduleCore.moveWeek(listOf(week, week.copy(tuanHocKy = 3)), 1, 1) == 3)
    check(RustScheduleCore.syncFailureDetail("Lỗi.", "Thi lỗi", false) == "Lỗi.\\nLịch thi: Thi lỗi")
    check(RustScheduleCore.syncFailureDetail("Lỗi.", null, true) == "Lỗi. Lịch đã lưu vẫn được giữ.")
    val events = async(start=CoroutineStart.UNDISPATCHED) { withTimeout(5000) { db.lessonNotes().first { it.isNotEmpty() } } }
    val note = LessonNoteEntity("id","Tiếng Việt ệ",1234567890123)
    db.upsertLessonNote(note)
    check(events.await() == listOf(note))
    db.close()
    val reopened = RustCacheStore.open(file)
    check(reopened.lessonNotes().first() == listOf(note))
    check(reopened.cachedTimetable(1).lessons==listOf(lesson))
    reopened.deleteLessons(1)
    check(reopened.lessonNotes().first()==listOf(note))
    reopened.clearOldLessonNotes(note.updatedAt)
    check(reopened.lessonNotes().first()==listOf(note))
    reopened.deleteLessonNote("id")
    check(reopened.lessonNotes().first().isEmpty())
    reopened.deleteExams(1); reopened.deleteWeeks(1); reopened.deletePeriods()
    check(reopened.examCount(1)==0 && reopened.weekCount(1)==0)
    reopened.close()
    try { reopened.periodsOnce(); error("Expected closed error") } catch (_: IllegalStateException) {}
    println("PASS: real Kotlin/native DAO, binary batches, transactions, Flow, persistence, close")
}
''')
    cp = str(kotlin_home / 'lib/kotlinx-coroutines-core-jvm.jar')
    files = [str(p) for p in tmp.glob('*.kt')] + [
        str(source / name) for name in [
            'data/AppDatabase.kt', 'schools/SchoolProvider.kt', 'rust/RustCacheStore.kt',
            'rust/RustPortalCore.kt', 'rust/RustScheduleCore.kt', 'rust/DatabaseBinaryEncoder.kt',
        ]
    ]
    subprocess.run([str(kotlin_home / 'bin/kotlinc'), *files, '-classpath', cp, '-include-runtime', '-d', str(tmp / 'smoke.jar')], check=True)
    subprocess.run(['java', '-Djava.library.path=' + str(root / 'rust-core/target/debug'), '-cp', str(tmp / 'smoke.jar') + ':' + cp, 'SmokeKt', str(tmp / 'tkb.db')], check=True)
