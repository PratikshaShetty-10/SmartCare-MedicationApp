package com.yourteam.smartcare.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        // We can safely set the timeout to 15 seconds here
        .connectTimeout(15000L, TimeUnit.MILLISECONDS)
        .readTimeout(15000L, TimeUnit.MILLISECONDS)
        .build()

    val apiService: SmartCareApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://orange-cod-r4vx9g9r5gwwhpjg9-5000.app.github.dev/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmartCareApiService::class.java)
    }
}