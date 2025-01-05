package com.eerimoq.moblink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

private const val startAction = "Moblink:Start"
private const val stopAction = "Moblink:Stop"

fun startService(context: Context) {
    controlService(context, startAction)
}

fun stopService(context: Context) {
    controlService(context, stopAction)
}

private fun controlService(context: Context, action: String) {
    val intent = Intent(context, MoblinkService::class.java)
    intent.action = action
    context.startForegroundService(intent)
}

class MoblinkService : Service() {
    companion object {
        private const val CHANNEL_ID = "MoblinkChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == stopAction) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(
                CHANNEL_ID,
                "Moblink Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Moblink")
                .setContentText("Moblink relay active?")
                .setContentIntent(pendingIntent)
                .build()
        startForeground(1, notification)
    }
}
