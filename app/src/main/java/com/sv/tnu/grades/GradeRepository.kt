package com.sv.tnu.grades

import android.content.Context
import com.sv.tnu.data.AuthStore
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
            val content = file.readText(Charsets.UTF_8)
            val parsed = GradeParser.fromJson(content)
            if (parsed != null) {
                memoryCache = parsed
                memoryCacheUser = user
            }
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
            val symbols = try {
                val symbolBytes = api.gradeSymbols(emptyBody).use { it.bytes() }
                GradeParser.parseSymbols(String(symbolBytes, Charsets.UTF_8))
            } catch (_: Exception) {
                emptyMap()
            }

            // Fetch primary grade transcript
            val rawBytes = api.studentGrades(hienThiMonTheoHkdk = false, body = emptyBody).use { it.bytes() }
            val rawJson = String(rawBytes, Charsets.UTF_8)

            val transcript = GradeParser.parseTranscript(
                jsonString = rawJson,
                studentId = studentId,
                studentName = studentName,
                symbols = symbols,
            )

            // Cache successfully fetched transcript
            val user = studentId.ifBlank { "default" }
            memoryCache = transcript
            memoryCacheUser = user
            try {
                val file = cacheFile()
                file.writeText(GradeParser.toJson(transcript), Charsets.UTF_8)
            } catch (_: Exception) {}

            Result.success(transcript)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
