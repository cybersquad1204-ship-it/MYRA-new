package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OrbAnimationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { IDLE, LISTENING, SPEAKING, THINKING }

    private var currentState = State.IDLE
    private var rotationAngle = 0f
    private var pulseScale = 1.0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            invalidate()
        }
    }

    private val pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f, 1.0f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulseScale = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
        pulseAnimator.start()
    }

    fun setState(state: State) {
        currentState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 3f) * pulseScale
        if (radius <= 0f) return

        val colorStart = when (currentState) {
            State.IDLE -> Color.parseColor("#B71C1C")
            State.LISTENING -> Color.parseColor("#FF1744")
            State.SPEAKING -> Color.parseColor("#E040FB")
            State.THINKING -> Color.parseColor("#40C4FF")
        }
        val colorEnd = when (currentState) {
            State.IDLE -> Color.parseColor("#880E4F")
            State.LISTENING -> Color.parseColor("#D500F9")
            State.SPEAKING -> Color.parseColor("#FF1744")
            State.THINKING -> Color.parseColor("#00B0FF")
        }

        paint.shader = RadialGradient(cx, cy, radius * 1.5f, colorStart, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.alpha = 150
        canvas.drawCircle(cx, cy, radius * 1.5f, paint)

        paint.shader = RadialGradient(cx, cy, radius, colorStart, colorEnd, Shader.TileMode.CLAMP)
        paint.alpha = 255
        canvas.drawCircle(cx, cy, radius, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = colorStart
        paint.alpha = 180

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        canvas.drawCircle(cx, cy, radius * 1.2f, paint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-rotationAngle * 1.5f, cx, cy)
        canvas.drawCircle(cx, cy, radius * 1.35f, paint)
        canvas.restore()

        paint.style = Paint.Style.FILL
    }
}