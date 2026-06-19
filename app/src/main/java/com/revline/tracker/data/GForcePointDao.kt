package com.revline.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GForcePointDao {

    @Insert
    suspend fun insert(point: GForcePoint): Long

    /** All G-force readings for a trip, in chronological order. */
    @Query("SELECT * FROM g_force_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getForTrip(tripId: Long): List<GForcePoint>

    @Query("SELECT COUNT(*) FROM g_force_points WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int
}
