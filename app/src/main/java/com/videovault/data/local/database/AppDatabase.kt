package com.videovault.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.videovault.data.local.database.dao.DownloadHistoryDao
import com.videovault.data.local.database.entity.DownloadHistoryEntity

@Database(entities = [DownloadHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "videovault.db")
                .fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
