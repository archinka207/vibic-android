package com.example.vibic

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class EditProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_edit_profile)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.editProfileContainer, EditProfileFragment())
                .commit()
        }
    }
}