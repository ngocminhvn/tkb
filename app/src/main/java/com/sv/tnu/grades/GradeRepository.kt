package com.sv.tnu.grades

import android.content.Context
import com.sv.tnu.data.AuthStore
import com.sv.tnu.rust.PortalBinaryDecoder
import com.sv.tnu.rust.RustPortalCore
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
                PortalBinaryDecoder.decodeGrades(bytes)
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

            // Parse and encode using pure Rust TKB1G wire format
            val now = System.currentTimeMillis()
            val wireBytes = RustPortalCore.mapGradesBytes(studentId, studentName, rawBytes, symbolBytes, now)
            val transcript = PortalBinaryDecoder.decodeGrades(wireBytes)

            // Cache binary wire bytes to disk (ultra fast, fraction of disk size)
            val user = studentId.ifBlank { "default" }
            memoryCache = transcript
            memoryCacheUser = user
            try {
                val file = cacheFile()
                file.writeBytes(wireBytes)
            } catch (_: Exception) {}

            Result.success(transcript)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
