package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bars = 20
    private val barHeights = FloatArray(bars) { 0.1f }
    private var targetAmplitude = 0.1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        style = Paint.Style.FILL
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 50
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            for (i in 0 until bars) {
                val randMultiplier = 0.5f + Random.nextFloat() * 0.5f
                val target = (targetAmplitude * randMultiplier).coerceIn(0.05f, 1.0f)
                barHeights[i] += (target - barHeights[i]) * 0.3f
            }
            invalidate()
        }
    }

    fun startAnimation() = animator.start()
    fun stopAnimation() = animator.cancel()

    fun setAmplitude(rms: Float) {
        targetAmplitude = rms.coerceIn(0.1f, 1.0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width.toFloat() / (bars * 2)
        val midY = height / 2f

        for (i in 0 until bars) {
            val h = barHeights[i] * height
            val x = i * (barWidth * 2) + barWidth / 2
            canvas.drawRoundRect(x, midY - h / 2, x + barWidth, midY + h / 2, 8f, 8f, paint)
        }
    }
}