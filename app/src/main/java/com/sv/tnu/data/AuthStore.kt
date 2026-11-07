package com.sv.tnu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sv.tnu.schools.SchoolIds
import com.sv.tnu.schools.SchoolSessionTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tài khoản + token chỉ lưu trên máy, mã hóa bằng EncryptedSharedPreferences.
 * Chỉ school provider tương ứng được sử dụng thông tin này.
 */
class AuthStore(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = getPrefs(context)

    // No migration needed: existing encrypted credentials remain TNUT.
    var schoolId: String
        get() = prefs.getString(KEY_SCHOOL_ID, SchoolIds.TNUT) ?: SchoolIds.TNUT
        set(value) = prefs.edit().putString(KEY_SCHOOL_ID, value).apply()

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

    var studentName: String?
        get() = prefs.getString(KEY_STUDENT_NAME, null)
        set(value) = prefs.edit().putString(KEY_STUDENT_NAME, value).apply()

    // Throttle optional student-name retrieval (the portal may return image only).
    var lastProfileCheck: Long
        get() = prefs.getLong(KEY_LAST_PROFILE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PROFILE_CHECK, value).apply()

    var showExamsOnHome: Boolean
        get() = prefs.getBoolean(KEY_SHOW_EXAMS_ON_HOME, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_EXAMS_ON_HOME, value).apply()

    var visibleWeekCount: Int
        get() = prefs.getInt(KEY_VISIBLE_WEEK_COUNT, 5).coerceIn(1, 20)
        set(value) = prefs.edit().putInt(KEY_VISIBLE_WEEK_COUNT, value.coerceIn(1, 20)).apply()

    // Both reminders are opt-in. Times are local minutes after midnight.
    var remindToday: Boolean
        get() = prefs.getBoolean(KEY_REMIND_TODAY, false)
        set(value) = prefs.edit().putBoolean(KEY_REMIND_TODAY, value).apply()

    var remindTomorrow: Boolean
        get() = prefs.getBoolean(KEY_REMIND_TOMORROW, false)
        set(value) = prefs.edit().putBoolean(KEY_REMIND_TOMORROW, value).apply()

    var todayReminderMinutes: Int
        get() = prefs.getInt(KEY_TODAY_REMINDER_MINUTES, 6 * 60 + 30).coerceIn(0, 1439)
        set(value) = prefs.edit().putInt(KEY_TODAY_REMINDER_MINUTES, value.coerceIn(0, 1439)).apply()

    var tomorrowReminderMinutes: Int
        get() = prefs.getInt(KEY_TOMORROW_REMINDER_MINUTES, 20 * 60).coerceIn(0, 1439)
        set(value) = prefs.edit().putInt(KEY_TOMORROW_REMINDER_MINUTES, value.coerceIn(0, 1439)).apply()

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

    fun saveLogin(username: String, password: String, response: SchoolSessionTokens) {
        if (this.username != null && this.username != username) {
            // A newly signed-in account must never inherit another
            // student's name/avatar from encrypted preferences.
            studentName = null
            avatarPath = null
            lastProfileCheck = 0L
        }
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_ACCESS_TOKEN, response.accessToken)
            .putString(KEY_REFRESH_TOKEN, response.refreshToken)
            // Every real login must trigger a fresh portal sync on the main
            // screen, even if an earlier account synced earlier today.
            // Preserve the previous Room cache and its last successful timestamp.
            .putLong(KEY_LAST_AUTO_SYNC_ATTEMPT, 0L)
            .apply()
        response.idpc?.let { idpc = it }
    }

    fun saveTokens(response: SchoolSessionTokens) {
        // The portal may rotate only access_token. An omitted refresh_token
        // must NOT erase the previously valid one.
        val editor = prefs.edit().putString(KEY_ACCESS_TOKEN, response.accessToken)
        response.refreshToken?.takeIf { it.isNotBlank() }?.let {
            editor.putString(KEY_REFRESH_TOKEN, it)
        }
        response.idpc?.takeIf { it.isNotBlank() }?.let {
            editor.putString(KEY_IDPC, it)
        }
        editor.apply()
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
        @Volatile
        private var sharedPrefs: SharedPreferences? = null

        private fun getPrefs(context: Context): SharedPreferences =
            sharedPrefs ?: synchronized(this) {
                sharedPrefs ?: run {
                    val appContext = context.applicationContext
                    val masterKey = MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        appContext,
                        "tkb_auth",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    ).also { sharedPrefs = it }
                }
            }

        private const val KEY_SCHOOL_ID = "school_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_IDPC = "idpc"
        private const val KEY_HOC_KY = "hoc_ky"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_AUTO_SYNC_ATTEMPT = "last_auto_sync_attempt"
        private const val KEY_AVATAR_PATH = "avatar_path"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_LAST_PROFILE_CHECK = "last_profile_check"
        private const val KEY_SHOW_EXAMS_ON_HOME = "show_exams_on_home"
        private const val KEY_VISIBLE_WEEK_COUNT = "visible_week_count"
        private const val KEY_REMIND_TODAY = "remind_today"
        private const val KEY_REMIND_TOMORROW = "remind_tomorrow"
        private const val KEY_TODAY_REMINDER_MINUTES = "today_reminder_minutes"
        private const val KEY_TOMORROW_REMINDER_MINUTES = "tomorrow_reminder_minutes"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
    }
}
