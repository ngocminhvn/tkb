package com.sv.tnu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sv.tnu.data.AuthStore
import com.sv.tnu.tnut.TnutRepository
import com.sv.tnu.ui.glass.GlassTabBar

enum class NavigationTab(val title: String, val icon: ImageVector) {
    TIMETABLE("TKB", Icons.Default.DateRange),
    MORE("Xem thêm", Icons.Default.GridView),
    SETTINGS("Cài đặt", Icons.Default.Settings),
}

@Composable
fun MainScreen(
    authStore: AuthStore,
    repository: TnutRepository,
    timetableViewModel: TimetableViewModel,
    onLogout: () -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val isDark = isSystemInDarkTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        // Tab content
        when (NavigationTab.entries[selectedIndex]) {
            NavigationTab.TIMETABLE -> {
                TimetableScreen(
                    viewModel = timetableViewModel,
                    contentPadding = PaddingValues(bottom = 96.dp),
                )
            }
            NavigationTab.MORE -> {
                MoreScreen(
                    authStore = authStore,
                    repository = repository,
                    timetableViewModel = timetableViewModel,
                    contentPadding = PaddingValues(bottom = 96.dp),
                )
            }
            NavigationTab.SETTINGS -> {
                SettingsScreen(
                    authStore = authStore,
                    onLogout = onLogout,
                    contentPadding = PaddingValues(bottom = 96.dp),
                )
            }
        }

        // Floating Liquid Glass Navigation Bar
        val selectedColor = MaterialTheme.colorScheme.primary
        val unselectedColor = if (isDark) Color(0xFFF9F9F9) else Color(0xFF1D1D1D)

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
    }
}
