package com.sv.tnu.rust

import com.sv.tnu.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Process-owned native DAO; SQLite opens lazily on IO. Never open Room against
 * the same path concurrently. All writers and observers share this instance.
 */
class RustCacheStore private constructor(private var handle: Long, private val file: File? = null) : TkbDao {
    private var closed = false
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    private val noteRevision = MutableStateFlow(0L)

    override suspend fun cachedTimetable(semester: Int): CachedTimetable = read {
        val rows = PortalBinaryDecoder.decodeTimetable(cacheNative(handle, semester))
        CachedTimetable(rows.weeks, rows.lessons)
    }

    fun cachedTimetableFlow(semester: Int): Flow<CachedTimetable> = observe { cachedTimetable(semester) }

    override fun lessonNotes(): Flow<List<LessonNoteEntity>> = noteRevision.map {
        read { PortalBinaryDecoder.decodeDatabaseNotes(notesNative(handle)) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun upsertLessonNote(note: LessonNoteEntity) = edit(0, note.lessonId, note.note, note.updatedAt)
    override suspend fun deleteLessonNote(id: String) = edit(1, id)
    override suspend fun clearOldLessonNotes(cutoff: Long) = edit(2, timestamp = cutoff)
    override suspend fun clearAllLessonNotes() = edit(3)

    private suspend fun edit(operation: Int, id: String = "", text: String = "", timestamp: Long = 0) = read {
        editNoteNative(handle, operation, id, text, timestamp)
        // A thrown SQLite/JNI error never publishes a successful commit.
        noteRevision.value += 1
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) {
                closeNative(handle)
                handle = 0
            }
            closed = true
        }
    }

    private suspend fun <T> read(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Database is closed" }
            if (handle == 0L) {
                val path = checkNotNull(file)
                val parent = checkNotNull(path.absoluteFile.parentFile)
                check(parent.isDirectory || parent.mkdirs()) { "Cannot create database directory" }
                handle = openNative(path.absolutePath).also { check(it > 0) }
            }
            block()
        }
    }

    private fun <T> observe(load: suspend () -> T): Flow<T> = revision.map { load() }.distinctUntilChanged().flowOn(Dispatchers.IO)
    private suspend fun timetableRows(op: Int, semester: Int = 0, week: Int = 0, date: String = "") = read {
        PortalBinaryDecoder.decodeTimetable(queryNative(handle, op, semester, week, date))
    }
    private suspend fun examRows(op: Int, semester: Int = 0, date: String = "") = read {
        PortalBinaryDecoder.decodeExams(queryNative(handle, op, semester, 0, date)).rows
    }
    private suspend fun mutate(op: Int, semester: Int = 0, midterm: Boolean = false, payload: ByteArray = byteArrayOf()): Long = read {
        val result = executeNative(handle, op, semester, midterm, payload)
        revision.value += 1
        result
    }
    override suspend fun periodsOnce(): List<PeriodEntity> = timetableRows(1).periods
    override suspend fun weeksOnce(hocKy: Int): List<WeekEntity> = timetableRows(2, hocKy).weeks
    override suspend fun semesterLessonsOnce(hocKy: Int): List<LessonEntity> = timetableRows(3, hocKy).lessons
    override suspend fun lessonsOn(date: String): List<LessonEntity> = timetableRows(5, date = date).lessons
    override suspend fun examsOn(date: String): List<ExamEntity> = examRows(6, date = date)
    override suspend fun reminderLessonsOn(hocKy: Int, date: String): List<LessonEntity> = timetableRows(7, hocKy, date = date).lessons
    override suspend fun reminderExamsOn(hocKy: Int, date: String): List<ExamEntity> = examRows(8, hocKy, date)
    override suspend fun examsOnce(hocKy: Int): List<ExamEntity> = examRows(9, hocKy)
    override fun periods(): Flow<List<PeriodEntity>> = observe { periodsOnce() }
    override fun weeks(hocKy: Int): Flow<List<WeekEntity>> = observe { weeksOnce(hocKy) }
    override fun semesterLessons(hocKy: Int): Flow<List<LessonEntity>> = observe { semesterLessonsOnce(hocKy) }
    override fun lessons(hocKy: Int, tuan: Int): Flow<List<LessonEntity>> = observe { timetableRows(4, hocKy, tuan).lessons }
    override fun exams(hocKy: Int): Flow<List<ExamEntity>> = observe { examsOnce(hocKy) }
    override suspend fun weekCount(hocKy: Int): Int = read {
        val bytes = queryNative(handle, 11, hocKy, 0, "")
        require(bytes.size == 9 && bytes.copyOfRange(0, 5).contentEquals("TKB1C".toByteArray(Charsets.US_ASCII)))
        java.nio.ByteBuffer.wrap(bytes, 5, 4).int
    }
    override suspend fun examCount(hocKy: Int): Int = read {
        val bytes = queryNative(handle, 12, hocKy, 0, "")
        require(bytes.size == 9 && bytes.copyOfRange(0, 5).contentEquals("TKB1C".toByteArray(Charsets.US_ASCII)))
        java.nio.ByteBuffer.wrap(bytes, 5, 4).int
    }
    override suspend fun insertPeriods(list: List<PeriodEntity>) { mutate(1, payload = DatabaseBinaryEncoder.timetable(periods = list)) }
    override suspend fun insertWeeks(list: List<WeekEntity>) { mutate(2, payload = DatabaseBinaryEncoder.timetable(weeks = list)) }
    override suspend fun insertLessons(list: List<LessonEntity>) { mutate(3, payload = DatabaseBinaryEncoder.timetable(lessons = list)) }
    override suspend fun insertExams(list: List<ExamEntity>) { mutate(4, payload = DatabaseBinaryEncoder.exams(list)) }
    override suspend fun deletePeriods() { mutate(8) }
    override suspend fun deleteWeeks(hocKy: Int) { mutate(9, hocKy) }
    override suspend fun deleteLessons(hocKy: Int) { mutate(10, hocKy) }
    override suspend fun deleteExams(hocKy: Int) { mutate(11, hocKy) }
    override suspend fun deleteExamsByType(hocKy: Int, isGiuaKy: Boolean) { mutate(12, hocKy, isGiuaKy) }
    override suspend fun replaceTimetable(hocKy: Int, periods: List<PeriodEntity>, weeks: List<WeekEntity>, lessons: List<LessonEntity>) {
        mutate(5, hocKy, payload = DatabaseBinaryEncoder.timetable(periods, weeks, lessons))
    }
    override suspend fun replaceExams(hocKy: Int, exams: List<ExamEntity>) { mutate(6, hocKy, payload = DatabaseBinaryEncoder.exams(exams)) }
    override suspend fun replaceExamsByType(hocKy: Int, isGiuaKy: Boolean, exams: List<ExamEntity>) { mutate(7, hocKy, isGiuaKy, DatabaseBinaryEncoder.exams(exams)) }
    override suspend fun removeLegacySampleExamsIfExact(hocKy: Int): Boolean = mutate(13, hocKy) != 0L
    override suspend fun commitPortalTimetable(hocKy: Int, fromDate: String, payload: ByteArray) = read {
        commitPortalTimetableNative(handle, hocKy, fromDate, payload)
        revision.value += 1
    }

    override suspend fun commitPortalExams(hocKy: Int, isGiuaKy: Boolean, exams: List<ExamEntity>): Boolean = read {
        val committed = executeNative(handle, 14, hocKy, isGiuaKy, DatabaseBinaryEncoder.exams(exams)) != 0L
        if (committed) revision.value += 1
        committed
    }

    private external fun commitPortalTimetableNative(handle: Long, semester: Int, fromDate: String, payload: ByteArray)

    private external fun queryNative(handle: Long, operation: Int, semester: Int, week: Int, date: String): ByteArray
    private external fun executeNative(handle: Long, operation: Int, semester: Int, midterm: Boolean, payload: ByteArray): Long

    private external fun openNative(path: String): Long
    private external fun closeNative(handle: Long)
    private external fun cacheNative(handle: Long, semester: Int): ByteArray
    private external fun notesNative(handle: Long): ByteArray
    private external fun editNoteNative(handle: Long, operation: Int, id: String, text: String, timestamp: Long)

    companion object {
        init { System.loadLibrary("tkb_core") }
        /** Creates the process-owned DAO without disk IO on the caller thread. */
        fun create(file: File): RustCacheStore = RustCacheStore(0, file)
        /** Caller must close Room first; file may be Context.getDatabasePath("tkb.db"). */
        suspend fun open(file: File): RustCacheStore {
            val caller = currentCoroutineContext()
            return withContext(NonCancellable) {
                val store = withContext(Dispatchers.IO) {
                    RustCacheStore(0).also { store ->
                        store.handle = store.openNative(file.absolutePath).also { check(it > 0) }
                    }
                }
                try {
                    caller.ensureActive()
                    store
                } catch (failure: Throwable) {
                    store.close()
                    throw failure
                }
            }
        }
    }
}
