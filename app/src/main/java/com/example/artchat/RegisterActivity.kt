package com.example.artchat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.artchat.databinding.ActivityRegisterBinding
import com.example.artchat.model.ApiResponse
import com.example.artchat.model.RegisterRequest
import com.example.artchat.model.User
import com.example.artchat.network.RetrofitClient
import com.example.artchat.utils.PreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val displayName = binding.etDisplayName.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (validateInput(email, username, displayName, password, confirmPassword)) {
                registerUser(email, username, displayName, password)
            }
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateInput(
        email: String,
        username: String,
        displayName: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            binding.etEmail.error = "Введите email"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Некорректный email"
            isValid = false
        }

        if (username.isEmpty()) {
            binding.etUsername.error = "Введите имя пользователя"
            isValid = false
        } else if (username.length < 3) {
            binding.etUsername.error = "Имя должно быть не менее 3 символов"
            isValid = false
        }

        if (displayName.isEmpty()) {
            binding.etDisplayName.error = "Введите отображаемое имя"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Введите пароль"
            isValid = false
        } else if (password.length < 6) {
            binding.etPassword.error = "Пароль должен быть не менее 6 символов"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Подтвердите пароль"
            isValid = false
        } else if (confirmPassword != password) {
            binding.etConfirmPassword.error = "Пароли не совпадают"
            isValid = false
        }

        return isValid
    }

    private fun registerUser(
        email: String,
        username: String,
        displayName: String,
        password: String
    ) {
        binding.btnRegister.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response<ApiResponse<User>> = RetrofitClient.apiService.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        username = username,
                        display_name = displayName
                    )
                )

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnRegister.isEnabled = true

                    if (response.isSuccessful) {
                        val registerResponse = response.body()

                        if (registerResponse?.success == true) {
                            // Save user data using extended method
                            val preferences = PreferencesManager(this@RegisterActivity)
                            preferences.saveUserDataExtended(
                                userId = registerResponse.user?.id ?: -1,
                                email = registerResponse.user?.email ?: email,
                                username = registerResponse.user?.username ?: username,
                                displayName = registerResponse.user?.display_name ?: displayName,
                                token = registerResponse.token,
                                isGuest = false,
                                bio = registerResponse.user?.bio,
                                avatarColor = registerResponse.user?.avatar_color,
                                avatarUrl = registerResponse.user?.avatar_url
                            )

                            Toast.makeText(
                                this@RegisterActivity,
                                "Регистрация успешна!",
                                Toast.LENGTH_LONG
                            ).show()

                            // После регистрации переходим в ГЛАВНОЕ МЕНЮ
                            startActivity(Intent(this@RegisterActivity, HomeActivity::class.java))
                            finish()
                        } else {
                            val errorMessage = registerResponse?.message ?: "Ошибка регистрации"
                            Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        try {
                            val errorBody = response.errorBody()?.string()
                            val errorResponse = gson.fromJson(errorBody, ApiResponse::class.java)
                            val errorMessage = errorResponse?.message ?: "Ошибка сервера"
                            Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@RegisterActivity,
                                "Ошибка: ${response.code()} - ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(
                        this@RegisterActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }
}