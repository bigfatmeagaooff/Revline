package com.revline.tracker

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.TrackPoint
import com.revline.tracker.data.Trip
import com.revline.tracker.data.TripRepository
import com.revline.tracker.databinding.ActivityTripSummaryBinding
import com.revline.tracker.util.GForceCalculator
import com.revline.tracker.util.SpeedCalculator
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
 * a G-force summary + timeline graph, and a speed-colored route map. Read-only — used
 * both right after a drive and when opened from history.
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
            bind(trip)
            bindGForce(repository.getGForcePoints(tripId))
            renderRoute(repository.getTrackPoints(tripId))
        }
    }

    private fun bind(trip: Trip) {
        binding.distanceValue.text = trip.distanceKm?.let {
            getString(R.string.summary_distance_value, it)
        } ?: getString(R.string.value_dash)

        binding.durationValue.text = trip.actualDurationMinutes?.let {
            getString(R.string.summary_duration_value, it)
        } ?: getString(R.string.value_dash)

        binding.avgSpeedValue.text = trip.avgSpeedKmh?.let {
            getString(R.string.summary_speed_value, it)
        } ?: getString(R.string.value_dash)

        binding.topSpeedValue.text = trip.topSpeedKmh?.let {
            getString(R.string.summary_speed_value, it)
        } ?: getString(R.string.value_dash)

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

    private fun bindGForce(points: List<com.revline.tracker.data.GForcePoint>) {
        if (points.isEmpty()) {
            binding.gforceSection.visibility = View.GONE
            return
        }
        val summary = GForceCalculator.summarize(points)
        binding.maxLateralValue.text = getString(R.string.summary_g_value, summary.maxLateralG)
        binding.maxAccelValue.text = getString(R.string.summary_g_value, summary.maxAccelG)
        binding.maxBrakingValue.text = getString(R.string.summary_g_value, summary.maxBrakingG)
        binding.gforceGraph.setData(points)
    }

    // --- Route map ---

    private fun setUpMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.overlays.add(CopyrightOverlay(this)) // "© OpenStreetMap contributors"
        // Let map gestures win over the surrounding ScrollView.
        binding.mapView.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun renderRoute(points: List<TrackPoint>) {
        val segments = SpeedCalculator.cleanSegments(points)
        if (segments.isEmpty()) {
            binding.routeSection.visibility = View.GONE
            return
        }

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
