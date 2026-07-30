package com.example.vibic

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class ThemePickerActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView

    private lateinit var optionLight: LinearLayout
    private lateinit var optionDark: LinearLayout
    private lateinit var optionSystem: LinearLayout

    private lateinit var checkLight: ImageView
    private lateinit var checkDark: ImageView
    private lateinit var checkSystem: ImageView

    private val PREFS_NAME = "app_prefs"
    private val KEY_THEME_MODE = "theme_mode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_picker)

        backButton = findViewById(R.id.backButton)

        optionLight = findViewById(R.id.optionLight)
        optionDark = findViewById(R.id.optionDark)
        optionSystem = findViewById(R.id.optionSystem)

        checkLight = findViewById(R.id.checkLight)
        checkDark = findViewById(R.id.checkDark)
        checkSystem = findViewById(R.id.checkSystem)

        backButton.setOnClickListener { finish() }

        optionLight.setOnClickListener { selectTheme("light") }
        optionDark.setOnClickListener { selectTheme("dark") }
        optionSystem.setOnClickListener { selectTheme("system") }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        updateChecks(prefs.getString(KEY_THEME_MODE, "system"))
    }

    private fun selectTheme(mode: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode).apply()
        updateChecks(mode)

        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun updateChecks(mode: String?) {
        checkLight.visibility = if (mode == "light") View.VISIBLE else View.INVISIBLE
        checkDark.visibility = if (mode == "dark") View.VISIBLE else View.INVISIBLE
        checkSystem.visibility = if (mode == "system") View.VISIBLE else View.INVISIBLE
    }
}
