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
import com.example.vibic.network.ApiClient
import com.example.vibic.network.Setup2FARequest
import com.example.vibic.network.UpdateProfileRequest
import kotlinx.coroutines.launch

class NicknameActivity : AppCompatActivity() {

    private lateinit var nicknameInput: EditText
    private lateinit var twoFaPasswordInput: EditText
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nickname)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        nicknameInput = findViewById(R.id.nicknameInput)
        twoFaPasswordInput = findViewById(R.id.twoFaPasswordInput)
        continueButton = findViewById(R.id.continueButton)

        val phoneNumber = intent.getStringExtra("PHONE") ?: intent.getStringExtra("PHONE_NUMBER")

        continueButton.setOnClickListener {
            val nickname = nicknameInput.text.toString().trim()
            val twoFaPassword = twoFaPasswordInput.text.toString()

            if (nickname.isNotEmpty()) {
                continueButton.isEnabled = false
                continueButton.text = "Сохранение..."

                lifecycleScope.launch {
                    try {
                        // 1. Отправляем имя на сервер
                        val profileRequest = UpdateProfileRequest(firstName = nickname)
                        val profileResponse = ApiClient.apiService.updateProfile(profileRequest)

                        if (profileResponse.isSuccessful) {
                            // Сохраняем имя локально
                            val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            sharedPref.edit().apply {
                                putString("user_nickname", nickname)
                                putString("user_first_name", nickname)
                                if (phoneNumber != null) {
                                    putString("user_phone", phoneNumber)
                                }
                                putBoolean("is_logged_in", true)
                                apply()
                            }

                            // 2. Если введен 2FA пароль, настраиваем его
                            if (twoFaPassword.isNotEmpty()) {
                                val faRequest = Setup2FARequest(password = twoFaPassword)
                                val faResponse = ApiClient.apiService.setup2FA(faRequest)
                                if (!faResponse.isSuccessful) {
                                    Toast.makeText(this@NicknameActivity, "Имя сохранено, но ошибка при настройке 2FA", Toast.LENGTH_SHORT).show()
                                }
                            }

                            Toast.makeText(this@NicknameActivity, "Добро пожаловать!", Toast.LENGTH_SHORT).show()

                            // Переходим на главный экран
                            val intent = Intent(this@NicknameActivity, MainActivityApp::class.java)
                            startActivity(intent)
                            finishAffinity()
                        } else {
                            Toast.makeText(this@NicknameActivity, "Ошибка сервера: ${profileResponse.code()}", Toast.LENGTH_SHORT).show()
                            continueButton.isEnabled = true
                            continueButton.text = "Готово"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@NicknameActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
                        continueButton.isEnabled = true
                        continueButton.text = "Готово"
                    }
                }
            } else {
                Toast.makeText(this, "Введите как к вам обращаться", Toast.LENGTH_SHORT).show()
            }
        }
    }
}