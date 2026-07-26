package com.example.vibic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class EditContactActivity : AppCompatActivity() {

    private lateinit var avatarText: TextView
    private lateinit var editName: EditText
    private lateinit var editNote: EditText
    private lateinit var phoneValue: TextView
    private lateinit var usernameValue: TextView

    private var chatId = ""

    companion object {
        const val EXTRA_CHAT_ID  = "chat_id"
        const val EXTRA_NAME     = "chat_name"
        const val EXTRA_PHONE    = "phone"
        const val EXTRA_USERNAME = "username"

        const val RESULT_NAME = "result_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bg)

        setContentView(R.layout.activity_edit_contact)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val phone = intent.getStringExtra(EXTRA_PHONE)
        val username = intent.getStringExtra(EXTRA_USERNAME)

        avatarText    = findViewById(R.id.avatarText)
        editName      = findViewById(R.id.editName)
        editNote      = findViewById(R.id.editNote)
        phoneValue    = findViewById(R.id.phoneValue)
        usernameValue = findViewById(R.id.usernameValue)

        editName.setText(name)
        avatarText.text = name.take(1).uppercase()

        // Пока бэкенд не отдаёт phone/username — оставляем placeholder из layout.
        // Когда данные появятся, просто заполняем значения:
        if (!phone.isNullOrBlank()) {
            phoneValue.text = phone
        }
        if (!username.isNullOrBlank()) {
            usernameValue.text = if (username.startsWith("@")) username else "@$username"
        }

        // первая буква обновляется по мере ввода имени
        editName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val t = s?.toString()?.trim().orEmpty()
                avatarText.text = if (t.isNotEmpty()) t.take(1).uppercase() else ""
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.doneButton).setOnClickListener { saveAndFinish() }

        findViewById<ImageView>(R.id.copyPhone).setOnClickListener {
            copyToClipboard("Телефон", phoneValue.text.toString())
        }

        findViewById<ImageView>(R.id.copyUsername).setOnClickListener {
            copyToClipboard("Юзернейм", usernameValue.text.toString())
        }

        findViewById<LinearLayout>(R.id.rowDelete).setOnClickListener {
            confirmDelete(name)
        }
    }

    private fun saveAndFinish() {
        val newName = editName.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }
        // TODO: отправить newName на сервер при наличии endpoint'а
        val data = Intent().apply { putExtra(RESULT_NAME, newName) }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isBlank() || text == "—") return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this, R.style.VibicAlertDialog)
            .setTitle("Удалить из контактов")
            .setMessage("Удалить $name из контактов?")
            .setPositiveButton("Удалить") { _, _ ->
                // TODO: вызвать удаление контакта на сервере
                Toast.makeText(this, "Контакт удалён", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}