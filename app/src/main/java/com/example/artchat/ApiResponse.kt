package com.example.artchat.model

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val token: String? = null,
    val user: T? = null,
    val messages: List<T>? = null,
    val users: List<T>? = null
)