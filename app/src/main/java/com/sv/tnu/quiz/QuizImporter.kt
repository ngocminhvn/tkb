package com.sv.tnu.quiz

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sv.tnu.rust.RustQuizCore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class QuizImportResult(
    val title: String,
    val questions: List<QuizQuestion>,
    val notice: String,
)

private data class TextLine(val text: String, val red: Boolean = false)

/** Imports one file on an IO dispatcher. File access is via Android's picker; no storage permission. */
object QuizImporter {
    private const val MAX_BYTES = 30 * 1024 * 1024
    private const val MAX_DOC_XML = 20 * 1024 * 1024

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
        val parsed = when (ext) {
            "json" -> RustQuizCore.parseJson(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
            "docx" -> RustQuizCore.parseLines(encodeLines(readDocx(bytes)))
            "pdf" -> {
                PDFBoxResourceLoader.init(context.applicationContext)
                val plain = PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
                    require(!doc.isEncrypted) { "PDF có mật khẩu nên không thể đọc" }
                    PDFTextStripper().getText(doc)
                }
                require(plain.isNotBlank()) {
                    "PDF không có lớp văn bản. Bản scan cần OCR và chưa được hỗ trợ."
                }
                RustQuizCore.parseLines(encodeLines(plain.lines().map(::TextLine)))
            }
            else -> RustQuizCore.parseLines(
                encodeLines(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").lines().map(::TextLine)),
            )
        }
        val array = JSONArray(parsed)
        val questions = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val options = item.getJSONArray("options")
            val correct = item.getJSONArray("correct")
            QuizQuestion(
                prompt = item.getString("prompt"),
                options = (0 until options.length()).map(options::getString),
                correct = (0 until correct.length()).map(correct::getInt).toSet(),
            )
        }
        require(questions.isNotEmpty()) {
            "Không tìm thấy câu hỏi hợp lệ. Kiểm tra định dạng 'Câu 1:', 'A.', 'B.' và nội dung tệp."
        }
        val missing = questions.count { it.correct.isEmpty() }
        val note = buildString {
            append("Đã nhận ")
            append(questions.size)
            append(" câu. ")
            if (missing > 0) append("$missing câu chưa có đáp án, hãy chọn trước khi lưu. ")
            if (ext == "pdf") append("PDF chỉ đọc lớp chữ; chưa tự xác định màu đỏ.")
        }
        QuizImportResult(name.substringBeforeLast('.').ifBlank { "Bộ đề mới" }, questions, note.trim())
    }

    private fun encodeLines(lines: List<TextLine>): String = JSONArray().apply {
        lines.filter { it.text.isNotBlank() }.take(100_000).forEach { line ->
            put(JSONObject().put("text", line.text).put("red", line.red))
        }
    }.toString()

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

    private fun readDocx(bytes: ByteArray): List<TextLine> {
        var document: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    document = readBounded(zip, MAX_DOC_XML)
                    break
                }
                zip.closeEntry()
            }
        }
        val xml = document ?: error("Không tìm thấy word/document.xml trong DOCX")
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val result = mutableListOf<TextLine>()
        var paragraph = StringBuilder()
        var paragraphRed = false
        var runRed = false
        var textNode = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> {
                        paragraph = StringBuilder()
                        paragraphRed = false
                    }
                    "r" -> runRed = false
                    "color" -> {
                        val hex = (0 until parser.attributeCount)
                            .firstOrNull { parser.getAttributeName(it) == "val" }
                            ?.let { parser.getAttributeValue(it) }
                        if (isRed(hex)) runRed = true
                    }
                    "t" -> textNode = true
                    "tab" -> paragraph.append(' ')
                    "br" -> paragraph.append(' ')
                }
                XmlPullParser.TEXT -> if (textNode) {
                    val value = parser.text.orEmpty()
                    paragraph.append(value)
                    if (runRed && value.isNotBlank()) paragraphRed = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> textNode = false
                    "p" -> {
                        val value = paragraph.toString().trim()
                        if (value.isNotBlank()) result += TextLine(value, paragraphRed)
                    }
                }
            }
            parser.next()
        }
        return result
    }

    private fun isRed(value: String?): Boolean {
        val color = value?.trim()?.trimStart('#') ?: return false
        if (color.length != 6) return false
        val code = color.toIntOrNull(16) ?: return false
        val r = (code shr 16) and 255
        val g = (code shr 8) and 255
        val b = code and 255
        return r >= 130 && r.toDouble() >= g * 1.6 && r.toDouble() >= b * 1.6
    }
}
