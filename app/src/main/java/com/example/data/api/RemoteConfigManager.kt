package com.example.data.api

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Ambil base URL API dari Firebase Remote Config, supaya base URL bisa
 * diganti dari Firebase Console tanpa perlu update APK.
 *
 * SENGAJA TIDAK ADA base URL cadangan yang di-hardcode di APK. Firebase
 * Remote Config adalah satu-satunya sumber base URL. Konsekuensinya:
 * - Kalau parameter "api_base_url" di Console kosong/belum diisi, app
 *   TIDAK BISA akses API sama sekali (request gagal) -- ini kepake juga
 *   sebagai kill-switch resmi buat matiin akses API dari jarak jauh.
 * - Firebase Remote Config sendiri nyimpen hasil fetch terakhir yang
 *   sukses di local storage device (bukan hardcode kita), jadi begitu
 *   pernah fetch sukses sekali, app tetap bisa jalan offline pakai nilai
 *   itu -- ini caching bawaan SDK Firebase, bukan fallback yang kita bikin
 *   sendiri di kode.
 *
 * Setup di Firebase Console:
 * 1. Buka Remote Config di project Firebase yang dipakai (google-services.json ini).
 * 2. Tambah parameter baru: key = "api_base_url", isi value dengan base URL API-nya.
 * 3. Publish. Untuk ganti base URL nanti (atau matiin app), tinggal edit/kosongin
 *    value parameter itu lalu Publish lagi.
 */
object RemoteConfigManager {

    private const val KEY_BASE_URL = "api_base_url"

    private val remoteConfig by lazy {
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 3600 // cache 1 jam, hemat kuota fetch
                }
            )
            // Sengaja TIDAK setDefaultsAsync(...) -- gak ada nilai cadangan
            // yang ditanam di kode. Kalau belum pernah fetch sukses sama
            // sekali, value-nya kosong dan currentBaseUrl() return null.
        }
    }

    /**
     * Ambil base URL terbaru dari server Firebase (fetch + activate).
     * Kalau fetch gagal (offline dll), diam-diam lanjut pakai nilai hasil
     * fetch sukses terakhir yang udah ke-cache Firebase SDK di device ini.
     * Panggil ini sekali saja saat app start, sebelum request API pertama.
     */
    suspend fun refresh() {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (_: Exception) {
            // Fetch gagal (mis. offline) — lanjut pakai cache lokal Firebase
            // SDK dari fetch sukses sebelumnya (kalau ada).
        }
    }

    /**
     * Sama seperti refresh(), tapi motong minimumFetchIntervalInSeconds
     * (paksa fetch ke server, gak peduli kapan fetch terakhir). Dipakai
     * pas user manual pencet "Coba Lagi" di layar error -- supaya begitu
     * admin baru aja publish api_base_url baru di Console, user gak perlu
     * nunggu sampai 1 jam atau force-close app buat itu kebaca.
     *
     * Aman dipanggil sesering apa pun karena cuma jalan atas aksi manual
     * user (tombol retry), bukan otomatis tiap buka layar.
     */
    suspend fun forceRefresh() {
        try {
            remoteConfig.fetch(0).await()
            remoteConfig.activate().await()
        } catch (_: Exception) {
            // Fetch gagal (mis. offline) — biarin, currentBaseUrl() bakal
            // tetap pakai nilai cache lokal yang ada.
        }
    }

    /**
     * Base URL saat ini, murni dari Firebase Remote Config. Return null
     * kalau:
     * - Parameter "api_base_url" kosong/belum di-set di Console (baik
     *   sengaja dikosongin sebagai kill-switch, atau memang belum pernah
     *   diisi sama sekali), ATAU
     * - App belum pernah berhasil fetch config sama sekali (mis. install
     *   baru + langsung dibuka offline sebelum ada koneksi).
     *
     * Di kedua kasus itu, TIDAK ADA fallback ke URL manapun yang
     * di-hardcode di kode -- request API-nya wajib gagal, bukan diam-diam
     * jalan ke server lain.
     */
    fun currentBaseUrl(): String? {
        val value = remoteConfig.getString(KEY_BASE_URL)
        return value.ifBlank { null }
    }
}
