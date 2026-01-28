package com.example.artchat

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.artchat.utils.PreferencesManager

class HomeActivity : AppCompatActivity() {

    private lateinit var preferences: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Используем существующий layout activity_main.xml

        preferences = PreferencesManager(this)

        // Проверяем, залогинен ли пользователь
        if (!preferences.isLoggedIn()) {
            // Если не залогинен, возвращаем на экран входа
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val tvUsername = findViewById<TextView>(R.id.tvUsername)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val tvChatHint = findViewById<TextView>(R.id.tvChatHint)
        val btnDraw = findViewById<Button>(R.id.btnDraw)
        val btnChat = findViewById<Button>(R.id.btnChat)
        val btnProfile = findViewById<Button>(R.id.btnProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnExit = findViewById<Button>(R.id.btnExit)

        // Устанавливаем имя пользователя
        val username = preferences.getDisplayName()
        tvUsername.text = "Привет, $username!"
        tvWelcome.text = "Добро пожаловать в ArtChat!"

        // Для гостя показываем подсказку
        if (preferences.isGuest()) {
            tvChatHint.text = "Для доступа к чату требуется регистрация"
            tvChatHint.visibility = android.view.View.VISIBLE
            btnChat.isEnabled = false
            btnChat.alpha = 0.5f
        } else {
            tvChatHint.visibility = android.view.View.GONE
            btnChat.isEnabled = true
            btnChat.alpha = 1f
        }

        btnDraw.setOnClickListener {
            startActivity(Intent(this, DrawingActivity::class.java))
        }

        btnChat.setOnClickListener {
            if (preferences.isGuest()) {
                Toast.makeText(this, "Для доступа к чату требуется регистрация", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, ChatActivity::class.java))
            }
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        btnExit.setOnClickListener {
            showExitDialog()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выход из аккаунта")
            .setMessage("Вы действительно хотите выйти из аккаунта?")
            .setPositiveButton("Выйти") { _, _ ->
                preferences.clear()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выход из приложения")
            .setMessage("Вы действительно хотите закрыть приложение?")
            .setPositiveButton("Да") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    override fun onBackPressed() {
        showExitDialog()
    }
}