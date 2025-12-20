package com.example.artchat.network

import com.example.artchat.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // === АУТЕНТИФИКАЦИЯ ===
    @POST("register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<User>>

    @POST("guest")
    suspend fun createGuest(): Response<ApiResponse<User>>

    @POST("logout")
    suspend fun logout(@Header("Authorization") token: String): Response<ApiResponse<Any>>

    // === ПРОФИЛЬ ===
    @GET("profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ApiResponse<User>>

    @PUT("profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<User>>

    @PUT("profile/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: ChangePasswordRequest
    ): Response<ApiResponse<Any>>

    @POST("profile/avatar")
    @Multipart
    suspend fun uploadAvatar(
        @Header("Authorization") token: String,
        @Part avatar: MultipartBody.Part
    ): Response<ApiResponse<User>>

    // === ЧАТ ===
    @GET("chat/global/messages")
    suspend fun getGlobalMessages(
        @Header("Authorization") token: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<ApiResponse<List<ChatMessage>>>

    @POST("chat/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: SendMessageRequest
    ): Response<ApiResponse<Any>>

    // === ПОЛЬЗОВАТЕЛИ ===
    @GET("users/online")
    suspend fun getOnlineUsers(@Header("Authorization") token: String): Response<ApiResponse<List<User>>>

    // === ДРУЗЬЯ ===
    @GET("friends")
    suspend fun getFriends(@Header("Authorization") token: String): Response<ApiResponse<List<User>>>

    @POST("friends/add/{friend_id}")
    suspend fun addFriend(
        @Header("Authorization") token: String,
        @Path("friend_id") friendId: Int
    ): Response<ApiResponse<Any>>

    // === ПРОВЕРКА ===
    @GET("health")
    suspend fun healthCheck(): Response<ApiResponse<Any>>
}