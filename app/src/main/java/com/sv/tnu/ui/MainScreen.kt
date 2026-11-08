package com.sv.tnu.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sv.tnu.data.AuthStore
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.ui.glass.GlassTabBar
import com.sv.tnu.update.AppUpdateViewModel

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

    // Trigger the real TNUT timetable + exam sync when the authenticated
    // main screen opens, regardless of which tab is currently selected.
    // The ViewModel rate-limits automatic checks to once per local day;
    // AuthStore.saveLogin resets the marker for a fresh login.
    LaunchedEffect(timetableViewModel) {
        timetableViewModel.refreshIfNeeded()
    }

    // Tự động kiểm tra bản cập nhật mới ngầm khi mở màn hình chính
    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdate(manual = false)
    }

    // Khi đang ở các trang phụ (Thêm, Cài đặt), bấm nút back của máy thì quay lại TKB
    BackHandler(enabled = selectedIndex != 0) {
        selectedIndex = 0
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
                MoreScreen(
                    onBack = { selectedIndex = 0 },
                    contentPadding = PaddingValues(bottom = 96.dp),
                )
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
            onSelected = { selectedIndex = it },
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
 * Trang "Thêm" nằm giữa TKB và Cài đặt, có dấu quay lại trang chính và để trống nội dung theo yêu cầu.
 */
@Composable
private fun MoreScreen(
    onBack: () -> Unit = {},
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
                Text(
                    text = "Thêm",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding),
        )
    }
}
