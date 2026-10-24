package com.womensafety.data.api

import android.content.Context
import com.womensafety.utils.SharedPrefManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Do not attach Authorization header to unauthenticated public auth endpoints
        val isAuthEndpoint = path.contains("/api/auth/login") ||
                path.contains("/api/auth/register") ||
                path.contains("/api/auth/otp/") ||
                path.contains("/api/auth/password/reset")

        val token = SharedPrefManager.getInstance(context).getAccessToken()

        return if (!isAuthEndpoint && !token.isNullOrEmpty() && !token.startsWith("demo_")) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
