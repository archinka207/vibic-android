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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.vibic.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_PHONES = "selected_phones"

        /**
         * БД хранит: 79991111111 (без +, 8→7)
         * Такой же формат использует ContactsFragment.phoneToDbFormat
         */
        fun phoneToDbFormat(phone: String): String {
            val digits = phone.replace(Regex("[^0-9]"), "")
            return when {
                digits.length == 11 && digits.startsWith("7") -> digits
                digits.length == 11 && digits.startsWith("8") -> "7${digits.drop(1)}"
                digits.length == 10                           -> "7$digits"
                else                                          -> digits
            }
        }
    }

    private lateinit var backButton: FrameLayout
    private lateinit var groupAvatarContainer: FrameLayout
    private lateinit var groupAvatarLetter: TextView
    private lateinit var groupAvatarImage: ImageView
    private lateinit var btnSetPhoto: TextView
    private lateinit var groupNameInput: EditText
    private lateinit var participantsCount: TextView
    private lateinit var btnCreateGroup: TextView

    private var selectedPhones: List<String> = emptyList()
    private val registeredUserIds = mutableListOf<String>()
    private var selectedAvatarUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
            groupAvatarLetter.visibility = View.GONE
            groupAvatarImage.visibility  = View.VISIBLE
            Glide.with(this).load(uri).circleCrop().into(groupAvatarImage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_group_info)

        selectedPhones = intent.getStringArrayListExtra(EXTRA_SELECTED_PHONES) ?: arrayListOf()

        backButton           = findViewById(R.id.backButton)
        groupAvatarContainer = findViewById(R.id.groupAvatarContainer)
        groupAvatarLetter    = findViewById(R.id.groupAvatarLetter)
        groupAvatarImage     = findViewById(R.id.groupAvatarImage)
        btnSetPhoto          = findViewById(R.id.btnSetPhoto)
        groupNameInput       = findViewById(R.id.groupNameInput)
        participantsCount    = findViewById(R.id.participantsCount)
        btnCreateGroup       = findViewById(R.id.btnCreateGroup)

        backButton.setOnClickListener { finish() }
        btnSetPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        groupAvatarContainer.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnCreateGroup.setOnClickListener { createGroup() }

        groupNameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val name = s?.toString()?.trim().orEmpty()
                groupAvatarLetter.text = if (name.isNotEmpty()) name.first().uppercase() else "?"
                if (groupAvatarImage.visibility != View.VISIBLE) {
                    groupAvatarLetter.visibility = View.VISIBLE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        updateCounterLabel(selectedPhones.size, checking = true)
        resolveRegisteredUsers()
    }

    private fun resolveRegisteredUsers() {
        btnCreateGroup.isEnabled = false
        btnCreateGroup.alpha     = 0.5f

        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                selectedPhones.map { phone ->
                    async {
                        try {
                            val searchPhone = phoneToDbFormat(phone)
                            if (searchPhone.isBlank()) return@async null
                            val response = ApiClient.apiService.searchUsers(searchPhone)
                            if (response.isSuccessful) response.body()?.firstOrNull()
                            else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            registeredUserIds.clear()
            registeredUserIds.addAll(found.map { it.id }.distinct())

            if (registeredUserIds.isEmpty()) {
                participantsCount.text   = "Нет зарегистрированных участников"
                btnCreateGroup.isEnabled = false
                btnCreateGroup.alpha     = 0.5f
            } else {
                updateCounterLabel(registeredUserIds.size, checking = false)
                btnCreateGroup.isEnabled = true
                btnCreateGroup.alpha     = 1f
            }
        }
    }

    private fun createGroup() {
        val name = groupNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Введите название группы", Toast.LENGTH_SHORT).show()
            return
        }
        if (registeredUserIds.isEmpty()) {
            Toast.makeText(this, "Нет зарегистрированных участников", Toast.LENGTH_SHORT).show()
            return
        }

        btnCreateGroup.isEnabled = false
        btnCreateGroup.alpha     = 0.5f

        lifecycleScope.launch {
            try {
                val request = CreateGroupChatRequest(
                    name     = name,
                    user_ids = registeredUserIds
                )
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createGroupChat(request)
                }

                if (response.isSuccessful) {
                    val chat = response.body()
                    if (chat != null) {
                        // Открываем чат без FLAG_ACTIVITY_CLEAR_TOP —
                        // стек закрывается через RESULT_OK (см. ниже)
                        val intent = Intent(this@GroupInfoActivity, ChatDetailActivity::class.java)
                        intent.putExtra("chat_id",   chat.id)
                        intent.putExtra("chat_name", chat.name ?: name)
                        startActivity(intent)

                        // RESULT_OK → CreateGroupActivity поймает и тоже вызовет finish()
                        // Стек: MainActivityApp → ChatDetailActivity  (чисто)
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        showError("Ошибка: пустой ответ сервера")
                    }
                } else {
                    showError("Ошибка создания группы (${response.code()})")
                }
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        btnCreateGroup.isEnabled = true
        btnCreateGroup.alpha     = 1f
    }

    private fun updateCounterLabel(count: Int, checking: Boolean) {
        participantsCount.text = if (checking) {
            "Проверка участников…"
        } else {
            when {
                count % 100 in 11..19 -> "$count участников выбрано"
                count % 10 == 1       -> "$count участник выбран"
                count % 10 in 2..4    -> "$count участника выбрано"
                else                  -> "$count участников выбрано"
            }
        }
    }
}