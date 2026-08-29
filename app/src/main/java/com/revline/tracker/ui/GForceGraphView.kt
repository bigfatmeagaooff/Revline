package com.revline.tracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.revline.tracker.R
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

    private companion object {
        /** Cap on plotted points — plenty for a smooth trace on a short graph. */
        const val MAX_POINTS = 2000
    }

    private fun c(res: Int) = ContextCompat.getColor(context, res)

    private val forwardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.redline)              // accel / brake
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val lateralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.print_dim)            // cornering
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.rule)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c(R.color.rule_dim)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setData(data: List<GForcePoint>) {
        // A long drive can hold tens of thousands of G points; drawing a lineTo for
        // every one on each frame is needless (the view is ~160dp tall). Evenly
        // downsample to a cap that still traces the same shape.
        points = if (data.size > MAX_POINTS) {
            val step = data.size.toFloat() / MAX_POINTS
            ArrayList<GForcePoint>(MAX_POINTS).apply {
                var i = 0f
                while (i < data.size) {
                    add(data[i.toInt()])
                    i += step
                }
            }
        } else {
            data
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f

        if (points.size < 2) {
            canvas.drawLine(0f, midY, w, midY, axisPaint)
            return
        }

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

        // ±0.5 G gridlines, then the zero baseline on top.
        val halfG = 0.5f * scale
        if (halfG < midY - pad) {
            canvas.drawLine(0f, midY - halfG, w, midY - halfG, gridPaint)
            canvas.drawLine(0f, midY + halfG, w, midY + halfG, gridPaint)
        }
        canvas.drawLine(0f, midY, w, midY, axisPaint)

        drawTrace(canvas, first, span, scale, midY, w, lateralPaint) { it.lateralG }
        drawTrace(canvas, first, span, scale, midY, w, forwardPaint) { it.forwardG }
    }

    private fun drawTrace(
        canvas: Canvas, first: Long, span: Float, scale: Float, midY: Float, w: Float,
        paint: Paint, value: (GForcePoint) -> Float
    ) {
        canvas.drawPath(buildPath(first, span, scale, midY, w, value), paint)
        val last = points.last()
        dotPaint.color = paint.color
        canvas.drawCircle(w - 1f, midY - value(last) * scale, 4f, dotPaint)
    }

    private fun buildPath(
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
