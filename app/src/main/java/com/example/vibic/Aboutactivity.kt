package com.example.vibic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_about)

        findViewById<FrameLayout>(R.id.backButton).setOnClickListener { finish() }

        // Версия и сборка
        val versionText = findViewById<TextView>(R.id.versionText)
        try {
            val pkg = packageManager.getPackageInfo(packageName, 0)
            val versionName = pkg.versionName ?: "1.0.0"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                pkg.longVersionCode.toString() else @Suppress("DEPRECATION") pkg.versionCode.toString()
            versionText.text = "Версия $versionName · build $versionCode"
        } catch (e: Exception) {
            versionText.text = "Версия 1.0.0"
        }

        // Пункты меню
        findViewById<LinearLayout>(R.id.rowWhatsNew).setOnClickListener {
            openDetail(AboutDetailActivity.SECTION_WHATS_NEW)
        }
        findViewById<LinearLayout>(R.id.rowLicenses).setOnClickListener {
            openDetail(AboutDetailActivity.SECTION_LICENSES)
        }
        findViewById<LinearLayout>(R.id.rowTerms).setOnClickListener {
            openDetail(AboutDetailActivity.SECTION_TERMS)
        }
        findViewById<LinearLayout>(R.id.rowPrivacy).setOnClickListener {
            openDetail(AboutDetailActivity.SECTION_PRIVACY)
        }
    }

    private fun openDetail(section: String) {
        val intent = Intent(this, AboutDetailActivity::class.java)
        intent.putExtra(AboutDetailActivity.EXTRA_SECTION, section)
        startActivity(intent)
    }
}