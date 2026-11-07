package com.sv.tnu.tnut

import com.sv.tnu.data.AuthStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object TnutApiClient {
    const val BASE_URL = "https://portal.tnut.edu.vn/api/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

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
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TnutApi::class.java)
    }

    private val refreshLock = Any()
    private val portalUnauthorized = Regex("""(?i)"code"\s*:\s*401\b""")

    fun json(): Json = json

    private fun isLogicalUnauthorized(response: Response): Boolean {
        if (!response.isSuccessful) return false
        val body = try {
            response.peekBody(4096).string()
        } catch (_: Exception) {
            return false
        }
        return portalUnauthorized.containsMatchIn(body) ||
            body.contains("notallowed", ignoreCase = true)
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
            if (current != null && current != rejectedToken) {
                return@synchronized current
            }

            val refreshToken = authStore.refreshToken?.takeIf { it.isNotBlank() }
            val refreshed = if (refreshToken != null) {
                try {
                    runBlocking { plainApi.refreshToken(refreshToken = refreshToken) }
                } catch (e: HttpException) {
                    // A server-side outage is not a reason to try the password.
                    if (e.code() >= 500) return@synchronized null
                    null
                } catch (_: IOException) {
                    return@synchronized null
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val session = if (refreshed?.accessToken.isNullOrBlank()) {
                val username = authStore.username?.takeIf { it.isNotBlank() }
                val password = authStore.password?.takeIf { it.isNotBlank() }
                if (username == null || password == null) return@synchronized null
                try {
                    runBlocking { plainApi.login(username = username, password = password) }
                } catch (_: Exception) {
                    return@synchronized null
                }
            } else {
                refreshed
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
            if (original.url.scheme != "https" || original.url.host != "portal.tnut.edu.vn") {
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
                if (response.request.url.scheme != "https" ||
                    response.request.url.host != "portal.tnut.edu.vn") return null
                if (response.request.url.encodedPath.endsWith("auth/login")) return null
                if (responseCount(response) >= 2) return null

                val rejectedToken = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
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
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TnutApi::class.java)
    }
}
