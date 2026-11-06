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
import com.sv.tnu.data.AppDatabase
import com.sv.tnu.data.AuthStore
import com.sv.tnu.tnut.TnutApiClient
import com.sv.tnu.tnut.TnutRepository
import com.sv.tnu.ui.LoginScreen
import com.sv.tnu.ui.LoginViewModel
import com.sv.tnu.ui.MainScreen
import com.sv.tnu.ui.TimetableViewModel
import com.sv.tnu.ui.theme.TkbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authStore = AuthStore(this)
        val api = TnutApiClient.create(authStore)
        val db = AppDatabase.get(this)
        val repository = TnutRepository(this, api, db.tkbDao(), authStore)

        setContent {
            val isDarkMode by authStore.isDarkModeFlow.collectAsState()
            TkbTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                val startDestination = if (authStore.isLoggedIn) "main" else "login"
                NavHost(navController = navController, startDestination = startDestination) {
                    composable("login") {
                        val vm: LoginViewModel = viewModel(
                            factory = vmFactory { LoginViewModel(api, authStore, repository) },
                        )
                        val state by vm.state.collectAsState()
                        LoginScreen(
                            viewModel = vm,
                            state = state,
                            onLoggedIn = {
                                navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("main") {
                        val timetableVm: TimetableViewModel = viewModel(
                            factory = vmFactory { TimetableViewModel(repository, authStore) },
                        )
                        MainScreen(
                            authStore = authStore,
                            repository = repository,
                            timetableViewModel = timetableVm,
                            onLogout = {
                                repository.clearAvatar()
                                authStore.clear()
                                navController.navigate("login") {
                                    popUpTo("main") { inclusive = true }
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
