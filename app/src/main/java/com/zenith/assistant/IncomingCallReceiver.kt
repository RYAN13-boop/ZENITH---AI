package com.zenith.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * BroadcastReceiver for phone state changes.
 * Detects incoming ringing calls, resolves contact identity, and notifies CallHandlingManager.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            val callerName = if (incomingNumber.isNotEmpty()) {
                val matches = Contacts.find(context, incomingNumber)
                if (matches.isNotEmpty()) matches[0].name else incomingNumber
            } else {
                "Unknown Caller"
            }

            CallHandlingManager.onIncomingCall(incomingNumber, callerName)
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            CallHandlingManager.clearRingingCall()
        }
    }
}
