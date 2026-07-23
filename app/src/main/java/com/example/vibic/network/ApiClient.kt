package com.example.vibic.network

import android.util.Log
import com.example.vibic.ErrorBus
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"

    private const val BASE_URL = "http://176.123.162.135:8080/"

    private var retrofitInstance: Retrofit? = null

    fun getInstance(): Retrofit {
        if (retrofitInstance == null) {
            retrofitInstance = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(getOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofitInstance!!
    }

    val apiService: ApiService by lazy {
        getInstance().create(ApiService::class.java)
    }

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(UnauthorizedInterceptor())
            .addInterceptor(LoggingInterceptor())
            .dns(Dns.SYSTEM)
            .retryOnConnectionFailure(true)
            .pingInterval(20, TimeUnit.SECONDS) // Уменьшено для более быстрого обнаружения обрыва
            .connectTimeout(15, TimeUnit.SECONDS) // Уменьшено (было 30)
            .readTimeout(15, TimeUnit.SECONDS) // Уменьшено (было 60)
            .writeTimeout(15, TimeUnit.SECONDS) // Уменьшено (было 60)
            .callTimeout(30, TimeUnit.SECONDS) // Уменьшено (было 90)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val path = originalRequest.url.encodedPath

            // ИСПРАВЛЕНИЕ: Мы НЕ должны отправлять протухший токен на эндпоинты авторизации
            // (особенно на /auth/refresh), иначе сервер сразу выдаст 401.
            if (path.contains("auth/")) {
                return chain.proceed(originalRequest)
            }

            val token = TokenManager.getAccessToken()
            val request = if (!token.isNullOrBlank()) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }

            return chain.proceed(request)
        }
    }

    class UnauthorizedInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // ИСПРАВЛЕНИЕ: Мы НЕ ДОЛЖНЫ перехватывать запрос на обновление токена,
            // иначе получим мертвую петлю (SocketTimeoutException) и зависание.
            if (originalRequest.url.encodedPath.contains("auth/refresh")) {
                return chain.proceed(originalRequest)
            }

            var response = chain.proceed(originalRequest)

            if (response.code == 401) {
                Log.d(TAG, "Получен 401, пробуем обновить токен...")
                response.close()

                val refreshed = runBlocking { TokenManager.refreshToken() }
                if (refreshed) {
                    val newToken = TokenManager.getAccessToken()
                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return chain.proceed(newRequest)
                }
            }

            return response
        }
    }

    class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()

            Log.d(TAG, "--> ${request.method} ${request.url}")

            return try {
                val response = chain.proceed(request)
                val duration = System.currentTimeMillis() - startTime

                Log.d(TAG, "<-- ${response.code} ${response.message} (${duration}ms)")

                // 401 не показываем — он обрабатывается автоматическим рефрешем токена
                // в UnauthorizedInterceptor, иначе кот будет мигать при каждом обновлении.
                if (!response.isSuccessful && response.code != 401) {
                    ErrorBus.report(response.code, response.message, request.url.encodedPath)
                }

                if (response.code != 401) {
                    val responseBody = response.peekBody(1024 * 1024)
                    Log.d(TAG, "Response body: ${responseBody.string().take(500)}")
                }

                response
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(
                    TAG,
                    "<-- HTTP FAILED (${duration}ms): ${e.javaClass.simpleName}: ${e.message}",
                    e
                )
                // 0 = нет соединения / таймаут (HTTP-кода в этом случае нет)
                ErrorBus.report(0, e.javaClass.simpleName, request.url.encodedPath)
                throw e
            }
        }
    }
}