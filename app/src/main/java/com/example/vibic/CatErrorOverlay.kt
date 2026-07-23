package com.example.vibic

import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Глобальная полноэкранная заглушка с котом.
 *
 * Вызывается автоматически из VibicApp при ЛЮБОЙ ошибке сети
 * (через ErrorBus), независимо от того, на каком экране находится
 * пользователь. Не нужно ничего добавлять в разметку экранов.
 */
object CatErrorOverlay {

    private const val OVERLAY_TAG = "cat_error_overlay"

    fun show(activity: Activity, error: AppError) {
        activity.runOnUiThread {
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread

            // Если кот уже на экране — не плодим дубликаты, просто обновляем текст
            val existing = root.findViewWithTag<View>(OVERLAY_TAG)
            if (existing != null) {
                updateTexts(existing, error)
                return@runOnUiThread
            }

            val density = activity.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            // Полноэкранный непрозрачный контейнер, перекрывает весь экран
            val overlay = FrameLayout(activity).apply {
                tag = OVERLAY_TAG
                setBackgroundColor(Color.parseColor("#121018"))
                isClickable = true   // перехватываем клики, чтобы не проходили "сквозь"
                isFocusable = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(32), dp(32), dp(32))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            }

            val cat = ImageView(activity).apply {
                setImageResource(R.drawable.cat_error)
                layoutParams = LinearLayout.LayoutParams(dp(180), dp(180))
            }

            val title = TextView(activity).apply {
                tag = "cat_title"
                text = catPhrase(error.code)
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
            }

            val sub = TextView(activity).apply {
                tag = "cat_sub"
                text = errorLabel(error)
                setTextColor(Color.parseColor("#9E9E9E"))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }

            // Кнопка закрытия — пользователь может убрать кота и вернуться
            val closeBtn = Button(activity).apply {
                text = "Закрыть"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#7C4DFF"))
                setPadding(dp(24), dp(12), dp(24), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(28) }
                setOnClickListener {
                    ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).setDuration(200).start()
                    overlay.postDelayed({ root.removeView(overlay) }, 210)
                }
            }

            container.addView(cat)
            container.addView(title)
            container.addView(sub)
            container.addView(closeBtn)
            overlay.addView(container)

            overlay.alpha = 0f
            root.addView(overlay)
            ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).setDuration(250).start()
        }
    }

    private fun updateTexts(overlay: View, error: AppError) {
        overlay.findViewWithTag<TextView>("cat_title")?.text = catPhrase(error.code)
        overlay.findViewWithTag<TextView>("cat_sub")?.text = errorLabel(error)
    }

    private fun errorLabel(error: AppError): String {
        val base = if (error.code == 0) "Нет соединения" else "Ошибка ${error.code}"
        return if (error.endpoint.isNotBlank()) "$base • ${error.endpoint}" else base
    }

    private fun catPhrase(code: Int): String = when (code) {
        0 -> "Кот не дозвонился до сервера"
        400 -> "Кот запутался в запросе"
        403 -> "Кота сюда не пускают"
        404 -> "Кот ничего не нашёл"
        500 -> "Сервер уронил кота"
        else -> "Что-то пошло не так"
    }
}