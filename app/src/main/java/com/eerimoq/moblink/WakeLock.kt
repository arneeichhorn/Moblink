package com.eerimoq.moblink

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

class WakeLock {
    private var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("WakelockTimeout")
    fun acquire(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Moblink:lock")
        wakeLock?.acquire()
    }

    fun release() {
        wakeLock?.release()
    }
}
