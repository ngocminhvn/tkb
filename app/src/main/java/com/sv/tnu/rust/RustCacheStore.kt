package com.sv.tnu.rust

import com.sv.tnu.data.CachedTimetable
import com.sv.tnu.data.LessonNoteEntity
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

/** Staged adapter: never open the same path while Room owns it.
 * Not selected by AppDatabase.get until installed-APK validation is complete.
 * One instance owns all writes/observers for its database. No polling.
 */
class RustCacheStore private constructor(private var handle: Long) {
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)

    suspend fun cachedTimetable(semester: Int): CachedTimetable = read {
        val rows = PortalBinaryDecoder.decodeTimetable(cacheNative(handle, semester))
        CachedTimetable(rows.weeks, rows.lessons)
    }

    fun lessonNotes(): Flow<List<LessonNoteEntity>> = revision.map {
        read { PortalBinaryDecoder.decodeDatabaseNotes(notesNative(handle)) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun upsertLessonNote(note: LessonNoteEntity) = edit(0, note.lessonId, note.note, note.updatedAt)
    suspend fun deleteLessonNote(id: String) = edit(1, id)
    suspend fun clearOldLessonNotes(cutoff: Long) = edit(2, timestamp = cutoff)
    suspend fun clearAllLessonNotes() = edit(3)

    private suspend fun edit(operation: Int, id: String = "", text: String = "", timestamp: Long = 0) = read {
        editNoteNative(handle, operation, id, text, timestamp)
        // A thrown SQLite/JNI error never publishes a successful commit.
        revision.value += 1
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (handle != 0L) {
                closeNative(handle)
                handle = 0
            }
        }
    }

    private suspend fun <T> read(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(handle != 0L) { "Database is closed" }
            block()
        }
    }

    private external fun openNative(path: String): Long
    private external fun closeNative(handle: Long)
    private external fun cacheNative(handle: Long, semester: Int): ByteArray
    private external fun notesNative(handle: Long): ByteArray
    private external fun editNoteNative(handle: Long, operation: Int, id: String, text: String, timestamp: Long)

    companion object {
        init { System.loadLibrary("tkb_core") }
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
