package com.videovault

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.videovault.data.local.database.AppDatabase
import com.videovault.data.local.DownloadManager

class App : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var downloadManager: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        downloadManager = DownloadManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Download progress"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SERVICE, "Download Service", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Active download"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_SERVICE = "service"
        @Volatile private var instance: App? = null
        fun getInstance(): App = instance ?: throw IllegalStateException("App not initialized")
    }
}
