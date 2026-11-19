package com.sv.tnu.quiz

import android.content.Context
import com.sv.tnu.rust.RustQuizCore

/** Isolated from tkb.db: importing quizzes never migrates or clears timetable cache. */
data class QuizQuestion(
    val prompt: String,
    val options: List<String>,
    val correct: Set<Int>,
    val id: Long = 0L,
    val selected: Set<Int>? = null,
    val wasCorrect: Boolean? = null,
    val originalIndices: List<Int>? = null,
)

data class QuizDeck(
    val id: Long,
    val title: String,
    val total: Int,
    val attempted: Int,
    val correct: Int,
)

class QuizStorage(private val context: Context) {
    private val dbPath: String
        get() {
            val file = context.applicationContext.getDatabasePath("quiz.db")
            file.parentFile?.mkdirs()
            return file.absolutePath
        }

    fun saveDeck(title: String, questions: List<QuizQuestion>): Long {
        require(questions.isNotEmpty()) { "Danh sách câu hỏi không được rỗng" }
        val wire = RustQuizCore.encodeQuestions(questions)
        return RustQuizCore.saveDeck(dbPath, title, wire)
    }

    fun listDecks(): List<QuizDeck> {
        val wire = RustQuizCore.listDecks(dbPath)
        return RustQuizCore.decodeDecks(wire)
    }

    fun loadQuestions(deckId: Long, wrongOnly: Boolean = false): List<QuizQuestion> {
        val wire = RustQuizCore.loadQuestions(dbPath, deckId, wrongOnly)
        return RustQuizCore.decodeQuestions(wire)
    }

    fun preparePracticeSession(
        deckId: Long,
        wrongOnly: Boolean = false,
        shuffleQuestions: Boolean = false,
        shuffleOptions: Boolean = false,
    ): List<QuizQuestion> {
        return RustQuizCore.preparePracticeSession(dbPath, deckId, wrongOnly, shuffleQuestions, shuffleOptions)
    }

    fun saveAnswer(question: QuizQuestion, selected: Set<Int>): Boolean {
        return RustQuizCore.saveAnswer(dbPath, question.id, selected.toIntArray())
    }

    fun deleteDeck(deckId: Long) {
        RustQuizCore.deleteDeck(dbPath, deckId)
    }
}
