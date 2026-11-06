package vn.tnut.tkb.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    const val BASE_URL = "https://portal.tnut.edu.vn/api/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun json(): Json = json

    fun create(authStore: AuthStore): PortalApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val isLogin = original.url.encodedPath.endsWith("auth/login")
            val builder = original.newBuilder()
            if (!isLogin) {
                authStore.accessToken?.let { token ->
                    builder.addHeader("Authorization", "Bearer $token")
                }
                builder.addHeader("idpc", authStore.idpc ?: "0")
            }
            chain.proceed(builder.build())
        }

        val authenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                if (responseCount(response) >= 2) return null
                val refreshToken = authStore.refreshToken ?: return null
                // Gọi refresh bằng client trần (không interceptor/authenticator) để tránh lặp.
                val plainClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val plainApi = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(plainClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(PortalApi::class.java)
                return try {
                    val refreshed = runBlocking {
                        plainApi.refreshToken(refreshToken = refreshToken)
                    }
                    if (refreshed.accessToken.isBlank()) return null
                    authStore.saveTokens(refreshed)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.accessToken}")
                        .build()
                } catch (_: Exception) {
                    null
                }
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
            .readTimeout(90, TimeUnit.SECONDS) // endpoint tuần trả chậm trên mạng yếu
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PortalApi::class.java)
    }
}
