package com.revline.tracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.GForcePoint
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.TrackPoint
import com.revline.tracker.data.Trip
import com.revline.tracker.data.TripRepository
import com.revline.tracker.data.UploadResult
import com.revline.tracker.databinding.ActivityTripSummaryBinding
import com.revline.tracker.databinding.CellStatBinding
import com.revline.tracker.util.GForceCalculator
import com.revline.tracker.util.SpeedCalculator
import com.revline.tracker.util.TripStatsCalculator
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The "wow" screen: hero top-speed in red, a 2×3 stat grid, a speed-colored route map,
 * a conditional G-force section, and share / re-upload actions. Handles empty/sparse and
 * server-restored trips gracefully.
 */
class TripSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripSummaryBinding
    private lateinit var repository: TripRepository
    private lateinit var sync: SyncRepository

    private val dash get() = getString(R.string.value_dash)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TripRepository.getInstance(this)
        sync = SyncRepository.getInstance(this)
        setUpMap()

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        if (tripId <= 0L) { finish(); return }

        lifecycleScope.launch {
            val trip = repository.getTrip(tripId) ?: run { finish(); return@launch }
            val trackPoints = repository.getTrackPoints(tripId)
            val gForcePoints = repository.getGForcePoints(tripId)
            val segments = SpeedCalculator.cleanSegments(trackPoints)
            val hasRoute = segments.size >= 2
            val movingG = SpeedCalculator.movingGForcePoints(trackPoints, gForcePoints)

            val durationMillis = trip.actualDurationMinutes?.let { (it * 60_000f).toLong() }
                ?: ((trip.endTime ?: trip.startTime) - trip.startTime)
            val stats = TripStatsCalculator.compute(trackPoints, durationMillis, trip.distanceKm ?: 0f)
            val gSummary = GForceCalculator.summarize(movingG)

            binding.restoredNote.visibility = if (trip.restoredFromServer) View.VISIBLE else View.GONE

            bindHero(trip)
            bindGrid(trip, stats, durationMillis, movingG, gSummary)
            bindPrediction(trip)
            renderRoute(segments, hasRoute)
            bindGForce(movingG)
            bindComments(trip)
            bindActions(trip)
            maybeUpload(trip)
        }
    }

    /** Show a Comments entry only once the trip has a server id (i.e. it's been uploaded). */
    private fun bindComments(trip: Trip) {
        val serverId = trip.serverTripId
        if (serverId.isNullOrBlank()) {
            binding.commentsSection.visibility = View.GONE
            return
        }
        binding.commentsSection.visibility = View.VISIBLE
        binding.viewCommentsButton.setOnClickListener {
            startActivity(
                Intent(this, CommentsActivity::class.java)
                    .putExtra(CommentsActivity.EXTRA_TRIP_ID, serverId)
            )
        }
    }

    private fun bindHero(trip: Trip) {
        binding.heroTopSpeed.text = trip.topSpeedKmh?.takeIf { it > 0f }?.roundToInt()?.toString() ?: dash
        binding.heroDate.text = HERO_DATE.format(Date(trip.startTime))
        val dist = trip.distanceKm?.let { String.format(Locale.getDefault(), "%.1f km", it) }
        val dur = trip.actualDurationMinutes?.let { String.format(Locale.getDefault(), "%.0f min", it) }
        binding.heroMeta.text = listOfNotNull(dist, dur).joinToString(" · ").ifBlank { dash }
    }

    private fun bindGrid(
        trip: Trip,
        stats: TripStatsCalculator.Stats,
        durationMillis: Long,
        movingG: List<GForcePoint>,
        g: GForceCalculator.Summary
    ) {
        cell(binding.cellDistance, trip.distanceKm?.let { fmt(it, 1) }, R.string.unit_km, R.string.label_distance)
        cell(binding.cellAvgSpeed, trip.avgSpeedKmh?.let { fmt(it, 0) }, R.string.unit_kmh, R.string.label_avg_speed)
        cell(binding.cellDuration, trip.actualDurationMinutes?.let { fmt(it, 0) }, R.string.unit_min, R.string.label_duration)

        val movingMin = ((durationMillis - stats.idleMillis).coerceAtLeast(0L)) / 60_000f
        cell(binding.cellMovingTime, if (trip.actualDurationMinutes != null) fmt(movingMin, 0) else null,
            R.string.unit_min, R.string.label_moving_time)

        cell(binding.cellZeroHundred, stats.zeroToHundredSec?.let { fmt(it, 1) }, R.string.unit_s, R.string.label_0100)

        val peakG = if (movingG.isEmpty()) null
        else max(g.maxLateralG, max(g.maxAccelG, g.maxBrakingG))
        cell(binding.cellPeakG, peakG?.let { fmt(it, 1) }, R.string.unit_g, R.string.label_peak_g)
    }

    private fun cell(cell: CellStatBinding, value: String?, unitRes: Int, labelRes: Int) {
        cell.statNumber.text = value ?: dash
        cell.statNumber.setTextColor(
            ContextCompat.getColor(this, if (value == null) R.color.text_muted else R.color.text_primary)
        )
        cell.statUnit.text = getString(unitRes)
        cell.statLabel.text = getString(labelRes)
    }

    private fun bindPrediction(trip: Trip) {
        val actual = trip.actualDurationMinutes
        if (actual == null) {
            binding.predictionDelta.visibility = View.GONE
            binding.predictionEntry.visibility = View.GONE
            return
        }
        if (trip.predictedMinutes <= 0) {
            binding.predictionDelta.visibility = View.GONE
            showPredictionEntry(trip)
            return
        }
        binding.predictionEntry.visibility = View.GONE
        val predicted = trip.predictedMinutes
        val actualRounded = actual.roundToInt()
        val delta = actualRounded - predicted
        val deltaText = when {
            delta > 0 -> getString(R.string.delta_over, delta)
            delta < 0 -> getString(R.string.delta_under, abs(delta))
            else -> getString(R.string.delta_exact)
        }
        binding.predictionDelta.visibility = View.VISIBLE
        binding.predictionDelta.text =
            getString(R.string.summary_prediction, predicted, actualRounded, deltaText)
    }

    private fun showPredictionEntry(trip: Trip) {
        binding.predictionEntry.visibility = View.VISIBLE
        binding.addPredictionButton.setOnClickListener {
            val minutes = binding.predictionInput.text?.toString()?.trim()?.toIntOrNull()
            if (minutes == null || minutes <= 0) {
                binding.predictionLayout.error = getString(R.string.error_minutes_required)
                return@setOnClickListener
            }
            binding.predictionLayout.error = null
            lifecycleScope.launch {
                val updated = trip.copy(predictedMinutes = minutes)
                repository.updateTrip(updated)
                bindPrediction(updated)
            }
        }
    }

    private fun bindGForce(movingG: List<GForcePoint>) {
        if (movingG.isEmpty()) {
            binding.gforceSection.visibility = View.GONE
            return
        }
        binding.gforceSection.visibility = View.VISIBLE
        binding.gforceGraph.setData(movingG)
        val hardest = GForceCalculator.hardestBraking(movingG)
        if (hardest != null) {
            binding.hardestBrakingValue.visibility = View.VISIBLE
            binding.hardestBrakingValue.text =
                getString(R.string.hardest_braking_simple, abs(hardest.forwardG))
        } else {
            binding.hardestBrakingValue.visibility = View.GONE
        }
    }

    private fun bindActions(trip: Trip) {
        binding.shareButton.setOnClickListener { share(trip) }
        if (sync.isLoggedIn && sync.hasValidStats(trip)) {
            binding.reuploadButton.visibility = View.VISIBLE
            binding.reuploadButton.setOnClickListener { triggerReupload(trip) }
        } else {
            binding.reuploadButton.visibility = View.GONE
        }
    }

    private fun share(trip: Trip) {
        val text = getString(
            R.string.share_text,
            HERO_DATE.format(Date(trip.startTime)),
            trip.topSpeedKmh ?: 0f,
            trip.distanceKm ?: 0f
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                getString(R.string.share)
            )
        )
    }

    private fun triggerReupload(trip: Trip) {
        binding.reuploadButton.isEnabled = false
        lifecycleScope.launch {
            when (sync.reuploadTrip(trip.id)) {
                is UploadResult.Success -> {
                    showStrip(getString(R.string.upload_done_strip), R.color.success, retry = false)
                    binding.reuploadButton.visibility = View.GONE
                }
                else -> {
                    showStrip(getString(R.string.upload_failed_strip), R.color.warning, retry = true) { triggerReupload(trip) }
                    binding.reuploadButton.isEnabled = true
                }
            }
        }
    }

    private fun maybeUpload(trip: Trip) {
        when {
            trip.restoredFromServer -> binding.uploadStatus.visibility = View.GONE
            !sync.isLoggedIn -> binding.uploadStatus.visibility = View.GONE
            trip.uploadedAt != null ->
                showStrip(getString(R.string.upload_done_strip), R.color.success, retry = false)
            else -> lifecycleScope.launch {
                when (val r = sync.uploadTrip(trip.id)) {
                    is UploadResult.Success ->
                        showStrip(getString(R.string.upload_done_strip), R.color.success, retry = false)
                    is UploadResult.AlreadyUploaded ->
                        showStrip(getString(R.string.upload_done_strip), R.color.success, retry = false)
                    is UploadResult.Failed ->
                        showStrip(getString(R.string.upload_failed_strip), R.color.warning, retry = true) { triggerReupload(trip) }
                    else -> binding.uploadStatus.visibility = View.GONE
                }
            }
        }
    }

    private fun showStrip(text: String, bgColor: Int, retry: Boolean, onRetry: (() -> Unit)? = null) {
        binding.uploadStatus.visibility = View.VISIBLE
        binding.uploadStatus.text = text
        binding.uploadStatus.setBackgroundColor(ContextCompat.getColor(this, bgColor))
        binding.uploadStatus.setOnClickListener(if (retry && onRetry != null) View.OnClickListener { onRetry() } else null)
    }

    // --- Route map ---

    private fun setUpMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.overlays.add(CopyrightOverlay(this))
        binding.mapView.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun renderRoute(segments: List<SpeedCalculator.Segment>, hasRoute: Boolean) {
        if (!hasRoute) {
            binding.mapView.visibility = View.GONE
            binding.mapPlaceholder.visibility = View.VISIBLE
            return
        }
        binding.mapView.visibility = View.VISIBLE
        binding.mapPlaceholder.visibility = View.GONE

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
                setPoints(listOf(GeoPoint(seg.startLat, seg.startLon), GeoPoint(seg.endLat, seg.endLon)))
            }
            binding.mapView.overlays.add(line)
        }
        val bbox = BoundingBox.fromGeoPoints(geoPoints).increaseByScale(1.3f)
        binding.mapView.post { binding.mapView.zoomToBoundingBox(bbox, false, 48) }
        binding.mapView.invalidate()
    }

    private fun speedColor(t: Float): Int = if (t < 0.5f) {
        Color.rgb((255 * (t / 0.5f)).roundToInt(), 255, 0)
    } else {
        Color.rgb(255, (255 * (1 - (t - 0.5f) / 0.5f)).roundToInt(), 0)
    }

    private fun percentile(sorted: List<Float>, p: Double): Float {
        if (sorted.isEmpty()) return 0f
        val idx = ((p / 100.0) * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun fmt(value: Float, decimals: Int) =
        String.format(Locale.getDefault(), "%.${decimals}f", value)

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
        private val HERO_DATE = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault())
    }
}
