package com.sv.tnu.schools.tnut

import com.sv.tnu.data.AuthStore
import com.sv.tnu.rust.RustPortalCore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

object TnutApiClient {
    const val BASE_URL = "https://portal.tnut.edu.vn/api/"

    // No auth interceptor here: refreshing credentials must never recursively
    // trigger the same authenticator or logical-401 interceptor.
    private val plainApi: TnutApi by lazy {
        val plainClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(plainClient)
            .build()
            .create(TnutApi::class.java)
    }

    private val refreshLock = Any()

    private fun isLogicalUnauthorized(response: Response): Boolean {
        if (!response.isSuccessful) return false
        val preview = try {
            response.peekBody(4096)
        } catch (_: Exception) {
            return false
        }
        val rawBytes = try {
            preview.bytes()
        } catch (_: Exception) {
            return false
        }
        return RustPortalCore.isLogicalUnauthorized(rawBytes)
    }

    /**
     * One session renewal per token, even if several portal calls fail at once.
     * If an earlier version lost the refresh token, recover through the
     * already-saved portal credentials; never clear student data on failure.
     * Runs only in OkHttp's background request thread, never on the UI thread.
     */
    private fun renewSession(authStore: AuthStore, rejectedToken: String?): String? =
        synchronized(refreshLock) {
            val current = authStore.accessToken?.takeIf { it.isNotBlank() }
            if (RustPortalCore.canReuseRotatedToken(current, rejectedToken)) {
                return@synchronized current
            }

            val refreshToken = authStore.refreshToken?.takeIf { it.isNotBlank() }
            var httpStatus = 0
            var ioFailure = false
            val refreshed = if (refreshToken != null) {
                try {
                    runBlocking { plainApi.refreshSession(refreshToken) }
                } catch (e: HttpException) {
                    httpStatus = e.code()
                    null
                } catch (_: IOException) {
                    ioFailure = true
                    null
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val session = when (RustPortalCore.renewalAction(httpStatus, ioFailure, refreshed?.accessToken)) {
                RustPortalCore.RenewalAction.STOP -> return@synchronized null
                RustPortalCore.RenewalAction.USE_REFRESH -> refreshed
                RustPortalCore.RenewalAction.FALLBACK_LOGIN -> {
                    val username = authStore.username?.takeIf { it.isNotBlank() }
                    val password = authStore.password?.takeIf { it.isNotBlank() }
                    if (username == null || password == null) return@synchronized null
                    try {
                        runBlocking { plainApi.loginSession(username, password) }
                    } catch (_: Exception) {
                        return@synchronized null
                    }
                }
            }

            val validSession = session?.takeIf { it.accessToken.isNotBlank() }
                ?: return@synchronized null
            authStore.saveTokens(validSession)
            validSession.accessToken
        }

    fun create(authStore: AuthStore): TnutApi {
        // Application interceptors see the response AFTER OkHttp's automatic
        // 401 handling. A synthetic 401 created here cannot reach Authenticator;
        // logical HTTP-200/code-401 responses must be retried explicitly.
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            // This client is only for the official TNUT portal. Never attach
            // saved credentials to requests redirected to another hostname.
            if (!RustPortalCore.isOfficialPortalOrigin(original.url.scheme, original.url.host)) {
                return@Interceptor chain.proceed(original)
            }
            val isLogin = original.url.encodedPath.endsWith("auth/login")
            val oldToken = authStore.accessToken?.takeIf { it.isNotBlank() }
            val request = original.newBuilder().apply {
                if (!isLogin) {
                    oldToken?.let { header("Authorization", "Bearer $it") }
                    header("idpc", authStore.idpc ?: "0")
                }
            }.build()

            var response = chain.proceed(request)
            if (isLogin || !isLogicalUnauthorized(response)) {
                return@Interceptor response
            }

            // Close the first body before calling proceed again. Application
            // interceptors may retry; network interceptors may not.
            val newToken = renewSession(authStore, oldToken)
            if (newToken != null && newToken != oldToken) {
                response.close()
                val retry = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("idpc", authStore.idpc ?: "0")
                    .build()
                response = chain.proceed(retry)
            }

            if (isLogicalUnauthorized(response)) {
                response.newBuilder()
                    .code(401)
                    .message("Unauthorized (portal session expired)")
                    .build()
            } else {
                response
            }
        }

        // Actual HTTP 401s are handled inside OkHttp's retry/follow-up chain.
        val authenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                val isOfficial = RustPortalCore.isOfficialPortalOrigin(
                    response.request.url.scheme, response.request.url.host
                )
                if (!RustPortalCore.shouldAuthenticateRetry(
                    isOfficial, response.request.url.encodedPath, responseCount(response)
                )) return null

                val rejectedToken = RustPortalCore.extractBearerToken(response.request.header("Authorization"))
                val newToken = renewSession(authStore, rejectedToken)
                    ?: return null
                if (newToken == rejectedToken) return null

                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .header("idpc", authStore.idpc ?: "0")
                    .build()
            }

            private fun responseCount(response: Response): Int {
                var count = 1
                var prior = response.priorResponse
                while (prior != null) {
                    count++
                    prior = prior.priorResponse
                }
                return count
            }
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .build()
            .create(TnutApi::class.java)
    }
}
