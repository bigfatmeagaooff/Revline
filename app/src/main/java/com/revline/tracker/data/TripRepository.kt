package com.revline.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Single point of access for trip/track-point persistence. UI and the tracking
 * service talk to this, never to Room/DAOs directly.
 *
 * This is the seam for the bigger product: a future `SyncRepository` (networking)
 * can sit alongside this local repository — or wrap it — without any UI changes.
 * Keep all data access flowing through here.
 */
class TripRepository(
    private val tripDao: TripDao,
    private val trackPointDao: TrackPointDao
) {

    fun observeTrips(): Flow<List<Trip>> = tripDao.observeAll()

    suspend fun getTrip(tripId: Long): Trip? = tripDao.getById(tripId)

    suspend fun createTrip(trip: Trip): Long = tripDao.insert(trip)

    suspend fun updateTrip(trip: Trip) = tripDao.update(trip)

    suspend fun addTrackPoint(point: TrackPoint): Long = trackPointDao.insert(point)

    suspend fun getTrackPoints(tripId: Long): List<TrackPoint> =
        trackPointDao.getForTrip(tripId)

    companion object {
        @Volatile
        private var INSTANCE: TripRepository? = null

        fun getInstance(context: Context): TripRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = AppDatabase.getInstance(context)
                    TripRepository(db.tripDao(), db.trackPointDao()).also { INSTANCE = it }
                }
            }
        }
    }
}
