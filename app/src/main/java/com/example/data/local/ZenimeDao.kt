package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ZenimeDao {

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :animeId)")
    fun isFavoriteFlow(animeId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :animeId")
    suspend fun deleteFavorite(animeId: String)

    // Watch History
    @Query("SELECT * FROM watch_history ORDER BY lastUpdated DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId LIMIT 1")
    fun getHistoryForAnime(animeId: String): Flow<WatchHistoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHistory(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE animeId = :animeId")
    suspend fun deleteHistory(animeId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    // Downloads
    @Query("SELECT * FROM downloaded_episodes ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes WHERE animeId = :animeId")
    fun getDownloadsForAnime(animeId: String): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes WHERE episodeId = :episodeId LIMIT 1")
    fun getDownloadForEpisode(episodeId: String): Flow<DownloadedEpisodeEntity?>

    @Query("SELECT * FROM downloaded_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getDownloadForEpisodeOnce(episodeId: String): DownloadedEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadedEpisodeEntity)

    @Query("DELETE FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun deleteDownload(episodeId: String)

    // Dipanggil sekali pas app start buat nyambungin lagi polling progress
    // punya download yang masih QUEUED/DOWNLOADING pas app kemarin ke-kill
    // (system DownloadManager sendiri tetep lanjut download di background).
    @Query("SELECT * FROM downloaded_episodes WHERE status = 'QUEUED' OR status = 'DOWNLOADING'")
    suspend fun getActiveDownloadsOnce(): List<DownloadedEpisodeEntity>

    // Dipakai buat cek kuota maksimal download offline -- FAILED gak
    // dihitung karena gak makan slot/storage beneran (file udah gagal/dihapus).
    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE status != 'FAILED'")
    suspend fun getActiveDownloadCountOnce(): Int
}
