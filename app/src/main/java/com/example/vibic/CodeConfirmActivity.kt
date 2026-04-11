package com.example.vibic

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.vibic.databinding.ActivityCodeConfirmBinding
import com.example.vibic.network.TokenManager
import kotlinx.coroutines.launch

class CodeConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodeConfirmBinding
    private var phoneNumber: String = ""
    private var countDownTimer: CountDownTimer? = null
    private val codeLength = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityCodeConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.main.setBackgroundResource(R.drawable.bg_auth_gradient)

        phoneNumber = intent.getStringExtra("PHONE") ?: ""
        binding.phoneNumberText.text = phoneNumber

        binding.code5.visibility = View.GONE
        binding.code6.visibility = View.GONE

        setupHiddenInput()
        startTimer()

        binding.resendText.setOnClickListener {
            resendCode()
        }

        binding.changeNumberText.setOnClickListener { finish() }
    }

    private fun setupHiddenInput() {
        val codeFields = listOf(binding.code1, binding.code2, binding.code3, binding.code4)
        val hiddenInput = binding.hiddenCodeInput

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""

                for (i in 0 until codeLength) {
                    codeFields[i].text = if (i < text.length) text[i].toString() else ""
                }

                if (text.length == codeLength) {
                    verifyCode(text)
                }
            }
        })

        hiddenInput.requestFocus()
    }

    private fun verifyCode(code: String) {
        binding.hiddenCodeInput.isEnabled = false

        lifecycleScope.launch {
            val result = AuthService.shared.verifyCode(phoneNumber, code)

            result.onSuccess { response ->
                Log.d("CodeConfirm", "verifyCode success: status=${response.status}, userId=${response.userId}")

                if (response.status == "success") {
                    // Проверяем, сохранился ли userId
                    val userId = TokenManager.getUserId()
                    Log.d("CodeConfirm", "Сохраненный userId: $userId")

                    if (response.isNewUser == true) {
                        val intent = Intent(this@CodeConfirmActivity, NicknameActivity::class.java)
                        intent.putExtra("PHONE", phoneNumber)
                        startActivity(intent)
                    } else {
                        startActivity(Intent(this@CodeConfirmActivity, MainActivityApp::class.java))
                    }
                    finishAffinity()
                } else if (response.status == "2fa_required") {
                    val intent = Intent(this@CodeConfirmActivity, TwoFactorActivity::class.java)
                    intent.putExtra("PHONE", phoneNumber)
                    startActivity(intent)
                }
            }.onFailure { error ->
                Log.e("CodeConfirm", "verifyCode error: ${error.message}")
                binding.hiddenCodeInput.isEnabled = true
                binding.hiddenCodeInput.text?.clear()
                Toast.makeText(this@CodeConfirmActivity, "Неверный код: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTimer() {
        binding.resendText.visibility = View.GONE
        binding.timerContainer.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.timerDigits.text = String.format("00:%02d", millisUntilFinished / 1000)
            }
            override fun onFinish() {
                binding.timerContainer.visibility = View.GONE
                binding.resendText.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun resendCode() {
        binding.hiddenCodeInput.text?.clear()
        startTimer()
        lifecycleScope.launch {
            AuthService.shared.sendCode(phoneNumber)
            Toast.makeText(this@CodeConfirmActivity, "Код отправлен заново", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}