package com.sv.tnu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sv.tnu.tnut.LoginResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tài khoản + token chỉ lưu trên máy, mã hóa bằng EncryptedSharedPreferences.
 * Không gửi đi đâu ngoài portal.tnut.edu.vn.
 */
class AuthStore(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "tkb_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var idpc: String?
        get() = prefs.getString(KEY_IDPC, null)
        set(value) = prefs.edit().putString(KEY_IDPC, value).apply()

    var hocKy: Int
        get() = prefs.getInt(KEY_HOC_KY, 0)
        set(value) = prefs.edit().putInt(KEY_HOC_KY, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    var lastAutoSyncAttempt: Long
        get() = prefs.getLong(KEY_LAST_AUTO_SYNC_ATTEMPT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTO_SYNC_ATTEMPT, value).apply()

    var avatarPath: String?
        get() = prefs.getString(KEY_AVATAR_PATH, null)
        set(value) = prefs.edit().putString(KEY_AVATAR_PATH, value).apply()

    var showExamsOnHome: Boolean
        get() = prefs.getBoolean(KEY_SHOW_EXAMS_ON_HOME, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_EXAMS_ON_HOME, value).apply()

    var visibleWeekCount: Int
        get() = prefs.getInt(KEY_VISIBLE_WEEK_COUNT, 5).coerceIn(1, 20)
        set(value) = prefs.edit().putInt(KEY_VISIBLE_WEEK_COUNT, value.coerceIn(1, 20)).apply()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_MODE, false))
    val isDarkModeFlow: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /** Chế độ giao diện: false = Sáng (Light Mode - Mặc định), true = Tối (Dark Mode). */
    var isDarkMode: Boolean
        get() = _isDarkMode.value
        set(value) {
            _isDarkMode.value = value
            prefs.edit().putBoolean(KEY_IS_DARK_MODE, value).apply()
            try {
                com.sv.tnu.widget.TimetableWidgetProvider.updateAll(appContext)
            } catch (_: Exception) {}
        }

    val hasCredentials: Boolean
        get() = !username.isNullOrBlank() && !password.isNullOrBlank()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun saveLogin(username: String, password: String, response: LoginResponse) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_ACCESS_TOKEN, response.accessToken)
            .putString(KEY_REFRESH_TOKEN, response.refreshToken)
            .apply()
        response.idpc?.let { idpc = it }
    }

    fun saveTokens(response: LoginResponse) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, response.accessToken)
            .putString(KEY_REFRESH_TOKEN, response.refreshToken)
            .apply()
    }

    fun clear() {
        avatarPath?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_IDPC = "idpc"
        private const val KEY_HOC_KY = "hoc_ky"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_AUTO_SYNC_ATTEMPT = "last_auto_sync_attempt"
        private const val KEY_AVATAR_PATH = "avatar_path"
        private const val KEY_SHOW_EXAMS_ON_HOME = "show_exams_on_home"
        private const val KEY_VISIBLE_WEEK_COUNT = "visible_week_count"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
    }
}
