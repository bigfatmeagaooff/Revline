package com.revline.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.revline.tracker.R
import com.revline.tracker.data.Trip
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders a shareable drive card as a [Bitmap] using plain Canvas drawing — no image
 * library. Revline's "Time Slip" look: the drive as a drag-strip timing printout —
 * torn-ticket edges, a run stub, redline trap speed, ruled rows with dotted leaders.
 *
 * Sized 1080×1350 (4:5), the portrait ratio Instagram/Snapchat post well.
 */
object TripCardGenerator {

    private const val W = 1080
    private const val H = 1350
    private const val MARGIN = 48f          // ink border around the slip
    private const val PAD = 56f             // slip inner padding
    private const val NOTCH_R = 22f

    // Palette mirrors colors.xml (Time Slip).
    private const val INK = 0xFF0E0F12.toInt()
    private const val SLIP = 0xFF16181D.toInt()
    private const val RULE = 0xFF2A2E36.toInt()
    private const val RED = 0xFFF5121C.toInt()
    private const val PRINT = 0xFFECEEF2.toInt()
    private const val DIM = 0xFF8A9099.toInt()
    private const val FAINT = 0xFF565C66.toInt()

    private val STUB_FMT = SimpleDateFormat("EEE d MMM yyyy · h:mm a", Locale.getDefault())

    fun render(
        context: Context,
        trip: Trip,
        segments: List<SpeedCalculator.Segment>,
        zeroToHundredSec: Float?
    ): Bitmap {
        val display = ResourcesCompat.getFont(context, R.font.rl_display) ?: Typeface.DEFAULT_BOLD
        val mono = ResourcesCompat.getFont(context, R.font.rl_mono) ?: Typeface.MONOSPACE
        val body = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(INK)

        val slipL = MARGIN
        val slipR = W - MARGIN
        val slipT = MARGIN
        val slipB = H - MARGIN

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        fill.color = SLIP
        canvas.drawRect(slipL, slipT, slipR, slipB, fill)
        drawPerforations(canvas, slipL, slipR, slipT, slipB, fill)

        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        val left = slipL + PAD
        val right = slipR - PAD

        // --- Masthead stub ---
        text.typeface = mono
        text.color = FAINT
        text.textSize = 24f
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("REVLINE TIMING", left, slipT + 78f, text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            STUB_FMT.format(Date(trip.startTime)).uppercase(Locale.getDefault()),
            right, slipT + 78f, text
        )

        // --- Hero: trap speed ---
        text.textAlign = Paint.Align.LEFT
        text.typeface = body
        text.color = FAINT
        text.textSize = 28f
        canvas.drawText("TRAP SPEED", left, slipT + 150f, text)

        val topSpeed = trip.topSpeedKmh?.takeIf { it > 0f }?.roundToInt()?.toString() ?: "—"
        text.typeface = display
        text.color = RED
        text.textSize = 300f
        val heroBaseline = slipT + 400f
        canvas.drawText(topSpeed, left, heroBaseline, text)
        val heroWidth = text.measureText(topSpeed)
        text.color = DIM
        text.textSize = 64f
        canvas.drawText("km/h", left + heroWidth + 24f, heroBaseline, text)

        // --- Ruled rows ---
        var y = heroBaseline + 70f
        drawRule(canvas, left, right, y, fill)
        y += 20f
        val rows = buildList {
            trip.distanceKm?.let { add("DISTANCE" to String.format(Locale.getDefault(), "%.1f km", it)) }
            trip.actualDurationMinutes?.let {
                val s = (it * 60).roundToInt()
                add("ELAPSED" to String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60))
            }
            trip.avgSpeedKmh?.takeIf { it > 0f }?.let {
                add("AVG SPEED" to "${it.roundToInt()} km/h")
            }
            zeroToHundredSec?.let { add("0–100" to String.format(Locale.getDefault(), "%.2f s", it)) }
        }
        for ((k, v) in rows) {
            y += 74f
            drawSlipRow(canvas, text, body, mono, left, right, y, k, v)
            drawRule(canvas, left, right, y + 26f, fill)
        }

        // --- Route panel ---
        val routeTop = y + 60f
        val routeBottom = (H - MARGIN - PAD - 96f).coerceAtLeast(routeTop + 260f)
        fill.color = INK
        canvas.drawRect(left, routeTop, right, routeBottom, fill)
        fill.color = SLIP
        drawRoute(canvas, segments, left + 24f, routeTop + 24f, right - 24f, routeBottom - 24f)

        // --- Footer: filed stamp + car ---
        text.typeface = mono
        text.textAlign = Paint.Align.LEFT
        text.textSize = 24f
        val filed = trip.uploadedAt != null
        text.color = if (filed) 0xFFD8FF3E.toInt() else FAINT
        canvas.drawText(
            if (filed) "✓ FILED TO LEADERBOARD" else "LOCAL RUN — NOT FILED",
            left, routeBottom + 54f, text
        )

        val profile = CarProfile.load(context)
        val car = listOfNotNull(profile.year?.toString(), profile.make, profile.model).joinToString(" ")
        if (car.isNotBlank()) {
            text.textAlign = Paint.Align.RIGHT
            text.color = DIM
            canvas.drawText(car.uppercase(Locale.getDefault()), right, routeBottom + 54f, text)
        }

        return bitmap
    }

    private fun drawPerforations(
        canvas: Canvas, l: Float, r: Float, t: Float, b: Float, fill: Paint
    ) {
        val prev = fill.color
        fill.color = INK
        var x = l + NOTCH_R
        while (x < r) {
            canvas.drawCircle(x, t, NOTCH_R, fill)
            canvas.drawCircle(x, b, NOTCH_R, fill)
            x += NOTCH_R * 2.4f
        }
        fill.color = prev
    }

    private fun drawRule(canvas: Canvas, l: Float, r: Float, y: Float, fill: Paint) {
        val prev = fill.color
        fill.color = RULE
        canvas.drawRect(l, y, r, y + 1.5f, fill)
        fill.color = prev
    }

    private fun drawSlipRow(
        canvas: Canvas, text: Paint, body: Typeface, mono: Typeface,
        left: Float, right: Float, baseline: Float, label: String, value: String
    ) {
        text.typeface = body
        text.color = DIM
        text.textSize = 30f
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(label, left, baseline, text)
        val labelW = text.measureText(label)

        text.typeface = mono
        text.color = PRINT
        text.textSize = 34f
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, right, baseline, text)
        val valueW = text.measureText(value)

        // dotted leader between them
        text.color = FAINT
        text.textAlign = Paint.Align.LEFT
        val dotStart = left + labelW + 16f
        val dotEnd = right - valueW - 16f
        var dx = dotStart
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; style = Paint.Style.FILL }
        while (dx < dotEnd) {
            canvas.drawCircle(dx, baseline - 10f, 2f, dot)
            dx += 12f
        }
    }

    private fun drawRoute(
        canvas: Canvas, segments: List<SpeedCalculator.Segment>,
        left: Float, top: Float, right: Float, bottom: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (segments.size < 2) {
            paint.color = FAINT
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.MONOSPACE
            canvas.drawText("NO ROUTE DATA", (left + right) / 2f, (top + bottom) / 2f, paint)
            return
        }

        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        for (s in segments) {
            minLat = minOf(minLat, s.startLat, s.endLat); maxLat = maxOf(maxLat, s.startLat, s.endLat)
            minLon = minOf(minLon, s.startLon, s.endLon); maxLon = maxOf(maxLon, s.startLon, s.endLon)
        }
        val latSpan = (maxLat - minLat).takeIf { it > 1e-9 } ?: 1e-9
        val lonSpan = (maxLon - minLon).takeIf { it > 1e-9 } ?: 1e-9

        val boxW = right - left
        val boxH = bottom - top
        val scale = minOf(boxW / lonSpan, boxH / latSpan)
        val offsetX = left + (boxW - (lonSpan * scale).toFloat()) / 2f
        val offsetY = top + (boxH - (latSpan * scale).toFloat()) / 2f

        fun px(lon: Double) = (offsetX + (lon - minLon) * scale).toFloat()
        fun py(lat: Double) = (offsetY + (maxLat - lat) * scale).toFloat()

        val speeds = segments.map { it.speedKmh }.sorted()
        val lo = speeds[(speeds.size * 0.05f).toInt().coerceIn(0, speeds.size - 1)]
        val hi = speeds[(speeds.size * 0.95f).toInt().coerceIn(0, speeds.size - 1)]

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        for (s in segments) {
            val t = if (hi > lo) ((s.speedKmh - lo) / (hi - lo)).coerceIn(0f, 1f) else 0.5f
            paint.color = speedColor(t)
            canvas.drawLine(px(s.startLon), py(s.startLat), px(s.endLon), py(s.endLat), paint)
        }
    }

    /** Cold slate (slow) → redline (fast), matching the summary map. */
    private fun speedColor(t: Float): Int {
        val e = t.coerceIn(0f, 1f).let { it * it }
        val cr = 0x55; val cg = 0x61; val cb = 0x70
        val hr = 0xF5; val hg = 0x12; val hb = 0x1C
        return Color.rgb(
            (cr + (hr - cr) * e).roundToInt(),
            (cg + (hg - cg) * e).roundToInt(),
            (cb + (hb - cb) * e).roundToInt()
        )
    }

    fun writeToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "revline-drive.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
