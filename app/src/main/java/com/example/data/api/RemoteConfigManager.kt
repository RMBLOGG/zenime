package com.example.data.api

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Ambil base URL API dari Firebase Remote Config, supaya base URL bisa
 * diganti dari Firebase Console tanpa perlu update APK.
 *
 * Setup di Firebase Console:
 * 1. Buka Remote Config di project Firebase yang dipakai (google-services.json ini).
 * 2. Tambah parameter baru: key = "api_base_url", default value = DEFAULT_BASE_URL di bawah.
 * 3. Publish. Untuk ganti base URL nanti, tinggal edit value parameter itu lalu Publish lagi.
 *    Client akan pakai nilai baru di sesi berikutnya (setelah fetch berhasil).
 */
object RemoteConfigManager {

    // Dipakai kalau Remote Config belum sempat fetch (mis. run pertama kali offline).
    private const val DEFAULT_BASE_URL = "http://203.175.11.166:5001/api/"
    private const val KEY_BASE_URL = "api_base_url"

    private val remoteConfig by lazy {
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600 // cache 1 jam, hemat kuota fetch
                }
            )
            setDefaultsAsync(mapOf(KEY_BASE_URL to DEFAULT_BASE_URL))
        }
    }

    /**
     * Ambil base URL terbaru dari server Firebase (fetch + activate).
     * Kalau gagal (offline dll), diam-diam pakai nilai cache/default yang sudah ada.
     * Panggil ini sekali saja saat app start, sebelum request API pertama kalau bisa.
     */
    suspend fun refresh() {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (_: Exception) {
            // Fetch gagal (mis. offline) — lanjut pakai nilai cache/default, tidak fatal.
        }
    }

    /** Base URL saat ini (cache lokal dari fetch terakhir, atau default). */
    fun currentBaseUrl(): String {
        val value = remoteConfig.getString(KEY_BASE_URL)
        return value.ifBlank { DEFAULT_BASE_URL }
    }
}
