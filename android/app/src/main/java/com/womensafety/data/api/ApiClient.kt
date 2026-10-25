package com.womensafety.data.api

import android.content.Context
import com.womensafety.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var apiService: ApiService? = null

    @Volatile
    private var currentBaseUrl: String? = null

    fun getService(context: Context): ApiService {
        val targetBaseUrl = Constants.getBaseUrl(context)
        val existing = apiService
        if (existing != null && currentBaseUrl == targetBaseUrl) {
            return existing
        }

        return synchronized(this) {
            val doubleCheck = apiService
            if (doubleCheck != null && currentBaseUrl == targetBaseUrl) {
                doubleCheck
            } else {
                currentBaseUrl = targetBaseUrl
                buildRetrofit(context, targetBaseUrl).create(ApiService::class.java).also {
                    apiService = it
                }
            }
        }
    }

    fun reset() {
        synchronized(this) {
            apiService = null
            currentBaseUrl = null
        }
    }

    private fun buildRetrofit(context: Context, baseUrl: String): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

