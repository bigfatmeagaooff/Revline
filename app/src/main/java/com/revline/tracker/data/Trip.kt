package com.revline.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single GPS-tracked drive.
 *
 * Future-proofing notes (see BUILD BRIEF context):
 *  - [deviceId] + [userId] are carried now so that when accounts exist later we can
 *    backfill ownership rather than run a schema migration.
 *  - Trips are cleanly exportable via [toJson] so "upload trip to leaderboard" is a
 *    trivial serialization later instead of a schema headache.
 *  - A future `carId: String?` foreign key (make/model/year identity) will slot in here.
 *    Intentionally left as a commented placeholder — do NOT implement the Car table yet.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Local pseudo-user-id from [com.revline.tracker.util.DeviceId]. */
    val deviceId: String,

    /** Null until an auth system exists; backfilled later. */
    val userId: String? = null,

    val startTime: Long,
    val endTime: Long? = null,

    /** What Google Maps predicted, user-entered before starting. */
    val predictedMinutes: Int,

    /** Optional user-entered predicted distance. */
    val predictedDistanceKm: Float? = null,

    // --- Computed at trip end ---
    val distanceKm: Float? = null,
    val avgSpeedKmh: Float? = null,
    val topSpeedKmh: Float? = null,
    val actualDurationMinutes: Float? = null,

    // future: val carId: String? = null  // FK to a future Car table (make/model/year). Not implemented in v1.
) {
    /**
     * Serializes this trip and its breadcrumb [trackPoints] to JSON. This is the
     * future hook for uploading a trip to a leaderboard / sync backend without
     * touching the schema.
     */
    fun toJson(trackPoints: List<TrackPoint> = emptyList()): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("deviceId", deviceId)
        json.put("userId", userId ?: JSONObject.NULL)
        json.put("startTime", startTime)
        json.put("endTime", endTime ?: JSONObject.NULL)
        json.put("predictedMinutes", predictedMinutes)
        json.put("predictedDistanceKm", predictedDistanceKm ?: JSONObject.NULL)
        json.put("distanceKm", distanceKm ?: JSONObject.NULL)
        json.put("avgSpeedKmh", avgSpeedKmh ?: JSONObject.NULL)
        json.put("topSpeedKmh", topSpeedKmh ?: JSONObject.NULL)
        json.put("actualDurationMinutes", actualDurationMinutes ?: JSONObject.NULL)

        val pointsArray = JSONArray()
        for (point in trackPoints) {
            pointsArray.put(point.toJson())
        }
        json.put("trackPoints", pointsArray)
        return json
    }
}
