package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status siklus hidup satu proses download episode. QUEUED/DOWNLOADING
 * ditulis sama DownloadWorker selagi jalan; FAILED biar UI bisa nawarin
 * retry; COMPLETED berarti file di [localFilePath] siap diputar offline.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

/**
 * Satu baris = satu episode yang pernah/sedang di-download ke penyimpanan
 * app-private (getExternalFilesDir, BUKAN gallery publik -- lihat
 * EpisodeDownloadManager buat alasannya). File ikut kehapus otomatis kalau
 * app di-uninstall, dan gak nongol di Galeri/Photos user.
 */
@Entity(tableName = "downloaded_episodes")
data class DownloadedEpisodeEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episodeTitle: String?,
    val episodeIndex: String?,
    val quality: String?,
    val localFilePath: String?,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val workRequestId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
