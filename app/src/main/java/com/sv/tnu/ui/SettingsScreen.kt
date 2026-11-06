package com.sv.tnu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authStore: AuthStore,
    repository: TnutRepository,
    timetableViewModel: TimetableViewModel,
    appUpdateViewModel: com.sv.tnu.update.AppUpdateViewModel,
    onLogout: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshing by timetableViewModel.refreshing.collectAsState()
    val lastSyncTime by timetableViewModel.lastSyncTime.collectAsState()
    val lessons by timetableViewModel.lessons.collectAsState()
    val isCheckingUpdate by appUpdateViewModel.isChecking.collectAsState()
    val avatarPath by repository.avatarPath.collectAsState()
    val exams by timetableViewModel.exams.collectAsState()
    var showExamDialog by remember { mutableStateOf(false) }

    LaunchedEffect(authStore.username, avatarPath) {
        val username = authStore.username
        if (!username.isNullOrBlank() && avatarPath.isNullOrBlank()) {
            repository.fetchAndSaveAvatar(username)
        }
    }

    val totalSubjects = lessons.map { it.maMon }.distinct().size
    val totalCredits = lessons.distinctBy { it.maMon }.sumOf { it.soTinChi.toIntOrNull() ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cài đặt",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                            label = "Môn tuần này",
                            value = "$totalSubjects",
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

            ActionCard(
                icon = Icons.Default.DateRange,
                title = "Lịch thi học kỳ",
                subtitle = if (exams.isEmpty()) "Chưa có lịch thi (sẽ tự tải khi nhà trường công bố)"
                else "${exams.count { !it.isHoanThi }} buổi thi${if (exams.any { it.isHoanThi }) " • Có môn hoãn thi" else ""}",
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Xem lịch thi",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                },
                onClick = { showExamDialog = true },
            )

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
                onClick = onLogout,
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
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đăng xuất tài khoản",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
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
                    if (exams.isEmpty()) {
                        Text(
                            text = "Chưa có thông tin lịch thi cho học kỳ này từ nhà trường.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
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
