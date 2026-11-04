package com.example.vibic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

/**
 * Большой круг записи (как в Telegram): сплошной круг с иконкой и
 * «дышащие» волны вокруг, размер которых зависит от громкости голоса.
 */
class RecordCircleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    val baseRadius = 41f * dp
    private val iconSize = (26 * dp).toInt()

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaintInner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaintOuter = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val startTime = SystemClock.uptimeMillis()

    private var amp = 0f
    private var ampTarget = 0f

    private var icon: Drawable? = null
    private var oldIcon: Drawable? = null
    private var iconProgress = 1f
    private var iconAnimator: ValueAnimator? = null

    fun setColor(color: Int) {
        circlePaint.color = color
        blobPaintInner.color = (color and 0x00FFFFFF) or (0x40 shl 24)
        blobPaintOuter.color = (color and 0x00FFFFFF) or (0x22 shl 24)
        invalidate()
    }

    /** 0..1 — громкость, волны плавно догоняют значение. */
    fun setAmplitude(value: Float) {
        ampTarget = value.coerceIn(0f, 1f)
    }

    fun setIcon(resId: Int, animate: Boolean) {
        val d = ContextCompat.getDrawable(context, resId)?.mutate()?.apply { setTint(Color.WHITE) }
        iconAnimator?.cancel()
        if (!animate || icon == null) {
            icon = d; oldIcon = null; iconProgress = 1f; invalidate(); return
        }
        oldIcon = icon; icon = d; iconProgress = 0f
        iconAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { iconProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun resetAmplitude() { amp = 0f; ampTarget = 0f }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        amp += (ampTarget - amp) * 0.18f
        val t = (SystemClock.uptimeMillis() - startTime) / 1000f

        drawBlob(canvas, cx, cy, baseRadius * (1.20f + amp * 0.55f), t * 0.9f, 0.06f, blobPaintOuter, 1.3f)
        drawBlob(canvas, cx, cy, baseRadius * (1.08f + amp * 0.35f), t * 1.25f, 0.05f, blobPaintInner, 2.1f)
        canvas.drawCircle(cx, cy, baseRadius, circlePaint)

        val p = iconProgress
        oldIcon?.let { if (p < 1f) drawIcon(canvas, it, cx, cy, 1f - p * 0.6f, 1f - p) }
        icon?.let { drawIcon(canvas, it, cx, cy, if (oldIcon != null && p < 1f) 0.4f + 0.6f * p else 1f, p) }

        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun drawIcon(c: Canvas, d: Drawable, cx: Float, cy: Float, scale: Float, alpha: Float) {
        val half = (iconSize * scale / 2f).toInt()
        d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        d.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        d.draw(c)
    }

    /** Мягкая «капля»: окружность, радиус которой слегка колеблется по точкам. */
    private fun drawBlob(c: Canvas, cx: Float, cy: Float, r: Float, t: Float, wobble: Float, paint: Paint, seed: Float) {
        val n = 8
        val xs = FloatArray(n); val ys = FloatArray(n)
        for (i in 0 until n) {
            val a = (Math.PI * 2 * i / n).toFloat()
            val rr = r * (1f + wobble * sin(t * 2.2f + i * seed * 1.7f) + wobble * 0.6f * cos(t * 1.4f + i * 2.3f))
            xs[i] = cx + rr * cos(a); ys[i] = cy + rr * sin(a)
        }
        path.reset()
        path.moveTo((xs[n - 1] + xs[0]) / 2f, (ys[n - 1] + ys[0]) / 2f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            path.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
        }
        path.close()
        c.drawPath(path, paint)
    }
}

/** Кольцо прогресса вокруг кружка во время записи (0..1 = 0..60 сек). */
class VideoProgressRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val stroke = 4f * dp
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = 0x40FFFFFF
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = Color.WHITE
    }
    private val rect = RectF()

    var progress = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    fun setProgressColor(color: Int) { progressPaint.color = color; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val half = stroke / 2f
        rect.set(half, half, width - half, height - half)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (progress > 0f) canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)
    }
}
