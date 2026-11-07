package com.sv.tnu.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.sv.tnu.data.AuthStore
import com.sv.tnu.tnut.TnutRepository
import com.sv.tnu.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authStore: AuthStore,
    repository: TnutRepository,
    timetableViewModel: TimetableViewModel,
    appUpdateViewModel: com.sv.tnu.update.AppUpdateViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshing by timetableViewModel.refreshing.collectAsState()
    val lastSyncTime by timetableViewModel.lastSyncTime.collectAsState()
    val lessons by timetableViewModel.semesterLessons.collectAsState()
    val isCheckingUpdate by appUpdateViewModel.isChecking.collectAsState()
    val avatarPath by repository.avatarPath.collectAsState()
    val exams by timetableViewModel.allExams.collectAsState()
    val showExamsOnHome by timetableViewModel.showExamsOnHome.collectAsState()
    val visibleWeekCount by timetableViewModel.visibleWeekCount.collectAsState()
    val isDarkMode by authStore.isDarkModeFlow.collectAsState()
    val isFetchingExams by timetableViewModel.isFetchingExams.collectAsState()
    val examSyncError by timetableViewModel.examSyncError.collectAsState()
    var showExamDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()
    val timetableJsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !importInProgress) {
            importInProgress = true
            importStatus = null
            importScope.launch {
                try {
                    repository.importPersonalTimetable(uri, timetableViewModel.hocKy)
                        .onSuccess { count -> importStatus = "Đã khôi phục $count buổi học từ tệp cá nhân." }
                        .onFailure { e -> importStatus = "Không thể nhập TKB: ${e.message ?: "Tệp không hợp lệ"}" }
                } finally {
                    importInProgress = false
                }
            }
        }
    }
    val examCsvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !importInProgress) {
            importInProgress = true
            importStatus = null
            importScope.launch {
                try {
                    repository.importPersonalExams(uri, timetableViewModel.hocKy)
                        .onSuccess { count -> importStatus = "Đã nhập $count môn thi từ bảng CSV/TSV." }
                        .onFailure { e -> importStatus = "Không thể nhập lịch thi: ${e.message ?: "Tệp không hợp lệ"}" }
                } finally {
                    importInProgress = false
                }
            }
        }
    }

    LaunchedEffect(authStore.username, avatarPath) {
        val username = authStore.username
        if (!username.isNullOrBlank() && avatarPath.isNullOrBlank()) {
            repository.fetchAndSaveAvatar(username)
        }
    }

    val totalSubjects = lessons.map { it.maMon }.distinct().size
    val totalCredits = lessons.distinctBy { it.maMon }.sumOf { it.soTinChi.toIntOrNull() ?: 0 }
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
                Text(
                    text = "Cài đặt",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Student summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            avatarPath = avatarPath,
                            defaultIcon = Icons.Default.School,
                            modifier = Modifier.size(48.dp),
                            iconSize = 28.dp,
                            contentDescription = "Ảnh đại diện sinh viên",
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Mã SV: ${authStore.username ?: "—"}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Cổng thông tin đào tạo sinh viên",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats in glass row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatItem(
                            modifier = Modifier.weight(1f),
                            label = "Môn học HK",
                            value = "$totalSubjects môn",
                        )
                        StatItem(
                            modifier = Modifier.weight(1f),
                            label = "Tín chỉ",
                            value = "$totalCredits TC",
                        )
                        StatItem(
                            modifier = Modifier.weight(1f),
                            label = "Học kỳ",
                            value = "${timetableViewModel.hocKy}",
                        )
                    }
                }
            }

            // Utilities section
            Text(
                text = "Tiện ích & Cài đặt",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )

            // Switch Hiện lịch thi học kỳ trên TKB trang chủ
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showExamDialog = true
                            timetableViewModel.fetchExamsOnly(force = true)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hiện lịch thi học kỳ",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!showExamsOnHome) "Đang tắt trên trang chủ • Chạm để xem chi tiết"
                            else if (exams.isEmpty()) "Chưa có lịch thi trong SQL"
                            else "${exams.count { !it.isHoanThi }} buổi thi • Hiện cùng TKB • Chạm xem",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showExamsOnHome,
                        onCheckedChange = { timetableViewModel.setShowExamsOnHome(it) },
                    )
                }
            }

            // Tùy chọn hiển thị số tuần (với nút - và +)
            val weeksBefore = (visibleWeekCount - 1) / 2
            val weeksAfter = visibleWeekCount - 1 - weeksBefore
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tùy chọn hiển thị",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$visibleWeekCount tuần ($weeksBefore trước, 1 hiện tại, $weeksAfter sau)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = { timetableViewModel.setVisibleWeekCount(visibleWeekCount - 1) },
                            enabled = visibleWeekCount > 1,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Giảm",
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Box(
                            modifier = Modifier.widthIn(min = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$visibleWeekCount",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { timetableViewModel.setVisibleWeekCount(visibleWeekCount + 1) },
                            enabled = visibleWeekCount < 15,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tăng",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Chế độ giao diện (Sáng / Tối) - Công tắc 2 option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Giao diện",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isDarkMode) "Chế độ tối" else "Chế độ sáng (mặc định)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }

                    // Công tắc 2 option: [ Sáng | Tối ]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (!isDarkMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { authStore.isDarkMode = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (!isDarkMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sáng",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (!isDarkMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isDarkMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { authStore.isDarkMode = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isDarkMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tối",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isDarkMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }

            ActionCard(
                icon = Icons.Default.Sync,
                title = "Đồng bộ lại thời khóa biểu",
                subtitle = if (refreshing) "Đang tải dữ liệu mới từ portal..." else "Lần cuối: ${formatLastSync(lastSyncTime)}",
                trailing = {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    if (!refreshing) {
                        timetableViewModel.refresh()
                    }
                },
            )

            ActionCard(
                icon = Icons.Default.School,
                title = "Khôi phục thời khóa biểu từ JSON",
                subtitle = "Chọn bản sao lưu cá nhân (được giữ offline trên máy)",
                onClick = { if (!importInProgress) timetableJsonPicker.launch("*/*") },
            )

            ActionCard(
                icon = Icons.Default.DateRange,
                title = "Nhập lịch thi từ Excel (CSV/TSV)",
                subtitle = "Xuất bảng Excel thành CSV UTF-8 rồi chọn tệp",
                onClick = { if (!importInProgress) examCsvPicker.launch("*/*") },
            )

            if (importInProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang kiểm tra và nhập dữ liệu...", style = MaterialTheme.typography.bodySmall)
                }
            }
            importStatus?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            ActionCard(
                icon = Icons.Default.Info,
                title = "Thông tin ứng dụng",
                subtitle = "SvTNU • Phiên bản ${appUpdateViewModel.currentVersionName}",
                onClick = { appUpdateViewModel.checkForUpdate(manual = true) },
            )

            ActionCard(
                icon = Icons.Default.Sync,
                title = "Kiểm tra cập nhật",
                subtitle = if (isCheckingUpdate) "Đang kiểm tra phiên bản mới từ server..." else "Nhấn để kiểm tra và tải bản mới nhất",
                trailing = {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Check",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                },
                onClick = {
                    if (!isCheckingUpdate) {
                        appUpdateViewModel.checkForUpdate(manual = true)
                    }
                },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đăng xuất tài khoản",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            // Khoảng đệm đáy để nội dung cuộn mượt mà trên thanh navigation bar
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 20.dp))
        }

        // Hộp thoại xác nhận đăng xuất tài khoản
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = {
                    Text(
                        text = "Đăng xuất tài khoản",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                text = {
                    Text(
                        text = "Bạn có chắc chắn muốn đăng xuất khỏi tài khoản ${authStore.username ?: ""} không?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            onLogout()
                        },
                    ) {
                        Text(
                            text = "Đăng xuất",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Hủy")
                    }
                },
            )
        }

        // Hộp thoại hiển thị chi tiết lịch thi học kỳ
        if (showExamDialog) {
            AlertDialog(
                onDismissRequest = { showExamDialog = false },
                title = {
                    Text(
                        text = "Lịch thi học kỳ ${timetableViewModel.hocKy}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                text = {
                    if (isFetchingExams && exams.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Đang tải lịch thi từ nhà trường...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else if (exams.isEmpty()) {
                        Text(
                            text = examSyncError ?: "Chưa có dữ liệu lịch thi đã xác minh từ portal cho học kỳ này. Nhấn đóng rồi mở lại mục Lịch thi để thử đồng bộ.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (examSyncError != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            examSyncError?.let { message ->
                                Text(
                                    text = "Dữ liệu đang hiển thị là lịch lưu trên máy. Đồng bộ mới không hoàn tất: $message",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            exams.forEach { exam ->
                                val isHoan = exam.isHoanThi
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    ),
                                    border = if (isHoan) BorderStroke(1.dp, Color(0xFFFFA000)) else null,
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                text = exam.tenMon,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (isHoan) Color(0xFFFFA000).copy(alpha = 0.18f)
                                                        else Color(0xFFE53935).copy(alpha = 0.16f)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = if (isHoan) "HOÃN THI" else "THI",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isHoan) Color(0xFFFFA000) else Color(0xFFE53935),
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Ngày & giờ thi
                                        val dayDisplay = try {
                                            val parts = exam.ngayThi.split("-")
                                            if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else exam.ngayThi
                                        } catch (_: Exception) { exam.ngayThi }

                                        Text(
                                            text = "Ngày: $dayDisplay • Giờ: ${exam.gioBatDau}${if (exam.soPhut > 0) " (${exam.soPhut}p)" else ""}${if (exam.isGiuaKy) " • Giữa kỳ" else ""}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isHoan) Color(0xFFFFA000) else Color(0xFFE53935),
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = "Phòng: ${exam.maPhong.ifBlank { "—" }}${if (exam.diaDiemThi.isNotBlank()) " (${exam.diaDiemThi})" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )

                                        if (exam.soBaoDanh.isNotBlank() || exam.hinhThucThi.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "SBD: ${exam.soBaoDanh.ifBlank { "—" }}${if (exam.hinhThucThi.isNotBlank()) " • " + exam.hinhThucThi else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExamDialog = false }) {
                        Text("Đóng")
                    }
                },
            )
        }
    }
}

private fun formatLastSync(timestamp: Long): String {
    if (timestamp <= 0L) return "Chưa từng đồng bộ"
    val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun StatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}
