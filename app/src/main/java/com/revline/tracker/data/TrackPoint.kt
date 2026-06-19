package com.revline.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 * A single GPS breadcrumb recorded during a [Trip]. Stored immediately on each
 * location update so a killed app/service never loses the whole trail. Retained
 * (even though there's no map UI yet) for a future route-map view.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val tripId: Long,

    val lat: Double,
    val lon: Double,

    /** Raw speed from the location provider in m/s, if available. */
    val speedMps: Float? = null,

    val timestamp: Long
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("tripId", tripId)
        json.put("lat", lat)
        json.put("lon", lon)
        json.put("speedMps", speedMps ?: JSONObject.NULL)
        json.put("timestamp", timestamp)
        return json
    }
}
