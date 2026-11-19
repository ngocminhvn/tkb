package com.sv.tnu.quiz

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sv.tnu.rust.RustQuizCore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuizImportResult(
    val title: String,
    val questions: List<QuizQuestion>,
    val notice: String,
)

/** Imports one file on an IO dispatcher. File access is via Android's picker; no storage permission. */
object QuizImporter {
    private const val MAX_BYTES = 30 * 1024 * 1024

    suspend fun import(context: Context, uri: Uri): QuizImportResult = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Bộ đề"
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(ext in setOf("txt", "json", "docx", "pdf")) {
            "Chỉ hỗ trợ DOCX, PDF văn bản, TXT và JSON"
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it, MAX_BYTES) }
            ?: error("Không thể mở tệp")

        val defaultTitle = name.substringBeforeLast('.').ifBlank { "Bộ đề mới" }
        val wireBytes = RustQuizCore.importQuiz(bytes, ext, defaultTitle)
        RustQuizCore.decodeQuizImportResult(wireBytes, defaultTitle)
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            count += size
            require(count <= limit) { "Tệp quá lớn (giới hạn 30 MB)" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
}
