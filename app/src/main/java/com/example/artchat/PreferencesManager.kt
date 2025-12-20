package com.example.artchat.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("artchat_flask", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_BIO = "bio"
        private const val KEY_TOKEN = "token"
        private const val KEY_IS_GUEST = "is_guest"
        private const val KEY_AVATAR_COLOR = "avatar_color"
        private const val KEY_AVATAR_URL = "avatar_url"
    }

    // Основной метод сохранения данных пользователя (старая сигнатура)
    fun saveUserData(
        userId: Int,
        email: String?,
        username: String,
        displayName: String,
        token: String?,
        isGuest: Boolean
    ) {
        prefs.edit().apply {
            putInt(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            putString(KEY_USERNAME, username)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_TOKEN, token)
            putBoolean(KEY_IS_GUEST, isGuest)
            apply()
        }
    }

    // Расширенный метод для сохранения полных данных пользователя
    fun saveUserDataExtended(
        userId: Int,
        email: String?,
        username: String,
        displayName: String,
        token: String?,
        isGuest: Boolean,
        bio: String? = null,
        avatarColor: String? = "#6200EE",
        avatarUrl: String? = null
    ) {
        prefs.edit().apply {
            putInt(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            putString(KEY_USERNAME, username)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_TOKEN, token)
            putBoolean(KEY_IS_GUEST, isGuest)
            bio?.let { putString(KEY_BIO, it) }
            avatarColor?.let { putString(KEY_AVATAR_COLOR, it) }
            avatarUrl?.let { putString(KEY_AVATAR_URL, it) }
            apply()
        }
    }

    // Метод для обновления только профильных данных
    fun updateProfileData(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
        avatarColor: String? = null,
        avatarUrl: String? = null
    ) {
        prefs.edit().apply {
            username?.let { putString(KEY_USERNAME, it) }
            displayName?.let { putString(KEY_DISPLAY_NAME, it) }
            bio?.let { putString(KEY_BIO, it) }
            avatarColor?.let { putString(KEY_AVATAR_COLOR, it) }
            avatarUrl?.let { putString(KEY_AVATAR_URL, it) }
            apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getUsername(): String = prefs.getString(KEY_USERNAME, "Гость") ?: "Гость"
    fun getDisplayName(): String = prefs.getString(KEY_DISPLAY_NAME, "Гость") ?: "Гость"
    fun getBio(): String? = prefs.getString(KEY_BIO, null)
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun isGuest(): Boolean = prefs.getBoolean(KEY_IS_GUEST, true)
    fun isLoggedIn(): Boolean = getUserId() != -1
    fun getAvatarColor(): String = prefs.getString(KEY_AVATAR_COLOR, "#6200EE") ?: "#6200EE"
    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    // Отдельные методы для сохранения
    fun saveBio(bio: String) {
        prefs.edit().putString(KEY_BIO, bio).apply()
    }

    fun saveAvatarUrl(avatarUrl: String) {
        prefs.edit().putString(KEY_AVATAR_URL, avatarUrl).apply()
    }

    fun saveAvatarColor(avatarColor: String) {
        prefs.edit().putString(KEY_AVATAR_COLOR, avatarColor).apply()
    }
}