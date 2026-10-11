package com.zenith.assistant

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class ReceivedMessage(
    val sender: String,
    val text: String,
    val packageName: String,
    val timestamp: Long,
    val pendingIntent: PendingIntent?,
    val remoteInput: RemoteInput?
)

/**
 * Notification Listener Service allowing ZENITH to read incoming messaging notifications
 * (SMS, WhatsApp, Telegram, etc.) and perform authorized direct replies after explicit confirmation.
 */
class ZenithNotificationService : NotificationListenerService() {

    companion object {
        @Volatile var instance: ZenithNotificationService? = null
            private set

        @Volatile var latestMessage: ReceivedMessage? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Extract direct reply RemoteInput action if available
        var replyPendingIntent: PendingIntent? = null
        var replyRemoteInput: RemoteInput? = null

        notification.actions?.forEach { action ->
            action.remoteInputs?.forEach { remoteInput ->
                if (remoteInput.allowFreeFormInput) {
                    replyPendingIntent = action.actionIntent
                    replyRemoteInput = remoteInput
                }
            }
        }

        latestMessage = ReceivedMessage(
            sender = title,
            text = text,
            packageName = sbn.packageName,
            timestamp = sbn.postTime,
            pendingIntent = replyPendingIntent,
            remoteInput = replyRemoteInput
        )
    }

    fun replyToLatest(replyText: String): Boolean {
        val msg = latestMessage ?: return false
        val pIntent = msg.pendingIntent ?: return false
        val rInput = msg.remoteInput ?: return false

        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(rInput.resultKey, replyText)
        RemoteInput.addResultsToIntent(arrayOf(rInput), intent, bundle)

        return try {
            pIntent.send(this, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }
}
