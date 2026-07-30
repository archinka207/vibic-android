package com.example.vibic

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

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
    private lateinit var valBlacklistCount: TextView

    // Значения видимости справа в строках
    private lateinit var valLastSeen: TextView
    private lateinit var valProfilePhoto: TextView
    private lateinit var valBio: TextView
    private lateinit var valPhoneVisibility: TextView

    // Переключатель
    private lateinit var switchReadReceipts: SwitchCompat

    private val PREFS_NAME = "privacy_prefs"
    private val APP_PREFS_NAME = "app_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Прозрачный статус-бар
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        valLastSeen = findViewById(R.id.valLastSeen)
        valProfilePhoto = findViewById(R.id.valProfilePhoto)
        valBio = findViewById(R.id.valBio)
        valPhoneVisibility = findViewById(R.id.valPhoneVisibility)

        switchReadReceipts = findViewById(R.id.switchReadReceipts)

        rowTwoFactor = findViewById(R.id.rowTwoFactor)
        rowDevices = findViewById(R.id.rowDevices)
        rowBlacklist = findViewById(R.id.rowBlacklist)
        valBlacklistCount = findViewById(R.id.valBlacklistCount)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // На скриншоте переключатель включен
        switchReadReceipts.isChecked = prefs.getBoolean("read_receipts", true)

        val appPrefs = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        valLastSeen.text = visibilityLabel(appPrefs.getString("vis_last_seen", "contacts"))
        valProfilePhoto.text = visibilityLabel(appPrefs.getString("vis_photo", "all"))
        valBio.text = visibilityLabel(appPrefs.getString("vis_bio", "all"))
        valPhoneVisibility.text = visibilityLabel(appPrefs.getString("vis_phone", "contacts"))
    }

    private fun visibilityLabel(value: String?): String = when (value) {
        "all" -> "Все"
        "nobody" -> "Никто"
        else -> "Контакты"
    }

    override fun onResume() {
        super.onResume()
        valBlacklistCount.text = BlockedStore.getAll(this).size.toString()
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        // Сохранение положения Switch
        switchReadReceipts.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("read_receipts", isChecked).apply()
        }

        rowLastSeen.setOnClickListener {
            showVisibilityPicker("Был(а) в сети", "vis_last_seen", valLastSeen)
        }
        rowProfilePhoto.setOnClickListener {
            showVisibilityPicker("Фото профиля", "vis_photo", valProfilePhoto)
        }
        rowBio.setOnClickListener {
            showVisibilityPicker("О себе", "vis_bio", valBio)
        }
        rowPhoneVisibility.setOnClickListener {
            showVisibilityPicker("Номер телефона", "vis_phone", valPhoneVisibility)
        }

        rowTwoFactor.setOnClickListener {
            Toast.makeText(this, "Двухфакторная защита в разработке", Toast.LENGTH_SHORT).show()
        }
        rowDevices.setOnClickListener {
            startActivity(Intent(this, DevicesActivity::class.java))
        }
        rowBlacklist.setOnClickListener {
            startActivity(Intent(this, BlacklistActivity::class.java))
        }
    }

    private fun showVisibilityPicker(title: String, prefKey: String, valueLabel: TextView) {
        val appPrefs = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        val defaultValue = when (prefKey) {
            "vis_photo", "vis_bio" -> "all"
            else -> "contacts"
        }
        val current = appPrefs.getString(prefKey, defaultValue)

        val dialog = BottomSheetDialog(this, R.style.BottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.sheet_visibility_picker, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.sheetTitle).text = title

        val optionAll = view.findViewById<LinearLayout>(R.id.optionAll)
        val optionContacts = view.findViewById<LinearLayout>(R.id.optionContacts)
        val optionNobody = view.findViewById<LinearLayout>(R.id.optionNobody)

        val checkAll = view.findViewById<ImageView>(R.id.checkAll)
        val checkContacts = view.findViewById<ImageView>(R.id.checkContacts)
        val checkNobody = view.findViewById<ImageView>(R.id.checkNobody)

        fun updateChecks(selected: String) {
            checkAll.visibility = if (selected == "all") View.VISIBLE else View.GONE
            checkContacts.visibility = if (selected == "contacts") View.VISIBLE else View.GONE
            checkNobody.visibility = if (selected == "nobody") View.VISIBLE else View.GONE
        }
        updateChecks(current ?: defaultValue)

        fun select(value: String) {
            appPrefs.edit().putString(prefKey, value).apply()
            valueLabel.text = visibilityLabel(value)
            dialog.dismiss()
        }

        optionAll.setOnClickListener { select("all") }
        optionContacts.setOnClickListener { select("contacts") }
        optionNobody.setOnClickListener { select("nobody") }

        view.findViewById<View>(R.id.btnCloseSheet).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}