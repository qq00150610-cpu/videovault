package com.videovault.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.videovault.App
import com.videovault.R
import com.videovault.ui.MainActivity
import kotlinx.coroutines.*

class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_STOP -> stopSelf() }
        val n = createNotification("Downloading videos...", 0)
        startForeground(NOTIFICATION_ID, n)
        return START_NOT_STICKY
    }

    private fun createNotification(msg: String, progress: Int): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, App.CHANNEL_SERVICE).setContentTitle("VideoVault").setContentText(msg).setSmallIcon(R.drawable.ic_download).setContentIntent(pi).setOngoing(true)
        if (progress > 0) b.setProgress(100, progress, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    companion object {
        const val ACTION_STOP = "com.videovault.action.STOP"
        const val NOTIFICATION_ID = 1001
    }
}
