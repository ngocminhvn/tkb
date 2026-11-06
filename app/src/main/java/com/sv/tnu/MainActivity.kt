package com.sv.tnu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sv.tnu.tnut.TnutApiClient
import com.sv.tnu.data.AppDatabase
import com.sv.tnu.data.AuthStore
import com.sv.tnu.tnut.TnutRepository
import com.sv.tnu.ui.LoginScreen
import com.sv.tnu.ui.LoginViewModel
import com.sv.tnu.ui.TimetableScreen
import com.sv.tnu.ui.TimetableViewModel
import com.sv.tnu.ui.theme.TkbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Điểm gắn trường đang dùng: hiện tại chỉ có TNUT (thư mục tnut/).
        // Thêm trường khác = thêm 1 thư mục như tnut/ rồi đổi chỗ này.
        val authStore = AuthStore(this)
        val api = TnutApiClient.create(authStore)
        val db = AppDatabase.get(this)
        val repository = TnutRepository(this, api, db.tkbDao(), authStore)

        setContent {
            TkbTheme {
                val navController = rememberNavController()
                val startDestination = if (authStore.isLoggedIn) "timetable" else "login"
                NavHost(navController = navController, startDestination = startDestination) {
                    composable("login") {
                        val vm: LoginViewModel = viewModel(
                            factory = vmFactory { LoginViewModel(api, authStore) },
                        )
                        val state by vm.state.collectAsState()
                        LoginScreen(
                            viewModel = vm,
                            state = state,
                            schoolName = "TNUT",
                            onLoggedIn = {
                                navController.navigate("timetable") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("timetable") {
                        val vm: TimetableViewModel = viewModel(
                            factory = vmFactory { TimetableViewModel(repository, authStore) },
                        )
                        TimetableScreen(
                            viewModel = vm,
                            onLogout = {
                                authStore.clear()
                                navController.navigate("login") {
                                    popUpTo("timetable") { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> vmFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
