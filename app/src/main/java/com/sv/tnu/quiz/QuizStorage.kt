package com.sv.tnu.quiz

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** Isolated from tkb.db: importing quizzes never migrates or clears timetable cache. */
data class QuizQuestion(
    val prompt: String,
    val options: List<String>,
    val correct: Set<Int>,
    val id: Long = 0L,
    val selected: Set<Int>? = null,
    val wasCorrect: Boolean? = null,
)

data class QuizDeck(
    val id: Long,
    val title: String,
    val total: Int,
    val attempted: Int,
    val correct: Int,
)

internal fun encodeIndices(values: Collection<Int>): String = JSONArray(values.sorted()).toString()

internal fun decodeIndices(value: String?): Set<Int> {
    if (value.isNullOrBlank()) return emptySet()
    val arr = JSONArray(value)
    return buildSet {
        for (i in 0 until arr.length()) add(arr.optInt(i, -1))
    }.filter { it >= 0 }.toSet()
}

class QuizStorage(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "quiz.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE quiz_decks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_ms INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE quiz_questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER NOT NULL REFERENCES quiz_decks(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                prompt TEXT NOT NULL,
                options_json TEXT NOT NULL,
                correct_json TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX idx_quiz_deck_order ON quiz_questions(deck_id, position)")
        db.execSQL(
            """CREATE TABLE quiz_progress (
                question_id INTEGER PRIMARY KEY REFERENCES quiz_questions(id) ON DELETE CASCADE,
                selected_json TEXT NOT NULL,
                is_correct INTEGER NOT NULL,
                updated_ms INTEGER NOT NULL
            )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Database v1 has no migrations yet. Never drop users' quiz data.
        error("Unsupported quiz database version: $oldVersion -> $newVersion")
    }

    fun saveDeck(title: String, questions: List<QuizQuestion>): Long {
        require(questions.isNotEmpty())
        val db = writableDatabase
        db.beginTransaction()
        try {
            val name = title.trim().ifBlank { "Bộ đề mới" }
            val id = db.compileStatement(
                "INSERT INTO quiz_decks(title, created_ms) VALUES (?, ?)",
            ).use { stmt ->
                stmt.bindString(1, name)
                stmt.bindLong(2, System.currentTimeMillis())
                stmt.executeInsert()
            }
            db.compileStatement(
                """INSERT INTO quiz_questions(deck_id, position, prompt, options_json, correct_json)
                    VALUES (?, ?, ?, ?, ?)""",
            ).use { stmt ->
                questions.forEachIndexed { index, q ->
                    stmt.clearBindings()
                    stmt.bindLong(1, id)
                    stmt.bindLong(2, index.toLong())
                    stmt.bindString(3, q.prompt)
                    stmt.bindString(4, JSONArray(q.options).toString())
                    stmt.bindString(5, encodeIndices(q.correct))
                    stmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun listDecks(): List<QuizDeck> {
        val rows = mutableListOf<QuizDeck>()
        readableDatabase.rawQuery(
            """SELECT d.id, d.title, COUNT(q.id),
                COUNT(p.question_id), COALESCE(SUM(p.is_correct), 0)
                FROM quiz_decks d
                LEFT JOIN quiz_questions q ON q.deck_id=d.id
                LEFT JOIN quiz_progress p ON p.question_id=q.id
                GROUP BY d.id ORDER BY d.created_ms DESC, d.id DESC""",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                rows += QuizDeck(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4))
            }
        }
        return rows
    }

    fun loadQuestions(deckId: Long, wrongOnly: Boolean = false): List<QuizQuestion> {
        val rows = mutableListOf<QuizQuestion>()
        val wrongFilter = if (wrongOnly) "AND p.is_correct=0" else ""
        readableDatabase.rawQuery(
            """SELECT q.id, q.prompt, q.options_json, q.correct_json,
                p.selected_json, p.is_correct
                FROM quiz_questions q
                LEFT JOIN quiz_progress p ON p.question_id=q.id
                WHERE q.deck_id=? """ + wrongFilter + " ORDER BY q.position ASC",
            arrayOf(deckId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val optionsArray = JSONArray(c.getString(2))
                val options = (0 until optionsArray.length()).map { optionsArray.getString(it) }
                rows += QuizQuestion(
                    prompt = c.getString(1),
                    options = options,
                    correct = decodeIndices(c.getString(3)),
                    id = c.getLong(0),
                    selected = if (c.isNull(4)) null else decodeIndices(c.getString(4)),
                    wasCorrect = if (c.isNull(5)) null else c.getInt(5) == 1,
                )
            }
        }
        return rows
    }

    fun saveAnswer(question: QuizQuestion, selected: Set<Int>): Boolean {
        val isCorrect = question.correct.isNotEmpty() && question.correct == selected
        writableDatabase.compileStatement(
            """INSERT OR REPLACE INTO quiz_progress
                (question_id, selected_json, is_correct, updated_ms) VALUES (?, ?, ?, ?)""",
        ).use { stmt ->
            stmt.bindLong(1, question.id)
            stmt.bindString(2, encodeIndices(selected))
            stmt.bindLong(3, if (isCorrect) 1 else 0)
            stmt.bindLong(4, System.currentTimeMillis())
            stmt.executeInsert()
        }
        return isCorrect
    }

    fun deleteDeck(deckId: Long) {
        writableDatabase.delete("quiz_decks", "id=?", arrayOf(deckId.toString()))
    }
}
