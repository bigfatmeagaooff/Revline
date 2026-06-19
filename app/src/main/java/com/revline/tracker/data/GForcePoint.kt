package com.revline.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 * A single accelerometer-derived G-force reading recorded during a [Trip].
 *
 * Readings are taken from the device's linear-acceleration sensor (gravity removed)
 * and expressed in G. Like [TrackPoint]s, they're written as they're sampled so a
 * killed service/app doesn't lose the data.
 *
 * Orientation assumption (Phase 2): the phone is in a fixed dash/windshield mount for
 * the whole trip. We do not do full road-frame sensor fusion — see README.
 */
@Entity(
    tableName = "g_force_points",
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
data class GForcePoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val tripId: Long,

    /** Left/right G (sign depends on mount; magnitude is what's used in summaries). */
    val lateralG: Float,

    /** Accel/braking G: positive = accelerating, negative = braking. */
    val forwardG: Float,

    val timestamp: Long
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("tripId", tripId)
        json.put("lateralG", lateralG.toDouble())
        json.put("forwardG", forwardG.toDouble())
        json.put("timestamp", timestamp)
        return json
    }
}
