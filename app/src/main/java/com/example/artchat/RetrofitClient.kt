package com.example.artchat.network

import com.example.artchat.utils.Config
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(Config.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(Config.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(Config.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(Config.getApiUrl())
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: FlaskApiService = retrofit.create(FlaskApiService::class.java)
}