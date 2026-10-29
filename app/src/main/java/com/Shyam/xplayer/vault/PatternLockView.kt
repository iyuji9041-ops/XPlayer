package com.Shyam.xplayer.vault

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
 * PatternLockView: Custom 3x3 dot matrix pattern lock view.
 * Responsive pattern drawing canvas that records connected dot coordinates
 * and outputs an encoded pattern string for PBKDF2 hashing.
 */
class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density: Float
        get() = context.resources.displayMetrics.density

    private val dotRadius: Float
        get() = 9f * density

    private val dotTouchRadius: Float
        get() = 42f * density

    private val selectedDotRadius: Float
        get() = 15f * density

    private val normalDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#77FFFFFF")
        style = Paint.Style.FILL
    }

    private val normalDotOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9900E676")
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val errorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FF5252")
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    data class Dot(val index: Int, val x: Float, val y: Float)

    private val dots = ArrayList<Dot>()
    private val selectedDots = ArrayList<Dot>()
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false
    private var isErrorState = false

    var onPatternListener: ((String) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dots.clear()

        val size = w.coerceAtMost(h).toFloat()
        val offsetX = (w - size) / 2f
        val offsetY = (h - size) / 2f

        val padding = size * 0.12f
        val step = (size - 2f * padding) / 2f

        var idx = 0
        for (row in 0..2) {
            for (col in 0..2) {
                val cx = offsetX + padding + col * step
                val cy = offsetY + padding + row * step
                dots.add(Dot(idx++, cx, cy))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val activeLinePaint = if (isErrorState) errorLinePaint else linePaint
        activeLinePaint.strokeWidth = 5f * density

        if (selectedDots.size > 1) {
            for (i in 0 until selectedDots.size - 1) {
                val d1 = selectedDots[i]
                val d2 = selectedDots[i + 1]
                canvas.drawLine(d1.x, d1.y, d2.x, d2.y, activeLinePaint)
            }
        }

        if (isDrawing && selectedDots.isNotEmpty()) {
            val last = selectedDots.last()
            canvas.drawLine(last.x, last.y, currentX, currentY, activeLinePaint)
        }

        for (dot in dots) {
            val isSelected = selectedDots.contains(dot)
            if (isSelected) {
                canvas.drawCircle(dot.x, dot.y, selectedDotRadius, selectedDotPaint)
                canvas.drawCircle(dot.x, dot.y, selectedDotRadius * 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#3300E676")
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                })
            } else {
                canvas.drawCircle(dot.x, dot.y, dotRadius, normalDotPaint)
                canvas.drawCircle(dot.x, dot.y, dotRadius * 2.2f, normalDotOuterPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resetPattern()
                isDrawing = true
                currentX = event.x
                currentY = event.y
                checkDotHit(currentX, currentY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = event.x
                    currentY = event.y
                    checkDotHit(currentX, currentY)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    if (selectedDots.isNotEmpty()) {
                        val patternString = selectedDots.joinToString("") { it.index.toString() }
                        onPatternListener?.invoke(patternString)
                    }
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkDotHit(x: Float, y: Float) {
        for (dot in dots) {
            if (!selectedDots.contains(dot)) {
                val dx = dot.x - x
                val dy = dot.y - y
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist <= dotTouchRadius) {
                    selectedDots.add(dot)
                    break
                }
            }
        }
    }

    fun setErrorState(error: Boolean) {
        isErrorState = error
        invalidate()
    }

    fun resetPattern() {
        selectedDots.clear()
        isErrorState = false
        isDrawing = false
        invalidate()
    }
}
