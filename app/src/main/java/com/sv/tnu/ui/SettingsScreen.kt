@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sv.tnu.ui

import com.sv.tnu.ui.theme.AppCardDefaults

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sv.tnu.ui.expressive.expressiveClickable
import com.sv.tnu.data.AuthStore
import com.sv.tnu.notifications.ScheduleReminders
import com.sv.tnu.rust.RustScheduleCore
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.schools.SchoolIds
import com.sv.tnu.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authStore: AuthStore,
    repository: SchoolRepository,
    timetableViewModel: TimetableViewModel,
    appUpdateViewModel: com.sv.tnu.update.AppUpdateViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshing by timetableViewModel.refreshing.collectAsState()
    val lastSyncTime by timetableViewModel.lastSyncTime.collectAsState()
    val avatarPath by repository.avatarPath.collectAsState()
    val exams by timetableViewModel.allExams.collectAsState()
    val showExamsOnHome by timetableViewModel.showExamsOnHome.collectAsState()
    val isDarkMode by authStore.isDarkModeFlow.collectAsState()
    val isFetchingExams by timetableViewModel.isFetchingExams.collectAsState()
    val examSyncError by timetableViewModel.examSyncError.collectAsState()
    var showExamDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var editingReminderTomorrow by remember { mutableStateOf<Boolean?>(null) }

    val context = LocalContext.current
    var remindToday by remember { mutableStateOf(authStore.remindToday) }
    var remindTomorrow by remember { mutableStateOf(authStore.remindTomorrow) }
    var todayMinutes by remember { mutableStateOf(authStore.todayReminderMinutes) }
    var tomorrowMinutes by remember { mutableStateOf(authStore.tomorrowReminderMinutes) }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    var pendingEnableForTomorrow by remember { mutableStateOf<Boolean?>(null) }

    fun setReminderEnabled(tomorrow: Boolean, enabled: Boolean) {
        if (tomorrow) {
            remindTomorrow = enabled
            authStore.remindTomorrow = enabled
        } else {
            remindToday = enabled
            authStore.remindToday = enabled
        }
        ScheduleReminders.syncAll(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val kind = pendingEnableForTomorrow
        pendingEnableForTomorrow = null
        if (granted && kind != null) {
            notificationPermissionDenied = false
            setReminderEnabled(kind, true)
        } else if (!granted) {
            notificationPermissionDenied = true
        }
    }

    fun toggleReminder(tomorrow: Boolean, enabled: Boolean) {
        if (!enabled) {
            setReminderEnabled(tomorrow, false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            pendingEnableForTomorrow = tomorrow
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setReminderEnabled(tomorrow, true)
        }
    }

    fun pickReminderTime(tomorrow: Boolean) {
        editingReminderTomorrow = tomorrow
    }
    LaunchedEffect(authStore.username, avatarPath) {
        val username = authStore.username
        if (!username.isNullOrBlank() && avatarPath.isNullOrBlank()) {
            repository.fetchAndSaveAvatar(username)
        }
    }

    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark

    val gradientBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(
                listOf(
                    Color(0xFF1B293D),
                    Color(0xFF111927),
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

    // Compose Expressive time picker, retaining local preferences and alarm schedule.
    val editingTomorrow = editingReminderTomorrow
    if (editingTomorrow != null) {
        key(editingTomorrow) {
            val initialMinutes = if (editingTomorrow) tomorrowMinutes else todayMinutes
            val timeState = rememberTimePickerState(
                initialHour = initialMinutes / 60,
                initialMinute = initialMinutes % 60,
                is24Hour = true,
            )
            var useKeyboard by remember { mutableStateOf(false) }
            TimePickerDialog(
                onDismissRequest = { editingReminderTomorrow = null },
                title = {
                    Text(
                        text = if (editingTomorrow) "Thông báo lịch ngày mai" else "Thông báo lịch hôm nay",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newMinutes = timeState.hour * 60 + timeState.minute
                        if (editingTomorrow) {
                            tomorrowMinutes = newMinutes
                            authStore.tomorrowReminderMinutes = newMinutes
                        } else {
                            todayMinutes = newMinutes
                            authStore.todayReminderMinutes = newMinutes
                        }
                        ScheduleReminders.syncAll(context)
                        editingReminderTomorrow = null
                    }) { Text("Lưu") }
                },
                dismissButton = {
                    TextButton(onClick = { editingReminderTomorrow = null }) { Text("Hủy") }
                },
                modeToggleButton = {
                    IconButton(onClick = { useKeyboard = !useKeyboard }) {
                        Icon(
                            imageVector = if (useKeyboard) Icons.Default.AccessTime else Icons.Default.Edit,
                            contentDescription = if (useKeyboard) "Chọn trên đồng hồ" else "Nhập giờ",
                        )
                    }
                },
                shape = RoundedCornerShape(24.dp),
            ) {
                if (useKeyboard) TimeInput(state = timeState)
                else TimePicker(state = timeState)
            }
        }
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
                Text(
                    text = "Cài đặt",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Student summary card with gradient matching OverviewGpaCard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,containerColor = Color.Transparent),
                elevation = AppCardDefaults.elevation(darkDp = 4.dp, lightDp = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(gradientBrush)
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFA9C9ED).copy(alpha = 0.28f) else Color(0xFF60A5FA).copy(alpha = 0.25f),
                            RoundedCornerShape(22.dp),
                        )
                        .padding(18.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
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
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                UserAvatar(
                                    avatarPath = avatarPath,
                                    defaultIcon = Icons.Default.School,
                                    modifier = Modifier.fillMaxSize(),
                                    iconSize = 28.dp,
                                    contentDescription = "Ảnh đại diện sinh viên",
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Mã SV: ${authStore.username ?: "—"}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                label = "Nhận diện",
                                value = if (authStore.isLoggedIn && authStore.schoolId == SchoolIds.TNUT)
                                    "SV TNUT" else "—",
                            )
                            StatItem(
                                modifier = Modifier.weight(1f),
                                label = "Lịch thi",
                                value = "${exams.count { !it.isHoanThi }} buổi",
                            )
                            StatItem(
                                modifier = Modifier.weight(1f),
                                label = "Học kỳ",
                                value = "${timetableViewModel.hocKy}",
                            )
                        }
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
                shape = AppCardDefaults.shape,
                colors = AppCardDefaults.colors(),
                elevation = AppCardDefaults.elevation(),
                border = AppCardDefaults.border(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .expressiveClickable {
                            showExamDialog = true
                            timetableViewModel.fetchExamsOnly(force = true)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!showExamsOnHome) "Đang tắt trên trang chủ"
                            else if (exams.isEmpty()) "Chưa có lịch thi trong SQL"
                            else "${exams.count { !it.isHoanThi }} buổi thi • Hiện cùng TKB",
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

            // Two independent opt-in reminders based on local Room data.
            ReminderSettingCard(
                title = "Thông báo lịch hôm nay",
                enabled = remindToday,
                minuteOfDay = todayMinutes,
                icon = Icons.Default.NotificationsActive,
                onToggle = { toggleReminder(false, it) },
                onPickTime = { pickReminderTime(false) },
            )
            ReminderSettingCard(
                title = "Thông báo lịch ngày mai",
                enabled = remindTomorrow,
                minuteOfDay = tomorrowMinutes,
                icon = Icons.Default.NotificationsNone,
                onToggle = { toggleReminder(true, it) },
                onPickTime = { pickReminderTime(true) },
            )
            if (notificationPermissionDenied) {
                Text(
                    text = "Chưa được cấp quyền thông báo. Hãy cho phép thông báo trong cài đặt Android để bật nhắc lịch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Timetable rows now load continuously on scroll; there is no
            // configurable before/after week window.

            // Chế độ giao diện (Sáng / Tối) - Công tắc 2 option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppCardDefaults.shape,
                colors = AppCardDefaults.colors(),
                elevation = AppCardDefaults.elevation(),
                border = AppCardDefaults.border(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isDarkMode) "Chế độ tối" else "Chế độ sáng (mặc định)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }

                    // Compact single-select connected button group (MD3E 1.5.0).
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToggleButton(
                            checked = !isDarkMode,
                            onCheckedChange = { if (it) authStore.isDarkMode = false },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sáng", style = MaterialTheme.typography.labelSmall)
                        }
                        ToggleButton(
                            checked = isDarkMode,
                            onCheckedChange = { if (it) authStore.isDarkMode = true },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tối", style = MaterialTheme.typography.labelSmall)
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
                        LoadingIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
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
                icon = Icons.Default.Info,
                title = "Thông tin ứng dụng",
                subtitle = "TKB SV • Phiên bản ${appUpdateViewModel.currentVersionName}",
                onClick = { appUpdateViewModel.checkForUpdate(manual = true) },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF2C1618) else Color(0xFFFEE2E2),
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(16.dp), pressedShape = RoundedCornerShape(20.dp)),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = if (isDark) 0.35f else 0.25f),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isDark) 2.dp else 1.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đăng xuất tài khoản",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Khoảng đệm đáy để nội dung cuộn mượt mà trên thanh navigation bar
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 20.dp))
        }

        // Hộp thoại xác nhận đăng xuất tài khoản
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = if (isDark) Color(0xFF19212E) else Color.White,
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
                shape = RoundedCornerShape(20.dp),
                containerColor = if (isDark) Color(0xFF19212E) else Color.White,
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
                            LoadingIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
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
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
                                        val dayDisplay = RustScheduleCore.formatDmy(exam.ngayThi)

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
private fun ReminderSettingCard(
    title: String,
    enabled: Boolean,
    minuteOfDay: Int,
    icon: ImageVector,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onPickTime),
        shape = AppCardDefaults.shape,
        colors = AppCardDefaults.colors(),
        elevation = AppCardDefaults.elevation(),
        border = AppCardDefaults.border(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Giờ nhắc: %02d:%02d".format(
                        Locale.US, minuteOfDay / 60, minuteOfDay % 60,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun StatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF1F2B3B).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.75f))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF60A5FA).copy(alpha = 0.20f),
                RoundedCornerShape(14.dp),
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    val isDark = com.sv.tnu.ui.glass.LocalGlassConfig.current.dark
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick),
        shape = AppCardDefaults.shape,
        colors = AppCardDefaults.colors(),
        elevation = AppCardDefaults.elevation(),
        border = AppCardDefaults.border(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
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
                    color = MaterialTheme.colorScheme.onSurface,
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
