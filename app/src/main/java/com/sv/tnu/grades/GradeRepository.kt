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
            val bytes = file.readBytes()
            val parsed = if (bytes.size >= 5 &&
                bytes[0] == 'T'.code.toByte() &&
                bytes[1] == 'K'.code.toByte() &&
                bytes[2] == 'B'.code.toByte() &&
                bytes[3] == '1'.code.toByte() &&
                bytes[4] == 'G'.code.toByte()
            ) {
                RustGradeCore.decodeGrades(bytes)
            } else {
                val content = String(bytes, Charsets.UTF_8)
                GradeParser.fromJson(content)
            }
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
            val symbolBytes = try {
                api.gradeSymbols(emptyBody).use { it.bytes() }
            } catch (_: Exception) {
                ByteArray(0)
            }

            // Fetch primary grade transcript raw bytes
            val rawBytes = api.studentGrades(hienThiMonTheoHkdk = false, body = emptyBody).use { it.bytes() }

            val now = System.currentTimeMillis()
            val user = studentId.ifBlank { "default" }

            // Parse and encode using pure Rust TKB1G wire format with resilient fallback
            val transcript = try {
                val wireBytes = RustGradeCore.mapGradesBytes(studentId, studentName, rawBytes, symbolBytes, now)
                val parsed = RustGradeCore.decodeGrades(wireBytes)
                try {
                    val file = cacheFile()
                    file.writeBytes(wireBytes)
                } catch (_: Exception) {}
                parsed
            } catch (_: Throwable) {
                val rawJson = String(rawBytes, Charsets.UTF_8)
                val symJson = if (symbolBytes.isNotEmpty()) String(symbolBytes, Charsets.UTF_8) else ""
                val symbols = if (symJson.isNotBlank()) GradeParser.parseSymbols(symJson) else emptyMap()
                val parsed = GradeParser.parseTranscript(rawJson, studentId, studentName, symbols)
                try {
                    val file = cacheFile()
                    file.writeText(GradeParser.toJson(parsed), Charsets.UTF_8)
                } catch (_: Exception) {}
                parsed
            }

            memoryCache = transcript
            memoryCacheUser = user

            Result.success(transcript)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
