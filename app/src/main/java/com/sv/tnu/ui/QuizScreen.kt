package com.sv.tnu.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sv.tnu.quiz.QuizDeck
import com.sv.tnu.quiz.QuizImportResult
import com.sv.tnu.quiz.QuizImporter
import com.sv.tnu.quiz.QuizQuestion
import com.sv.tnu.quiz.QuizStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class QuizPage { LIBRARY, PREVIEW, PRACTICE }

/** Offline-only quiz section. Persistent data is wholly separate from TKB. */
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember(context) { QuizStorage(context) }
    var page by remember { mutableStateOf(QuizPage.LIBRARY) }
    var decks by remember { mutableStateOf<List<QuizDeck>>(emptyList()) }
    var imported by remember { mutableStateOf<QuizImportResult?>(null) }
    var deckName by remember { mutableStateOf("") }
    var showMissingOnly by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var markedCorrect by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<QuizDeck?>(null) }

    fun refreshLibrary() {
        scope.launch {
            decks = withContext(Dispatchers.IO) { storage.listDecks() }
        }
    }

    fun returnToLibrary() {
        page = QuizPage.LIBRARY
        questions = emptyList()
        imported = null
        selected = emptySet()
        markedCorrect = null
        refreshLibrary()
    }

    fun openPractice(deck: QuizDeck, wrongOnly: Boolean) {
        scope.launch {
            busy = true
            error = null
            try {
                val found = withContext(Dispatchers.IO) { storage.loadQuestions(deck.id, wrongOnly) }
                if (found.isEmpty()) {
                    error = if (wrongOnly) "Chưa có câu trả lời sai để ôn lại." else "Bộ đề chưa có câu hỏi."
                } else {
                    questions = found
                    currentIndex = 0
                    selected = found[0].selected ?: emptySet()
                    markedCorrect = found[0].wasCorrect
                    page = QuizPage.PRACTICE
                }
            } catch (e: Exception) {
                error = e.message ?: "Không mở được bộ đề"
            } finally {
                busy = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = null
                try {
                    val result = QuizImporter.import(context, uri)
                    imported = result
                    deckName = result.title
                    showMissingOnly = result.questions.any { it.correct.isEmpty() }
                    page = QuizPage.PREVIEW
                } catch (e: Exception) {
                    error = e.message ?: "Không thể đọc file"
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        decks = withContext(Dispatchers.IO) { storage.listDecks() }
    }
    BackHandler(enabled = page != QuizPage.LIBRARY) { returnToLibrary() }

    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Xóa bộ đề?") },
            text = { Text("Bộ đề và toàn bộ tiến độ sẽ bị xóa khỏi thiết bị.") },
            confirmButton = {
                TextButton(onClick = {
                    val deck = deleteCandidate ?: return@TextButton
                    deleteCandidate = null
                    scope.launch {
                        withContext(Dispatchers.IO) { storage.deleteDeck(deck.id) }
                        refreshLibrary()
                    }
                }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Hủy") } },
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (page == QuizPage.LIBRARY) onBack() else returnToLibrary()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                Text(
                    text = when (page) {
                        QuizPage.LIBRARY -> "Ôn tập"
                        QuizPage.PREVIEW -> "Kiểm tra đề"
                        QuizPage.PRACTICE -> "Làm bài"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(contentPadding)) {
            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(modifier = Modifier.size(30.dp)) }
            }
            if (error != null) {
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            when (page) {
                QuizPage.LIBRARY -> {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Button(
                            enabled = !busy,
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Import DOCX, PDF, TXT, JSON")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Lưu trên máy · không cần Internet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bộ đề của tôi", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (decks.isEmpty() && !busy) {
                            Text("Chưa có bộ đề. Nhấn Import để bắt đầu ôn tập.")
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(decks, key = { it.id }) { deck ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(deck.title, fontWeight = FontWeight.Bold)
                                        Text(
                                            deck.total.toString() + " câu · Đã làm " +
                                                deck.attempted + " · Đúng " + deck.correct,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Button(onClick = { openPractice(deck, wrongOnly = false) }) {
                                                Text("Làm bài")
                                            }
                                            OutlinedButton(onClick = { openPractice(deck, wrongOnly = true) }) {
                                                Text("Câu sai")
                                            }
                                            IconButton(onClick = { deleteCandidate = deck }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa bộ đề")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                QuizPage.PREVIEW -> {
                    val value = imported
                    if (value != null) {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            OutlinedTextField(
                                value = deckName,
                                onValueChange = { deckName = it },
                                singleLine = true,
                                label = { Text("Tên bộ đề") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(value.notice, style = MaterialTheme.typography.bodySmall)
                            val unanswered = value.questions.count { it.correct.isEmpty() }
                            if (unanswered > 0) {
                                Text(
                                    "Còn " + unanswered + " câu chưa có đáp án. Chọn đáp án trước khi lưu.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (unanswered > 0 || showMissingOnly) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showMissingOnly = !showMissingOnly },
                                ) {
                                    Text(if (showMissingOnly) "Xem tất cả câu hỏi" else
                                        "Chỉ xem " + unanswered + " câu thiếu đáp án")
                                }
                            }
                            Button(
                                enabled = !busy && unanswered == 0 && deckName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        error = null
                                        try {
                                            withContext(Dispatchers.IO) {
                                                storage.saveDeck(deckName, value.questions)
                                            }
                                            returnToLibrary()
                                        } catch (e: Exception) {
                                            error = e.message ?: "Không lưu được bộ đề"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text("Lưu " + value.questions.size + " câu vào máy") }
                            Text(
                                "Nhấn vào đáp án để chỉnh; có thể chọn nhiều đáp án đúng.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val visibleQuestions = value.questions.withIndex().filter {
                                !showMissingOnly || it.value.correct.isEmpty()
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(visibleQuestions, key = { it.index }) { entry ->
                                    val index = entry.index
                                    val question = entry.value
                                    QuizQuestionCard(
                                        question = question,
                                        number = index + 1,
                                        chosen = question.correct,
                                        onToggle = { option ->
                                            val updated = value.questions.toMutableList()
                                            val next = question.correct.toMutableSet().apply {
                                                if (!add(option)) remove(option)
                                            }
                                            updated[index] = question.copy(correct = next)
                                            imported = value.copy(questions = updated)
                                            if (showMissingOnly && updated.none { it.correct.isEmpty() }) {
                                                showMissingOnly = false
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                QuizPage.PRACTICE -> {
                    if (questions.isNotEmpty()) {
                        val question = questions[currentIndex]
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                "Câu " + (currentIndex + 1) + "/" + questions.size,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            QuizQuestionCard(
                                question = question,
                                number = currentIndex + 1,
                                chosen = selected,
                                onToggle = { choice ->
                                    selected = if (question.correct.size > 1) {
                                        selected.toMutableSet().apply {
                                            if (!add(choice)) remove(choice)
                                        }
                                    } else {
                                        setOf(choice)
                                    }
                                    markedCorrect = null
                                },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (markedCorrect != null) {
                                Text(
                                    if (markedCorrect == true) "Chính xác!" else
                                        "Chưa đúng. Đáp án: " +
                                            question.correct.joinToString(", ") { ('A' + it).toString() },
                                    color = if (markedCorrect == true) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Button(
                                enabled = selected.isNotEmpty() && !busy,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val correct = withContext(Dispatchers.IO) {
                                                storage.saveAnswer(question, selected)
                                            }
                                            markedCorrect = correct
                                            questions = questions.toMutableList().also {
                                                it[currentIndex] = question.copy(
                                                    selected = selected, wasCorrect = correct,
                                                )
                                            }
                                            refreshLibrary()
                                        } catch (e: Exception) {
                                            error = e.message ?: "Không lưu được đáp án"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text("Kiểm tra đáp án") }
                            if (markedCorrect != null) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (currentIndex == questions.lastIndex) {
                                            returnToLibrary()
                                        } else {
                                            currentIndex++
                                            val next = questions[currentIndex]
                                            selected = next.selected ?: emptySet()
                                            markedCorrect = next.wasCorrect
                                        }
                                    },
                                ) {
                                    Text(if (currentIndex == questions.lastIndex) "Kết thúc" else "Câu tiếp theo")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    question: QuizQuestion,
    number: Int,
    chosen: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Câu " + number + ". " + question.prompt, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            question.options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(index) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = index in chosen, onCheckedChange = { onToggle(index) })
                    Text(('A' + index).toString() + ". " + option, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
