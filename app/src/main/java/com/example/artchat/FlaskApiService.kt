package com.example.artchat.network

import com.example.artchat.model.*
import retrofit2.Response
import retrofit2.http.*

interface FlaskApiService {

    // === АУТЕНТИФИКАЦИЯ ===

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<User>>

    @POST("api/guest")
    suspend fun createGuest(): Response<ApiResponse<User>>

    // === ЧАТ ===

    @GET("api/chat/global/messages")
    suspend fun getGlobalMessages(@Query("limit") limit: Int = 100): Response<ApiResponse<ChatMessage>>

    @POST("api/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<ApiResponse<Any>>

    // === ПОЛЬЗОВАТЕЛИ ===

    @GET("api/users/online")
    suspend fun getOnlineUsers(): Response<ApiResponse<User>>

    // === ДРУЗЬЯ ===

    @GET("api/friends")
    suspend fun getFriends(): Response<ApiResponse<User>>

    @POST("api/friends/add/{friend_id}")
    suspend fun addFriend(@Path("friend_id") friendId: Int): Response<ApiResponse<Any>>

    // === ПРОВЕРКА ===

    @GET("api/health")
    suspend fun healthCheck(): Response<ApiResponse<Any>>
}