package com.example.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.ZenimeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ngatur download episode buat ditonton offline (fitur khusus premium --
 * gating-nya dicek di pemanggil, lihat AnimeRepository.enqueueEpisodeDownload
 * dan PremiumAccess.kt).
 *
 * SENGAJA pakai android.app.DownloadManager (system service), pola yang
 * sama kayak ApkDownloader di util/ -- BUKAN custom downloader di dalam
 * proses app sendiri. Alasannya:
 *  - Jalan di proses/service sistem sendiri, jadi download tetep lanjut
 *    meskipun app di-swipe/kill dari recent apps.
 *  - Dukung custom header (Referer/User-Agent) yang dibutuhin server
 *    video ini, lewat addRequestHeader().
 *  - Auto-retry dan notifikasi bawaan sistem -- gak perlu bikin Foreground
 *    Service + notification channel sendiri dari nol.
 *
 * PENTING soal lokasi file: selalu disimpen ke
 * getExternalFilesDir(DIRECTORY_MOVIES), app-private -- BUKAN folder
 * Movies publik / MediaStore / Gallery. Alasannya:
 *  1) Gak butuh runtime storage permission di API manapun,
 *  2) Gak nongol di Galeri/Google Photos user lain,
 *  3) File ikut kehapus otomatis pas app di-uninstall,
 *  4) Gating premium tetep berarti -- file gak segampang itu di-share
 *     lepas dari kontrol app kalau disimpen di storage publik.
 */
class EpisodeDownloadManager(
    private val appContext: Context,
    private val dao: ZenimeDao
) {
    companion object {
        // Batas jumlah episode yang boleh disimpan offline BARENGAN (selesai +
        // lagi jalan). Sengaja dibatasi -- video full episode gampang beberapa
        // ratus MB, jadi tanpa batas storage user bisa penuh gak sadar.
        // Angka ini murni proteksi storage, BUKAN benefit tier premium --
        // semua user premium dapet limit yang sama.
        const val MAX_ACTIVE_DOWNLOADS = 15
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val systemDownloadManager: DownloadManager
        get() = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val allDownloads: Flow<List<DownloadedEpisodeEntity>> = dao.getAllDownloads()

    fun downloadsForAnime(animeId: String): Flow<List<DownloadedEpisodeEntity>> =
        dao.getDownloadsForAnime(animeId)

    fun downloadForEpisode(episodeId: String): Flow<DownloadedEpisodeEntity?> =
        dao.getDownloadForEpisode(episodeId)

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun destinationFile(animeId: String, episodeId: String): File {
        val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES), sanitize(animeId))
        dir.mkdirs()
        return File(dir, "${sanitize(episodeId)}.mp4")
    }

    /**
     * Mulai download satu episode. [videoUrl] wajib link stream yang BARU
     * aja di-fetch (bukan dari cache lama) -- link ini biasanya signed URL
     * dari upstream dengan masa berlaku pendek, lihat komentar di
     * AnimeRepository.getEpisodeStream soal ini.
     */
    suspend fun startDownload(
        episodeId: String,
        animeId: String,
        animeTitle: String,
        posterUrl: String?,
        episodeTitle: String?,
        episodeIndex: String?,
        quality: String?,
        videoUrl: String,
        episodeThumbnailUrl: String? = null
    ): Result<Unit> {
        val existing = dao.getDownloadForEpisodeOnce(episodeId)
        if (existing?.status == DownloadStatus.COMPLETED ||
            existing?.status == DownloadStatus.DOWNLOADING ||
            existing?.status == DownloadStatus.QUEUED
        ) {
            return Result.success(Unit) // udah ada / lagi jalan, gak usah dobel-download
        }

        // Kuota: FAILED gak ikut dihitung (lihat getActiveDownloadCountOnce),
        // jadi retry setelah gagal gak kejegal limit ini.
        val activeCount = dao.getActiveDownloadCountOnce()
        if (activeCount >= MAX_ACTIVE_DOWNLOADS) {
            return Result.failure(
                IllegalStateException(
                    "Batas maksimal $MAX_ACTIVE_DOWNLOADS episode offline udah tercapai. Hapus beberapa episode yang udah didownload dulu."
                )
            )
        }

        val file = destinationFile(animeId, episodeId)
        if (file.exists()) file.delete()

        val request = try {
            DownloadManager.Request(Uri.parse(videoUrl))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        request
            .setTitle(animeTitle)
            .setDescription(episodeTitle ?: "Episode ${episodeIndex.orEmpty()}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .addRequestHeader("Referer", "https://animeinweb.com/")
            .addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = try {
            systemDownloadManager.enqueue(request)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        dao.upsertDownload(
            DownloadedEpisodeEntity(
                episodeId = episodeId,
                animeId = animeId,
                animeTitle = animeTitle,
                posterUrl = posterUrl,
                episodeTitle = episodeTitle,
                episodeIndex = episodeIndex,
                quality = quality,
                localFilePath = file.absolutePath,
                status = DownloadStatus.QUEUED,
                workRequestId = downloadId.toString(),
                episodeThumbnailUrl = episodeThumbnailUrl
            )
        )

        pollProgress(episodeId, downloadId)
        return Result.success(Unit)
    }

    /** Balikin file lokal kalau episode ini udah kelar didownload & filenya masih ada di disk. */
    suspend fun localFileFor(episodeId: String): File? {
        val entry = dao.getDownloadForEpisodeOnce(episodeId) ?: return null
        if (entry.status != DownloadStatus.COMPLETED) return null
        val file = File(entry.localFilePath ?: return null)
        return if (file.exists()) file else null
    }

    /**
     * Dipanggil sekali pas app start (MainActivity). System DownloadManager
     * tetep lanjut download walau app-nya sendiri sempet ke-kill, jadi ini
     * cuma nyambungin lagi loop polling progress buat baris yang masih
     * QUEUED/DOWNLOADING di DB pas sesi sebelumnya.
     */
    fun reconcileActiveDownloads() {
        scope.launch {
            dao.getActiveDownloadsOnce().forEach { entry ->
                val id = entry.workRequestId?.toLongOrNull()
                if (id != null) pollProgress(entry.episodeId, id) else markFailed(entry.episodeId)
            }
        }
    }

    private fun pollProgress(episodeId: String, downloadId: Long) {
        scope.launch {
            var finished = false
            while (isActive && !finished) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = systemDownloadManager.query(query)
                if (cursor == null) {
                    markFailed(episodeId)
                    finished = true
                } else {
                    cursor.use {
                        if (!it.moveToFirst()) {
                            markFailed(episodeId)
                            finished = true
                            return@use
                        }
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                updateProgress(episodeId, bytes, total, DownloadStatus.COMPLETED)
                                finished = true
                            }
                            DownloadManager.STATUS_FAILED -> {
                                markFailed(episodeId)
                                finished = true
                            }
                            else -> updateProgress(episodeId, bytes, total, DownloadStatus.DOWNLOADING)
                        }
                    }
                }
                if (!finished) delay(500)
            }
        }
    }

    private suspend fun updateProgress(episodeId: String, bytes: Long, total: Long, status: DownloadStatus) {
        val current = dao.getDownloadForEpisodeOnce(episodeId) ?: return
        dao.upsertDownload(
            current.copy(
                downloadedBytes = bytes,
                totalBytes = if (total > 0) total else current.totalBytes,
                status = status,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun markFailed(episodeId: String) {
        val current = dao.getDownloadForEpisodeOnce(episodeId) ?: return
        dao.upsertDownload(current.copy(status = DownloadStatus.FAILED, updatedAt = System.currentTimeMillis()))
    }

    /** Batalin download yang lagi jalan (kalau ada) dan hapus file + record-nya sekalian. */
    suspend fun deleteDownload(episodeId: String) {
        val entry = dao.getDownloadForEpisodeOnce(episodeId) ?: return
        entry.workRequestId?.toLongOrNull()?.let { id ->
            runCatching { systemDownloadManager.remove(id) }
        }
        entry.localFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }
        dao.deleteDownload(episodeId)
    }
}
