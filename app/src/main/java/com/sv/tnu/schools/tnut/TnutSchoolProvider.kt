package com.sv.tnu.schools.tnut

import android.content.Context
import com.sv.tnu.data.AuthStore
import com.sv.tnu.data.TkbDao
import com.sv.tnu.rust.RustPortalCore
import com.sv.tnu.schools.SchoolIds
import com.sv.tnu.schools.SchoolInfo
import com.sv.tnu.schools.SchoolProvider
import com.sv.tnu.schools.SchoolRepository
import com.sv.tnu.schools.SchoolSession
import com.sv.tnu.schools.SchoolSessionTokens

/** TNUT-only auth, HTTP transport and timetable parsing. */
object TnutSchoolProvider : SchoolProvider {
    override val info = SchoolInfo(SchoolIds.TNUT, "TNUT", 20261)

    override fun open(context: Context, authStore: AuthStore, dao: TkbDao): SchoolSession {
        val api = TnutApiClient.create(authStore)
        val timetable = TnutRepository(context, api, dao, authStore)
        return object : SchoolSession {
            override val repository: SchoolRepository = timetable

            override suspend fun login(username: String, password: String) {
                val normalized = RustPortalCore.normalizeUsername(username)
                val response = api.login(username = normalized, password = password)
                check(response.accessToken.isNotBlank()) {
                    "Đăng nhập thất bại: không nhận được phiên làm việc."
                }
                authStore.saveLogin(normalized, password, SchoolSessionTokens(
                    response.accessToken, response.refreshToken, response.idpc,
                ))
                // Optional avatar failure must not prevent valid login.
                timetable.fetchAndSaveAvatar(normalized)
            }
        }
    }
}
