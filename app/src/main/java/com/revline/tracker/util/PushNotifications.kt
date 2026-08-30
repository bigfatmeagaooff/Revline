package com.revline.tracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.revline.tracker.MainActivity
import com.revline.tracker.R
import com.revline.tracker.ui.NotificationsActivity
import kotlin.random.Random

/**
 * Turns an incoming FCM message into a system notification. The message carries a
 * `type` in its data payload — `announcement` routes to the announcement channel and
 * opens the app (or the announcement's link); anything else is a social
 * notification and opens the notification centre.
 */
object PushNotifications {

    const val CHANNEL_SOCIAL = "revline_social"
    const val CHANNEL_ANNOUNCEMENTS = "revline_announcements"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SOCIAL,
                context.getString(R.string.push_channel_social),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.push_channel_social_desc) }
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                context.getString(R.string.push_channel_announcements),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.push_channel_announcements_desc) }
        )
    }

    fun show(context: Context, message: RemoteMessage) {
        ensureChannels(context)
        val data = message.data
        val isAnnouncement = data["type"] == "announcement"

        val notif = message.notification
        val title = notif?.title ?: data["title"] ?: context.getString(R.string.app_name)
        val body = notif?.body ?: data["body"] ?: return

        val channel = if (isAnnouncement) CHANNEL_ANNOUNCEMENTS else CHANNEL_SOCIAL
        val url = data["url"]

        val intent = when {
            isAnnouncement && !url.isNullOrBlank() -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
            isAnnouncement -> Intent(context, MainActivity::class.java)
            else -> Intent(context, NotificationsActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (isAnnouncement) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )

        // notify() silently no-ops if POST_NOTIFICATIONS isn't granted (Android 13+).
        runCatching {
            NotificationManagerCompat.from(context).notify(Random.nextInt(), builder.build())
        }
    }
}
