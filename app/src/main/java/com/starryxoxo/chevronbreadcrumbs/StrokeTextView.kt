package com.starryxoxo.chevronbreadcrumbs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class StrokeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var isStrokeEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var strokeWidthValue: Float = 2f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (isStrokeEnabled && strokeWidthValue > 0) {
            val states = textColors
            
            // Draw the stroke
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidthValue
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            setTextColor(0xFF121212.toInt())
            super.onDraw(canvas)

            // Draw the main text on top
            paint.style = Paint.Style.FILL
            setTextColor(states)
            super.onDraw(canvas)
        } else {
            super.onDraw(canvas)
        }
    }
}
