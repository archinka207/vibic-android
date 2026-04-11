package com.example.vibic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NicknameActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var nicknameInput: EditText
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nickname)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        authRepository = AuthRepository(this)

        nicknameInput = findViewById(R.id.nicknameInput)
        continueButton = findViewById(R.id.continueButton)

        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
        val code = intent.getStringExtra("CODE")

        continueButton.setOnClickListener {
            val nickname = nicknameInput.text.toString()

            if (nickname.isNotEmpty()) {
                // Отправляем регистрацию на бэк
                lifecycleScope.launch {
                    registerUser(phoneNumber ?: "", nickname)
                }
            } else {
                Toast.makeText(this, "Введите никнейм", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun registerUser(phone: String, name: String) {
        val result = authRepository.register(phone, name)

        result.onSuccess { response ->
            runOnUiThread {
                Toast.makeText(this, "Регистрация успешна!", Toast.LENGTH_SHORT).show()

                // Сохраняем данные
                val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("user_nickname", name)
                    putString("user_phone", phone)
                    putBoolean("is_logged_in", true)
                    apply()
                }

                // Переходим на главный экран
                val intent = Intent(this, MainActivityApp::class.java)
                startActivity(intent)
                finish()
            }
        }.onFailure { exception ->
            runOnUiThread {
                Toast.makeText(this, "Ошибка: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}