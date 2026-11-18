package com.sv.tnu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sv.tnu.data.AuthStore
import com.sv.tnu.grades.GradeRepository
import com.sv.tnu.grades.GradeViewModel
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.schools.tnut.TnutApiClient
import com.sv.tnu.ui.glass.GlassTabBar
import com.sv.tnu.update.AppUpdateViewModel
import com.sv.tnu.vmFactory
import kotlinx.coroutines.delay

enum class NavigationTab(val title: String, val icon: ImageVector) {
    TIMETABLE("TKB", Icons.Default.DateRange),
    MORE("Thêm", Icons.Default.GridView),
    SETTINGS("Cài đặt", Icons.Default.Settings),
}

@Composable
fun MainScreen(
    authStore: AuthStore,
    repository: SchoolRepository,
    timetableViewModel: TimetableViewModel,
    appUpdateViewModel: AppUpdateViewModel = viewModel(),
    onLogout: () -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var showQuiz by rememberSaveable { mutableStateOf(false) }
    var showGrades by rememberSaveable { mutableStateOf(false) }
    val firstLoginRenderEpoch by timetableViewModel.firstLoginRenderEpoch.collectAsState()
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark

    val syncStatus by timetableViewModel.syncStatus.collectAsState()
    val showUpdateDialog by appUpdateViewModel.showDialog.collectAsState()
    val updateInfo by appUpdateViewModel.updateInfo.collectAsState()
    val downloading by appUpdateViewModel.downloading.collectAsState()
    val downloadedBytes by appUpdateViewModel.downloadedBytes.collectAsState()
    val totalBytes by appUpdateViewModel.totalBytes.collectAsState()
    val downloadResult by appUpdateViewModel.downloadResult.collectAsState()
    val updateError by appUpdateViewModel.errorMessage.collectAsState()

    // The first login loads immediately; recurring automatic reloads are
    // eligible only after 06:30 in Vietnam, at most once per Vietnam day.
    // Sleep until the next eligible window rather than polling for changes.
    LaunchedEffect(timetableViewModel) {
        while (true) {
            timetableViewModel.refreshIfNeeded()
            delay(timetableViewModel.nextAutoSyncDelayMillis().coerceAtLeast(1_000L))
        }
    }
    // If the device slept through 06:30, check again upon returning to app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, timetableViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                timetableViewModel.refreshIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tự động kiểm tra bản cập nhật mới ngầm khi mở màn hình chính
    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdate(manual = false)
    }

    // Khi đang ở các trang phụ (Thêm, Cài đặt), bấm nút back của máy thì quay lại TKB
    BackHandler(enabled = selectedIndex != 0 && !showQuiz && !showGrades) {
        selectedIndex = 0
    }
    BackHandler(enabled = selectedIndex == NavigationTab.MORE.ordinal && (showQuiz || showGrades)) {
        showQuiz = false
        showGrades = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tab content
        when (NavigationTab.entries[selectedIndex]) {
            NavigationTab.TIMETABLE -> {
                // Equivalent to leaving and reopening this tab once after the
                // first successful sync; preserves the ViewModel and Room data.
                // The epoch does not change on subsequent cached app starts.
                key(firstLoginRenderEpoch) {
                    TimetableScreen(
                        viewModel = timetableViewModel,
                        contentPadding = PaddingValues(bottom = 96.dp),
                    )
                }
            }
            NavigationTab.MORE -> {
                if (showGrades) {
                    val context = LocalContext.current
                    val gradesVm: GradeViewModel = viewModel(
                        factory = vmFactory {
                            val api = TnutApiClient.create(authStore)
                            val gradeRepo = GradeRepository(context, authStore, api)
                            GradeViewModel(gradeRepo)
                        }
                    )
                    GradesScreen(
                        viewModel = gradesVm,
                        onBack = { showGrades = false },
                        contentPadding = PaddingValues(bottom = 96.dp),
                    )
                } else if (showQuiz) {
                    QuizScreen(
                        onBack = { showQuiz = false },
                        contentPadding = PaddingValues(bottom = 96.dp),
                    )
                } else {
                    MoreScreen(
                        studentName = authStore.studentName,
                        studentId = authStore.username,
                        onBack = { selectedIndex = 0 },
                        onOpenGrades = { showGrades = true },
                        onOpenQuiz = { showQuiz = true },
                        contentPadding = PaddingValues(bottom = 96.dp),
                    )
                }
            }
            NavigationTab.SETTINGS -> {
                SettingsScreen(
                    authStore = authStore,
                    repository = repository,
                    timetableViewModel = timetableViewModel,
                    appUpdateViewModel = appUpdateViewModel,
                    onLogout = onLogout,
                    onBack = { selectedIndex = 0 },
                    contentPadding = PaddingValues(bottom = 96.dp),
                )
            }
        }

        // Floating Liquid Glass Navigation Bar
        val selectedColor = MaterialTheme.colorScheme.primary
        val unselectedColor = if (isDark) Color(0xFF9EA7B8) else Color(0xFF5E6573)

        GlassTabBar(
            itemCount = NavigationTab.entries.size,
            selectedIndex = selectedIndex,
            onSelected = { tab ->
                selectedIndex = tab
                if (tab != NavigationTab.MORE.ordinal) {
                    showQuiz = false
                    showGrades = false
                }
                // Works even when TKB is already the selected tab.
                if (tab == NavigationTab.TIMETABLE.ordinal) {
                    timetableViewModel.requestReturnToToday()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            height = 64.dp,
        ) { index, isSelected ->
            val tab = NavigationTab.entries[index]
            val tint = if (isSelected) selectedColor else unselectedColor

            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.title,
                    tint = tint,
                    modifier = Modifier.size(23.dp),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            }
        }

        // Show sync results over any optional app-update prompt, on every tab.
        if (syncStatus != null) {
            TimetableSyncDialog(
                status = syncStatus!!,
                onDismiss = { timetableViewModel.dismissSyncStatus() },
            )
        } else if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                updateInfo = updateInfo,
                currentVersionName = appUpdateViewModel.currentVersionName,
                downloading = downloading,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                downloadResult = downloadResult,
                error = updateError,
                onDismiss = { appUpdateViewModel.dismissDialog() },
                onStartDownload = { info -> appUpdateViewModel.startDownload(info) },
            )
        }
    }
}

/**
 * Trang "Thêm" nằm giữa TKB và Cài đặt, cung cấp các tiện ích học tập (Xem điểm, Ôn tập).
 */
@Composable
private fun MoreScreen(
    studentName: String? = null,
    studentId: String? = null,
    onBack: () -> Unit = {},
    onOpenGrades: () -> Unit,
    onOpenQuiz: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark
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
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Thêm",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Student greeting snapshot
            if (!studentName.isNullOrBlank() || !studentId.isNullOrBlank()) {
                val greeting = if (!studentName.isNullOrBlank()) studentName else "Sinh viên TNUT"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E242E) else Color(0xFFF8FAFC),
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = greeting.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            if (!studentId.isNullOrBlank()) {
                                Text(
                                    text = "MSSV: $studentId • Đại học Kỹ thuật Công nghiệp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "HỌC TẬP & ĐÀO TẠO",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            // HÀNG ĐẦU TIÊN: Xem điểm (Grades)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenGrades),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B2029) else Color.White,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 3.dp else 2.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF4F46E5),
                                        Color(0xFF7C3AED),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Xem điểm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF6366F1).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Mới",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                    ),
                                    color = Color(0xFF818CF8),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Tra cứu điểm học kỳ, điểm tổng kết và tích lũy GPA",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // HÀNG THỨ HAI: Ôn tập (Quiz)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenQuiz),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B2029) else Color.White,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 3.dp else 2.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0D9488),
                                        Color(0xFF10B981),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Ôn tập",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Import bộ đề và luyện trắc nghiệm offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tip card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF191F28) else Color(0xFFF1F5F9),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Mẹo: Bảng điểm và bộ đề ôn tập được tự động lưu offline để tra cứu mọi lúc ngay cả khi mất mạng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
