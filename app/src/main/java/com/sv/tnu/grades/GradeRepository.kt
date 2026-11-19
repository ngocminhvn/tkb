package com.sv.tnu.grades

import android.content.Context
import com.sv.tnu.data.AuthStore
import com.sv.tnu.rust.RustGradeCore
import com.sv.tnu.schools.tnut.TnutApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class GradeRepository(
    private val context: Context,
    private val authStore: AuthStore,
    private val api: TnutApi,
) {
    companion object {
        @Volatile
        private var memoryCache: GradeTranscript? = null

        @Volatile
        private var memoryCacheUser: String? = null

        fun clearMemoryCache() {
            memoryCache = null
            memoryCacheUser = null
        }
    }

    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    fun getCurrentSemesterId(): String? = authStore.hocKy.takeIf { it != 0 }?.toString()

    private fun cacheFile(): File {
        val user = authStore.username?.takeIf { it.isNotBlank() } ?: "default"
        return File(context.filesDir, "grades_cache_${user}.json")
    }

    suspend fun loadCachedTranscript(): GradeTranscript? = withContext(Dispatchers.IO) {
        val user = authStore.username?.takeIf { it.isNotBlank() } ?: "default"
        if (memoryCacheUser == user && memoryCache != null) {
            return@withContext memoryCache
        }
        val file = cacheFile()
        if (!file.exists()) return@withContext null
        try {
            val bytes = RustGradeCore.loadCachedGrades(file.absolutePath) ?: return@withContext null
            val parsed = RustGradeCore.decodeGrades(bytes)
            memoryCache = parsed
            memoryCacheUser = user
            parsed
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchGrades(): Result<GradeTranscript> = withContext(Dispatchers.IO) {
        try {
            val studentId = authStore.username ?: ""
            val studentName = authStore.studentName ?: ""
            val emptyBody = "".toRequestBody(jsonMediaType)

            // Attempt to load symbols dictionary
            val symbolBytes = try {
                api.gradeSymbols(emptyBody).use { it.bytes() }
            } catch (_: Exception) {
                ByteArray(0)
            }

            // Fetch primary grade transcript raw bytes
            val rawBytes = api.studentGrades(hienThiMonTheoHkdk = false, body = emptyBody).use { it.bytes() }

            val now = System.currentTimeMillis()
            val user = studentId.ifBlank { "default" }

            // Parse and encode using pure Rust TKB1G wire format
            val wireBytes = RustGradeCore.mapGradesBytes(studentId, studentName, rawBytes, symbolBytes, now)
            val transcript = RustGradeCore.decodeGrades(wireBytes)
            try {
                val file = cacheFile()
                RustGradeCore.saveCachedGrades(file.absolutePath, wireBytes)
            } catch (_: Exception) {}

            memoryCache = transcript
            memoryCacheUser = user

            Result.success(transcript)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
