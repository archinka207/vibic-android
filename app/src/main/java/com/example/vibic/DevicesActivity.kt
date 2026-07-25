package com.example.vibic

import android.graphics.Color
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class DevicesActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView

    private lateinit var otherSessionsContainer: LinearLayout
    private lateinit var otherSessionsTitle: TextView
    private lateinit var sessionMac: LinearLayout
    private lateinit var sessionWeb: LinearLayout
    private lateinit var separatorMacWeb: View

    private lateinit var btnTerminateMac: TextView
    private lateinit var btnTerminateWeb: TextView
    private lateinit var btnTerminateAll: TextView

    private var activeOtherSessions = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_bg)

        setContentView(R.layout.activity_devices)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)

        otherSessionsContainer = findViewById(R.id.otherSessionsContainer)
        otherSessionsTitle = findViewById(R.id.otherSessionsTitle)

        sessionMac = findViewById(R.id.sessionMac)
        sessionWeb = findViewById(R.id.sessionWeb)
        separatorMacWeb = findViewById(R.id.separatorMacWeb)

        btnTerminateMac = findViewById(R.id.btnTerminateMac)
        btnTerminateWeb = findViewById(R.id.btnTerminateWeb)
        btnTerminateAll = findViewById(R.id.btnTerminateAll)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        btnTerminateMac.setOnClickListener {
            terminateSession(sessionMac)
            Toast.makeText(this, "Сессия MacBook завершена", Toast.LENGTH_SHORT).show()
        }

        btnTerminateWeb.setOnClickListener {
            terminateSession(sessionWeb)
            Toast.makeText(this, "Сессия Web завершена", Toast.LENGTH_SHORT).show()
        }

        btnTerminateAll.setOnClickListener {
            otherSessionsContainer.animate().alpha(0f).setDuration(300).withEndAction {
                otherSessionsContainer.visibility = View.GONE
            }.start()
            Toast.makeText(this, "Все другие сессии завершены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun terminateSession(sessionView: View) {
        // Простая анимация исчезновения сессии
        sessionView.animate().alpha(0f).setDuration(200).withEndAction {
            sessionView.visibility = View.GONE
            activeOtherSessions--

            // Прячем полоску, если она осталась
            separatorMacWeb.visibility = View.GONE

            // Обновляем заголовок
            if (activeOtherSessions > 0) {
                otherSessionsTitle.text = "ДРУГИЕ СЕССИИ · $activeOtherSessions"
            } else {
                // Если сессий не осталось, прячем весь блок
                otherSessionsContainer.visibility = View.GONE
            }
        }.start()
    }
}