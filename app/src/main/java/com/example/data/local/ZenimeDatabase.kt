package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteEntity::class, WatchHistoryEntity::class, DownloadedEpisodeEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DownloadStatusConverter::class)
abstract class ZenimeDatabase : RoomDatabase() {
    abstract fun zenimeDao(): ZenimeDao

    companion object {
        @Volatile
        private var INSTANCE: ZenimeDatabase? = null

        // v1 -> v2: nambah tabel downloaded_episodes buat fitur nonton
        // offline. Ditulis manual (bukan destructive migration) biar
        // favorit & histori nonton user lama gak ikut kehapus.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloaded_episodes` (
                        `episodeId` TEXT NOT NULL PRIMARY KEY,
                        `animeId` TEXT NOT NULL,
                        `animeTitle` TEXT NOT NULL,
                        `posterUrl` TEXT,
                        `episodeTitle` TEXT,
                        `episodeIndex` TEXT,
                        `quality` TEXT,
                        `localFilePath` TEXT,
                        `totalBytes` INTEGER NOT NULL DEFAULT 0,
                        `downloadedBytes` INTEGER NOT NULL DEFAULT 0,
                        `status` TEXT NOT NULL DEFAULT 'QUEUED',
                        `workRequestId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): ZenimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenimeDatabase::class.java,
                    "zenime_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
