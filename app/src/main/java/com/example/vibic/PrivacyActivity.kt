package com.example.vibic

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.widget.ImageView
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat

class PrivacyActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView

    // Строки меню
    private lateinit var rowLastSeen: LinearLayout
    private lateinit var rowProfilePhoto: LinearLayout
    private lateinit var rowBio: LinearLayout
    private lateinit var rowPhoneVisibility: LinearLayout
    private lateinit var rowTwoFactor: LinearLayout
    private lateinit var rowDevices: LinearLayout
    private lateinit var rowBlacklist: LinearLayout

    // Переключатель
    private lateinit var switchReadReceipts: SwitchCompat

    private val PREFS_NAME = "privacy_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Прозрачный статус-бар
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bg)

        setContentView(R.layout.activity_privacy)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)

        rowLastSeen = findViewById(R.id.rowLastSeen)
        rowProfilePhoto = findViewById(R.id.rowProfilePhoto)
        rowBio = findViewById(R.id.rowBio)
        rowPhoneVisibility = findViewById(R.id.rowPhoneVisibility)

        switchReadReceipts = findViewById(R.id.switchReadReceipts)

        rowTwoFactor = findViewById(R.id.rowTwoFactor)
        rowDevices = findViewById(R.id.rowDevices)
        rowBlacklist = findViewById(R.id.rowBlacklist)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // На скриншоте переключатель включен
        switchReadReceipts.isChecked = prefs.getBoolean("read_receipts", true)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        // Сохранение положения Switch
        switchReadReceipts.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("read_receipts", isChecked).apply()
        }

        // Заглушки для пунктов меню, которые открывают другие экраны/popups
        val soonClickListener = android.view.View.OnClickListener {
            Toast.makeText(this, "Настройки видимости скоро появятся", Toast.LENGTH_SHORT).show()
        }

        rowLastSeen.setOnClickListener(soonClickListener)
        rowProfilePhoto.setOnClickListener(soonClickListener)
        rowBio.setOnClickListener(soonClickListener)
        rowPhoneVisibility.setOnClickListener(soonClickListener)

        rowTwoFactor.setOnClickListener {
            Toast.makeText(this, "Двухфакторная защита в разработке", Toast.LENGTH_SHORT).show()
        }
        rowDevices.setOnClickListener {
            startActivity(Intent(this, DevicesActivity::class.java))
        }
        rowBlacklist.setOnClickListener {
            Toast.makeText(this, "Чёрный список в разработке", Toast.LENGTH_SHORT).show()
        }
    }
}