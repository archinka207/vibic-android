package com.example.vibic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

    private lateinit var avatarCard: CardView
    private lateinit var avatarText: TextView
    private lateinit var avatarImage: ImageView
    private lateinit var nicknameText: TextView
    private lateinit var phoneText: TextView
    private lateinit var logoutButton: LinearLayout
    private lateinit var rootView: View

    private val takeImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            saveBitmapToFile(it)
            loadSavedAvatar()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            saveImageFromUri(it)
            loadSavedAvatar()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takeImageLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Нет прав на камеру", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGradientBackground()

        avatarCard = view.findViewById(R.id.avatarCard)
        avatarText = view.findViewById(R.id.avatarText)
        avatarImage = view.findViewById(R.id.avatarImage)
        nicknameText = view.findViewById(R.id.nicknameInput)
        phoneText = view.findViewById(R.id.phoneNumberText)
        logoutButton = view.findViewById(R.id.logoutButton)

        // Сначала показываем локальные данные (мгновенно)
        loadLocalUserData()

        // Затем загружаем актуальные с сервера в фоне
        loadUserProfileInBackground()

        avatarCard.setOnClickListener { showImageSourceDialog() }
        logoutButton.setOnClickListener { showLogoutConfirmDialog() }
    }

    private fun setupGradientBackground() {
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#0D001A"),
                Color.parseColor("#1A0526"),
                Color.parseColor("#260D33")
            )
        )
        rootView.background = gradientDrawable
    }

    private fun loadLocalUserData() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val name = prefs.getString("user_nickname", null)
        val firstName = prefs.getString("user_first_name", null)
        val lastName = prefs.getString("user_last_name", null)
        val phone = TokenManager.getPhone() ?: prefs.getString("user_phone", null)

        // Формируем имя
        val fullName = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            !lastName.isNullOrBlank() -> lastName
            !name.isNullOrBlank() -> name
            else -> null
        }

        if (fullName != null) {
            nicknameText.text = fullName
            avatarText.text = fullName.take(1).uppercase()
        } else {
            nicknameText.text = "Пользователь"
            avatarText.text = "А"
        }

        phoneText.text = formatPhoneNumber(phone ?: TokenManager.getPhone() ?: "+7")

        Log.d("ProfileFragment", "Загружены локальные данные: name=$fullName, phone=$phone")

        loadSavedAvatar()
    }

    private fun loadUserProfileInBackground() {
        lifecycleScope.launch {
            try {
                Log.d("ProfileFragment", "Фоновая загрузка профиля...")
                val response = ApiClient.apiService.getCurrentUser()

                if (response.isSuccessful()) {
                    val user = response.body()
                    Log.d("ProfileFragment", "User data: $user")

                    if (user != null) {
                        val fullName = buildFullName(user.first_name, user.last_name)

                        // Сохраняем в SharedPreferences для кэша
                        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("user_nickname", fullName)
                            putString("user_first_name", user.first_name)
                            putString("user_last_name", user.last_name)
                            user.phone?.let { putString("user_phone", it) }
                            apply()
                        }

                        // Обновляем UI если имя изменилось
                        requireActivity().runOnUiThread {
                            nicknameText.text = fullName
                            phoneText.text = formatPhoneNumber(user.phone ?: TokenManager.getPhone())

                            // Обновляем аватарку если нет фото
                            if (avatarImage.visibility != View.VISIBLE) {
                                avatarText.text = fullName.take(1).uppercase()
                            }
                        }

                        Log.d("ProfileFragment", "✅ Профиль обновлен: $fullName")
                    }
                } else {
                    Log.e("ProfileFragment", "Ошибка загрузки профиля: ${response.code()}")
                    when (response.code()) {
                        401 -> {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Сессия истекла, войдите заново", Toast.LENGTH_SHORT).show()
                                logout()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Ошибка фоновой загрузки", e)
            }
        }
    }

    private fun buildFullName(firstName: String?, lastName: String?): String {
        return when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            !lastName.isNullOrBlank() -> lastName
            else -> "Пользователь"
        }
    }

    private fun showImageSourceDialog() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val avatarPath = prefs.getString("avatar_path", null)
        val hasAvatar = avatarPath != null && File(avatarPath).exists()

        val options = if (hasAvatar) {
            arrayOf("Сделать фото", "Выбрать из галереи", "Удалить фото", "Отмена")
        } else {
            arrayOf("Сделать фото", "Выбрать из галереи", "Отмена")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Аватарка")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> pickImageLauncher.launch("image/*")
                    2 -> {
                        if (hasAvatar) {
                            showDeleteConfirmDialog()
                        }
                    }
                }
            }
            .show()
    }

    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { _, _ ->
                logout()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            takeImageLauncher.launch(null)
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить фото")
            .setMessage("Вы уверены, что хотите удалить аватарку?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteAvatar()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteAvatar() {
        try {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val path = prefs.getString("avatar_path", null)

            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }

            prefs.edit().remove("avatar_path").apply()

            avatarText.visibility = View.VISIBLE
            avatarImage.visibility = View.GONE

            val name = prefs.getString("user_nickname", "А")
            avatarText.text = name?.take(1)?.uppercase() ?: "А"

            Toast.makeText(requireContext(), "Фото удалено", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка при удалении", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap) {
        try {
            val dir = File(requireContext().filesDir, "avatars")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val filename = "avatar_${System.currentTimeMillis()}.jpg"
            val file = File(dir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("avatar_path", file.absolutePath).apply()

            // Обновляем аватарку в UI
            avatarImage.setImageBitmap(bitmap)
            avatarText.visibility = View.GONE
            avatarImage.visibility = View.VISIBLE

            Toast.makeText(requireContext(), "Фото сохранено", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                saveBitmapToFile(bitmap)
            } else {
                Toast.makeText(requireContext(), "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedAvatar() {
        try {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val path = prefs.getString("avatar_path", null)

            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    avatarImage.setImageBitmap(bitmap)
                    avatarText.visibility = View.GONE
                    avatarImage.visibility = View.VISIBLE
                    return
                } else {
                    prefs.edit().remove("avatar_path").apply()
                }
            }

            showInitialsAvatar()
        } catch (e: Exception) {
            e.printStackTrace()
            showInitialsAvatar()
        }
    }

    private fun showInitialsAvatar() {
        avatarText.visibility = View.VISIBLE
        avatarImage.visibility = View.GONE
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("user_nickname", "А")
        avatarText.text = name?.take(1)?.uppercase() ?: "А"
    }

    private fun formatPhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank()) return "+7 (999) 123-45-67"

        val digits = phone.filter { it.isDigit() }

        return when (digits.length) {
            11 -> {
                val code = digits.drop(1).take(3)
                val first = digits.drop(4).take(3)
                val second = digits.drop(7).take(2)
                val third = digits.drop(9).take(2)
                "+7 ($code) $first-$second-$third"
            }
            10 -> {
                val code = digits.take(3)
                val first = digits.drop(3).take(3)
                val second = digits.drop(6).take(2)
                val third = digits.drop(8).take(2)
                "+7 ($code) $first-$second-$third"
            }
            else -> {
                if (phone.startsWith("+")) phone else "+$phone"
            }
        }
    }

    private fun logout() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        TokenManager.clearTokens()
        WebSocketManager.shared.disconnect()

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}