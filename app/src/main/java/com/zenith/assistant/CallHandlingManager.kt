package com.zenith.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager

data class RingingCallInfo(
    val number: String,
    val callerName: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CallHandlingManager {
    @Volatile var activeRingingCall: RingingCallInfo? = null

    fun onIncomingCall(number: String, callerName: String) {
        activeRingingCall = RingingCallInfo(number, callerName)
    }

    fun clearRingingCall() {
        activeRingingCall = null
    }

    fun acceptRingingCall(ctx: Context): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return try {
            val telecomManager = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telecomManager.acceptRingingCall()
                clearRingingCall()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
