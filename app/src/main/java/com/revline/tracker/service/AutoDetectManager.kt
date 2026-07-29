package com.revline.tracker.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Turns automatic trip detection on and off.
 *
 * Uses Play Services' activity-transition API rather than polling GPS: the system
 * reports an IN_VEHICLE enter/exit event using low-power sensors, so nothing of ours
 * runs (and no GPS is used) while the user isn't driving. Full-frequency GPS only
 * starts once a drive is actually detected.
 */
object AutoDetectManager {

    private const val TAG = "AutoDetect"

    /** True once the OS-level permissions auto-detect needs are all granted. */
    fun hasPermissions(context: Context): Boolean {
        val recognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            true
        }
        // Detection fires while the app is in the background, so background location is
        // required — without it we'd detect a drive we aren't allowed to track.
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return recognition && background && granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Subscribes to IN_VEHICLE transitions. No-op (returns false) without permissions. */
    fun start(context: Context): Boolean {
        if (!hasPermissions(context)) return false
        val request = ActivityTransitionRequest(
            listOf(
                transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
                transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            )
        )
        return try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pendingIntent(context))
            Log.i(TAG, "Activity transition updates requested")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission for transition updates", e)
            false
        }
    }

    /** Unsubscribes. Safe to call even if updates were never registered. */
    fun stop(context: Context) {
        try {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent(context))
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't remove transition updates", e)
        }
    }

    private fun transition(activity: Int, type: Int) = ActivityTransition.Builder()
        .setActivityType(activity)
        .setActivityTransition(type)
        .build()

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
            .setAction(ActivityTransitionReceiver.ACTION_TRANSITION)
        // Mutable: Play Services writes the transition result into this intent.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private const val REQUEST_CODE = 4201
}
