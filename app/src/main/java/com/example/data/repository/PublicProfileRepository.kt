package com.example.data.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.ZenimeApp
import com.example.data.api.SupabaseNetworkModule
import com.example.data.api.ZenimeSupabaseApi
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.model.FavoriteUpsert
import com.example.data.model.PublicProfileContentResponse
import com.example.data.model.WatchHistoryUpsert
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response

private const val TAG = "PublicProfileSync"

/**
 * Sinkronisasi Favorit & Riwayat Tontonan ke Supabase (`user_favorites`,
 * `user_watch_history`), sekaligus baca konten publik punya user LAIN
 * langsung lewat PostgREST -- toggle privasi dipaksa di level RLS (lihat
 * backend/supabase/public_profile_setup.sql), bukan lewat Edge Function.
 *
 * Room ([com.example.data.local.ZenimeDatabase]) tetap sumber utama buat
 * pemilik data sendiri -- semua fungsi sync di sini best-effort/fire-and-
 * forget, dipanggil SETELAH tulis ke Room berhasil (lihat [AnimeRepository]).
 * Gagal sync gak dilempar balik ke pemanggil (biar gak ganggu pengalaman
 * nonton/nge-favorite kalau lagi offline), TAPI selalu di-log ke Logcat DAN
 * ditampilin sebagai Toast (lewat [ZenimeApp.instance], gak butuh Context
 * dari pemanggil) -- biar kelihatan LANGSUNG di HP pas testing tanpa perlu
 * adb/PC. Ini penting karena endpoint yang balikin `Response<Void>` TIDAK
 * nge-throw buat status HTTP non-2xx (beda sama endpoint yang balikin tipe
 * data biasa) -- tanpa cek `isSuccessful` manual, request yang ditolak
 * RLS/validasi server bakal keliatan "sukses" padahal gak nyimpen apa-apa.
 */
class PublicProfileRepository(
    private val api: ZenimeSupabaseApi = SupabaseNetworkModule.api
) {
    private fun showErrorToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ZenimeApp.instance, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun <T> logIfFailed(action: String, response: Response<T>) {
        if (!response.isSuccessful) {
            val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
            val message = "$action GAGAL -- HTTP ${response.code()}: $errorBody"
            Log.e(TAG, message)
            showErrorToast(message)
        }
    }

    private fun logException(action: String, e: Throwable) {
        Log.e(TAG, "$action EXCEPTION", e)
        showErrorToast("$action EXCEPTION: ${e.message}")
    }

    suspend fun syncFavoriteAdded(firebaseUid: String, favorite: FavoriteEntity) {
        runCatching {
            // Lewat RPC (fungsi security definer) -- bypass RLS di internal
            // upsert-nya, jadi gak kena bug 42501 pas toggle privasi lagi off.
            // Lihat catatan di ZenimeSupabaseApi.kt.
            val response = api.upsertFavorite(
                body = FavoriteUpsert(
                    firebaseUid = firebaseUid,
                    animeId = favorite.id,
                    title = favorite.title,
                    posterUrl = favorite.posterUrl,
                    type = favorite.type,
                    status = favorite.status
                )
            )
            logIfFailed("syncFavoriteAdded", response)
        }.onFailure { e -> logException("syncFavoriteAdded", e) }
    }

    suspend fun syncFavoriteRemoved(firebaseUid: String, animeId: String) {
        runCatching {
            val response = api.deleteFavoriteRemote(firebaseUidEq = "eq.$firebaseUid", animeIdEq = "eq.$animeId")
            logIfFailed("syncFavoriteRemoved", response)
        }.onFailure { e -> logException("syncFavoriteRemoved", e) }
    }

    suspend fun syncWatchProgress(firebaseUid: String, history: WatchHistoryEntity) {
        runCatching {
            // Lewat RPC (fungsi security definer) -- bypass RLS di internal
            // upsert-nya, jadi gak kena bug 42501 pas toggle privasi lagi off.
            // Lihat catatan di ZenimeSupabaseApi.kt.
            val response = api.upsertWatchHistoryRemote(
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
            logIfFailed("syncWatchProgress", response)
        }.onFailure { e -> logException("syncWatchProgress", e) }
    }

    suspend fun syncHistoryRemoved(firebaseUid: String, animeId: String) {
        runCatching {
            val response = api.deleteWatchHistoryRemote(firebaseUidEq = "eq.$firebaseUid", animeIdEq = "eq.$animeId")
            logIfFailed("syncHistoryRemoved", response)
        }.onFailure { e -> logException("syncHistoryRemoved", e) }
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
