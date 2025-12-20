package com.example.artchat

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.artchat.databinding.ActivityLoginBinding
import com.example.artchat.model.ApiResponse
import com.example.artchat.model.LoginRequest
import com.example.artchat.model.User
import com.example.artchat.network.RetrofitClient
import com.example.artchat.utils.PreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferences: PreferencesManager
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()

        binding.etEmail.apply {
            hint = "Email"
            setHintTextColor(ContextCompat.getColor(this@LoginActivity, R.color.purple_500))
            setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.black))
        }

        binding.etPassword.apply {
            hint = "Пароль"
            setHintTextColor(ContextCompat.getColor(this@LoginActivity, R.color.purple_500))
            setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.black))
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInput(email, password)) {
                loginUser(email, password)
            }
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.btnGuest.setOnClickListener {
            createGuestAccount()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            binding.etEmail.error = "Введите email"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Введите пароль"
            isValid = false
        } else if (password.length < 6) {
            binding.etPassword.error = "Пароль должен быть не менее 6 символов"
            isValid = false
        }

        return isValid
    }

    private fun loginUser(email: String, password: String) {
        binding.btnLogin.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.login(LoginRequest(email, password))

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnLogin.isEnabled = true

                    if (response.isSuccessful && response.body()?.success == true) {
                        val loginResponse = response.body()!!

                        // Save user data using extended method
                        preferences.saveUserDataExtended(
                            userId = loginResponse.user?.id ?: -1,
                            email = loginResponse.user?.email ?: email,
                            username = loginResponse.user?.username ?: "User",
                            displayName = loginResponse.user?.display_name ?: "User",
                            token = loginResponse.token,
                            isGuest = false,
                            bio = loginResponse.user?.bio,
                            avatarColor = loginResponse.user?.avatar_color,
                            avatarUrl = loginResponse.user?.avatar_url
                        )

                        // Start main activity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        try {
                            val errorBody = response.errorBody()?.string()
                            val errorResponse = gson.fromJson(errorBody, ApiResponse::class.java)
                            val errorMessage = errorResponse?.message ?: "Ошибка входа"
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@LoginActivity,
                                "Ошибка: ${response.code()} - ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(
                        this@LoginActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createGuestAccount() {
        binding.btnGuest.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.createGuest()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnGuest.isEnabled = true

                    if (response.isSuccessful && response.body()?.success == true) {
                        val guestResponse = response.body()!!

                        // Save guest data using extended method
                        preferences.saveUserDataExtended(
                            userId = guestResponse.user?.id ?: -1,
                            email = null,
                            username = guestResponse.user?.username ?: "Guest",
                            displayName = guestResponse.user?.display_name ?: "Guest",
                            token = guestResponse.token,
                            isGuest = true,
                            bio = guestResponse.user?.bio,
                            avatarColor = guestResponse.user?.avatar_color,
                            avatarUrl = guestResponse.user?.avatar_url
                        )

                        // Start main activity
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        try {
                            val errorBody = response.errorBody()?.string()
                            val errorResponse = gson.fromJson(errorBody, ApiResponse::class.java)
                            val errorMessage = errorResponse?.message ?: "Ошибка создания гостя"
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@LoginActivity,
                                "Ошибка: ${response.code()} - ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnGuest.isEnabled = true
                    Toast.makeText(
                        this@LoginActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }
}