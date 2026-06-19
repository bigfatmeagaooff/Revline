package com.revline.tracker

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.GForcePoint
import com.revline.tracker.data.TrackPoint
import com.revline.tracker.data.Trip
import com.revline.tracker.data.TripRepository
import com.revline.tracker.databinding.ActivityTripSummaryBinding
import com.revline.tracker.util.GForceCalculator
import com.revline.tracker.util.SpeedCalculator
import com.revline.tracker.util.TripStatsCalculator
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Post-trip stats: distance, duration, avg/top speed, the predicted-vs-actual delta,
 * the enhanced drive-detail stats, a G-force summary + timeline graph, and a
 * speed-colored route map. Read-only — used right after a drive and from history.
 *
 * Handles empty/sparse trips (too few GPS points to be meaningful, e.g. an indoor
 * smoke test): distance/speed read "—" instead of a misleading 0, the map shows a
 * placeholder, and the detail stats are omitted — while a genuinely slow-but-tracked
 * drive still shows its real (low) numbers.
 */
class TripSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripSummaryBinding
    private lateinit var repository: TripRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OSMDroid needs a user agent set (OSM tile policy) before the map is used.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityTripSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TripRepository.getInstance(this)
        setUpMap()

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        if (tripId <= 0L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val trip = repository.getTrip(tripId)
            if (trip == null) {
                finish()
                return@launch
            }
            val trackPoints = repository.getTrackPoints(tripId)
            val gForcePoints = repository.getGForcePoints(tripId)
            val segments = SpeedCalculator.cleanSegments(trackPoints)
            // "Usable route" = enough confident points to be meaningful (~3+ ⇒ 2+ segments).
            val hasRoute = segments.size >= 2

            bind(trip, hasRoute)
            bindDetail(trip, trackPoints, hasRoute)
            bindGForce(trip, gForcePoints)
            renderRoute(segments, hasRoute)
        }
    }

    private fun bind(trip: Trip, hasRoute: Boolean) {
        binding.distanceValue.text = if (hasRoute && trip.distanceKm != null) {
            getString(R.string.summary_distance_value, trip.distanceKm)
        } else getString(R.string.value_dash)

        // Duration is real regardless of GPS — always show it.
        binding.durationValue.text = trip.actualDurationMinutes?.let {
            getString(R.string.summary_duration_value, it)
        } ?: getString(R.string.value_dash)

        binding.avgSpeedValue.text = if (hasRoute && trip.avgSpeedKmh != null) {
            getString(R.string.summary_speed_value, trip.avgSpeedKmh)
        } else getString(R.string.value_dash)

        binding.topSpeedValue.text = if (hasRoute && trip.topSpeedKmh != null) {
            getString(R.string.summary_speed_value, trip.topSpeedKmh)
        } else getString(R.string.value_dash)

        bindPredictionDelta(trip)
    }

    private fun bindPredictionDelta(trip: Trip) {
        val actualMinutes = trip.actualDurationMinutes
        if (actualMinutes == null) {
            binding.predictionDelta.visibility = View.GONE
            return
        }

        val predicted = trip.predictedMinutes
        val actualRounded = actualMinutes.roundToInt()
        val delta = actualRounded - predicted
        val deltaText = when {
            delta > 0 -> getString(R.string.delta_over, delta)
            delta < 0 -> getString(R.string.delta_under, abs(delta))
            else -> getString(R.string.delta_exact)
        }

        binding.predictionDelta.visibility = View.VISIBLE
        binding.predictionDelta.text = getString(
            R.string.summary_prediction,
            predicted,
            actualRounded,
            deltaText
        )
    }

    // --- Enhanced drive-detail stats (Feature 3) ---

    private fun bindDetail(trip: Trip, trackPoints: List<TrackPoint>, hasRoute: Boolean) {
        if (!hasRoute) {
            binding.detailSection.visibility = View.GONE
            return
        }
        binding.detailSection.visibility = View.VISIBLE

        val durationMillis = trip.actualDurationMinutes?.let { (it * 60_000f).toLong() }
            ?: ((trip.endTime ?: trip.startTime) - trip.startTime)
        val stats = TripStatsCalculator.compute(
            points = trackPoints,
            totalDurationMillis = durationMillis,
            distanceKm = trip.distanceKm ?: 0f
        )

        binding.idleValue.text = formatDurationShort(stats.idleMillis)

        setOptionalRow(
            binding.rowMovingAvg, binding.movingAvgValue,
            stats.movingAvgKmh?.let { getString(R.string.summary_speed_value, it) }
        )
        setOptionalRow(
            binding.rowZeroHundred, binding.zeroHundredValue,
            stats.zeroToHundredSec?.let { getString(R.string.detail_seconds_value, it) }
        )
        setOptionalRow(
            binding.rowZeroSixty, binding.zeroSixtyValue,
            stats.zeroToSixtySec?.let { getString(R.string.detail_seconds_value, it) }
        )

        binding.longestStretchLabel.text =
            getString(R.string.label_longest_stretch, stats.longestStretchThresholdKmh)
        setOptionalRow(
            binding.rowLongestStretch, binding.longestStretchValue,
            if (stats.longestStretchKm > 0f) {
                getString(R.string.detail_distance_value, stats.longestStretchKm)
            } else null
        )
    }

    // --- G-force ---

    private fun bindGForce(trip: Trip, points: List<GForcePoint>) {
        if (points.isEmpty()) {
            binding.gforceSection.visibility = View.GONE
            return
        }
        binding.gforceSection.visibility = View.VISIBLE

        val summary = GForceCalculator.summarize(points)
        binding.maxLateralValue.text = getString(R.string.summary_g_value, summary.maxLateralG)
        binding.maxAccelValue.text = getString(R.string.summary_g_value, summary.maxAccelG)
        binding.maxBrakingValue.text = getString(R.string.summary_g_value, summary.maxBrakingG)
        binding.gforceGraph.setData(points)

        val hardest = GForceCalculator.hardestBraking(points)
        if (hardest != null) {
            val offset = hardest.timestamp - trip.startTime
            binding.hardestBrakingValue.visibility = View.VISIBLE
            binding.hardestBrakingValue.text = getString(
                R.string.hardest_braking_callout,
                hardest.forwardG, // already negative — renders as e.g. "-0.71 G"
                formatOffset(offset)
            )
        } else {
            binding.hardestBrakingValue.visibility = View.GONE
        }
    }

    // --- Route map ---

    private fun setUpMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.overlays.add(CopyrightOverlay(this)) // "© OpenStreetMap contributors"
        // Let map gestures win over the surrounding ScrollView.
        binding.mapView.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun renderRoute(segments: List<SpeedCalculator.Segment>, hasRoute: Boolean) {
        if (!hasRoute) {
            // Don't show a world-zoomed default map for a trip with no usable route.
            binding.mapView.visibility = View.GONE
            binding.mapPlaceholder.visibility = View.VISIBLE
            return
        }
        binding.mapView.visibility = View.VISIBLE
        binding.mapPlaceholder.visibility = View.GONE

        // Relative color range within this trip (percentiles avoid outliers skewing it).
        val sortedSpeeds = segments.map { it.speedKmh }.sorted()
        val lo = percentile(sortedSpeeds, 5.0)
        val hi = percentile(sortedSpeeds, 95.0)

        val geoPoints = ArrayList<GeoPoint>(segments.size + 1)
        segments.forEachIndexed { index, seg ->
            if (index == 0) geoPoints.add(GeoPoint(seg.startLat, seg.startLon))
            geoPoints.add(GeoPoint(seg.endLat, seg.endLon))

            val t = if (hi > lo) ((seg.speedKmh - lo) / (hi - lo)).coerceIn(0f, 1f) else 0.5f
            val line = Polyline(binding.mapView).apply {
                outlinePaint.color = speedColor(t)
                outlinePaint.strokeWidth = 10f
                setPoints(
                    listOf(
                        GeoPoint(seg.startLat, seg.startLon),
                        GeoPoint(seg.endLat, seg.endLon)
                    )
                )
            }
            binding.mapView.overlays.add(line)
        }

        val bbox = BoundingBox.fromGeoPoints(geoPoints).increaseByScale(1.3f)
        binding.mapView.post {
            binding.mapView.zoomToBoundingBox(bbox, false, 48)
        }
        binding.mapView.invalidate()
    }

    /** Green (slow) → yellow → red (fast) for t in [0,1]. */
    private fun speedColor(t: Float): Int {
        return if (t < 0.5f) {
            val k = t / 0.5f
            Color.rgb((255 * k).roundToInt(), 255, 0)
        } else {
            val k = (t - 0.5f) / 0.5f
            Color.rgb(255, (255 * (1 - k)).roundToInt(), 0)
        }
    }

    private fun percentile(sorted: List<Float>, p: Double): Float {
        if (sorted.isEmpty()) return 0f
        val idx = ((p / 100.0) * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun setOptionalRow(row: View, valueView: TextView, text: String?) {
        if (text == null) {
            row.visibility = View.GONE
        } else {
            valueView.text = text
            row.visibility = View.VISIBLE
        }
    }

    /** e.g. 252_000 ms → "4m 12s". */
    private fun formatDurationShort(millis: Long): String {
        val totalSeconds = millis / 1000
        return "${totalSeconds / 60}m ${totalSeconds % 60}s"
    }

    /** Offset into the drive as mm:ss (minutes can exceed 59). */
    private fun formatOffset(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        return getString(R.string.elapsed_format, totalSeconds / 60, totalSeconds % 60)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}
