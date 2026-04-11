package com.example.vibic.network

import android.util.Log
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

    // Пока сервер такой — оставляем.
    // Но для нормальной работы с VPN желательно HTTPS + домен + 443
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
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = TokenManager.getAccessToken()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }

            return chain.proceed(request)
        }
    }

    class UnauthorizedInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            var response = chain.proceed(originalRequest)

            var retryCount = 0
            while (response.code == 401 && retryCount < 2) {
                Log.d(TAG, "Получен 401, пробуем обновить токен... попытка ${retryCount + 1}")
                response.close()

                val refreshed = runBlocking { TokenManager.refreshToken() }
                if (!refreshed) {
                    Log.e(TAG, "Не удалось обновить токен")
                    break
                }

                val newToken = TokenManager.getAccessToken()
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()

                response = chain.proceed(newRequest)
                retryCount++
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
                throw e
            }
        }
    }
}