package com.revline.tracker

import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.revline.tracker.util.TripCardGenerator
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
import com.revline.tracker.util.EdgeToEdge

/**
 * The "wow" screen: hero top-speed in red, a 2×3 stat grid, a speed-colored route map,
 * a conditional G-force section, and share / re-upload actions. Handles empty/sparse and
 * server-restored trips gracefully.
 */
class TripSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripSummaryBinding
    private lateinit var repository: TripRepository
    private lateinit var sync: SyncRepository

    /** Derived trip data, kept so the share card can reuse it without recomputing. */
    private var computed: Computed? = null

    private val dash get() = getString(R.string.value_dash)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)

        repository = TripRepository.getInstance(this)
        sync = SyncRepository.getInstance(this)
        setUpMap()

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        if (tripId <= 0L) { finish(); return }

        lifecycleScope.launch {
            val trip = repository.getTrip(tripId) ?: run { finish(); return@launch }

            // All the heavy lifting (segment cleaning, speed/G matching, stats) runs off
            // the main thread. A long drive has tens of thousands of points, and doing
            // this on the UI thread froze it long enough for Android to kill the app.
            val data = withContext(Dispatchers.Default) {
                val trackPoints = repository.getTrackPoints(tripId)
                val gForcePoints = repository.getGForcePoints(tripId)
                val segments = SpeedCalculator.cleanSegments(trackPoints)
                val movingG = SpeedCalculator.movingGForcePoints(trackPoints, gForcePoints)
                val durationMillis = trip.actualDurationMinutes?.let { (it * 60_000f).toLong() }
                    ?: ((trip.endTime ?: trip.startTime) - trip.startTime)
                val stats = TripStatsCalculator.compute(trackPoints, durationMillis, trip.distanceKm ?: 0f)
                val gSummary = GForceCalculator.summarize(movingG)
                Computed(segments, movingG, stats, gSummary, durationMillis)
            }
            computed = data

            binding.restoredNote.visibility = if (trip.restoredFromServer) View.VISIBLE else View.GONE

            bindHero(trip)
            bindGrid(trip, data.stats, data.durationMillis, data.movingG, data.gSummary)
            bindPrediction(trip)
            renderRoute(data.segments, data.segments.size >= 2)
            bindGForce(data.movingG)
            bindDetail(data.stats, data.gSummary, data.movingG)
            bindComments(trip)
            bindActions(trip)
            maybeUpload(trip)
        }
    }

    /** Everything derived from a trip's raw points, computed once on a background thread. */
    private data class Computed(
        val segments: List<SpeedCalculator.Segment>,
        val movingG: List<GForcePoint>,
        val stats: TripStatsCalculator.Stats,
        val gSummary: GForceCalculator.Summary,
        val durationMillis: Long
    )

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
        binding.heroDate.text = HERO_DATE.format(Date(trip.startTime)).uppercase(Locale.getDefault())
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

    /**
     * Drive Detail: stops, cornering G (peak + average) and elevation. Each row is
     * shown only when that trip actually has the data — no zeroes or garbage numbers.
     */
    private fun bindDetail(
        stats: TripStatsCalculator.Stats,
        g: GForceCalculator.Summary,
        movingG: List<GForcePoint>
    ) {
        var anyShown = false

        if (stats.stopCount > 0) {
            binding.rowStops.visibility = View.VISIBLE
            binding.valueStops.text = stats.stopCount.toString()
            anyShown = true
        }

        if (movingG.isNotEmpty() && g.maxLateralG > 0f) {
            binding.rowCorneringPeak.visibility = View.VISIBLE
            binding.valueCorneringPeak.text = getString(R.string.detail_g_value, g.maxLateralG)
            anyShown = true
        }

        g.avgCorneringG?.let { avg ->
            binding.rowCorneringAvg.visibility = View.VISIBLE
            binding.valueCorneringAvg.text = getString(R.string.detail_g_value, avg)
            anyShown = true
        }

        val gain = stats.elevationGainM
        val loss = stats.elevationLossM
        if (gain != null && loss != null && (gain >= 1f || loss >= 1f)) {
            binding.rowElevation.visibility = View.VISIBLE
            binding.valueElevation.text = getString(R.string.detail_elevation_value, gain, loss)
            anyShown = true
        }

        binding.detailSection.visibility = if (anyShown) View.VISIBLE else View.GONE
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

    /**
     * Generates the shareable drive card and hands it to the system share sheet.
     * Rendered on demand (most drives never get shared) and off the main thread.
     */
    private fun share(trip: Trip) {
        val data = computed ?: return
        binding.shareButton.isEnabled = false
        lifecycleScope.launch {
            val file = withContext(Dispatchers.Default) {
                val bitmap = TripCardGenerator.render(
                    this@TripSummaryActivity, trip, data.segments, data.stats.zeroToHundredSec
                )
                TripCardGenerator.writeToCache(this@TripSummaryActivity, bitmap)
                    .also { bitmap.recycle() }
            }
            binding.shareButton.isEnabled = true

            val uri = runCatching {
                FileProvider.getUriForFile(
                    this@TripSummaryActivity, "$packageName.fileprovider", file
                )
            }.getOrNull()
            if (uri == null) {
                Toast.makeText(this@TripSummaryActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val text = getString(
                R.string.share_text,
                HERO_DATE.format(Date(trip.startTime)),
                trip.topSpeedKmh ?: 0f,
                trip.distanceKm ?: 0f
            )
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.share)))
        }
    }

    private fun triggerReupload(trip: Trip) {
        binding.reuploadButton.isEnabled = false
        lifecycleScope.launch {
            when (sync.reuploadTrip(trip.id)) {
                is UploadResult.Success -> {
                    showStrip(getString(R.string.upload_done_strip), R.color.stage, retry = false)
                    binding.reuploadButton.visibility = View.GONE
                    repository.getTrip(trip.id)?.let { bindComments(it) }
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
                showStrip(getString(R.string.upload_done_strip), R.color.stage, retry = false)
            else -> lifecycleScope.launch {
                when (val r = sync.uploadTrip(trip.id)) {
                    is UploadResult.Success -> {
                        showStrip(getString(R.string.upload_done_strip), R.color.stage, retry = false)
                        // The upload stamped a serverTripId — re-read so Comments appears now.
                        repository.getTrip(trip.id)?.let { bindComments(it) }
                    }
                    is UploadResult.AlreadyUploaded ->
                        showStrip(getString(R.string.upload_done_strip), R.color.stage, retry = false)
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
        // Knock the bright street tiles back so the slip's dark world holds:
        // greyscale, then darken and cool slightly. The route line carries the colour.
        val mapFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                0.44f, 0f, 0f, 0f, 6f,
                0f, 0.44f, 0f, 0f, 6f,
                0f, 0f, 0.50f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            )))
        })
        binding.mapView.post {
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(mapFilter)
            binding.mapView.invalidate()
        }
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

        // Colour each segment into one of a few buckets, then merge consecutive
        // same-bucket segments into a single polyline. A one-overlay-per-segment map
        // meant thousands of Polyline objects on a long drive — slow to draw and a
        // memory risk. This keeps the speed colouring but collapses to a handful of
        // overlays, with no gaps in the line.
        val geoPoints = ArrayList<GeoPoint>(segments.size + 1)
        for ((index, seg) in segments.withIndex()) {
            if (index == 0) geoPoints.add(GeoPoint(seg.startLat, seg.startLon))
            geoPoints.add(GeoPoint(seg.endLat, seg.endLon))
        }

        fun bucketOf(seg: SpeedCalculator.Segment): Int {
            val t = if (hi > lo) ((seg.speedKmh - lo) / (hi - lo)).coerceIn(0f, 1f) else 0.5f
            return (t * SPEED_BUCKETS).toInt().coerceIn(0, SPEED_BUCKETS - 1)
        }

        var runStart = 0
        while (runStart < segments.size) {
            val bucket = bucketOf(segments[runStart])
            var runEnd = runStart
            while (runEnd + 1 < segments.size && bucketOf(segments[runEnd + 1]) == bucket) runEnd++
            val pts = ArrayList<GeoPoint>(runEnd - runStart + 2)
            pts.add(GeoPoint(segments[runStart].startLat, segments[runStart].startLon))
            for (i in runStart..runEnd) pts.add(GeoPoint(segments[i].endLat, segments[i].endLon))
            val line = Polyline(binding.mapView).apply {
                outlinePaint.color = speedColor((bucket + 0.5f) / SPEED_BUCKETS)
                outlinePaint.strokeWidth = 10f
                setPoints(pts)
            }
            binding.mapView.overlays.add(line)
            runStart = runEnd + 1
        }

        val bbox = BoundingBox.fromGeoPoints(geoPoints).increaseByScale(1.3f)
        binding.mapView.post { binding.mapView.zoomToBoundingBox(bbox, false, 48) }
        binding.mapView.invalidate()
    }

    /**
     * Route colour: cold slate where the drive was slow, burning to redline where it
     * was fast. Grey→red interpolates clean (no muddy orange mid-tones), and it keeps
     * the eye on the fast sections — the point of the map.
     */
    private fun speedColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        // slate #556170  →  redline #F5121C
        val cold = intArrayOf(0x55, 0x61, 0x70)
        val hot = intArrayOf(0xF5, 0x12, 0x1C)
        // ease so most of the line stays cool and only the top end lights up
        val e = c * c
        return Color.rgb(
            (cold[0] + (hot[0] - cold[0]) * e).roundToInt(),
            (cold[1] + (hot[1] - cold[1]) * e).roundToInt(),
            (cold[2] + (hot[2] - cold[2]) * e).roundToInt()
        )
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
        private const val SPEED_BUCKETS = 16
        private val HERO_DATE = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault())
    }
}
