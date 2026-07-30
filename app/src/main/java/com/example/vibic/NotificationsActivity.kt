package com.example.vibic

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat

class NotificationsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView

    private lateinit var switchMaster: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibration: SwitchCompat
    private lateinit var switchPreview: SwitchCompat
    private lateinit var switchGroup: SwitchCompat

    private val PREFS_NAME = "notifications_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Делаем статус-бар прозрачным, как во всем приложении
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_notifications)

        backButton = findViewById(R.id.backButton)
        switchMaster = findViewById(R.id.switchMaster)
        switchSound = findViewById(R.id.switchSound)
        switchVibration = findViewById(R.id.switchVibration)
        switchPreview = findViewById(R.id.switchPreview)
        switchGroup = findViewById(R.id.switchGroup)

        backButton.setOnClickListener { finish() }

        loadSettings()

        // Главный рубильник (если выключаем, можно делать остальные неактивными)
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("master", isChecked)
            toggleInnerSwitches(isChecked)
        }

        switchSound.setOnCheckedChangeListener { _, isChecked -> saveSetting("sound", isChecked) }
        switchVibration.setOnCheckedChangeListener { _, isChecked -> saveSetting("vibration", isChecked) }
        switchPreview.setOnCheckedChangeListener { _, isChecked -> saveSetting("preview", isChecked) }
        switchGroup.setOnCheckedChangeListener { _, isChecked -> saveSetting("group", isChecked) }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // По умолчанию все включено (true)
        val masterEnabled = prefs.getBoolean("master", true)
        switchMaster.isChecked = masterEnabled

        switchSound.isChecked = prefs.getBoolean("sound", true)
        switchVibration.isChecked = prefs.getBoolean("vibration", true)
        switchPreview.isChecked = prefs.getBoolean("preview", true)
        switchGroup.isChecked = prefs.getBoolean("group", false) // На скрине он выключен

        toggleInnerSwitches(masterEnabled)
    }

    private fun saveSetting(key: String, value: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun toggleInnerSwitches(enabled: Boolean) {
        switchSound.isEnabled = enabled
        switchVibration.isEnabled = enabled
        switchPreview.isEnabled = enabled
        switchGroup.isEnabled = enabled

        // Визуально глушим альфу, если главный выключен
        val alpha = if (enabled) 1.0f else 0.5f
        switchSound.alpha = alpha
        switchVibration.alpha = alpha
        switchPreview.alpha = alpha
        switchGroup.alpha = alpha
    }
}