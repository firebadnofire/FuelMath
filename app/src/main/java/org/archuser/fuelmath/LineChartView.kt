package org.archuser.fuelmath

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
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
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorPrimaryContainer, R.color.fuel_primary_light)
        style = Paint.Style.FILL
        alpha = 180
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorSecondary, R.color.chart_point)
        style = Paint.Style.FILL
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.text_secondary)
        textSize = 11f.sp()
    }
    private val pointLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOnSurface, R.color.text_primary)
        textSize = 11f.sp()
        typeface = Typeface.DEFAULT_BOLD
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themedColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.text_secondary)
        textSize = 13f.sp()
    }
    private val textBounds = Rect()

    private var unitLabel: String = ""
    private var points: List<ChartPoint> = emptyList()
    private var showArea: Boolean = false
    private var showPointLabels: Boolean = true

    fun setChartData(
        unitLabel: String,
        points: List<ChartPoint>,
        showArea: Boolean = false,
        showPointLabels: Boolean = true,
    ) {
        this.unitLabel = unitLabel
        this.points = points.filter { it.value.isFinite() }
        this.showArea = showArea
        this.showPointLabels = showPointLabels
        contentDescription = "Chart with ${this.points.size} points"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 116.dpInt()
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val measuredWidth = resolveSize(320.dpInt(), widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
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

        val rawMinValue = points.minOf { it.value }
        val rawMaxValue = points.maxOf { it.value }
        val tickValues = buildTicks(rawMinValue, rawMaxValue, 4)
        val minValue = tickValues.minOrNull() ?: rawMinValue
        val maxValue = tickValues.maxOrNull() ?: rawMaxValue
        val range = (maxValue - minValue).takeIf { abs(it) > 0.000001 } ?: 1.0
        val maxTickLabelWidth = tickValues.maxOfOrNull { labelWidth(formatAxisValue(it), axisLabelPaint) } ?: 0f
        val top = 12f.dp()
        val left = 14f.dp() + maxTickLabelWidth + 10f.dp()
        val right = width - 12f.dp()
        val bottom = height - 20f.dp()

        fun xFor(index: Int): Float =
            if (points.size == 1) {
                (left + right) / 2f
            } else {
                left + ((right - left) * (index.toFloat() / points.lastIndex.toFloat()))
            }

        fun yFor(value: Double): Float =
            bottom - ((bottom - top) * ((value - minValue) / range).toFloat())

        tickValues.forEachIndexed { tickIndex, tickValue ->
            val y = yFor(tickValue)
            canvas.drawLine(left, y, right, y, if (tickValue == minValue) axisPaint else gridPaint)
            val label = formatAxisValue(tickValue)
            val labelWidth = labelWidth(label, axisLabelPaint)
            val labelBaseline = (y + textHeight(axisLabelPaint) / 2f).coerceIn(top + textHeight(axisLabelPaint), bottom)
            canvas.drawText(label, left - 8f.dp() - labelWidth, labelBaseline, axisLabelPaint)
        }

        val linePath = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.value)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        if (showArea && points.size >= 2) {
            val areaPath = Path(linePath)
            areaPath.lineTo(right, bottom)
            areaPath.lineTo(left, bottom)
            areaPath.close()
            canvas.drawPath(areaPath, areaPaint)
        }

        canvas.drawPath(linePath, linePaint)

        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.value)
            canvas.drawCircle(x, y, 4f.dp(), pointPaint)
            if (showPointLabels && points.size <= 6) {
                val label = formatPointValue(point.value)
                pointLabelPaint.getTextBounds(label, 0, label.length, textBounds)
                val labelX = (x - textBounds.width() / 2f).coerceIn(left, right - textBounds.width().toFloat())
                val labelY = (y - 8f.dp()).coerceAtLeast(top + textBounds.height())
                canvas.drawText(label, labelX, labelY, pointLabelPaint)
            }
        }

        xAxisIndexes(points.size).forEach { index ->
            val label = points[index].label
            val x = xFor(index)
            axisLabelPaint.getTextBounds(label, 0, label.length, textBounds)
            val labelX = (x - textBounds.width() / 2f).coerceIn(left, right - textBounds.width().toFloat())
            canvas.drawText(label, labelX, height - 6f.dp(), axisLabelPaint)
        }
    }

    private fun minChartValue(minValue: Double, maxValue: Double): Double =
        when {
            minValue >= 0.0 && maxValue > 0.0 -> 0.0
            else -> minValue
        }

    private fun maxChartValue(minValue: Double, maxValue: Double): Double =
        if (abs(maxValue - minValue) < 0.000001) {
            maxValue + 1.0
        } else {
            maxValue
        }

    private fun buildTicks(minValue: Double, maxValue: Double, count: Int): List<Double> {
        if (count <= 1) return listOf(maxValue)
        if (abs(maxValue - minValue) < 0.000001) {
            val single = if (minValue >= 0.0) listOf(0.0, max(1.0, maxValue)) else listOf(minValue, maxValue + 1.0)
            return single.distinct().sortedDescending()
        }

        val adjustedMin = if (minValue >= 0.0) 0.0 else minValue
        val rawStep = (maxValue - adjustedMin) / (count - 1).toDouble()
        val step = niceStep(rawStep)
        val niceMin = floor(adjustedMin / step) * step
        val niceMax = ceil(maxValue / step) * step

        val ticks = mutableListOf<Double>()
        var value = niceMin
        while (value <= niceMax + (step / 2.0)) {
            ticks += value
            value += step
        }
        return ticks.sortedDescending()
    }

    private fun xAxisIndexes(size: Int): List<Int> {
        if (size <= 6) return (0 until size).toList()
        val indexes = listOf(0, (size - 1) / 3, ((size - 1) * 2) / 3, size - 1)
        return indexes.distinct()
    }

    private fun formatAxisValue(value: Double): String =
        if (isCurrencyUnit()) {
            "$${value.roundToInt()}"
        } else if (abs(value) >= 100.0) {
            value.roundToInt().toString()
        } else {
            "%.1f".format(value)
        }

    private fun formatPointValue(value: Double): String =
        if (isCurrencyUnit()) {
            "$${value.roundToInt()}"
        } else if (abs(value) >= 100.0) {
            value.roundToInt().toString()
        } else {
            "%.1f".format(value)
        }

    private fun isCurrencyUnit(): Boolean = unitLabel.equals("USD", ignoreCase = true)

    private fun niceStep(value: Double): Double {
        if (value <= 0.0) return 1.0
        val exponent = floor(log10(value))
        val fraction = value / 10.0.pow(exponent)
        val niceFraction = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return niceFraction * 10.0.pow(exponent)
    }

    private fun labelWidth(text: String, paint: Paint): Float = paint.measureText(text)

    private fun textHeight(paint: Paint): Float = paint.fontMetrics.run { bottom - top }

    private fun Float.dp(): Float = this * resources.displayMetrics.density

    private fun Float.sp(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)

    private fun Int.dpInt(): Int = (this * resources.displayMetrics.density).toInt()

    private fun themedColor(attr: Int, fallbackResId: Int): Int =
        MaterialColors.getColor(context, attr, ContextCompat.getColor(context, fallbackResId))
}
