package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Baris di tabel `chat_profiles` -- override username/avatar buat Chat
 * Global, SEKALIGUS dipakai sebagai data profil akun Zenime secara umum
 * (dipakai juga di ProfileScreen). `bannerUrl` sama-sama upload foto asli
 * kayak avatar, dan sama-sama dibatasi khusus member Premium (lihat
 * BannerUploader & ProfileViewModel.uploadBanner).
 */
@JsonClass(generateAdapter = true)
data class ChatProfile(
    @Json(name = "firebase_uid") val firebaseUid: String = "",
    @Json(name = "username") val username: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    // Warna custom username (hex, misal "#FF5733") -- null berarti pakai
    // warna default aplikasi (ZenimePrimary). Dipilih user sendiri lewat
    // dialog "Edit Profil" di Chat Global, dan kepake di SEMUA bubble chat
    // dia (punya sendiri maupun yang dilihat orang lain).
    @Json(name = "username_color") val usernameColor: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    // ID urut user (disalin dari profiles.user_number lewat trigger SQL) --
    // dipakai buat nampilin "#ID" di Chat Global tanpa Edge Function.
    @Json(name = "user_number") val userNumber: Long? = null,
    // Toggle privasi Favorit/Riwayat -- diatur user sendiri di ProfileScreen.
    // Default false (privat) sampai user sengaja nyalain. Dicek langsung di
    // RLS `user_favorites`/`user_watch_history` (lihat public_profile_setup.sql)
    // buat mutusin apa favorit/riwayat dia boleh keliatan user lain.
    @Json(name = "favorites_public") val favoritesPublic: Boolean = false,
    @Json(name = "history_public") val historyPublic: Boolean = false
)

/** Body buat upsert profil (insert kalau belum ada, update kalau udah ada). */
@JsonClass(generateAdapter = true)
data class ChatProfileUpsert(
    @Json(name = "firebase_uid") val firebaseUid: String,
    @Json(name = "username") val username: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "username_color") val usernameColor: String? = null,
    // WAJIB selalu kirim nilai toggle yang lagi aktif (bukan cuma pas
    // diubah) -- sama aturannya kayak field lain di sini, upsert ini
    // nge-replace SEMUA kolom yang dikirim.
    @Json(name = "favorites_public") val favoritesPublic: Boolean = false,
    @Json(name = "history_public") val historyPublic: Boolean = false
)
