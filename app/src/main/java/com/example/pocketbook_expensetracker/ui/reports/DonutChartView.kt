package com.example.pocketbook_expensetracker.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DonutChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = listOf(
        Color.rgb(12, 144, 147),
        Color.rgb(243, 172, 70),
        Color.rgb(91, 119, 208),
        Color.rgb(233, 54, 85),
        Color.rgb(142, 111, 211),
        Color.rgb(70, 185, 150)
    )
    private var values = emptyList<Double>()

    fun setData(amounts: List<Double>) {
        values = amounts.filter { it > 0 }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val stroke = size * 0.16f
        val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.BUTT

        val total = values.sum()
        if (total <= 0) {
            paint.color = Color.LTGRAY
            canvas.drawArc(rect, 0f, 360f, false, paint)
            return
        }

        var startAngle = -90f

        values.forEachIndexed { index, value ->
            val sweepAngle = (value / total * 360).toFloat()
            paint.color = colors[index % colors.size]
            canvas.drawArc(rect, startAngle, sweepAngle, false, paint)
            startAngle += sweepAngle
        }
    }
}
