package com.example.pocketbook_expensetracker.ui.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class SpendingTrendView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val navy = Color.rgb(20, 33, 63)
    private val teal = Color.rgb(12, 144, 147)
    private val grey = Color.rgb(102, 119, 149)
    private var values = emptyList<Double>()
    private var labels = emptyList<String>()

    fun setData(amounts: List<Double>, names: List<String>) {
        values = amounts
        labels = names
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val density = resources.displayMetrics.density
        val left = 12f * density
        val top = 12f * density
        val bottom = height - 28f * density
        val chartHeight = bottom - top
        val chartWidth = width - left * 2
        val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val slotWidth = chartWidth / values.size
        val barWidth = (slotWidth * 0.55f).coerceAtMost(38f * density)

        paint.color = Color.rgb(232, 237, 243)
        paint.strokeWidth = density
        canvas.drawLine(left, bottom, width - left, bottom, paint)

        values.forEachIndexed { index, amount ->
            val barHeight = (amount / maxValue * chartHeight * 0.85).toFloat()
            val centerX = left + slotWidth * index + slotWidth / 2
            val rect = RectF(centerX - barWidth / 2, bottom - barHeight, centerX + barWidth / 2, bottom)

            paint.color = if (amount == maxValue) teal else Color.rgb(147, 217, 214)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, 6f * density, 6f * density, paint)

            paint.color = grey
            paint.textSize = 10f * resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(labels.getOrElse(index) { "" }, centerX, bottom + 17f * density, paint)
        }
    }
}
