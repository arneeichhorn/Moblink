package com.eerimoq.moblink

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

class WakeLock {
    private var wakeLock: PowerManager.WakeLock? = null
    private var acquired = false

    fun setup(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Moblink:lock")
    }

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        if (!acquired) {
            wakeLock?.acquire()
            acquired = true
        }
    }

    fun release() {
        if (acquired) {
            wakeLock?.release()
            acquired = false
        }
    }
}
