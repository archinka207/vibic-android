package com.example.vibic

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.vibic.databinding.ActivityMainBinding
import com.example.vibic.AuthService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Убираем черные полосы (100% работает)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)

        // Если уже авторизованы -> идем в чаты
        if (AuthService.shared.isAuthenticated) {
            startActivity(Intent(this, MainActivityApp::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.main.setBackgroundResource(R.drawable.bg_auth_gradient)

        setupPhoneMask()

        binding.continueButton.setOnClickListener {
            val phone = binding.phoneInput.text.toString().filter { it.isDigit() }
            if (phone.length == 11) {
                sendCode(phone)
            } else {
                showToast("Введите полный номер телефона")
            }
        }
    }

    private fun sendCode(phone: String) {
        binding.continueButton.isEnabled = false
        binding.continueButton.text = "Отправка..."

        lifecycleScope.launch {
            val result = AuthService.shared.sendCode(phone)
            binding.continueButton.isEnabled = true
            binding.continueButton.text = "Продолжить"

            result.onSuccess {
                val intent = Intent(this@MainActivity, CodeConfirmActivity::class.java)
                intent.putExtra("PHONE", phone)
                startActivity(intent)
            }.onFailure { error ->
                showToast(error.localizedMessage ?: "Ошибка отправки")
            }
        }
    }

    private fun setupPhoneMask() {
        binding.phoneInput.setText("+7")
        binding.phoneInput.addTextChangedListener(object : TextWatcher {
            var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                val digits = s.toString().filter { it.isDigit() }
                if (digits.length > 11) {
                    s?.replace(0, s.length, s.substring(0, s.length - 1))
                    isFormatting = false
                    return
                }

                var formatted = "+7"
                if (digits.length > 1) {
                    val codeDigits = digits.drop(1)
                    if (codeDigits.length <= 3) {
                        formatted = "+7 ($codeDigits"
                    } else if (codeDigits.length <= 6) {
                        formatted = "+7 (${codeDigits.take(3)}) ${codeDigits.drop(3)}"
                    } else if (codeDigits.length <= 8) {
                        formatted = "+7 (${codeDigits.take(3)}) ${codeDigits.drop(3).take(3)}-${codeDigits.drop(6)}"
                    } else {
                        formatted = "+7 (${codeDigits.take(3)}) ${codeDigits.drop(3).take(3)}-${codeDigits.drop(6).take(2)}-${codeDigits.drop(8)}"
                    }
                }

                s?.replace(0, s.length, formatted)
                isFormatting = false
            }
        })
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}