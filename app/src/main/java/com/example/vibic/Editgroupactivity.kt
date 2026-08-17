package com.example.vibic

import android.content.Intent
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vibic.network.ApiClient
import com.example.vibic.network.TokenManager
import com.example.vibic.network.UpdateChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Экран редактирования группы (открывается по карандашу из GroupProfileActivity).
 *
 * Бэкенд PATCH api/chats/{chatId} умеет обновлять: name, description, avatar_url
 * (только owner/admin). Аватар грузится через api/media/upload → получаем URL.
 * Удаление DELETE api/chats/{chatId} — только owner.
 *
 * "История чата для новых" сервер не хранит — сохраняется локально.
 *
 * Extras:
 *   chat_id — id группы (обязателен)
 *   chat_name — имя (для предзаполнения)
 *
 * Возвращает RESULT_OK, если что-то изменилось (профиль обновит данные).
 */
class EditGroupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"
    }

    private lateinit var backButton: ImageView
    private lateinit var btnDone: TextView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarGroupIcon: ImageView
    private lateinit var changePhoto: TextView
    private lateinit var nameInput: EditText
    private lateinit var nameCounter: TextView
    private lateinit var descriptionInput: EditText
    private lateinit var switchHistory: IosSwitch
    private lateinit var rowDeleteGroup: LinearLayout

    private var chatId = ""
    private var chatName = ""
    private var myRole = ""

    // Загруженный URL нового аватара (если фото меняли)
    private var pendingAvatarUrl: String? = null
    private var isSaving = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) uploadAvatar(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_edit_group)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        chatName = intent.getStringExtra(EXTRA_CHAT_NAME) ?: ""
        if (chatId.isBlank()) {
            Toast.makeText(this, "Ошибка: чат не передан", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        bind()
        setupListeners()
        loadChat()
    }

    private fun bind() {
        backButton = findViewById(R.id.backButton)
        btnDone = findViewById(R.id.btnDone)
        avatarImage = findViewById(R.id.avatarImage)
        avatarGroupIcon = findViewById(R.id.avatarGroupIcon)
        changePhoto = findViewById(R.id.changePhoto)
        nameInput = findViewById(R.id.nameInput)
        nameCounter = findViewById(R.id.nameCounter)
        descriptionInput = findViewById(R.id.descriptionInput)
        switchHistory = findViewById(R.id.switchHistory)
        rowDeleteGroup = findViewById(R.id.rowDeleteGroup)

        // Предзаполнение имени из intent + счётчик
        nameInput.setText(chatName)
        updateNameCounter()

        // История чата для новых — локальная настройка
        switchHistory.isChecked = getSharedPreferences("group_edit_prefs", MODE_PRIVATE)
            .getBoolean("history_$chatId", false)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        btnDone.setOnClickListener { save() }

        val pick = View.OnClickListener { pickImageLauncher.launch("image/*") }
        changePhoto.setOnClickListener(pick)
        avatarImage.setOnClickListener(pick)
        avatarGroupIcon.setOnClickListener(pick)

        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateNameCounter() }
            override fun afterTextChanged(s: Editable?) {}
        })

        switchHistory.setOnCheckedChangeListener { checked ->
            getSharedPreferences("group_edit_prefs", MODE_PRIVATE)
                .edit().putBoolean("history_$chatId", checked).apply()
        }

        rowDeleteGroup.setOnClickListener { confirmDelete() }
    }

    private fun updateNameCounter() {
        val len = nameInput.text?.length ?: 0
        nameCounter.text = (100 - len).toString()
    }

    private fun loadChat() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.getChat(chatId)
                if (resp.isSuccessful) {
                    val chat = resp.body() ?: return@launch
                    nameInput.setText(chat.name ?: chatName)
                    nameInput.setSelection(nameInput.text.length)
                    updateNameCounter()
                    descriptionInput.setText(chat.description ?: "")

                    if (!chat.avatarUrl.isNullOrBlank()) {
                        avatarImage.visibility = View.VISIBLE
                        avatarGroupIcon.visibility = View.GONE
                        Glide.with(this@EditGroupActivity)
                            .load(chat.avatarUrl).circleCrop().into(avatarImage)
                    }
                }

                // Роль (удаление доступно только owner)
                val pResp = ApiClient.apiService.getParticipants(chatId, limit = 200, offset = 0)
                if (pResp.isSuccessful) {
                    val me = TokenManager.getUserId()
                    myRole = pResp.body()?.firstOrNull { it.userId == me }?.role?.lowercase() ?: ""
                    rowDeleteGroup.visibility = if (myRole == "owner") View.VISIBLE else View.GONE
                }
            } catch (_: Exception) {
                // оставляем то, что есть из intent
            }
        }
    }

    private fun uploadAvatar(uri: Uri) {
        Toast.makeText(this, "Загрузка фото…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyUriToFile(uri) } ?: return@launch
                val typePart = "image".toRequestBody("text/plain".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val up = ApiClient.apiService.uploadMedia(typePart, filePart)
                if (up.isSuccessful) {
                    val url = up.body()?.url
                    if (!url.isNullOrBlank()) {
                        pendingAvatarUrl = url
                        avatarImage.visibility = View.VISIBLE
                        avatarGroupIcon.visibility = View.GONE
                        Glide.with(this@EditGroupActivity).load(url).circleCrop().into(avatarImage)
                    }
                } else {
                    Toast.makeText(this@EditGroupActivity, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
                }
                file.delete()
            } catch (e: Exception) {
                Toast.makeText(this@EditGroupActivity, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "group_avatar_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out -> input.copyTo(out) }
            input.close()
            file
        } catch (_: Exception) { null }
    }

    private fun save() {
        if (isSaving) return
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }
        val description = descriptionInput.text.toString().trim()

        isSaving = true
        btnDone.isEnabled = false
        btnDone.alpha = 0.5f

        lifecycleScope.launch {
            try {
                val req = UpdateChatRequest(
                    name = name,
                    description = description,
                    avatarUrl = pendingAvatarUrl // null → поле не меняется
                )
                val resp = ApiClient.apiService.updateChat(chatId, req)
                if (resp.isSuccessful) {
                    Toast.makeText(this@EditGroupActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val text = if (resp.code() == 403) "Недостаточно прав"
                    else "Не удалось сохранить (${resp.code()})"
                    Toast.makeText(this@EditGroupActivity, text, Toast.LENGTH_SHORT).show()
                    isSaving = false
                    btnDone.isEnabled = true
                    btnDone.alpha = 1f
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditGroupActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
                isSaving = false
                btnDone.isEnabled = true
                btnDone.alpha = 1f
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Удалить группу")
            .setMessage("Группа будет удалена навсегда для всех участников. Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ -> deleteGroup() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteGroup() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.deleteChat(chatId)
                if (resp.isSuccessful) {
                    Toast.makeText(this@EditGroupActivity, "Группа удалена", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@EditGroupActivity, MainActivityApp::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                } else {
                    val text = if (resp.code() == 403) "Удалять может только владелец"
                    else "Не удалось удалить (${resp.code()})"
                    Toast.makeText(this@EditGroupActivity, text, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditGroupActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
            }
        }
    }
}