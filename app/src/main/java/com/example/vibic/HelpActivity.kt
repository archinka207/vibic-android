package com.example.vibic

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class HelpActivity : AppCompatActivity() {

    private data class FaqItem(
        val question: LinearLayout,
        val chevron: ImageView,
        val answer: TextView,
        var expanded: Boolean = false
    )

    private lateinit var faqItems: List<FaqItem>
    private var expandedIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_help)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // Плитки популярных тем — заглушки
        findViewById<LinearLayout>(R.id.topicGettingStarted).setOnClickListener {
            Toast.makeText(this, "Начало работы", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.topicPrivacy).setOnClickListener {
            Toast.makeText(this, "Приватность", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.topicChatsGroups).setOnClickListener {
            Toast.makeText(this, "Чаты и группы", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.topicSubscription).setOnClickListener {
            Toast.makeText(this, "Подписка", Toast.LENGTH_SHORT).show()
        }

        // FAQ-аккордеон
        faqItems = listOf(
            FaqItem(
                findViewById(R.id.faqQuestion1),
                findViewById(R.id.faqChevron1),
                findViewById(R.id.faqAnswer1)
            ),
            FaqItem(
                findViewById(R.id.faqQuestion2),
                findViewById(R.id.faqChevron2),
                findViewById(R.id.faqAnswer2)
            ),
            FaqItem(
                findViewById(R.id.faqQuestion3),
                findViewById(R.id.faqChevron3),
                findViewById(R.id.faqAnswer3)
            )
        )

        faqItems.forEachIndexed { index, item ->
            item.question.setOnClickListener { toggleFaq(index) }
        }

        // Блок "Не нашли ответ?" — заглушки
        findViewById<LinearLayout>(R.id.rowContactSupport).setOnClickListener {
            Toast.makeText(this, "Скоро", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.rowTermsPrivacy).setOnClickListener {
            Toast.makeText(this, "Скоро", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFaq(index: Int) {
        val previousIndex = expandedIndex
        if (previousIndex == index) {
            collapse(faqItems[index])
            expandedIndex = -1
            return
        }
        if (previousIndex != -1) {
            collapse(faqItems[previousIndex])
        }
        expand(faqItems[index])
        expandedIndex = index
    }

    private fun expand(item: FaqItem) {
        val answer = item.answer
        answer.visibility = View.VISIBLE
        val availableWidth = (answer.parent as View).width
        answer.measure(
            View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED
        )
        val targetHeight = answer.measuredHeight

        answer.layoutParams.height = 0
        answer.requestLayout()

        ValueAnimator.ofInt(0, targetHeight).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                answer.layoutParams.height = animator.animatedValue as Int
                answer.requestLayout()
            }
        }.start()

        item.chevron.animate().rotation(90f).setDuration(250).start()
        item.chevron.setColorFilter(getColorFromAttr(R.attr.vibicBrand))
        item.expanded = true
    }

    private fun collapse(item: FaqItem) {
        val answer = item.answer
        val startHeight = answer.height

        ValueAnimator.ofInt(startHeight, 0).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                answer.layoutParams.height = animator.animatedValue as Int
                answer.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    answer.visibility = View.GONE
                }
            })
        }.start()

        item.chevron.animate().rotation(0f).setDuration(250).start()
        item.chevron.clearColorFilter()
        item.chevron.setColorFilter(getColorFromAttr(R.attr.vibicIconMuted))
        item.expanded = false
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
