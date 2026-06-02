package org.archuser.fuelmath

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.abs

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOnSurface, R.color.text_primary)
        textSize = 16f.sp()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOutline, R.color.chart_axis)
        strokeWidth = 1f.dp()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOutlineVariant, R.color.chart_grid)
        strokeWidth = 1f.dp()
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(androidx.appcompat.R.attr.colorPrimary, R.color.chart_line)
        strokeWidth = 3f.dp()
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorSecondary, R.color.chart_point)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.text_secondary)
        textSize = 12f.sp()
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.text_secondary)
        textSize = 14f.sp()
    }
    private val textBounds = Rect()

    private var title: String = ""
    private var unitLabel: String = ""
    private var points: List<ChartPoint> = emptyList()

    fun setChartData(title: String, unitLabel: String, points: List<ChartPoint>) {
        this.title = title
        this.unitLabel = unitLabel
        this.points = points.filter { it.value.isFinite() }
        contentDescription = "$title chart with ${this.points.size} points"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 180.dpInt()
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val measuredWidth = resolveSize(320.dpInt(), widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = 14f.dp()
        val top = 34f.dp()
        val left = 44f.dp()
        val right = width - padding
        val bottom = height - 28f.dp()

        canvas.drawText(title, padding, 22f.dp(), titlePaint)

        if (points.isEmpty()) {
            val message = "No chart data yet"
            emptyPaint.getTextBounds(message, 0, message.length, textBounds)
            canvas.drawText(
                message,
                (width - textBounds.width()) / 2f,
                (height + textBounds.height()) / 2f,
                emptyPaint,
            )
            return
        }

        canvas.drawLine(left, bottom, right, bottom, axisPaint)
        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, gridPaint)

        val minValue = points.minOf { it.value }
        val maxValue = points.maxOf { it.value }
        val range = (maxValue - minValue).takeIf { abs(it) > 0.000001 } ?: 1.0

        fun xFor(index: Int): Float =
            if (points.size == 1) {
                (left + right) / 2f
            } else {
                left + ((right - left) * (index.toFloat() / (points.lastIndex).toFloat()))
            }

        fun yFor(value: Double): Float =
            bottom - ((bottom - top) * ((value - minValue) / range).toFloat())

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        points.forEachIndexed { index, point ->
            canvas.drawCircle(xFor(index), yFor(point.value), 4f.dp(), pointPaint)
        }

        canvas.drawText(formatValue(maxValue), padding, top + 4f.dp(), labelPaint)
        canvas.drawText(formatValue(minValue), padding, bottom, labelPaint)

        val first = points.first().label
        val last = points.last().label
        canvas.drawText(first, left, height - 8f.dp(), labelPaint)
        labelPaint.getTextBounds(last, 0, last.length, textBounds)
        canvas.drawText(last, right - textBounds.width(), height - 8f.dp(), labelPaint)
    }

    private fun formatValue(value: Double): String {
        val formatted = if (abs(value) >= 100.0) {
            "%.0f".format(value)
        } else {
            "%.2f".format(value)
        }
        return if (unitLabel.isBlank()) formatted else "$formatted $unitLabel"
    }

    private fun Float.dp(): Float = this * resources.displayMetrics.density

    private fun Float.sp(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)

    private fun Int.dpInt(): Int = (this * resources.displayMetrics.density).toInt()

    private fun themedColor(attr: Int, fallbackResId: Int): Int =
        MaterialColors.getColor(context, attr, ContextCompat.getColor(context, fallbackResId))
}
