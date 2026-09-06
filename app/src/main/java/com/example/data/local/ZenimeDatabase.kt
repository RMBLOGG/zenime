package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        DownloadedEpisodeEntity::class,
        ComicFavoriteEntity::class,
        ComicReadingProgressEntity::class
    ],
    version = 4,
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

        // v2 -> v3: nambah kolom episodeThumbnailUrl -- thumbnail episode itu
        // sendiri (beda sama posterUrl yang gambar anime generik), dipakai
        // di kartu tab "Download" biar konsisten sama thumbnail yang udah
        // tampil di daftar episode DetailScreen. NULL buat baris lama,
        // otomatis fallback ke posterUrl di UI.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloaded_episodes` ADD COLUMN `episodeThumbnailUrl` TEXT")
            }
        }

        // v3 -> v4: nambah tabel comic_favorites (bookmark komik) dan
        // comic_reading_progress ("Lanjutkan Baca" per komik). Ditulis manual
        // biar data lama (favorit/histori/download anime) gak ikut kehapus.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `comic_favorites` (
                        `slug` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `cover` TEXT,
                        `status` TEXT,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `comic_reading_progress` (
                        `comicSlug` TEXT NOT NULL PRIMARY KEY,
                        `comicTitle` TEXT NOT NULL,
                        `comicCover` TEXT,
                        `chapterSlug` TEXT NOT NULL,
                        `chapterLabel` TEXT,
                        `scrollItemIndex` INTEGER NOT NULL DEFAULT 0,
                        `scrollItemOffset` INTEGER NOT NULL DEFAULT 0,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
