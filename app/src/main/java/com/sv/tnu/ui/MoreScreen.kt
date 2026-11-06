package com.sv.tnu.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    authStore: AuthStore,
    repository: TnutRepository,
    timetableViewModel: TimetableViewModel,
    appUpdateViewModel: com.sv.tnu.update.AppUpdateViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val refreshing by timetableViewModel.refreshing.collectAsState()
    val lessons by timetableViewModel.lessons.collectAsState()
    val selectedWeek by timetableViewModel.selectedWeek.collectAsState()
    val isCheckingUpdate by appUpdateViewModel.isChecking.collectAsState()

    val totalSubjects = lessons.map { it.maMon }.distinct().size
    val totalCredits = lessons.distinctBy { it.maMon }.sumOf { it.soTinChi.toIntOrNull() ?: 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Xem thêm",
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
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "School",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
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
                text = "Tiện ích & Liên kết",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )

            ActionCard(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = "Cổng thông tin đào tạo",
                subtitle = "Truy cập portal trực tuyến của trường",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qldt.tnut.edu.vn"))
                    context.startActivity(intent)
                },
            )

            ActionCard(
                icon = Icons.Default.Sync,
                title = "Đồng bộ lại thời khóa biểu",
                subtitle = if (refreshing) "Đang tải dữ liệu mới từ portal..." else "Lấy lịch học mới nhất từ trường",
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
                subtitle = "SvTNU • Phiên bản ${appUpdateViewModel.currentVersionName} (Build ${appUpdateViewModel.currentVersionCode})",
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

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp, horizontal = 8.dp),
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
                style = MaterialTheme.typography.labelSmall,
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
    trailing: (@Composable () -> Unit)? = null,
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
