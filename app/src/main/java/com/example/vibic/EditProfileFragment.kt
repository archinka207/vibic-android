package com.example.vibic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import com.example.vibic.network.UpdateProfileRequest
import com.example.vibic.network.WebSocketManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Полноэкранный редактор профиля.
 *
 * Поля:
 *  - Имя       → first_name на бэке.
 *  - Юзернейм  → пока хранится только локально (в app_prefs/user_username),
 *                бэк username не поддерживает (на момент написания).
 *  - О себе    → status на бэке.
 *
 * Аватарка хранится в filesDir/avatars/avatar_<ts>.jpg и записывается путём
 * в app_prefs/avatar_path. Те же ключи использует ProfileFragment, поэтому
 * после смены аватара тут он сразу виден на главном экране профиля.
 */
class EditProfileFragment : Fragment() {

    private lateinit var btnBack: ImageView
    private lateinit var btnDone: TextView
    private lateinit var avatarCard: CardView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarText: TextView
    private lateinit var avatarEditBadge: CardView
    private lateinit var inputFirstName: EditText
    private lateinit var inputUsername: EditText
    private lateinit var inputBio: EditText
    private lateinit var usernamePreview: TextView
    private lateinit var bioCounter: TextView
    private lateinit var logoutButton: LinearLayout

    // ── Лаунчеры выбора аватара ───────────────────────────────────────────────

    private val takeImageLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            saveAvatarBitmap(it)
            loadAvatar()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            saveAvatarFromUri(it)
            loadAvatar()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takeImageLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Нет прав на камеру", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Жизненный цикл ────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_edit_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Учитываем системные инсеты, чтобы статус-бар не накрывал шапку.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        btnBack          = view.findViewById(R.id.btnBack)
        btnDone          = view.findViewById(R.id.btnDone)
        avatarCard       = view.findViewById(R.id.editAvatarCard)
        avatarImage      = view.findViewById(R.id.editAvatarImage)
        avatarText       = view.findViewById(R.id.editAvatarText)
        avatarEditBadge  = view.findViewById(R.id.avatarEditBadge)
        inputFirstName   = view.findViewById(R.id.inputFirstName)
        inputUsername    = view.findViewById(R.id.inputUsername)
        inputBio         = view.findViewById(R.id.inputBio)
        usernamePreview  = view.findViewById(R.id.usernamePreview)
        bioCounter       = view.findViewById(R.id.bioCounter)
        logoutButton     = view.findViewById(R.id.logoutButton)

        loadFromPrefs()
        setupListeners()
    }

    // ── Загрузка значений ─────────────────────────────────────────────────────

    private fun loadFromPrefs() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val firstName = prefs.getString("user_first_name", null).orEmpty()
        val lastName  = prefs.getString("user_last_name", null).orEmpty()
        val username  = prefs.getString("user_username", null).orEmpty()
        val status    = prefs.getString("user_status", null).orEmpty()

        // Поле "Имя" — склеиваем имя+фамилию.
        val combinedName = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        inputFirstName.setText(combinedName)

        // Юзернейм без префикса "@"
        inputUsername.setText(username.removePrefix("@"))
        updateUsernamePreview(inputUsername.text.toString())

        inputBio.setText(status)
        updateBioCounter(status.length)

        loadAvatar()
    }

    private fun loadAvatar() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val path = prefs.getString("avatar_path", null)

        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    avatarImage.setImageBitmap(bmp)
                    avatarImage.visibility = View.VISIBLE
                    avatarText.visibility = View.GONE
                    return
                }
            } else {
                prefs.edit().remove("avatar_path").apply()
            }
        }

        val initial = inputFirstName.text.toString().trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: prefs.getString("user_nickname", "?").orEmpty().trim()
                .firstOrNull()?.uppercaseChar()?.toString()
            ?: "?"
        avatarText.text = initial
        avatarText.visibility = View.VISIBLE
        avatarImage.visibility = View.GONE
    }

    // ── Слушатели ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        // ВАЖНО: открыли экран как Activity → закрываем именно её, никакого back-stack-а тут нет.
        btnBack.setOnClickListener {
            hideKeyboard()
            activity?.finish()
        }

        btnDone.setOnClickListener {
            saveProfile()
        }

        logoutButton.setOnClickListener {
            hideKeyboard()
            showLogoutConfirmDialog()
        }

        // Юзернейм → превью "vibic.app/..."
        inputUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateUsernamePreview(s?.toString().orEmpty())
            }
        })

        // Счётчик "о себе"
        inputBio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateBioCounter(s?.length ?: 0)
            }
        })

        // Меняется имя — обновляем буквы в аватаре, если картинка не загружена.
        inputFirstName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (avatarImage.visibility != View.VISIBLE) {
                    val initial = s?.toString()?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    avatarText.text = initial
                }
            }
        })

        // Клик по аватару и по карандашу — открывает диалог выбора источника.
        val avatarTapHandler = View.OnClickListener { showImageSourceDialog() }
        avatarCard.setOnClickListener(avatarTapHandler)
        avatarEditBadge.setOnClickListener(avatarTapHandler)
    }

    // ── Выход из аккаунта ─────────────────────────────────────────────────────

    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(requireContext(), R.style.VibicAlertDialog)
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { _, _ -> logout() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun logout() {
        PushTokenSyncManager.deleteCurrentTokenFromBackend()

        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        TokenManager.clearTokens()
        WebSocketManager.shared.disconnect()

        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    // ── Превью/счётчик ────────────────────────────────────────────────────────

    private fun updateUsernamePreview(raw: String) {
        val clean = raw.trim().removePrefix("@")
        usernamePreview.text = if (clean.isEmpty()) "" else "vibic.app/$clean"
    }

    private fun updateBioCounter(length: Int) {
        bioCounter.text = (70 - length).toString()
    }

    // ── Аватар ────────────────────────────────────────────────────────────────

    private fun showImageSourceDialog() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val avatarPath = prefs.getString("avatar_path", null)
        val hasAvatar = avatarPath != null && File(avatarPath).exists()

        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.sheet_photo_source, null)
        dialog.setContentView(view)

        val btnDeletePhoto = view.findViewById<View>(R.id.btnDeletePhoto)
        btnDeletePhoto.visibility = if (hasAvatar) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.tilePhotoCamera).setOnClickListener {
            checkCameraPermissionAndLaunch()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.tilePhotoGallery).setOnClickListener {
            pickImageLauncher.launch("image/*")
            dialog.dismiss()
        }
        btnDeletePhoto.setOnClickListener {
            deleteAvatar()
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnClosePhoto).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndLaunch() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) takeImageLauncher.launch(null)
        else requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun saveAvatarBitmap(bitmap: Bitmap) {
        try {
            val dir = File(requireContext().filesDir, "avatars")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            // Удаляем старый файл, если был — чтобы не копились
            prefs.getString("avatar_path", null)?.let { oldPath ->
                if (oldPath != file.absolutePath) {
                    runCatching { File(oldPath).delete() }
                }
            }
            prefs.edit().putString("avatar_path", file.absolutePath).apply()
        } catch (e: Exception) {
            Log.e("EditProfileFragment", "Ошибка сохранения аватарки", e)
            Toast.makeText(requireContext(), "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAvatarFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                saveAvatarBitmap(bitmap)
            } else {
                Toast.makeText(requireContext(), "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAvatar() {
        try {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.getString("avatar_path", null)?.let { path ->
                runCatching { File(path).delete() }
            }
            prefs.edit().remove("avatar_path").apply()
            loadAvatar()
            Toast.makeText(requireContext(), "Фото удалено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Сохранение ────────────────────────────────────────────────────────────

    private fun saveProfile() {
        val nameRaw = inputFirstName.text.toString().trim()
        val usernameRaw = inputUsername.text.toString().trim().removePrefix("@")
        val bio = inputBio.text.toString().trim()

        if (nameRaw.isEmpty()) {
            Toast.makeText(requireContext(), "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }

        // Имя → first_name + last_name
        val parts = nameRaw.split("\\s+".toRegex(), limit = 2)
        val firstName = parts.getOrNull(0).orEmpty()
        val lastName  = parts.getOrNull(1)

        // 1) Локальный prefs — мгновенно. ВАЖНО: юзернейм пишем в user_username
        //    (его читает ProfileFragment), а в user_nickname кладём полное имя
        //    для совместимости со старой логикой (initials, и т.п.).
        val appContext = requireContext().applicationContext
        val prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_first_name", firstName)
            .putString("user_last_name", lastName)
            .putString("user_nickname", nameRaw)        // full name для UI
            .putString("user_username", usernameRaw)    // именно юзернейм @osound
            .putString("user_status", bio)
            .apply()

        // 2) Закрываем экран сразу.
        hideKeyboard()
        activity?.finish()

        // 3) Сервер — в фоне.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val resp = ApiClient.apiService.updateProfile(
                    UpdateProfileRequest(
                        firstName = firstName,
                        lastName  = lastName,
                        status    = bio
                    )
                )
                if (!resp.isSuccessful) {
                    Log.e("EditProfileFragment", "updateProfile failed: ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "Ошибка сохранения профиля", e)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = view?.findFocus() ?: return
        imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }
}