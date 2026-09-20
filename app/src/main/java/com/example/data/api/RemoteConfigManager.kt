package com.example.data.api

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

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
/**
 * Data pop up pengumuman in-app yang dibaca dari Remote Config
 * (lihat RemoteConfigManager.currentPopup()).
 *
 * [id] dipakai buat nandain "popup ini udah pernah dilihat user" --
 * ganti nilai popup_id di Console tiap mau nampilin popup BARU.
 */
data class AnnouncementPopup(
    val id: String,
    val title: String,
    val message: String,
    val buttonText: String,
    val buttonUrl: String
)

object RemoteConfigManager {

    private const val KEY_BASE_URL = "api_base_url"

    // Catatan: pengecekan force-update TIDAK lagi lewat Remote Config --
    // sekarang pakai GithubUpdateChecker (cek langsung ke GitHub Releases),
    // supaya gak kena cache/throttle minimumFetchIntervalInSeconds di bawah.

    // --- Feature flags ---
    // KEY_FEATURE_FLAGS: JSON object flat, mis. {"downloads":false,"live_chat":true}.
    // Fitur yang KEY-nya gak ada di JSON dianggap AKTIF (default true) --
    // supaya nambah fitur baru gak perlu daftarin dulu di Console.
    private const val KEY_FEATURE_FLAGS = "feature_flags"

    // --- Maintenance mode ---
    // Blokir total app (lewat MaintenanceScreen) kapan pun tanpa perlu
    // rilis versi baru -- dipakai pas Supabase/server backend lagi
    // diperbaiki. Setup di Console: tambah parameter "maintenance_mode"
    // (Boolean), lalu opsional "maintenance_title"/"maintenance_message"
    // (String) buat custom pesannya. Kosong = pakai default di bawah.
    private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
    private const val KEY_MAINTENANCE_TITLE = "maintenance_title"
    private const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"

    // --- Pop up pengumuman in-app (real-time) ---
    // Saklar utama = "popup_enabled" (Boolean). OFF (atau parameter belum
    // ada di Console) -> gak ada popup sama sekali, dan kalau lagi tampil
    // langsung hilang begitu di-OFF-in + Publish.
    // Parameter lain (semua String):
    //   popup_id           -> ganti tiap mau nampilin popup baru; user yang
    //                         udah nutup popup dengan id yang sama gak akan
    //                         lihat lagi. Kosong = id dibikin dari isi teks.
    //   popup_title        -> judul popup
    //   popup_message      -> isi popup
    //   popup_button_text  -> teks tombol aksi (opsional)
    //   popup_button_url   -> link http/https yang dibuka tombol (opsional)
    private const val KEY_POPUP_ENABLED = "popup_enabled"
    private const val KEY_POPUP_ID = "popup_id"
    private const val KEY_POPUP_TITLE = "popup_title"
    private const val KEY_POPUP_MESSAGE = "popup_message"
    private const val KEY_POPUP_BUTTON_TEXT = "popup_button_text"
    private const val KEY_POPUP_BUTTON_URL = "popup_button_url"

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
            featureFlagsCache = null // config baru ke-activate, buang cache lama
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
            featureFlagsCache = null // config baru ke-activate, buang cache lama
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

    /** True kalau parameter "maintenance_mode" di Console lagi diaktifkan. */
    fun isMaintenanceMode(): Boolean = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE)

    fun maintenanceTitle(): String =
        remoteConfig.getString(KEY_MAINTENANCE_TITLE).ifBlank { "Sedang Maintenance" }

    fun maintenanceMessage(): String =
        remoteConfig.getString(KEY_MAINTENANCE_MESSAGE).ifBlank {
            "Server sedang dalam perbaikan. Zenime akan kembali normal sebentar lagi."
        }

    // Cache hasil parse JSON feature_flags biar gak parse ulang tiap
    // isFeatureEnabled() dipanggil. Di-reset tiap kali refresh()/forceRefresh()
    // sukses activate config baru (lihat activateFeatureFlagsCache di bawah).
    private var featureFlagsCache: JSONObject? = null

    /**
     * Cek apakah sebuah fitur aktif dari parameter "feature_flags" di
     * Firebase Console. Key yang gak ada di JSON dianggap aktif (default
     * true) supaya fitur baru gak perlu didaftarin dulu di Console sebelum
     * dipakai.
     *
     * Contoh isi parameter "feature_flags" di Console:
     * {"downloads": false, "live_chat": true}
     */
    fun isFeatureEnabled(key: String, default: Boolean = true): Boolean {
        val flags = featureFlagsCache ?: parseFeatureFlags().also { featureFlagsCache = it }
        if (!flags.has(key)) return default
        return flags.optBoolean(key, default)
    }

    private fun parseFeatureFlags(): JSONObject {
        val raw = remoteConfig.getString(KEY_FEATURE_FLAGS)
        return try {
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            // JSON di Console salah format -- daripada app crash, anggap
            // semua fitur pakai default masing-masing.
            JSONObject()
        }
    }

    /**
     * Pop up pengumuman yang lagi aktif, atau null kalau saklar
     * "popup_enabled" OFF / judul & isi sama-sama kosong.
     */
    fun currentPopup(): AnnouncementPopup? {
        if (!remoteConfig.getBoolean(KEY_POPUP_ENABLED)) return null
        val title = remoteConfig.getString(KEY_POPUP_TITLE).trim()
        val message = remoteConfig.getString(KEY_POPUP_MESSAGE).trim()
        if (title.isEmpty() && message.isEmpty()) return null
        val id = remoteConfig.getString(KEY_POPUP_ID).trim()
            .ifEmpty { "$title|$message".hashCode().toString() }
        return AnnouncementPopup(
            id = id,
            title = title,
            message = message,
            buttonText = remoteConfig.getString(KEY_POPUP_BUTTON_TEXT).trim(),
            buttonUrl = remoteConfig.getString(KEY_POPUP_BUTTON_URL).trim()
        )
    }

    /**
     * Dengerin update Remote Config secara REAL-TIME (server nge-push begitu
     * kamu Publish di Console, gak kena cache 1 jam). Config baru otomatis
     * di-activate dulu, baru [onUpdated] dipanggil -- di dalamnya tinggal
     * baca ulang currentPopup() dll.
     *
     * Panggil di onStart dan lepas (remove()) di onStop. Cuma jalan selama
     * app kebuka; kalau app lagi ketutup, perubahan kebaca pas app dibuka
     * lagi. [onUpdated] bisa dipanggil dari thread background.
     */
    fun listenRealtime(onUpdated: () -> Unit): ConfigUpdateListenerRegistration =
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener {
                    featureFlagsCache = null
                    onUpdated()
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                // Koneksi real-time putus/gagal -- diemin aja, SDK nyoba
                // nyambung lagi sendiri. Nilai terakhir tetap dipakai.
            }
        })
}
