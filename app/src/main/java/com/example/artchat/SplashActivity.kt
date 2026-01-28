package com.example.artchat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.artchat.utils.PreferencesManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash) // Убедитесь, что этот файл существует

        Handler(Looper.getMainLooper()).postDelayed({
            val preferences = PreferencesManager(this)

            // Проверяем, залогинен ли пользователь
            if (preferences.isLoggedIn()) {
                // Если залогинен - идем в главное меню (HomeActivity)
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                // Если не залогинен - идем на экран входа
                startActivity(Intent(this, LoginActivity::class.java))
            }

            finish() // Закрываем сплеш-экран
        }, 2000)
    }
}