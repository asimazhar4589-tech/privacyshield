package com.privacyshield.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class PrivacyOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private var currentAlpha: Int = 0
    private var maxAlpha: Int = 255
    private var animator: ValueAnimator? = null

    fun setMaxAlpha(fraction: Float) {
        maxAlpha = (fraction.coerceIn(0f, 1f) * 255).toInt()
    }

    /**
     * Update alpha smoothly (0f = transparent, 1f = fully opaque).
     */
    fun updateAlpha(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * maxAlpha).toInt()
        if (target == currentAlpha) return

        animator?.cancel()
        animator = ValueAnimator.ofInt(currentAlpha, target).apply {
            duration = 120
            addUpdateListener {
                currentAlpha = it.animatedValue as Int
                paint.alpha = currentAlpha
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}