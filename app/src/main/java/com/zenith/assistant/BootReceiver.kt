package com.zenith.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver to automatically restart the wake word service on device boot if opt-in enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val db = Db(context)
            if (db.getBooleanSetting("wake_word_enabled", false)) {
                WakeWordService.startService(context)
            }
        }
    }
}
