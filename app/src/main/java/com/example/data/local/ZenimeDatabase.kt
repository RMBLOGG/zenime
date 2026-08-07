package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteEntity::class, WatchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ZenimeDatabase : RoomDatabase() {
    abstract fun zenimeDao(): ZenimeDao

    companion object {
        @Volatile
        private var INSTANCE: ZenimeDatabase? = null

        fun getInstance(context: Context): ZenimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenimeDatabase::class.java,
                    "zenime_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
