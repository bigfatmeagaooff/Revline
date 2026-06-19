package com.revline.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.revline.tracker.MainActivity
import com.revline.tracker.R
import com.revline.tracker.data.TrackPoint
import com.revline.tracker.data.Trip
import com.revline.tracker.data.TripRepository
import com.revline.tracker.util.DeviceId
import com.revline.tracker.util.SpeedCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that GPS-tracks an in-progress drive.
 *
 * Creates the [Trip] row on start, persists a [TrackPoint] on every location update
 * (never buffering only in memory, so a killed process can't lose the trail), and on
 * stop computes the summary stats and writes them back before exposing the finished
 * trip id to the UI.
 */
class TrackingService : LifecycleService() {

    private lateinit var repository: TripRepository
    private lateinit var fusedClient: FusedLocationProviderClient

    private var activeTripId: Long = 0L
    private var tripStartTime: Long = 0L
    private var stopping = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val tripId = activeTripId
            if (tripId == 0L) return
            for (location in result.locations) {
                val point = TrackPoint(
                    tripId = tripId,
                    lat = location.latitude,
                    lon = location.longitude,
                    speedMps = if (location.hasSpeed()) location.speed else null,
                    timestamp = System.currentTimeMillis()
                )
                // Persist immediately; survives the process being killed mid-trip.
                lifecycleScope.launch { repository.addTrackPoint(point) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = TripRepository.getInstance(this)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val predictedMinutes = intent.getIntExtra(EXTRA_PREDICTED_MINUTES, 0)
                val predictedDistanceKm = if (intent.hasExtra(EXTRA_PREDICTED_DISTANCE)) {
                    intent.getFloatExtra(EXTRA_PREDICTED_DISTANCE, 0f)
                } else null
                startTracking(predictedMinutes, predictedDistanceKm)
            }
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking(predictedMinutes: Int, predictedDistanceKm: Float?) {
        if (activeTripId != 0L) return // already tracking

        tripStartTime = System.currentTimeMillis()
        startForegroundWithNotification(elapsedMinutes = 0)

        lifecycleScope.launch {
            val trip = Trip(
                deviceId = DeviceId.get(this@TrackingService),
                userId = null,
                startTime = tripStartTime,
                predictedMinutes = predictedMinutes,
                predictedDistanceKm = predictedDistanceKm
            )
            activeTripId = repository.createTrip(trip)
            _state.value = TrackingState(
                isTracking = true,
                activeTripId = activeTripId,
                startTime = tripStartTime
            )
            requestLocationUpdates()
            tickNotification()
        }
    }

    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Location permission revoked while running; stop cleanly.
            stopTracking()
        }
    }

    /** Refresh the notification's elapsed-time text roughly once a minute. */
    private fun tickNotification() {
        lifecycleScope.launch {
            while (isActive && activeTripId != 0L && !stopping) {
                val elapsedMinutes = ((System.currentTimeMillis() - tripStartTime) / 60_000L).toInt()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(elapsedMinutes))
                delay(30_000L)
            }
        }
    }

    private fun stopTracking() {
        if (stopping) return
        stopping = true
        fusedClient.removeLocationUpdates(locationCallback)
        val tripId = activeTripId

        lifecycleScope.launch {
            if (tripId != 0L) {
                finalizeTrip(tripId)
            }
            _state.value = TrackingState(
                isTracking = false,
                activeTripId = null,
                startTime = null,
                finishedTripId = if (tripId != 0L) tripId else null
            )
            activeTripId = 0L
            ServiceCompat.stopForeground(this@TrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun finalizeTrip(tripId: Long) = withContext(Dispatchers.IO) {
        val trip = repository.getTrip(tripId) ?: return@withContext
        val points = repository.getTrackPoints(tripId)
        val endTime = System.currentTimeMillis()

        val distanceKm = SpeedCalculator.distanceKm(points)
        val durationMillis = endTime - trip.startTime
        val avgSpeedKmh = SpeedCalculator.avgSpeedKmh(distanceKm, durationMillis)
        val topSpeedKmh = SpeedCalculator.topSpeedKmh(points)
        val actualDurationMinutes = durationMillis / 60_000f

        repository.updateTrip(
            trip.copy(
                endTime = endTime,
                distanceKm = distanceKm,
                avgSpeedKmh = avgSpeedKmh,
                topSpeedKmh = topSpeedKmh,
                actualDurationMinutes = actualDurationMinutes
            )
        )
    }

    // --- Notification ---

    private fun startForegroundWithNotification(elapsedMinutes: Int) {
        val notification = buildNotification(elapsedMinutes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(elapsedMinutes: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, elapsedMinutes))
            .setSmallIcon(R.drawable.ic_directions_car)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.stop_drive), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.revline.tracker.action.START"
        const val ACTION_STOP = "com.revline.tracker.action.STOP"
        const val EXTRA_PREDICTED_MINUTES = "extra_predicted_minutes"
        const val EXTRA_PREDICTED_DISTANCE = "extra_predicted_distance"

        private const val CHANNEL_ID = "revline_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val LOCATION_FASTEST_INTERVAL_MS = 1_000L

        /**
         * Observable tracking state shared with the UI. Survives config changes and
         * lets the in-progress screen react to start/stop without binding to the service.
         */
        private val _state = MutableStateFlow(TrackingState())
        val state: StateFlow<TrackingState> = _state.asStateFlow()

        fun start(
            context: Context,
            predictedMinutes: Int,
            predictedDistanceKm: Float?
        ) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PREDICTED_MINUTES, predictedMinutes)
                if (predictedDistanceKm != null) {
                    putExtra(EXTRA_PREDICTED_DISTANCE, predictedDistanceKm)
                }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Clears a consumed finishedTripId so it isn't re-handled on recomposition. */
        fun consumeFinishedTrip() {
            _state.value = _state.value.copy(finishedTripId = null)
        }
    }
}

data class TrackingState(
    val isTracking: Boolean = false,
    val activeTripId: Long? = null,
    val startTime: Long? = null,
    val finishedTripId: Long? = null
)
