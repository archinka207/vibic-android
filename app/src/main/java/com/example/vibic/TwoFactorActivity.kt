package com.example.vibic

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.vibic.databinding.ActivityTwoFactorBinding
import com.example.vibic.AuthService
import kotlinx.coroutines.launch

class TwoFactorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTwoFactorBinding
    private var phoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        // Создай layout activity_two_factor.xml по аналогии с iOS
        binding = ActivityTwoFactorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        phoneNumber = intent.getStringExtra("PHONE") ?: ""

        binding.confirmButton.setOnClickListener {
            val password = binding.passwordTextField.text.toString()
            if (password.isNotEmpty()) {
                verify2FA(password)
            } else {
                Toast.makeText(this, "Введите пароль", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verify2FA(password: String) {
        binding.confirmButton.isEnabled = false
        binding.confirmButton.text = "Проверка..."

        lifecycleScope.launch {
            val result = AuthService.shared.verify2FA(password)

            result.onSuccess { response ->
                if (response.isNewUser == true) {
                    val intent = Intent(this@TwoFactorActivity, NicknameActivity::class.java)
                    intent.putExtra("PHONE", phoneNumber)
                    startActivity(intent)
                } else {
                    startActivity(Intent(this@TwoFactorActivity, MainActivityApp::class.java))
                }
                finishAffinity()
            }.onFailure { error ->
                binding.confirmButton.isEnabled = true
                binding.confirmButton.text = "Подтвердить"
                Toast.makeText(this@TwoFactorActivity, error.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}