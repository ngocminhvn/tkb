package vn.tnut.tkb

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
import vn.tnut.tkb.data.ApiClient
import vn.tnut.tkb.data.AppDatabase
import vn.tnut.tkb.data.AuthStore
import vn.tnut.tkb.data.TimetableRepository
import vn.tnut.tkb.ui.LoginScreen
import vn.tnut.tkb.ui.LoginViewModel
import vn.tnut.tkb.ui.TimetableScreen
import vn.tnut.tkb.ui.TimetableViewModel
import vn.tnut.tkb.ui.theme.TkbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authStore = AuthStore(this)
        val api = ApiClient.create(authStore)
        val db = AppDatabase.get(this)
        val repository = TimetableRepository(this, api, db.tkbDao(), authStore)

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
