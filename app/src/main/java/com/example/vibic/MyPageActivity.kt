package com.example.vibic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vibic.network.TokenManager
import java.io.File

class MyPageActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarText: TextView
    private lateinit var profileName: TextView
    private lateinit var usernameRow: LinearLayout
    private lateinit var profileUsername: TextView
    private lateinit var profilePhone: TextView
    private lateinit var aboutSection: LinearLayout
    private lateinit var aboutText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_page)

        backButton = findViewById(R.id.backButton)
        avatarImage = findViewById(R.id.avatarImage)
        avatarText = findViewById(R.id.avatarText)
        profileName = findViewById(R.id.profileName)
        usernameRow = findViewById(R.id.usernameRow)
        profileUsername = findViewById(R.id.profileUsername)
        profilePhone = findViewById(R.id.profilePhone)
        aboutSection = findViewById(R.id.aboutSection)
        aboutText = findViewById(R.id.aboutText)

        backButton.setOnClickListener { finish() }
        usernameRow.setOnClickListener { copyUsername() }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun copyUsername() {
        val username = profileUsername.text.toString()
        if (username.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("username", username))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun loadProfile() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val firstName = prefs.getString("user_first_name", null)
        val lastName = prefs.getString("user_last_name", null)
        val fullName = listOfNotNull(
            firstName?.takeIf { it.isNotBlank() },
            lastName?.takeIf { it.isNotBlank() }
        ).joinToString(" ")
        profileName.text = fullName.ifBlank { "Без имени" }

        val username = prefs.getString("user_username", null)
        if (!username.isNullOrBlank()) {
            profileUsername.text = "@$username"
            usernameRow.visibility = View.VISIBLE
        } else {
            usernameRow.visibility = View.GONE
        }

        val bio = prefs.getString("user_status", null)
        if (!bio.isNullOrBlank()) {
            aboutText.text = bio
            aboutSection.visibility = View.VISIBLE
        } else {
            aboutSection.visibility = View.GONE
        }

        val phone = TokenManager.getPhone() ?: prefs.getString("user_phone", null)
        val visPhone = prefs.getString("vis_phone", "contacts")
        profilePhone.visibility =
            if (!phone.isNullOrBlank() && visPhone != "nobody") {
                profilePhone.text = phone
                View.VISIBLE
            } else {
                View.GONE
            }

        val avatarPath = prefs.getString("avatar_path", null)
        val avatarFile = avatarPath?.let { File(it) }
        if (avatarFile != null && avatarFile.exists()) {
            avatarImage.setImageBitmap(BitmapFactory.decodeFile(avatarPath))
            avatarImage.visibility = View.VISIBLE
            avatarText.visibility = View.GONE
        } else {
            avatarImage.visibility = View.GONE
            avatarText.visibility = View.VISIBLE
            avatarText.text = profileName.text.toString().take(1).uppercase()
        }
    }
}
