package com.sv.tnu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sv.tnu.grades.GradeCourse
import com.sv.tnu.grades.GradeOverview
import com.sv.tnu.grades.GradeSemester
import com.sv.tnu.grades.GradeTranscript
import com.sv.tnu.grades.GradeUiState
import com.sv.tnu.grades.GradeViewModel
import com.sv.tnu.ui.glass.LocalGlassConfig
import kotlinx.coroutines.delay

@Composable
fun GradesScreen(
    viewModel: GradeViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val isDark = LocalGlassConfig.current.dark
    val uiState by viewModel.uiState.collectAsState()
    val selectedSemester by viewModel.selectedSemester.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val expandedCourses by viewModel.expandedCourses.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.autoSelectCurrentSemester()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing)),
        label = "spinAngle",
    )

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
                // Nút quay lại kiểu Liquid Glass
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

                Text(
                    text = "Xem điểm",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // Nút refresh xoay vòng
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
                        .clickable(enabled = !isRefreshing) { viewModel.refresh() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Làm mới",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(20.dp)
                            .then(if (isRefreshing) Modifier.rotate(spinAngle) else Modifier),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                is GradeUiState.Loading -> {
                    if (state.cached != null && state.cached.semesters.isNotEmpty()) {
                        GradeContent(
                            transcript = state.cached,
                            isOffline = true,
                            selectedSemester = selectedSemester,
                            searchQuery = searchQuery,
                            expandedCourses = expandedCourses,
                            onSelectSemester = viewModel::selectSemester,
                            onUpdateSearch = viewModel::updateSearchQuery,
                            onToggleExpand = viewModel::toggleCourseExpanded,
                            contentPadding = contentPadding,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(42.dp),
                                    strokeWidth = 4.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Đang tải bảng điểm từ portal TNUT...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                is GradeUiState.Error -> {
                    if (state.cached != null && state.cached.semesters.isNotEmpty()) {
                        GradeContent(
                            transcript = state.cached,
                            isOffline = true,
                            selectedSemester = selectedSemester,
                            searchQuery = searchQuery,
                            expandedCourses = expandedCourses,
                            onSelectSemester = viewModel::selectSemester,
                            onUpdateSearch = viewModel::updateSearchQuery,
                            onToggleExpand = viewModel::toggleCourseExpanded,
                            contentPadding = contentPadding,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1F242D) else Color.White,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Không thể tải bảng điểm",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = { viewModel.refresh() },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Thử lại")
                                    }
                                }
                            }
                        }
                    }
                }

                is GradeUiState.Success -> {
                    GradeContent(
                        transcript = state.transcript,
                        isOffline = state.isOffline,
                        selectedSemester = selectedSemester,
                        searchQuery = searchQuery,
                        expandedCourses = expandedCourses,
                        onSelectSemester = viewModel::selectSemester,
                        onUpdateSearch = viewModel::updateSearchQuery,
                        onToggleExpand = viewModel::toggleCourseExpanded,
                        contentPadding = contentPadding,
                    )
                }

                GradeUiState.Initial -> {}
            }
        }

        if (isRefreshing) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                    )
                }
            }
        }

        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                icon = {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = {
                    Text(
                        text = "Không thể tải bảng điểm",
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text("Đóng")
                    }
                },
                shape = RoundedCornerShape(22.dp),
            )
        }
    }
}

@Composable
private fun GradeContent(
    transcript: GradeTranscript,
    isOffline: Boolean,
    selectedSemester: String?,
    searchQuery: String,
    expandedCourses: Set<String>,
    onSelectSemester: (String?) -> Unit,
    onUpdateSearch: (String) -> Unit,
    onToggleExpand: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalGlassConfig.current.dark

    val filteredSemesters = remember(transcript.semesters, selectedSemester, searchQuery) {
        val base = if (selectedSemester == null) {
            transcript.semesters
        } else {
            transcript.semesters.filter { it.hocKy == selectedSemester }
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim().lowercase()
            base.mapNotNull { sem ->
                val matchingCourses = sem.courses.filter {
                    it.tenMon.lowercase().contains(q) || it.maMon.lowercase().contains(q)
                }
                if (matchingCourses.isNotEmpty()) sem.copy(courses = matchingCourses) else null
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Thông báo bản lưu offline
        if (isOffline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) Color(0xFF2C2415) else Color(0xFFFEF3C7)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Đang hiển thị bản lưu offline",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                    )
                }
            }
        }

        // Thẻ tổng quan GPA tích lũy toàn khóa
        item {
            OverviewGpaCard(
                overview = transcript.overview,
                isDark = isDark,
            )
        }

        // Chọn học kỳ qua Select Dropdown / Dialog
        item {
            SemesterSelectCard(
                semesters = transcript.semesters,
                selectedSemesterId = selectedSemester,
                onSelectSemester = onSelectSemester,
                isDark = isDark,
            )
        }

        // Ô tìm kiếm môn học
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onUpdateSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tìm theo tên hoặc mã môn học...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onUpdateSearch("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (isDark) Color(0xFF1B2028).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.8f),
                    focusedContainerColor = if (isDark) Color(0xFF1B2028) else Color.White,
                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        // Danh sách môn học theo học kỳ
        if (filteredSemesters.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Không tìm thấy môn học nào",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            filteredSemesters.forEach { semester ->
                if (selectedSemester == null) {
                    item(key = "header_${semester.hocKy}") {
                        SemesterHeaderCard(semester = semester, isDark = isDark)
                    }
                }

                items(semester.courses, key = { "${semester.hocKy}_${it.maMon}_${it.nhomTo}" }) { course ->
                    val courseKey = "${semester.hocKy}_${course.maMon}_${course.nhomTo}"
                    val isExpanded = expandedCourses.contains(courseKey)
                    CourseGradeCard(
                        course = course,
                        isExpanded = isExpanded,
                        onToggle = { onToggleExpand(courseKey) },
                        isDark = isDark,
                    )
                }
            }
        }
    }
}

/**
 * Thẻ Select chọn học kỳ (thay vì dàn ngang nhiều chip).
 * Nhấp vào mở hộp thoại chọn nhanh học kỳ (mặc định là học kỳ mới nhất).
 */
@Composable
private fun SemesterSelectCard(
    semesters: List<GradeSemester>,
    selectedSemesterId: String?,
    onSelectSemester: (String?) -> Unit,
    isDark: Boolean,
) {
    var showDialog by remember { mutableStateOf(false) }

    val selectedSemester = semesters.find { it.hocKy == selectedSemesterId }
    val displayText = when {
        selectedSemester != null -> selectedSemester.tenHocKy.ifBlank { "Học kỳ ${selectedSemester.hocKy}" }
        selectedSemesterId == null -> "Tất cả các học kỳ"
        else -> "Học kỳ $selectedSemesterId"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E242E) else Color.White,
        ),
        border = BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Học kỳ xem điểm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Chọn học kỳ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }

            if (selectedSemester != null) {
                val hasStats = (selectedSemester.dtbHk4.isNotBlank() && selectedSemester.dtbHk4 != "-") ||
                    (selectedSemester.dtbHk10.isNotBlank() && selectedSemester.dtbHk10 != "-") ||
                    (selectedSemester.soTinChiHk.isNotBlank() && selectedSemester.soTinChiHk != "-") ||
                    (selectedSemester.diemRlHk.isNotBlank() && selectedSemester.diemRlHk != "-")
                if (hasStats) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedSemester.dtbHk4.isNotBlank() && selectedSemester.dtbHk4 != "-") {
                            SemesterStatBadge(label = "TB (4)", value = selectedSemester.dtbHk4, isPrimary = true)
                        }
                        if (selectedSemester.dtbHk10.isNotBlank() && selectedSemester.dtbHk10 != "-") {
                            SemesterStatBadge(label = "TB (10)", value = selectedSemester.dtbHk10)
                        }
                        if (selectedSemester.soTinChiHk.isNotBlank() && selectedSemester.soTinChiHk != "-") {
                            SemesterStatBadge(label = "Đạt", value = "${selectedSemester.soTinChiHk} TC")
                        }
                        if (selectedSemester.diemRlHk.isNotBlank() && selectedSemester.diemRlHk != "-") {
                            SemesterStatBadge(label = "ĐRL", value = selectedSemester.diemRlHk)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        SemesterSelectionDialog(
            semesters = semesters,
            selectedSemesterId = selectedSemesterId,
            onSelect = {
                onSelectSemester(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            isDark = isDark,
        )
    }
}

/**
 * Hộp thoại chọn học kỳ với danh sách sắp xếp học kỳ mới nhất ở trên.
 */
@Composable
private fun SemesterSelectionDialog(
    semesters: List<GradeSemester>,
    selectedSemesterId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Chọn học kỳ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Tùy chọn tất cả các học kỳ
                val isAllSelected = selectedSemesterId == null
                SemesterOptionItem(
                    title = "Tất cả các học kỳ",
                    subtitle = "Hiển thị tất cả môn học trong chương trình đào tạo",
                    isSelected = isAllSelected,
                    onClick = { onSelect(null) },
                    isDark = isDark,
                )

                // Hiển thị các học kỳ từ mới nhất đến cũ hơn
                semesters.reversed().forEach { sem ->
                    val isSelected = selectedSemesterId == sem.hocKy
                    val subtitle = buildString {
                        if (sem.dtbHk4.isNotBlank() && sem.dtbHk4 != "-") append("GPA: ${sem.dtbHk4}")
                        if (sem.soTinChiHk.isNotBlank() && sem.soTinChiHk != "-") {
                            if (isNotEmpty()) append(" • ")
                            append("${sem.soTinChiHk} TC")
                        }
                    }
                    SemesterOptionItem(
                        title = sem.tenHocKy.ifBlank { "Học kỳ ${sem.hocKy}" },
                        subtitle = subtitle.ifBlank { null },
                        isSelected = isSelected,
                        onClick = { onSelect(sem.hocKy) },
                        isDark = isDark,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isDark) Color(0xFF1E242E) else Color.White,
    )
}

@Composable
private fun SemesterOptionItem(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.12f)
        } else {
            if (isDark) Color(0xFF222834) else Color(0xFFF8FAFC)
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Đã chọn",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewGpaCard(
    overview: GradeOverview,
    isDark: Boolean,
) {
    val gpa4 = overview.dtbTichLuy4.ifBlank { "--" }
    val gpa10 = overview.dtbTichLuy10.ifBlank { "--" }
    val tc = overview.soTinChiTichLuy.ifBlank { "--" }
    val xepLoai = overview.xepLoaiHocTap.ifBlank { "Chưa xếp loại" }

    val gradientBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(
                listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                ),
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color(0xFFF0FDF4),
                    Color(0xFFE0F2FE),
                ),
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 2.dp),
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFF60A5FA).copy(alpha = 0.25f),
                    RoundedCornerShape(22.dp),
                )
                .padding(20.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tích lũy toàn khóa",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (xepLoai.isNotBlank() && xepLoai != "Chưa xếp loại") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = xepLoai,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricColumn(
                        label = "GPA Hệ 4",
                        value = gpa4,
                        highlight = true,
                        isDark = isDark,
                    )
                    MetricColumn(
                        label = "GPA Hệ 10",
                        value = gpa10,
                        highlight = false,
                        isDark = isDark,
                    )
                    MetricColumn(
                        label = "Tín chỉ tích lũy",
                        value = tc,
                        unit = "TC",
                        highlight = false,
                        isDark = isDark,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    unit: String? = null,
    highlight: Boolean,
    isDark: Boolean,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                ),
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SemesterHeaderCard(
    semester: GradeSemester,
    isDark: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF171B22) else Color(0xFFF1F5F9),
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = semester.tenHocKy,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val hasStats = (semester.dtbHk4.isNotBlank() && semester.dtbHk4 != "-") ||
                (semester.dtbHk10.isNotBlank() && semester.dtbHk10 != "-") ||
                (semester.soTinChiHk.isNotBlank() && semester.soTinChiHk != "-") ||
                (semester.diemRlHk.isNotBlank() && semester.diemRlHk != "-")
            if (hasStats) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (semester.dtbHk4.isNotBlank() && semester.dtbHk4 != "-") {
                        SemesterStatBadge(label = "TB (4)", value = semester.dtbHk4, isPrimary = true)
                    }
                    if (semester.dtbHk10.isNotBlank() && semester.dtbHk10 != "-") {
                        SemesterStatBadge(label = "TB (10)", value = semester.dtbHk10)
                    }
                    if (semester.soTinChiHk.isNotBlank() && semester.soTinChiHk != "-") {
                        SemesterStatBadge(label = "Đạt", value = "${semester.soTinChiHk} TC")
                    }
                    if (semester.diemRlHk.isNotBlank() && semester.diemRlHk != "-") {
                        SemesterStatBadge(label = "ĐRL", value = semester.diemRlHk)
                    }
                }
            }
        }
    }
}

@Composable
private fun SemesterStatBadge(
    label: String,
    value: String,
    isPrimary: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            softWrap = false,
        )
    }
}

@Composable
private fun CourseGradeCard(
    course: GradeCourse,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isDark: Boolean,
) {
    val gradeColor = gradeColorFor(course.diemChu)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1B2029) else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 3.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(16.dp),
        ) {
            // Tiêu đề môn học: Mã môn, Nhóm tổ, Số tín chỉ và Badge điểm chữ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Mã môn học
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF2A313E) else Color(0xFFE2E8F0))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = course.maMon,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (course.nhomTo.isNotBlank()) {
                        Text(
                            text = "Nhóm ${course.nhomTo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (course.soTinChi.isNotBlank()) {
                        Text(
                            text = "• ${course.soTinChi} TC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Điểm chữ (A, B, C, D, F, M)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(gradeColor.copy(alpha = 0.18f))
                        .border(1.5.dp, gradeColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = course.diemChu.ifBlank { "-" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = gradeColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tên môn học
            Text(
                text = course.tenMon,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Các cột điểm chính: Thi, Tổng kết hệ 10, Tổng kết hệ 4, Kết quả
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF222834) else Color(0xFFF8FAFC))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreItem(label = "Điểm thi", value = course.diemThi.ifBlank { "-" })
                ScoreItem(label = "Tổng kết (10)", value = course.diemTk10.ifBlank { "-" }, isBold = true)
                ScoreItem(label = "Tổng kết (4)", value = course.diemTk4.ifBlank { "-" })

                // Kết quả: Đạt hoặc Học lại
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (course.isDat) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Đạt",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Đạt",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF10B981),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Học lại",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Học lại",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFEF4444),
                        )
                    }
                }
            }

            // Mở rộng chi tiết các điểm thành phần (chuyên cần, giữa kỳ, kiểm tra...)
            if (course.thanhPhan.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isExpanded) "Thu gọn chi tiết" else "Xem ${course.thanhPhan.size} điểm thành phần",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF151921) else Color(0xFFF1F5F9))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Chi tiết điểm thành phần:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        course.thanhPhan.forEach { tp ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = tp.tenThanhPhan,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = tp.diem.ifBlank { "-" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreItem(
    label: String,
    value: String,
    isBold: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun gradeColorFor(grade: String): Color {
    return when (grade.uppercase()) {
        "A", "A+" -> Color(0xFF10B981) // Xanh lá
        "B", "B+" -> Color(0xFF3B82F6) // Xanh dương
        "C", "C+" -> Color(0xFFF59E0B) // Vàng hổ phách
        "D", "D+" -> Color(0xFFF97316) // Cam
        "F" -> Color(0xFFEF4444) // Đỏ
        "M" -> Color(0xFF06B6D4) // Xanh ngọc (Miễn)
        else -> Color(0xFF94A3B8) // Xám đá
    }
}
