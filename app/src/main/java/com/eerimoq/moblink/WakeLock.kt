package com.eerimoq.moblink

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

class WakeLock {
    private var wakeLock: PowerManager.WakeLock? = null

    fun setup(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Moblink:lock")
    }

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        wakeLock?.acquire()
    }

    fun release() {
        wakeLock?.release()
    }
}
