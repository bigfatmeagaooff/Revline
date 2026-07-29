package com.revline.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.revline.tracker.util.AppSettings

/**
 * Receives IN_VEHICLE transitions and starts tracking automatically.
 *
 * Only ENTER starts a drive. EXIT is deliberately ignored: the activity API is slow and
 * jittery about deciding you've stopped driving, so [TrackingService] ends the trip from
 * actual GPS speed instead (sustained near-standstill), which is far more reliable.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRANSITION) return
        if (!AppSettings.isAutoDetectEnabled(context)) return
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val enteredVehicle = result.transitionEvents.any {
            it.activityType == DetectedActivity.IN_VEHICLE &&
                it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }
        if (!enteredVehicle) return

        if (TrackingService.state.value.isTracking) return // already recording

        Log.i(TAG, "IN_VEHICLE detected — auto-starting a drive")
        try {
            TrackingService.startAuto(context)
        } catch (e: Exception) {
            // Android restricts starting a foreground service from the background;
            // activity-transition events are an allowed trigger, but never crash the
            // app if an OEM or future release disagrees.
            Log.w(TAG, "Couldn't auto-start tracking", e)
        }
    }

    companion object {
        private const val TAG = "AutoDetect"
        const val ACTION_TRANSITION = "com.revline.tracker.action.ACTIVITY_TRANSITION"
    }
}
