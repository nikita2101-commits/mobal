package com.example.artchat.model

data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String,
    val confirm_password: String
)