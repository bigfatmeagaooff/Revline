package com.revline.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackPointDao {

    @Insert
    suspend fun insert(point: TrackPoint): Long

    /** All breadcrumbs for a trip, in chronological order. */
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getForTrip(tripId: Long): List<TrackPoint>

    @Query("SELECT COUNT(*) FROM track_points WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int
}
