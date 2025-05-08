package com.example.businesscare.data.network

import com.example.businesscare.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getToken()

        if (originalRequest.url.encodedPath.endsWith("/auth/login")) {
            return chain.proceed(originalRequest)
        }

        if (token != null) {
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            return chain.proceed(newRequest)
        }
        return chain.proceed(originalRequest)
    }
}