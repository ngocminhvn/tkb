package com.sv.tnu.rust

import com.sv.tnu.quiz.QuizDeck
import com.sv.tnu.quiz.QuizImportResult
import com.sv.tnu.quiz.QuizQuestion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

object RustQuizCore {
    init {
        System.loadLibrary("tkb_core")
    }

    private external fun parseLinesNative(linesJson: String): String
    private external fun parseJsonNative(sourceJson: String): String

    private external fun importQuizNative(
        fileBytes: ByteArray,
        fileExt: String,
        defaultTitle: String,
    ): ByteArray

    private external fun listDecksNative(dbPath: String): ByteArray

    private external fun loadQuestionsNative(
        dbPath: String,
        deckId: Long,
        wrongOnly: Boolean,
    ): ByteArray

    private external fun saveDeckNative(
        dbPath: String,
        title: String,
        questionsBytes: ByteArray,
    ): Long

    private external fun saveAnswerNative(
        dbPath: String,
        questionId: Long,
        selected: IntArray,
    ): Boolean

    private external fun deleteDeckNative(dbPath: String, deckId: Long)

    fun parseLines(linesJson: String): String = parseLinesNative(linesJson)
    fun parseJson(sourceJson: String): String = parseJsonNative(sourceJson)

    fun importQuiz(
        fileBytes: ByteArray,
        fileExt: String,
        defaultTitle: String,
    ): ByteArray = importQuizNative(fileBytes, fileExt, defaultTitle)

    fun listDecks(dbPath: String): ByteArray = listDecksNative(dbPath)

    fun loadQuestions(dbPath: String, deckId: Long, wrongOnly: Boolean): ByteArray =
        loadQuestionsNative(dbPath, deckId, wrongOnly)

    fun saveDeck(dbPath: String, title: String, questionsBytes: ByteArray): Long =
        saveDeckNative(dbPath, title, questionsBytes)

    fun saveAnswer(dbPath: String, questionId: Long, selected: IntArray): Boolean =
        saveAnswerNative(dbPath, questionId, selected)

    fun deleteDeck(dbPath: String, deckId: Long) {
        deleteDeckNative(dbPath, deckId)
    }

    private class Reader(bytes: ByteArray, type: Byte) {
        private val data = DataInputStream(ByteArrayInputStream(bytes))

        init {
            val signature = ByteArray(5)
            data.readFully(signature)
            require(
                signature[0] == 'T'.code.toByte() &&
                    signature[1] == 'K'.code.toByte() &&
                    signature[2] == 'B'.code.toByte() &&
                    signature[3] == '1'.code.toByte() &&
                    signature[4] == type,
            ) { "Rust/Android binary protocol mismatch: expected TKB1${type.toInt().toChar()}" }
        }

        fun int(): Int = data.readInt()
        fun long(): Long = data.readLong()
        fun byte(): Int = data.readByte().toInt()

        fun count(max: Int = 100_000): Int = int().also {
            require(it in 0..max) { "Invalid Rust record count: $it" }
        }

        fun string(): String {
            val length = int()
            require(length in 0..8_388_608 && length <= data.available()) {
                "Invalid Rust string length: $length"
            }
            val raw = ByteArray(length)
            data.readFully(raw)
            return String(raw, StandardCharsets.UTF_8)
        }
    }

    fun decodeQuizImportResult(bytes: ByteArray, title: String): QuizImportResult {
        val reader = Reader(bytes, 'Q'.code.toByte())
        val notice = reader.string()
        val count = reader.count()
        val questions = ArrayList<QuizQuestion>(count)
        for (i in 0 until count) {
            val id = reader.long()
            val prompt = reader.string()
            val optCount = reader.count(20)
            val options = ArrayList<String>(optCount)
            for (j in 0 until optCount) {
                options.add(reader.string())
            }
            val corCount = reader.count(optCount)
            val correct = HashSet<Int>(corCount)
            for (j in 0 until corCount) {
                correct.add(reader.int())
            }
            val selCount = reader.int()
            val selected = if (selCount >= 0) {
                val sel = HashSet<Int>(selCount)
                for (j in 0 until selCount) sel.add(reader.int())
                sel
            } else {
                null
            }
            val wasCorrect = when (reader.byte()) {
                1 -> true
                0 -> false
                else -> null
            }
            questions.add(
                QuizQuestion(
                    prompt = prompt,
                    options = options,
                    correct = correct,
                    id = id,
                    selected = selected,
                    wasCorrect = wasCorrect,
                ),
            )
        }
        return QuizImportResult(title = title, questions = questions, notice = notice)
    }

    fun decodeQuestions(bytes: ByteArray): List<QuizQuestion> =
        decodeQuizImportResult(bytes, "").questions

    fun decodeDecks(bytes: ByteArray): List<QuizDeck> {
        val reader = Reader(bytes, 'D'.code.toByte())
        val count = reader.count()
        val decks = ArrayList<QuizDeck>(count)
        for (i in 0 until count) {
            val id = reader.long()
            val title = reader.string()
            val total = reader.int()
            val attempted = reader.int()
            val correct = reader.int()
            decks.add(QuizDeck(id = id, title = title, total = total, attempted = attempted, correct = correct))
        }
        return decks
    }

    fun encodeQuestions(questions: List<QuizQuestion>): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        dos.write("TKB1Q".toByteArray(StandardCharsets.US_ASCII))
        val noticeBytes = ByteArray(0)
        dos.writeInt(noticeBytes.size)
        dos.writeInt(questions.size)
        for (q in questions) {
            dos.writeLong(q.id)
            val promptBytes = q.prompt.toByteArray(StandardCharsets.UTF_8)
            dos.writeInt(promptBytes.size)
            dos.write(promptBytes)

            dos.writeInt(q.options.size)
            for (opt in q.options) {
                val optBytes = opt.toByteArray(StandardCharsets.UTF_8)
                dos.writeInt(optBytes.size)
                dos.write(optBytes)
            }

            dos.writeInt(q.correct.size)
            for (c in q.correct) {
                dos.writeInt(c)
            }

            if (q.selected != null) {
                dos.writeInt(q.selected.size)
                for (s in q.selected) dos.writeInt(s)
            } else {
                dos.writeInt(-1)
            }

            dos.writeByte(
                when (q.wasCorrect) {
                    true -> 1
                    false -> 0
                    null -> -1
                },
            )
        }
        dos.flush()
        return bos.toByteArray()
    }
}
