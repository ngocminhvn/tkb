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
    decoder = (source / 'rust/RustPortalCore.kt').read_text()
    decoder = decoder[decoder.index('object PortalBinaryDecoder'):decoder.index('    fun decodeLogin')]
    (tmp / 'Decoder.kt').write_text('package com.sv.tnu.rust\nimport com.sv.tnu.data.*\nimport java.io.*\nimport java.nio.charset.StandardCharsets\n' + decoder + '}')
    (tmp / 'Smoke.kt').write_text('''
import com.sv.tnu.rust.RustCacheStore
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
    db.replaceTimetable(1,listOf(period),listOf(week),listOf(lesson))
    check(db.periodsOnce() == listOf(period))
    check(db.weeksOnce(1) == listOf(week))
    check(db.semesterLessonsOnce(1) == listOf(lesson))
    check(db.cachedTimetable(1) == CachedTimetable(listOf(week),listOf(lesson)))
    check(db.weekCount(1) == 1)
    check(db.lessonsOn("2026-11-16") == listOf(lesson))
    check(db.reminderLessonsOn(2,"2026-11-16").isEmpty())
    check(db.lessons(1,1).first() == listOf(lesson))
    val mid = ExamEntity(hocKy=1,isGiuaKy=true,ngayThi="2026-11-16",tenMon="Thi",gioBatDau="07:00")
    db.replaceExamsByType(1,true,listOf(mid))
    db.replaceExamsByType(1,false,listOf(mid.copy(isGiuaKy=false)))
    check(db.examCount(1)==2)
    check(db.examsOn("2026-11-16").size==2)
    db.replaceExamsByType(1,false,emptyList())
    check(db.examsOnce(1).single().isGiuaKy)
    try { db.replaceExamsByType(1,false,listOf(mid)); error("Expected SQLite rejection") } catch (_: IllegalStateException) {}
    check(db.examCount(1)==1)
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
    files = [str(p) for p in tmp.glob('*.kt')] + [str(source / 'data/AppDatabase.kt')] + [str(source / 'rust' / name) for name in ['RustCacheStore.kt','DatabaseBinaryEncoder.kt']]
    subprocess.run([str(kotlin_home / 'bin/kotlinc'), *files, '-classpath', cp, '-include-runtime', '-d', str(tmp / 'smoke.jar')], check=True)
    subprocess.run(['java', '-Djava.library.path=' + str(root / 'rust-core/target/debug'), '-cp', str(tmp / 'smoke.jar') + ':' + cp, 'SmokeKt', str(tmp / 'tkb.db')], check=True)
