package com.example.artchat

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.artchat.model.*
import com.example.artchat.network.RetrofitClient
import com.example.artchat.utils.PreferencesManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var preferences: PreferencesManager
    private lateinit var textViewUsername: TextView
    private lateinit var textViewEmail: TextView
    private lateinit var textViewUserId: TextView
    private lateinit var textViewDisplayName: TextView
    private lateinit var textViewBio: TextView
    private lateinit var imageViewAvatar: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEditProfile: Button
    private lateinit var btnChangePassword: Button
    private lateinit var btnChangeAvatar: Button
    private lateinit var btnLogout: Button
    private lateinit var btnBack: Button
    private val gson = Gson()

    // Для загрузки аватара
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val REQUEST_CAMERA_PERMISSION = 101
    private val REQUEST_STORAGE_PERMISSION = 102
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_improved)

        preferences = PreferencesManager(this)

        // Находим элементы по ID
        textViewUsername = findViewById(R.id.textViewUsername)
        textViewEmail = findViewById(R.id.textViewEmail)
        textViewUserId = findViewById(R.id.textViewUserId)
        textViewDisplayName = findViewById(R.id.textViewDisplayName)
        textViewBio = findViewById(R.id.textViewBio)
        imageViewAvatar = findViewById(R.id.imageViewAvatar)
        progressBar = findViewById(R.id.progressBar)

        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar)
        btnLogout = findViewById(R.id.btnLogout)
        btnBack = findViewById(R.id.btnBack)

        // Проверяем, является ли пользователь гостем
        val isGuest = preferences.isGuest()

        // Загружаем данные профиля
        loadUserData()

        // Устанавливаем цвет аватара
        val avatarColor = preferences.getAvatarColor()
        try {
            imageViewAvatar.setBackgroundColor(Color.parseColor(avatarColor))
        } catch (e: Exception) {
            imageViewAvatar.setBackgroundColor(Color.parseColor("#6200EE"))
        }

        // Блокируем функции для гостя
        if (isGuest) {
            btnEditProfile.isEnabled = false
            btnEditProfile.alpha = 0.5f
            btnEditProfile.text = "Редактировать профиль (недоступно для гостя)"

            btnChangePassword.isEnabled = false
            btnChangePassword.alpha = 0.5f
            btnChangePassword.text = "Сменить пароль (недоступно для гостя)"

            btnChangeAvatar.isEnabled = false
            btnChangeAvatar.alpha = 0.5f
            btnChangeAvatar.text = "Изменить аватар (недоступно для гостя)"

            // Для гостя меняем текст кнопки выхода
            btnLogout.text = "Выйти из гостевого режима"
        }

        // Обработчики кликов
        btnEditProfile.setOnClickListener {
            if (preferences.isGuest()) {
                showGuestRestrictionDialog()
                return@setOnClickListener
            }
            showEditProfileDialog()
        }

        btnChangePassword.setOnClickListener {
            if (preferences.isGuest()) {
                showGuestRestrictionDialog()
                return@setOnClickListener
            }
            showChangePasswordDialog()
        }

        btnChangeAvatar.setOnClickListener {
            if (preferences.isGuest()) {
                showGuestRestrictionDialog()
                return@setOnClickListener
            }
            showAvatarOptionsDialog()
        }

        btnLogout.setOnClickListener {
            logoutUser()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Обновляем данные профиля при возвращении на экран
        if (!preferences.isGuest()) {
            loadUserDataFromApi()
        }
    }

    private fun loadUserData() {
        val username = preferences.getUsername() ?: "Неизвестно"
        val email = preferences.getEmail() ?: "Не указан"
        val userId = preferences.getUserId()
        val displayName = preferences.getDisplayName() ?: "Не указано"

        textViewUsername.text = "Имя пользователя: $username"
        textViewEmail.text = "Email: $email"
        textViewUserId.text = "ID пользователя: $userId"
        textViewDisplayName.text = "Отображаемое имя: $displayName"

        // Для гостя показываем специальное сообщение в поле "О себе"
        val bioText = if (preferences.isGuest()) {
            "Гостевой аккаунт. Для полного доступа к функциям зарегистрируйтесь."
        } else {
            preferences.getBio() ?: "Не указано"
        }
        textViewBio.text = "О себе: $bioText"

        // Загружаем аватар если есть URL
        val avatarUrl = preferences.getAvatarUrl()
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_person)
                .into(imageViewAvatar)
        }
    }

    private fun loadUserDataFromApi() {
        if (preferences.isGuest()) {
            return
        }

        val token = preferences.getToken() ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.getProfile("Bearer $token")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.user
                        if (user != null) {
                            // Обновляем локальные данные
                            preferences.updateProfileData(
                                username = user.username,
                                displayName = user.display_name,
                                bio = user.bio,
                                avatarColor = user.avatar_color,
                                avatarUrl = user.avatar_url
                            )

                            // Обновляем UI
                            loadUserData()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showGuestRestrictionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Функция недоступна")
            .setMessage("Эта функция недоступна для гостевого аккаунта. Зарегистрируйтесь или войдите в свой аккаунт для полного доступа к функциям профиля.")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showEditProfileDialog() {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        val etUsername = dialogView.findViewById<EditText>(R.id.etUsername)
        val etDisplayName = dialogView.findViewById<EditText>(R.id.etDisplayName)
        val etBio = dialogView.findViewById<EditText>(R.id.etBio)
        val colorPicker = dialogView.findViewById<Button>(R.id.btnColorPicker)
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)

        // Заполняем текущие данные
        etUsername.setText(preferences.getUsername())
        etDisplayName.setText(preferences.getDisplayName())
        etBio.setText(preferences.getBio() ?: "")

        // Устанавливаем текущий цвет
        val currentColor = preferences.getAvatarColor()
        colorPreview.setBackgroundColor(Color.parseColor(currentColor))
        var selectedColor = currentColor

        colorPicker.setOnClickListener {
            showColorPickerDialog { color ->
                selectedColor = color
                colorPreview.setBackgroundColor(Color.parseColor(color))
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Редактировать профиль")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newUsername = etUsername.text.toString().trim()
                val newDisplayName = etDisplayName.text.toString().trim()
                val bio = etBio.text.toString().trim()

                if (newUsername.isEmpty() || newDisplayName.isEmpty()) {
                    Toast.makeText(this, "Заполните все обязательные поля", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateProfile(newUsername, newDisplayName, bio, selectedColor)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showColorPickerDialog(onColorSelected: (String) -> Unit) {
        val colors = listOf(
            "#6200EE", "#03DAC6", "#018786", "#BB86FC", "#3700B3",
            "#03DAC5", "#018786", "#CF6679", "#FF9800", "#4CAF50",
            "#2196F3", "#F44336", "#9C27B0", "#FFEB3B", "#795548"
        )

        val colorNames = listOf(
            "Фиолетовый", "Бирюзовый", "Зеленый", "Светло-фиолетовый", "Темно-фиолетовый",
            "Светло-бирюзовый", "Темно-зеленый", "Розовый", "Оранжевый", "Зеленый",
            "Синий", "Красный", "Пурпурный", "Желтый", "Коричневый"
        )

        AlertDialog.Builder(this)
            .setTitle("Выберите цвет аватара")
            .setItems(colorNames.toTypedArray()) { _, which ->
                onColorSelected(colors[which])
            }
            .show()
    }

    private fun updateProfile(username: String, displayName: String, bio: String, avatarColor: String) {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        val token = preferences.getToken() ?: return

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = UpdateProfileRequest(
                    username = username,
                    display_name = displayName,
                    bio = bio,
                    avatar_color = avatarColor
                )

                val response = RetrofitClient.apiService.updateProfile("Bearer $token", request)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.user

                        // Обновляем локальные данные
                        preferences.updateProfileData(
                            username = user?.username ?: username,
                            displayName = user?.display_name ?: displayName,
                            bio = user?.bio ?: bio,
                            avatarColor = user?.avatar_color ?: avatarColor,
                            avatarUrl = user?.avatar_url
                        )

                        loadUserData()
                        Toast.makeText(this@ProfileActivity, "Профиль успешно обновлен", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val errorBody = response.errorBody()?.string()
                            val errorResponse = gson.fromJson(errorBody, ApiResponse::class.java)
                            val errorMessage = errorResponse?.message ?: "Ошибка обновления профиля"
                            Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Ошибка: ${response.code()} - ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@ProfileActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showChangePasswordDialog() {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        // Создаем диалоговое окно без использования XML файла
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 30, 50, 30)

        // Текущий пароль
        val etCurrentPassword = EditText(this)
        etCurrentPassword.hint = "Текущий пароль"
        etCurrentPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        etCurrentPassword.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 20
        }
        layout.addView(etCurrentPassword)

        // Новый пароль
        val etNewPassword = EditText(this)
        etNewPassword.hint = "Новый пароль"
        etNewPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        etNewPassword.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 20
        }
        layout.addView(etNewPassword)

        // Подтверждение пароля
        val etConfirmPassword = EditText(this)
        etConfirmPassword.hint = "Подтвердите новый пароль"
        etConfirmPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        etConfirmPassword.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.addView(etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("Сменить пароль")
            .setView(layout)
            .setPositiveButton("Сменить") { _, _ ->
                val currentPassword = etCurrentPassword.text.toString().trim()
                val newPassword = etNewPassword.text.toString().trim()
                val confirmPassword = etConfirmPassword.text.toString().trim()

                if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword.length < 6) {
                    Toast.makeText(this, "Пароль должен быть не менее 6 символов", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword != confirmPassword) {
                    Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                changePassword(currentPassword, newPassword, confirmPassword)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        val token = preferences.getToken() ?: return

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ChangePasswordRequest(
                    current_password = currentPassword,
                    new_password = newPassword,
                    confirm_password = confirmPassword
                )

                val response = RetrofitClient.apiService.changePassword("Bearer $token", request)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@ProfileActivity, "Пароль успешно изменен", Toast.LENGTH_SHORT).show()

                        // Если пользователь гость или нужно выйти после смены пароля
                        if (preferences.isGuest()) {
                            logoutUser()
                        }
                    } else {
                        try {
                            val errorBody = response.errorBody()?.string()
                            val errorResponse = gson.fromJson(errorBody, ApiResponse::class.java)
                            val errorMessage = errorResponse?.message ?: "Ошибка смены пароля"
                            Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Ошибка: ${response.code()} - ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@ProfileActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showAvatarOptionsDialog() {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        val options = arrayOf("Сделать фото", "Выбрать из галереи", "Отмена")

        AlertDialog.Builder(this)
            .setTitle("Изменить аватар")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickImageFromGallery()
                }
            }
            .show()
    }

    private fun takePhoto() {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile = createImageFile()
            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    photoFile
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    private fun pickImageFromGallery() {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), REQUEST_STORAGE_PERMISSION)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
            }
            return
        }

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    Toast.makeText(this, "Нужно разрешение на использование камеры", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickImageFromGallery()
                } else {
                    Toast.makeText(this, "Нужно разрешение на доступ к галерее", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    currentPhotoPath?.let { path ->
                        val file = File(path)
                        uploadAvatar(file)
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        getFileFromUri(uri)?.let { file ->
                            uploadAvatar(file)
                        }
                    }
                }
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "avatar_$timeStamp.jpg")
            FileOutputStream(file).use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun uploadAvatar(file: File) {
        if (preferences.isGuest()) {
            showGuestRestrictionDialog()
            return
        }

        val token = preferences.getToken() ?: return

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("avatar", file.name, requestFile)

                val response = RetrofitClient.apiService.uploadAvatar("Bearer $token", body)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.user

                        // Обновляем аватар
                        user?.avatar_url?.let { avatarUrl ->
                            preferences.saveAvatarUrl(avatarUrl)

                            Glide.with(this@ProfileActivity)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_person)
                                .into(imageViewAvatar)
                        }

                        Toast.makeText(this@ProfileActivity, "Аватар успешно обновлен", Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val errorBody = response.errorBody()?.string()
                            val errorResponse = gson.fromJson(errorBody, ApiResponse::class.java)
                            val errorMessage = errorResponse?.message ?: "Ошибка загрузки аватара"
                            Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Ошибка: ${response.code()} - ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@ProfileActivity,
                        "Ошибка сети: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun logoutUser() {
        val token = preferences.getToken()

        if (token != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.apiService.logout("Bearer $token")
                } catch (e: Exception) {
                    // Игнорируем ошибки при логауте
                }
            }
        }

        preferences.clear()

        val message = if (preferences.isGuest()) {
            "Вы вышли из гостевого режима"
        } else {
            "Вы вышли из системы"
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}