package com.example.artchat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.artchat.databinding.ActivityMainBinding
import com.example.artchat.utils.PreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)

        // Display user info
        val username = preferences.getUsername()
        val isGuest = preferences.isGuest()

        binding.tvUsername.text = username
        if (isGuest) {
            binding.tvChatHint.visibility = android.view.View.VISIBLE
            binding.tvWelcome.text = "Гостевой режим"
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Drawing button
        binding.btnDraw.setOnClickListener {
            startActivity(Intent(this, DrawingActivity::class.java))
        }

        // Chat button
        binding.btnChat.setOnClickListener {
            if (preferences.isGuest()) {
                Toast.makeText(this, "Для чата требуется регистрация", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, ChatActivity::class.java))
            }
        }

        // Profile button
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            preferences.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Exit button
        binding.btnExit.setOnClickListener {
            finishAffinity()
        }

    }
}