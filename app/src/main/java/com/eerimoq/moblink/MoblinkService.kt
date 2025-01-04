package com.eerimoq.moblink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

private enum class Actions {
    START,
    STOP,
}

fun startService(context: Context) {
    controlService(context, Actions.START)
}

fun stopService(context: Context) {
    controlService(context, Actions.STOP)
}

private fun controlService(context: Context, action: Actions) {
    val intent = Intent(context, MoblinkService::class.java)
    intent.action = action.name
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
        if (intent?.action == Actions.START.name) {
            Log.i("Moblink", "Start service")
        } else {
            Log.i("Moblink", "Stop service")
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
