package com.example.vibic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

class QrOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val cornerPaint = Paint().apply {
        color = MaterialColors.getColor(this@QrOverlayView, R.attr.vibicBrand)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val scanRect = RectF()
    private val cornerLen = 48f
    private val cornerRadius = 12f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        setLayerType(LAYER_TYPE_HARDWARE, null)

        val size = (minOf(width, height) * 0.68f)
        val left   = (width  - size) / 2f
        val top    = (height - size) / 2f - height * 0.04f
        val right  = left + size
        val bottom = top  + size
        scanRect.set(left, top, right, bottom)

        // Затемнение
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Прозрачное окно
        canvas.drawRoundRect(scanRect, cornerRadius, cornerRadius, clearPaint)

        // Угловые маркеры
        val path = Path()
        // Верхний левый
        path.moveTo(left, top + cornerLen)
        path.lineTo(left, top + cornerRadius)
        path.quadTo(left, top, left + cornerRadius, top)
        path.lineTo(left + cornerLen, top)
        // Верхний правый
        path.moveTo(right - cornerLen, top)
        path.lineTo(right - cornerRadius, top)
        path.quadTo(right, top, right, top + cornerRadius)
        path.lineTo(right, top + cornerLen)
        // Нижний правый
        path.moveTo(right, bottom - cornerLen)
        path.lineTo(right, bottom - cornerRadius)
        path.quadTo(right, bottom, right - cornerRadius, bottom)
        path.lineTo(right - cornerLen, bottom)
        // Нижний левый
        path.moveTo(left + cornerLen, bottom)
        path.lineTo(left + cornerRadius, bottom)
        path.quadTo(left, bottom, left, bottom - cornerRadius)
        path.lineTo(left, bottom - cornerLen)

        canvas.drawPath(path, cornerPaint)
    }
}