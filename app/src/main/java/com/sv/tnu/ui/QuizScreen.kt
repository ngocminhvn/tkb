@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sv.tnu.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sv.tnu.ui.expressive.expressiveClickable
import com.sv.tnu.quiz.QuizDeck
import com.sv.tnu.quiz.QuizImportResult
import com.sv.tnu.quiz.QuizImporter
import com.sv.tnu.quiz.QuizQuestion
import com.sv.tnu.quiz.QuizStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark
    val scope = rememberCoroutineScope()
    val storage = remember(context) { QuizStorage(context) }
    val sharedPrefs = remember(context) { context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE) }

    var page by remember { mutableStateOf(QuizPage.LIBRARY) }
    var decks by remember { mutableStateOf<List<QuizDeck>>(emptyList()) }
    var selectedDeckId by remember { mutableStateOf<Long?>(null) }
    var imported by remember { mutableStateOf<QuizImportResult?>(null) }
    var deckName by remember { mutableStateOf("") }
    var showMissingOnly by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var markedCorrect by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<QuizDeck?>(null) }

    // 4 tùy chọn làm bài trắc nghiệm: lưu vào SharedPreferences
    var shuffleQuestions by remember {
        mutableStateOf(sharedPrefs.getBoolean("quiz_shuffle_questions", true))
    }
    fun updateShuffleQuestions(enabled: Boolean) {
        shuffleQuestions = enabled
        sharedPrefs.edit().putBoolean("quiz_shuffle_questions", enabled).apply()
    }

    var shuffleOptions by remember {
        mutableStateOf(sharedPrefs.getBoolean("quiz_shuffle_options", true))
    }
    fun updateShuffleOptions(enabled: Boolean) {
        shuffleOptions = enabled
        sharedPrefs.edit().putBoolean("quiz_shuffle_options", enabled).apply()
    }

    // Chế độ tự động chấm (hiện đáp án ngay khi chọn): lưu vào SharedPreferences
    var autoGrade by remember {
        mutableStateOf(sharedPrefs.getBoolean("auto_grade", true))
    }
    fun updateAutoGrade(enabled: Boolean) {
        autoGrade = enabled
        sharedPrefs.edit().putBoolean("auto_grade", enabled).apply()
    }

    // Chế độ tính giờ
    var isTimedModeOption by remember {
        mutableStateOf(sharedPrefs.getBoolean("quiz_timed_mode", false))
    }
    fun updateTimedMode(enabled: Boolean) {
        isTimedModeOption = enabled
        sharedPrefs.edit().putBoolean("quiz_timed_mode", enabled).apply()
    }

    var isTimedMode by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showFinishExamDialog by remember { mutableStateOf(false) }

    LaunchedEffect(page, isTimedMode) {
        if (page == QuizPage.PRACTICE && isTimedMode) {
            elapsedSeconds = 0
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    fun refreshLibrary() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { storage.listDecks() }
            decks = list
            if (selectedDeckId == null || list.none { it.id == selectedDeckId }) {
                selectedDeckId = list.firstOrNull()?.id
            }
        }
    }

    fun returnToLibrary() {
        page = QuizPage.LIBRARY
        questions = emptyList()
        imported = null
        selected = emptySet()
        markedCorrect = null
        isTimedMode = false
        refreshLibrary()
    }

    fun openPractice(
        deck: QuizDeck,
        wrongOnly: Boolean = false,
        shuffle: Boolean = false,
        shuffleOpts: Boolean = false,
        timed: Boolean = false,
    ) {
        scope.launch {
            busy = true
            error = null
            try {
                val finalQuestions = withContext(Dispatchers.IO) {
                    storage.preparePracticeSession(deck.id, wrongOnly, shuffle, shuffleOpts)
                }
                if (finalQuestions.isEmpty()) {
                    error = if (wrongOnly) "Chưa có câu trả lời sai để ôn lại." else "Bộ đề chưa có câu hỏi."
                } else {
                    questions = finalQuestions
                    currentIndex = 0
                    selected = finalQuestions[0].selected ?: emptySet()
                    markedCorrect = finalQuestions[0].wasCorrect
                    isTimedMode = timed
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
        refreshLibrary()
    }

    BackHandler(enabled = page != QuizPage.LIBRARY && !busy) { returnToLibrary() }

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

    if (showFinishExamDialog) {
        val total = questions.size
        val correctCount = questions.count { it.wasCorrect == true }
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        AlertDialog(
            onDismissRequest = {
                showFinishExamDialog = false
                returnToLibrary()
            },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32)) },
            title = { Text("Kết quả thi thử") },
            text = {
                Column {
                    Text("Thời gian làm bài: ${String.format("%02d:%02d", minutes, seconds)}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Số câu đúng: $correctCount / $total", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (total > 0) "Tỉ lệ đúng: ${(correctCount * 100) / total}%" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishExamDialog = false
                    returnToLibrary()
                }) { Text("Xác nhận") }
            },
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuizCircleBackButton(isDark = isDark, enabled = !busy) {
                    if (page == QuizPage.LIBRARY) onBack() else returnToLibrary()
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = when (page) {
                        QuizPage.LIBRARY -> "Ôn tập trắc nghiệm"
                        QuizPage.PREVIEW -> "Kiểm tra đề"
                        QuizPage.PRACTICE -> if (isTimedMode) "Thi thử tính giờ" else "Làm bài"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (page == QuizPage.PRACTICE && isTimedMode) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF262D3D) else Color(0xFFE8EEF9),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(contentPadding)) {
            if (error != null) {
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            when (page) {
                QuizPage.LIBRARY -> {
                    val activeDeck = decks.find { it.id == selectedDeckId } ?: decks.firstOrNull()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // 1. Hàng 2 thẻ: Import đề & Bộ đề đã lưu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .expressiveClickable(enabled = !busy) { filePicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1B2029) else Color(0xFFF4F6FA),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Import đề",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Chọn file từ máy",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1B2029) else Color(0xFFF4F6FA),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                ),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Bộ đề đã lưu",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Học khi offline",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Tùy chọn làm bài: 4 checkbox cân đối 2x2
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1B2029) else Color(0xFFF8F9FB),
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    QuizCheckboxItem(
                                        modifier = Modifier.weight(1f),
                                        label = "Trộn thứ tự câu hỏi",
                                        checked = shuffleQuestions,
                                        enabled = !busy,
                                        onCheckedChange = { updateShuffleQuestions(it) },
                                    )
                                    QuizCheckboxItem(
                                        modifier = Modifier.weight(1f),
                                        label = "Đảo đáp án A/B/C/D",
                                        checked = shuffleOptions,
                                        enabled = !busy,
                                        onCheckedChange = { updateShuffleOptions(it) },
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    QuizCheckboxItem(
                                        modifier = Modifier.weight(1f),
                                        label = "Hiện đáp án ngay khi chọn",
                                        checked = autoGrade,
                                        enabled = !busy,
                                        onCheckedChange = { updateAutoGrade(it) },
                                    )
                                    QuizCheckboxItem(
                                        modifier = Modifier.weight(1f),
                                        label = "Bật tính giờ làm bài",
                                        checked = isTimedModeOption,
                                        enabled = !busy,
                                        onCheckedChange = { updateTimedMode(it) },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 3. Danh sách bộ đề
                        Text(
                            "Bộ đề của tôi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (decks.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1B2029) else Color(0xFFF8F9FB),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                ),
                            ) {
                                if (busy) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(42.dp))
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Chưa có bộ đề nào",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "Nhấn nút Import đề ở trên để bắt đầu ôn tập.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                decks.forEach { deck ->
                                    val isSelected = deck.id == selectedDeckId
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .expressiveClickable(enabled = !busy) {
                                                selectedDeckId = deck.id
                                                openPractice(
                                                    deck = deck,
                                                    wrongOnly = false,
                                                    shuffle = shuffleQuestions,
                                                    shuffleOpts = shuffleOptions,
                                                    timed = isTimedModeOption,
                                                )
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                if (isDark) Color(0xFF222B3D) else Color(0xFFEAF1FF)
                                            } else {
                                                if (isDark) Color(0xFF1B2029) else Color.White
                                            },
                                        ),
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                             else if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                        ),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = deck.title,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    } else {
                                                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                                    },
                                                ) {
                                                    Text(
                                                        text = "${deck.total} câu",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                val statusText = if (deck.attempted > 0) {
                                                    "Đã làm ${deck.attempted}/${deck.total} · Đúng ${deck.correct}"
                                                } else {
                                                    "Chưa làm · Sẵn sàng ôn tập"
                                                }
                                                Text(
                                                    text = statusText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        enabled = !busy,
                                                        onClick = { deleteCandidate = deck },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DeleteOutline,
                                                            contentDescription = "Xóa bộ đề",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                }
                                if (busy) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(42.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                QuizPage.PREVIEW -> {
                    val value = imported
                    if (value != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            OutlinedTextField(
                                value = deckName,
                                onValueChange = { deckName = it },
                                singleLine = true,
                                label = { Text("Tên bộ đề") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val unanswered = value.questions.count { it.correct.isEmpty() }
                            val answered = value.questions.size - unanswered

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1B2029) else Color(0xFFF4F6FA),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (unanswered == 0) Color(0xFF2E7D32).copy(alpha = 0.4f)
                                    else if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (unanswered == 0) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (unanswered == 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Đã nhận diện ${value.questions.size} câu hỏi",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = if (unanswered == 0) {
                                                "Tất cả ${value.questions.size} câu đều có đáp án (nhận diện từ chữ đỏ)."
                                            } else {
                                                "Đã nhận ${answered} câu có đáp án đỏ · Còn ${unanswered} câu chưa có đáp án."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (unanswered == 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (unanswered > 0 || showMissingOnly) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(12.dp), pressedShape = RoundedCornerShape(16.dp)),
                                    onClick = { showMissingOnly = !showMissingOnly },
                                ) {
                                    Text(
                                        if (showMissingOnly) "Xem tất cả câu hỏi (${value.questions.size})"
                                        else "Chỉ xem $unanswered câu thiếu đáp án",
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            Button(
                                enabled = !busy && deckName.isNotBlank(),
                                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(12.dp), pressedShape = RoundedCornerShape(16.dp)),
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
                            ) {
                                if (busy) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Đang lưu...", fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Lưu ${value.questions.size} câu vào máy", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Chạm vào đáp án để chỉnh; có thể chọn nhiều đáp án đúng.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val visibleQuestions = value.questions.withIndex().filter {
                                !showMissingOnly || it.value.correct.isEmpty()
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                            ) {
                                items(visibleQuestions, key = { it.index }) { entry ->
                                    val index = entry.index
                                    val question = entry.value
                                    QuizQuestionCard(
                                        question = question,
                                        number = index + 1,
                                        chosen = question.correct,
                                        isPreviewMode = true,
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

                        fun submitAnswer(choices: Set<Int>) {
                            scope.launch {
                                try {
                                    val toSave = question.originalIndices?.let { orig ->
                                        choices.map { orig[it] }.toSet()
                                    } ?: choices
                                    withContext(Dispatchers.IO) {
                                        storage.saveAnswer(question, toSave)
                                    }
                                    val isCorrect = question.correct.isNotEmpty() && choices == question.correct
                                    markedCorrect = isCorrect
                                    questions = questions.toMutableList().also {
                                        it[currentIndex] = question.copy(
                                            selected = choices, wasCorrect = isCorrect,
                                        )
                                    }
                                    refreshLibrary()
                                } catch (e: Exception) {
                                    error = e.message ?: "Không lưu được đáp án"
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Câu " + (currentIndex + 1) + "/" + questions.size,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (question.correct.size > 1) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                            modifier = Modifier.padding(end = 8.dp),
                                        ) {
                                            Text(
                                                "Chọn nhiều đáp án",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    // Switch toggle nhanh ngay khi làm bài
                                    Text(
                                        if (autoGrade) "Tự động chấm" else "Chấm thủ công",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = autoGrade,
                                        onCheckedChange = { updateAutoGrade(it) },
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            QuizQuestionCard(
                                question = question,
                                number = currentIndex + 1,
                                chosen = selected,
                                isPreviewMode = false,
                                isAnswered = markedCorrect != null,
                                onToggle = { choice ->
                                    val nextSelected = if (question.correct.size > 1) {
                                        selected.toMutableSet().apply {
                                            if (!add(choice)) remove(choice)
                                        }
                                    } else {
                                        setOf(choice)
                                    }
                                    selected = nextSelected

                                    // Nếu chế độ tự động chấm: chấm điểm ngay lập tức
                                    if (autoGrade) {
                                        if (nextSelected.isNotEmpty()) {
                                            submitAnswer(nextSelected)
                                        } else {
                                            markedCorrect = null
                                        }
                                    } else {
                                        // Chấm thủ công: chỉ chọn, chưa chấm
                                        markedCorrect = null
                                    }
                                },
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Khi chế độ chấm thủ công: hiện nút "Kiểm tra đáp án"
                            if (!autoGrade && markedCorrect == null) {
                                Button(
                                    enabled = selected.isNotEmpty(),
                                    onClick = { submitAnswer(selected) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(12.dp), pressedShape = RoundedCornerShape(16.dp)),
                                ) {
                                    Text("Kiểm tra đáp án", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            if (markedCorrect != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (markedCorrect == true) {
                                        if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
                                    } else {
                                        if (isDark) Color(0xFF381B1B) else Color(0xFFFFEBEE)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (markedCorrect == true) Color(0xFF2E7D32).copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (markedCorrect == true) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (markedCorrect == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (markedCorrect == true) "Chính xác!" else
                                                "Chưa đúng. Đáp án: " +
                                                    question.correct.joinToString(", ") { ('A' + it).toString() },
                                            color = if (markedCorrect == true) Color(0xFF2E7D32)
                                            else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (currentIndex > 0) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(12.dp), pressedShape = RoundedCornerShape(16.dp)),
                                        onClick = {
                                            currentIndex--
                                            val prev = questions[currentIndex]
                                            selected = prev.selected ?: emptySet()
                                            markedCorrect = prev.wasCorrect
                                        },
                                    ) { Text("Câu trước") }
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(12.dp), pressedShape = RoundedCornerShape(16.dp)),
                                    onClick = {
                                        if (currentIndex == questions.lastIndex) {
                                            if (isTimedMode) {
                                                showFinishExamDialog = true
                                            } else {
                                                returnToLibrary()
                                            }
                                        } else {
                                            currentIndex++
                                            val next = questions[currentIndex]
                                            selected = next.selected ?: emptySet()
                                            markedCorrect = next.wasCorrect
                                        }
                                    },
                                ) {
                                    Text(
                                        if (currentIndex == questions.lastIndex) "Kết thúc" else "Câu tiếp theo",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(120.dp))
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun QuizCircleBackButton(
    isDark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0x18000000),
            )
            .background(
                if (isDark) Color.White.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.65f),
                CircleShape,
            )
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.20f)
                else Color.White.copy(alpha = 0.85f),
                CircleShape,
            )
            .clip(CircleShape)
            .expressiveClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Quay lại",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun QuizCheckboxItem(
    modifier: Modifier = Modifier,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .expressiveClickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuizQuestionCard(
    question: QuizQuestion,
    number: Int,
    chosen: Set<Int>,
    isPreviewMode: Boolean = false,
    isAnswered: Boolean = false,
    onToggle: (Int) -> Unit,
) {
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1B2029) else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 2.dp else 1.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Câu $number. ${question.prompt}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            question.options.forEachIndexed { index, option ->
                val isSelected = index in chosen
                val isCorrectAnswer = (isAnswered || isPreviewMode) && index in question.correct
                val isWrongSelection = isAnswered && !isPreviewMode && isSelected && index !in question.correct

                val rowBgColor = when {
                    isCorrectAnswer -> if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9)
                    isWrongSelection -> if (isDark) Color(0xFF381B1B) else Color(0xFFFFEBEE)
                    isSelected -> if (isDark) Color(0xFF222B3D) else Color(0xFFEAF1FF)
                    else -> Color.Transparent
                }

                val rowBorderColor = when {
                    isCorrectAnswer -> Color(0xFF2E7D32).copy(alpha = if (isDark) 0.6f else 0.4f)
                    isWrongSelection -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
                }

                val badgeBgColor = when {
                    isCorrectAnswer -> Color(0xFF2E7D32)
                    isWrongSelection -> MaterialTheme.colorScheme.error
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                }

                val badgeTextColor = when {
                    isCorrectAnswer || isWrongSelection || isSelected -> Color.White
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                val optionTextColor = when {
                    isCorrectAnswer -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                    isWrongSelection -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(rowBgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, rowBorderColor, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .expressiveClickable { onToggle(index) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(badgeBgColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ('A' + index).toString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeTextColor,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = option,
                        color = optionTextColor,
                        fontWeight = if (isCorrectAnswer || isWrongSelection || isSelected) FontWeight.Medium else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )

                    if (isCorrectAnswer) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Đáp án đúng",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp),
                        )
                    } else if (isWrongSelection) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Chọn sai",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
