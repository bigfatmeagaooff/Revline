package com.revline.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    /** Reactive list for the trip history screen, most recent first. */
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Trip>>

    /**
     * Visible trips only: finished, with real stats. Hides ghost/in-progress rows and
     * "0 km/h · 0.0 km" junk drives from the list.
     */
    @Query(
        "SELECT * FROM trips WHERE endTime IS NOT NULL AND (distanceKm > 0.1 OR topSpeedKmh > 5) " +
            "ORDER BY startTime DESC"
    )
    fun observeVisible(): Flow<List<Trip>>

    /**
     * Removes leftover in-progress rows (e.g. a drive whose service was killed).
     * [exceptId] is the currently-tracking trip, which must never be deleted — it's
     * a ghost by definition (no endTime until the drive finishes), and deleting it
     * out from under the running service loses the drive and makes the next
     * track/G-force insert fail its foreign key.
     */
    @Query("DELETE FROM trips WHERE endTime IS NULL AND id != :exceptId")
    suspend fun deleteGhostTrips(exceptId: Long): Int

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getById(tripId: Long): Trip?
}
