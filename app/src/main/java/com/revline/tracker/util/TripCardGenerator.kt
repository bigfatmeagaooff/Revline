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
 * library. Revline's own look: near-black background, racing-red hero number, Barlow
 * Condensed display type, and a speed-coloured mini route.
 *
 * Sized 1080×1350 (4:5), which is the portrait ratio Instagram/Snapchat post well.
 */
object TripCardGenerator {

    private const val W = 1080
    private const val H = 1350
    private const val PAD = 72f

    // Palette mirrors colors.xml so the card matches the app.
    private const val BG = 0xFF0A0A0A.toInt()
    private const val CARD = 0xFF111111.toInt()
    private const val RED = 0xFFE8000D.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GREY = 0xFF888888.toInt()
    private const val MUTED = 0xFF444444.toInt()

    private val DATE_FMT = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())

    /**
     * Draws the card. [segments] are the cleaned route segments (may be empty — the
     * route panel is simply skipped), [zeroToHundredSec] is optional.
     */
    fun render(
        context: Context,
        trip: Trip,
        segments: List<SpeedCalculator.Segment>,
        zeroToHundredSec: Float?
    ): Bitmap {
        val display = ResourcesCompat.getFont(context, R.font.barlow_condensed_bold)
            ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)

        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // --- Header: wordmark + red rule, date on the right ---
        text.typeface = display
        text.color = WHITE
        text.textSize = 58f
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("REVLINE", PAD, PAD + 50f, text)

        fill.color = RED
        canvas.drawRect(PAD, PAD + 66f, PAD + 132f, PAD + 72f, fill)

        text.typeface = body
        text.color = GREY
        text.textSize = 30f
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(DATE_FMT.format(Date(trip.startTime)).uppercase(Locale.getDefault()), W - PAD, PAD + 50f, text)

        // --- Hero: top speed ---
        val topSpeed = trip.topSpeedKmh?.takeIf { it > 0f }?.roundToInt()?.toString() ?: "—"
        text.typeface = display
        text.color = RED
        text.textSize = 300f
        text.textAlign = Paint.Align.LEFT
        val heroBaseline = 480f
        canvas.drawText(topSpeed, PAD, heroBaseline, text)

        val heroWidth = text.measureText(topSpeed)
        text.color = GREY
        text.textSize = 64f
        canvas.drawText("KM/H", PAD + heroWidth + 20f, heroBaseline, text)

        text.typeface = body
        text.color = MUTED
        text.textSize = 28f
        canvas.drawText("TOP SPEED", PAD, heroBaseline + 48f, text)

        // --- Route panel ---
        val routeTop = heroBaseline + 96f
        val routeBottom = routeTop + 420f
        fill.color = CARD
        canvas.drawRect(PAD, routeTop, W - PAD, routeBottom, fill)
        drawRoute(canvas, segments, PAD + 28f, routeTop + 28f, W - PAD - 28f, routeBottom - 28f)

        // --- Stat row ---
        val statTop = routeBottom + 86f
        val third = (W - PAD * 2) / 3f
        stat(canvas, display, body, PAD, statTop, "DISTANCE",
            trip.distanceKm?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—", "KM")
        stat(canvas, display, body, PAD + third, statTop, "DURATION",
            trip.actualDurationMinutes?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—", "MIN")
        stat(canvas, display, body, PAD + third * 2, statTop, "0–100",
            zeroToHundredSec?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—", "S")

        // --- Footer: car (from the saved car profile) ---
        val profile = CarProfile.load(context)
        val car = listOfNotNull(profile.year?.toString(), profile.make, profile.model)
            .joinToString(" ")
        if (car.isNotBlank()) {
            text.typeface = body
            text.color = GREY
            text.textSize = 32f
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(car.uppercase(Locale.getDefault()), PAD, H - PAD, text)
        }

        return bitmap
    }

    /** One stat column: small label, big number, small unit. */
    private fun stat(
        canvas: Canvas,
        display: Typeface,
        body: Typeface,
        x: Float,
        top: Float,
        label: String,
        value: String,
        unit: String
    ) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.textAlign = Paint.Align.LEFT

        text.typeface = body
        text.color = MUTED
        text.textSize = 26f
        canvas.drawText(label, x, top, text)

        text.typeface = display
        text.color = WHITE
        text.textSize = 96f
        canvas.drawText(value, x, top + 96f, text)

        val valueWidth = text.measureText(value)
        text.color = GREY
        text.textSize = 34f
        canvas.drawText(unit, x + valueWidth + 10f, top + 96f, text)
    }

    /**
     * Draws the route scaled to fit the given box, preserving aspect ratio, coloured
     * by speed the same way the summary map is. Falls back to a "no route" note.
     */
    private fun drawRoute(
        canvas: Canvas,
        segments: List<SpeedCalculator.Segment>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (segments.size < 2) {
            paint.color = MUTED
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("NO ROUTE DATA", (left + right) / 2f, (top + bottom) / 2f, paint)
            return
        }

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (s in segments) {
            minLat = minOf(minLat, s.startLat, s.endLat)
            maxLat = maxOf(maxLat, s.startLat, s.endLat)
            minLon = minOf(minLon, s.startLon, s.endLon)
            maxLon = maxOf(maxLon, s.startLon, s.endLon)
        }
        val latSpan = (maxLat - minLat).takeIf { it > 1e-9 } ?: 1e-9
        val lonSpan = (maxLon - minLon).takeIf { it > 1e-9 } ?: 1e-9

        // Uniform scale so the shape isn't distorted, then centre it in the box.
        val boxW = right - left
        val boxH = bottom - top
        val scale = minOf(boxW / lonSpan, boxH / latSpan)
        val drawW = (lonSpan * scale).toFloat()
        val drawH = (latSpan * scale).toFloat()
        val offsetX = left + (boxW - drawW) / 2f
        val offsetY = top + (boxH - drawH) / 2f

        fun px(lon: Double) = (offsetX + (lon - minLon) * scale).toFloat()
        // Latitude grows northward but screen Y grows downward, so invert.
        fun py(lat: Double) = (offsetY + (maxLat - lat) * scale).toFloat()

        val speeds = segments.map { it.speedKmh }.sorted()
        val lo = speeds[(speeds.size * 0.05f).toInt().coerceIn(0, speeds.size - 1)]
        val hi = speeds[(speeds.size * 0.95f).toInt().coerceIn(0, speeds.size - 1)]

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.strokeCap = Paint.Cap.ROUND

        for (s in segments) {
            val t = if (hi > lo) ((s.speedKmh - lo) / (hi - lo)).coerceIn(0f, 1f) else 0.5f
            paint.color = speedColor(t)
            canvas.drawLine(px(s.startLon), py(s.startLat), px(s.endLon), py(s.endLat), paint)
        }
    }

    /** Green (slow) → yellow → red (fast), matching the summary map. */
    private fun speedColor(t: Float): Int = if (t < 0.5f) {
        Color.rgb((255 * (t / 0.5f)).roundToInt(), 255, 0)
    } else {
        Color.rgb(255, (255 * (1 - (t - 0.5f) / 0.5f)).roundToInt(), 0)
    }

    /**
     * Writes the card to app cache as a PNG and returns the file, ready to hand to a
     * FileProvider share intent. Old cards are replaced rather than piling up.
     */
    fun writeToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "revline-drive.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
