package com.example.vibic

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.vibic.databinding.ActivitySplashBinding

/**
 * Заставка при холодном старте (~1.5 сек), как в Telegram.
 * Показывается один раз, затем сама уходит на чаты (или логин).
 *
 * Тайминг:
 *  0–450ms    — логотип: fade-in + scale 0.8 → 1.0 с overshoot
 *  50–1150ms  — три кольца-пульса расходятся от центра (старт: 50 / 300 / 550ms)
 *  1350–1700ms — весь сплэш scale → 1.08 + fade-out, под ним проступает следующий экран
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Системный сплэш (Android 12+ / core-splashscreen) — убирает белое мигание.
        // Фон в теме тот же тёмно-фиолетовый, иконка прозрачная, поэтому переход бесшовный.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startIntro()
    }

    private fun startIntro() {
        // --- Логотип: 0–450ms ---
        binding.logo.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setInterpolator(OvershootInterpolator(1.3f))
                .start()
        }

        // --- Кольца-пульсы: старт 50 / 300 / 550ms ---
        pulse(binding.ring1, 50)
        pulse(binding.ring2, 300)
        pulse(binding.ring3, 550)

        // --- Финал: 1350ms → fade-out и переход ---
        binding.splashRoot.postDelayed({ finishSplash() }, 1350)
    }

    /** Кольцо: масштаб 1× → 2.2× с одновременным затуханием. */
    private fun pulse(ring: View, startDelay: Long) {
        ring.postDelayed({
            ring.alpha = 0.6f
            ring.scaleX = 1f
            ring.scaleY = 1f
            ring.animate()
                .scaleX(2.2f)
                .scaleY(2.2f)
                .alpha(0f)
                .setDuration(600)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, startDelay)
    }

    private fun finishSplash() {
        if (navigated) return
        navigated = true

        binding.splashRoot.animate()
            .alpha(0f)
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(350)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { goNext() }
            .start()
    }

    private fun goNext() {
        val target = if (AuthService.shared.isAuthenticated) {
            MainActivityApp::class.java // сразу в чаты
        } else {
            MainActivity::class.java    // экран входа по телефону
        }

        val next = Intent(this, target)
        // ОБЯЗАТЕЛЬНО: пробрасываем extras (chat_id из пуша и т.д.)
        intent.extras?.let { next.putExtras(it) }
        startActivity(next)
        // Плавный кроссфейд вместо стандартной анимации перехода
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        // На всякий случай снимаем отложенные колбэки
        if (::binding.isInitialized) {
            binding.splashRoot.animate().cancel()
        }
        super.onDestroy()
    }
}