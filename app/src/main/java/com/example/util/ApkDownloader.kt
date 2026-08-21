package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * State progres download APK update, di-observe ForceUpdateScreen buat
 * nentuin UI mana yang ditampilin (tombol "Update" / progress bar /
 * tombol "Install" / error + "Coba Lagi").
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Downloaded(val fileUri: Uri) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

/**
 * Download APK update LANGSUNG DI DALAM APP pakai android.app.DownloadManager
 * (bawaan sistem, jalan di proses terpisah jadi tahan app di-background/
 * di-kill sekalipun) -- GAK ADA LAGI Intent.ACTION_VIEW ke browser. Progress
 * di-poll tiap 300ms dari DownloadManager.query() dan di-expose lewat
 * [state] (StateFlow) supaya Compose tinggal collectAsState.
 *
 * Begitu selesai, file APK-nya dikasih ke PackageInstaller sistem lewat
 * content:// URI dari FileProvider (lihat file_paths.xml + <provider> di
 * AndroidManifest) -- file:// URI antar-app diblokir sejak API 24+.
 *
 * Dipakai dari MainActivity, di-scope ke lifecycle Activity situ (bukan
 * singleton/object) supaya gak nyisain coroutine polling nyangkut kalau
 * Activity-nya udah kelar.
 */
class ApkDownloader(private val context: Context) {

    private val downloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var currentDownloadId: Long? = null
    private var pollingJob: Job? = null

    // Simpan ke folder app-specific (getExternalFilesDir) -- TIDAK butuh
    // permission WRITE_EXTERNAL_STORAGE di API manapun, dan cuma folder
    // "Download/" di dalamnya yang di-expose FileProvider (lihat file_paths.xml).
    private val destinationFile: File
        get() = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "zenime-update.apk"
        )

    /** Mulai download APK dari [url]. Aman dipanggil ulang buat retry. */
    fun startDownload(url: String) {
        if (url.isBlank()) {
            _state.value = DownloadState.Failed("Link download belum diisi admin di Remote Config.")
            return
        }

        val file = destinationFile
        file.parentFile?.mkdirs()
        // Hapus sisa download lama (misal gagal/dibatalin sebelumnya) supaya
        // DownloadManager gak nolak/nimpa aneh ke path yang sama.
        if (file.exists()) file.delete()

        val request = try {
            DownloadManager.Request(Uri.parse(url))
        } catch (_: Exception) {
            _state.value = DownloadState.Failed("Link download tidak valid.")
            return
        }

        request
            .setTitle("Update Zenime")
            .setDescription("Mengunduh pembaruan aplikasi…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        currentDownloadId = try {
            downloadManager.enqueue(request)
        } catch (_: Exception) {
            _state.value = DownloadState.Failed("Gagal memulai unduhan, coba lagi.")
            return
        }

        _state.value = DownloadState.Downloading(0, 0, 0)
        startPolling(file)
    }

    private fun startPolling(file: File) {
        pollingJob?.cancel()
        val id = currentDownloadId ?: return
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(id)
                var finished = false

                downloadManager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        // Entry-nya hilang (mis. dihapus dari luar) -- anggap gagal.
                        _state.value = DownloadState.Failed("Unduhan tidak ditemukan, coba lagi.")
                        finished = true
                        return@use
                    }

                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            _state.value = DownloadState.Downloaded(uri)
                            finished = true
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            _state.value = DownloadState.Failed(
                                "Unduhan gagal (kode ${cursor.getInt(reasonIdx)}), coba lagi."
                            )
                            finished = true
                        }
                        else -> {
                            val progress = if (total > 0) ((bytes * 100) / total).toInt() else 0
                            _state.value = DownloadState.Downloading(progress, bytes, total)
                        }
                    }
                } ?: run {
                    _state.value = DownloadState.Failed("Unduhan tidak ditemukan, coba lagi.")
                    finished = true
                }

                if (finished) return@launch
                delay(300)
            }
        }
    }

    /**
     * Buka PackageInstaller sistem buat APK yang udah kelar didownload.
     * Kalau app ini belum diizinkan "Install dari sumber ini" (API 26+),
     * PackageInstaller sendiri yang bakal nampilin prompt buat aktifin itu
     * di Settings -- gak perlu dihandle manual di sini.
     */
    fun launchInstall(fileUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Batalin download yang lagi jalan (mis. dipanggil dari onCleared/onDestroy). */
    fun cancel() {
        pollingJob?.cancel()
        currentDownloadId?.let { downloadManager.remove(it) }
        currentDownloadId = null
    }
}
