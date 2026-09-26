package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimeSupabaseApi
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.FavoriteUpsert
import com.example.data.model.PublicProfileContentResponse
import com.example.data.model.WatchHistoryUpsert
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Sinkronisasi Favorit & Riwayat Tontonan ke Supabase (`user_favorites`,
 * `user_watch_history`), sekaligus baca konten publik punya user LAIN
 * langsung lewat PostgREST -- toggle privasi dipaksa di level RLS (lihat
 * backend/supabase/public_profile_setup.sql), bukan lewat Edge Function.
 *
 * Room ([com.example.data.local.ZenimeDatabase]) tetap sumber utama buat
 * pemilik data sendiri -- semua fungsi sync di sini best-effort/fire-and-
 * forget, dipanggil SETELAH tulis ke Room berhasil (lihat [AnimeRepository]),
 * dan errornya sengaja ditelan biar gak ganggu pengalaman nonton/nge-favorite
 * kalau lagi offline atau server lagi bermasalah.
 */
class PublicProfileRepository(
    private val api: ZenimeSupabaseApi = SupabaseNetworkModule.api
) {
    suspend fun syncFavoriteAdded(firebaseUid: String, favorite: FavoriteEntity) {
        runCatching {
            api.upsertFavorite(
                body = FavoriteUpsert(
                    firebaseUid = firebaseUid,
                    animeId = favorite.id,
                    title = favorite.title,
                    posterUrl = favorite.posterUrl,
                    type = favorite.type,
                    status = favorite.status
                )
            )
        }
    }

    suspend fun syncFavoriteRemoved(firebaseUid: String, animeId: String) {
        runCatching {
            api.deleteFavoriteRemote(firebaseUidEq = "eq.$firebaseUid", animeIdEq = "eq.$animeId")
        }
    }

    suspend fun syncWatchProgress(firebaseUid: String, history: WatchHistoryEntity) {
        runCatching {
            api.upsertWatchHistoryRemote(
                body = WatchHistoryUpsert(
                    firebaseUid = firebaseUid,
                    animeId = history.animeId,
                    animeTitle = history.animeTitle,
                    posterUrl = history.posterUrl,
                    episodeId = history.episodeId,
                    episodeTitle = history.episodeTitle,
                    episodeIndex = history.episodeIndex,
                    progressMs = history.progressMs,
                    durationMs = history.durationMs
                )
            )
        }
    }

    suspend fun syncHistoryRemoved(firebaseUid: String, animeId: String) {
        runCatching {
            api.deleteWatchHistoryRemote(firebaseUidEq = "eq.$firebaseUid", animeIdEq = "eq.$animeId")
        }
    }

    /**
     * Ambil favorit/riwayat user lain -- null di dalamnya berarti privat, bukan error.
     *
     * Toggle privasi dibaca dulu dari `chat_profiles` (buat bedain "privat"
     * vs "publik tapi kosong"), baru favorit/riwayat ditarik BARENGAN kalau
     * toggle-nya nyala -- kalau mati, gak usah nembak request yang RLS-nya
     * bakal balikin kosong doang.
     */
    suspend fun getPublicContent(targetFirebaseUid: String): Result<PublicProfileContentResponse> =
        runCatching {
            coroutineScope {
                val profile = api.getChatProfile(firebaseUidEq = "eq.$targetFirebaseUid").firstOrNull()
                val favoritesPublic = profile?.favoritesPublic ?: false
                val historyPublic = profile?.historyPublic ?: false

                val favoritesDeferred = async {
                    if (favoritesPublic) api.getPublicFavorites(firebaseUidEq = "eq.$targetFirebaseUid") else null
                }
                val historyDeferred = async {
                    if (historyPublic) api.getPublicWatchHistory(firebaseUidEq = "eq.$targetFirebaseUid") else null
                }

                PublicProfileContentResponse(
                    favoritesPublic = favoritesPublic,
                    historyPublic = historyPublic,
                    favorites = favoritesDeferred.await(),
                    history = historyDeferred.await()
                )
            }
        }
}
