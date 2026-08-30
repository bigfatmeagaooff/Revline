package com.revline.tracker.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.revline.tracker.util.Push
import com.revline.tracker.util.PushNotifications

/**
 * Receives FCM messages. Only ever instantiated by the OS when Firebase is
 * configured (google-services.json was present at build time), so there's no need
 * to guard for that here.
 */
class RevlineMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Push.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.show(applicationContext, message)
    }
}
