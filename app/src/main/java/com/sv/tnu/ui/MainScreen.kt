package com.sv.tnu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sv.tnu.ui.expressive.expressiveClickable
import com.sv.tnu.data.AuthStore
import com.sv.tnu.grades.GradeRepository
import com.sv.tnu.grades.GradeViewModel
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.schools.tnut.TnutApiClient
import com.sv.tnu.ui.components.UserAvatar
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

    // Share navigation semantics between edge gestures and GlassTabBar taps.
    fun selectNavigationTab(index: Int) {
        if (index !in NavigationTab.entries.indices) return
        selectedIndex = index
        if (index != NavigationTab.MORE.ordinal) {
            showQuiz = false
            showGrades = false
        }
        if (index == NavigationTab.TIMETABLE.ordinal) {
            timetableViewModel.requestReturnToToday()
        }
    }

    // One active screen at a time. A short non-spring fade avoids horizontal
    // jitter and a second expensive timetable layout during fast tab switching.
    val tabOpacity = remember { Animatable(1f) }
    var previousTabIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (previousTabIndex != selectedIndex) {
            previousTabIndex = selectedIndex
            tabOpacity.snapTo(0.96f)
            tabOpacity.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = tabOpacity.value },
        ) {
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
        }

        // Edge-only gestures: short thumb-reachable zones above the floating glass bar.
        // A 180dp system gesture exclusion per side allows tab swipes even with
        // Android gesture navigation, while preserving Back in the rest of the edge.
        if (selectedIndex > NavigationTab.TIMETABLE.ordinal) {
            EdgeTabSwipeHandle(
                fromLeft = true,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 104.dp),
                onSwipe = { selectNavigationTab(selectedIndex - 1) },
            )
        }
        if (selectedIndex < NavigationTab.entries.lastIndex) {
            EdgeTabSwipeHandle(
                fromLeft = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 104.dp),
                onSwipe = { selectNavigationTab(selectedIndex + 1) },
            )
        }

        // Floating Liquid Glass Navigation Bar
        val selectedColor = MaterialTheme.colorScheme.primary
        val unselectedColor = if (isDark) Color(0xFF9EA7B8) else Color(0xFF5E6573)

        GlassTabBar(
            itemCount = NavigationTab.entries.size,
            selectedIndex = selectedIndex,
            onSelected = ::selectNavigationTab,
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
                    fontWeight = FontWeight.Medium,
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
 * A short edge handle for adjacent tabs, bounded to 180dp of each lower
 * screen edge so Android's Back gesture remains available everywhere else.
 * A completed inward drag is required; accidental taps have no effect.
 */
@Composable
private fun EdgeTabSwipeHandle(
    fromLeft: Boolean,
    modifier: Modifier = Modifier,
    onSwipe: () -> Unit,
) {
    val latestSwipe by rememberUpdatedState(onSwipe)
    val thresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    Box(
        modifier = modifier
            .width(24.dp)
            .height(180.dp)
            .systemGestureExclusion()
            .pointerInput(fromLeft, thresholdPx) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        if ((fromLeft && dragDistance >= thresholdPx) ||
                            (!fromLeft && dragDistance <= -thresholdPx)
                        ) {
                            latestSwipe()
                        }
                        dragDistance = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // Only inward drags count; do not consume outward swipes.
                        dragDistance += dragAmount
                        if ((fromLeft && dragDistance > 0f && dragAmount > 0f) ||
                            (!fromLeft && dragDistance < 0f && dragAmount < 0f)
                        ) {
                            change.consume()
                        }
                    },
                )
            },
    )
}

/**
 * Trang "Thêm" nằm giữa TKB và Cài đặt, cung cấp các tiện ích học tập (Xem điểm, Ôn tập).
 */
@Composable
private fun MoreScreen(
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
                        .expressiveClickable(onClick = onBack),
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
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    .expressiveClickable(onClick = onOpenGrades),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B2029) else Color.White,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 3.dp else 2.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Mới",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
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
                    .expressiveClickable(onClick = onOpenQuiz),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B2029) else Color.White,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 3.dp else 2.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
                    containerColor = if (isDark) Color(0xFF171B22) else Color(0xFFF1F5F9),
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
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
                        tint = MaterialTheme.colorScheme.primary,
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
