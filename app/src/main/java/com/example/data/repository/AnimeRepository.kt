package com.example.data.repository

import com.example.data.api.DayynimeV5Api
import com.example.data.common.Result
import com.example.data.local.FavoriteEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WatchHistoryEntity
import com.example.data.local.ZenimeDao
import com.example.data.model.AnimeItem
import com.example.data.model.EpisodeItem
import com.example.data.model.GenreItem
import com.example.data.model.HomeResponse
import com.example.data.model.SearchResponse
import com.example.data.model.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AnimeRepository(
    private val api: DayynimeV5Api,
    private val dao: ZenimeDao,
    val userPrefs: UserPreferencesRepository
) {

    fun getHome(): Flow<Result<HomeResponse>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getHome()
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat beranda"))
        }
    }.flowOn(Dispatchers.IO)

    fun search(
        query: String = "",
        page: Int? = null,
        sort: String? = null,
        genreIn: String? = null,
        status: String? = null,
        type: String? = null
    ): Flow<Result<SearchResponse>> = flow {
        emit(Result.Loading)
        try {
            val response = api.search(
                keyword = query,
                page = page,
                sort = sort,
                genreIn = genreIn,
                status = status,
                type = type
            )
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal melakukan pencarian"))
        }
    }.flowOn(Dispatchers.IO)

    fun getDetail(id: String): Flow<Result<AnimeItem>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getDetail(id)
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat detail anime"))
        }
    }.flowOn(Dispatchers.IO)

    fun getEpisodes(id: String, page: Int? = null): Flow<Result<List<EpisodeItem>>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getEpisodes(id, page)
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat daftar episode"))
        }
    }.flowOn(Dispatchers.IO)

    // animeinweb /api/anime/{id}/episodes dipaginasi upstream (30/halaman).
    // Buat anime yang episode-nya banyak (One Piece dkk bisa 1000+), loop semua
    // halaman di sini sampe ketemu halaman kosong. Batch pertama request TANPA
    // page param sama sekali (bukan page=1) -- page=1 itu udah batch KEDUA
    // di upstream. Sama persis pattern yang dipakai di Aniku.
    fun getAllEpisodes(id: String): Flow<Result<List<EpisodeItem>>> = flow {
        emit(Result.Loading)
        try {
            val allEpisodes = mutableListOf<EpisodeItem>()
            val firstBatch = api.getEpisodes(id, page = null)
            allEpisodes.addAll(firstBatch)
            if (firstBatch.isNotEmpty()) {
                var epPage = 1
                val MAX_EPISODE_PAGES = 60 // ~1800 episode, jauh di atas anime terpanjang yang ada
                while (epPage <= MAX_EPISODE_PAGES) {
                    val pageResult = api.getEpisodes(id, page = epPage)
                    if (pageResult.isEmpty()) break
                    allEpisodes.addAll(pageResult)
                    epPage++
                }
            }
            emit(Result.Success(allEpisodes.toList()))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat daftar episode"))
        }
    }.flowOn(Dispatchers.IO)

    fun getEpisodeStream(episodeId: String): Flow<Result<StreamResponse>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getEpisodeStream(episodeId)
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat link streaming"))
        }
    }.flowOn(Dispatchers.IO)

    fun getSchedule(day: String): Flow<Result<List<AnimeItem>>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getSchedule(day)
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat jadwal tayang"))
        }
    }.flowOn(Dispatchers.IO)

    fun getGenres(): Flow<Result<List<GenreItem>>> = flow {
        emit(Result.Loading)
        try {
            val response = api.getGenres()
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat daftar genre"))
        }
    }.flowOn(Dispatchers.IO)

    // Local DB - Favorites
    val favorites: Flow<List<FavoriteEntity>> = dao.getAllFavorites()

    fun isFavorite(animeId: String): Flow<Boolean> = dao.isFavoriteFlow(animeId)

    suspend fun toggleFavorite(anime: AnimeItem, isCurrentlyFavorite: Boolean) {
        if (isCurrentlyFavorite) {
            dao.deleteFavorite(anime.id)
        } else {
            dao.insertFavorite(
                FavoriteEntity(
                    id = anime.id,
                    title = anime.title ?: "Tanpa Judul",
                    posterUrl = anime.image_poster,
                    type = anime.type,
                    status = anime.status
                )
            )
        }
    }

    // Local DB - Watch History
    val watchHistory: Flow<List<WatchHistoryEntity>> = dao.getAllHistory()

    fun getHistoryForAnime(animeId: String): Flow<WatchHistoryEntity?> = dao.getHistoryForAnime(animeId)

    suspend fun saveWatchProgress(
        animeId: String,
        animeTitle: String,
        posterUrl: String?,
        episodeId: String,
        episodeTitle: String?,
        episodeIndex: String?,
        progressMs: Long,
        durationMs: Long
    ) {
        dao.insertOrUpdateHistory(
            WatchHistoryEntity(
                animeId = animeId,
                animeTitle = animeTitle,
                posterUrl = posterUrl,
                episodeId = episodeId,
                episodeTitle = episodeTitle,
                episodeIndex = episodeIndex,
                progressMs = progressMs,
                durationMs = durationMs,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistory(animeId: String) = dao.deleteHistory(animeId)
    suspend fun clearHistory() = dao.clearHistory()
}
