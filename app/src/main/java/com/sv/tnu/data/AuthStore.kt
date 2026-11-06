package com.sv.tnu.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Tài khoản + token chỉ lưu trên máy, mã hóa bằng EncryptedSharedPreferences.
 * Không gửi đi đâu ngoài portal.tnut.edu.vn.
 */
class AuthStore(context: Context) {
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
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_IDPC = "idpc"
        private const val KEY_HOC_KY = "hoc_ky"
    }
}
