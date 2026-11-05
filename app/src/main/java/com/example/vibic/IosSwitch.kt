package com.example.vibic

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.google.android.material.color.MaterialColors

/** iOS-style тумблер. API совместим со SwitchCompat: isChecked + setOnCheckedChangeListener. */
class IosSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Цвет дорожки в выключенном состоянии. По умолчанию — цвет разделителя темы. */
    var trackColorOff: Int = MaterialColors.getColor(context, R.attr.vibicDivider, "IosSwitch")
        set(value) { field = value; invalidate() }
    private val trackColorOn = MaterialColors.getColor(context, R.attr.vibicBrand, "IosSwitch")

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(dp(2f), 0f, dp(1f), 0x33000000)
    }

    private val trackRect = RectF()
    private val argbEvaluator = ArgbEvaluator()

    // 0f = выключено, 1f = включено
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private var _checked = false
    var isChecked: Boolean
        get() = _checked
        set(value) = setChecked(value, animate = true)

    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true

        attrs?.let {
            val a = context.obtainStyledAttributes(it, intArrayOf(android.R.attr.checked))
            _checked = a.getBoolean(0, false)
            progress = if (_checked) 1f else 0f
            a.recycle()
        }

        setOnClickListener { setChecked(!_checked, animate = true) }
    }

    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        onCheckedChangeListener = listener
    }

    /** [animate] = false — выставить состояние сразу, без скольжения (для начальной загрузки). */
    fun setChecked(checked: Boolean, animate: Boolean) {
        if (_checked == checked) return
        _checked = checked

        animator?.cancel()
        if (animate && isLaidOut) {
            animator = ValueAnimator.ofFloat(progress, if (checked) 1f else 0f).apply {
                duration = 200
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    progress = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = if (checked) 1f else 0f
            invalidate()
        }

        onCheckedChangeListener?.invoke(checked)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(51f).toInt()
        val desiredHeight = dp(31f).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cornerRadius = h / 2f
        val padding = h * PADDING_RATIO
        val thumbRadius = h / 2f - padding

        trackPaint.color = argbEvaluator.evaluate(progress.coerceIn(0f, 1f), trackColorOff, trackColorOn) as Int
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        val offX = h / 2f
        val onX = w - h / 2f
        val thumbX = offX + (onX - offX) * progress
        canvas.drawCircle(thumbX, h / 2f, thumbRadius, thumbPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val PADDING_RATIO = 2f / 31f
    }
}
