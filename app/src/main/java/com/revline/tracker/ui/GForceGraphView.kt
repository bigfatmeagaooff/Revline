package com.revline.tracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.revline.tracker.data.GForcePoint
import kotlin.math.abs
import kotlin.math.max

/**
 * Minimal Canvas-drawn line graph of G over the trip timeline: forward G (accel/brake)
 * and lateral G plotted against time, with a zero baseline. No charting library — kept
 * deliberately small, same "functional over polished" standard as the rest of the app.
 */
class GForceGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<GForcePoint> = emptyList()

    private val forwardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F") // revline red
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val lateralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2") // blue
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    fun setData(data: List<GForcePoint>) {
        points = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f

        // Zero baseline.
        canvas.drawLine(0f, midY, w, midY, axisPaint)

        if (points.size < 2) return

        val first = points.first().timestamp
        val last = points.last().timestamp
        val span = (last - first).toFloat().takeIf { it > 0f } ?: return

        // Symmetric scale; floor at 0.5G so a calm drive doesn't look jagged.
        var maxAbs = 0.5f
        for (p in points) {
            maxAbs = max(maxAbs, max(abs(p.forwardG), abs(p.lateralG)))
        }
        val pad = 8f
        val scale = (midY - pad) / maxAbs

        canvas.drawPath(buildPath(first, span, scale, midY, w) { it.forwardG }, forwardPaint)
        canvas.drawPath(buildPath(first, span, scale, midY, w) { it.lateralG }, lateralPaint)
    }

    private inline fun buildPath(
        first: Long,
        span: Float,
        scale: Float,
        midY: Float,
        w: Float,
        value: (GForcePoint) -> Float
    ): Path {
        val path = Path()
        points.forEachIndexed { index, p ->
            val x = (p.timestamp - first) / span * w
            val y = midY - value(p) * scale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        return path
    }
}
