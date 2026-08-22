package com.example.data.repository

import com.example.data.api.AnimeinApi
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class AnimeRepository(
    private val api: AnimeinApi,
    private val dao: ZenimeDao,
    val userPrefs: UserPreferencesRepository
) {

    // day key Inggris ("monday"..) yang dipake ScheduleViewModel -> format
    // yang diminta backend native ANIMEIN (SENIN..MINGGU, huruf besar).
    private fun dayKeyToIndonesian(dayKey: String): String = when (dayKey.lowercase()) {
        "monday" -> "SENIN"
        "tuesday" -> "SELASA"
        "wednesday" -> "RABU"
        "thursday" -> "KAMIS"
        "friday" -> "JUMAT"
        "saturday" -> "SABTU"
        "sunday" -> "MINGGU"
        else -> dayKey.uppercase()
    }

    // ---- Cache infrastructure ----------------------------------------
    // In-memory, per-process cache. Ilang kalau proses app di-kill, tapi
    // itu udah cukup buat ngurangin beban API karena kasus paling sering
    // adalah user gonta-ganti tab / back-forth antar layar dalam satu sesi.
    private data class CacheEntry<T>(val data: T, val timestamp: Long)

    private fun isFresh(entry: CacheEntry<*>?, ttlMillis: Long): Boolean =
        entry != null && (System.currentTimeMillis() - entry.timestamp) < ttlMillis

    companion object {
        private const val TTL_HOME = 5 * 60 * 1000L        // 5 menit, konten homepage sering berubah
        private const val TTL_SEARCH = 5 * 60 * 1000L      // 5 menit
        private const val TTL_DETAIL = 30 * 60 * 1000L     // 30 menit, detail anime jarang berubah
        private const val TTL_EPISODES = 15 * 60 * 1000L   // 15 menit, episode baru bisa nambah
        private const val TTL_SCHEDULE = 30 * 60 * 1000L   // 30 menit
        private const val TTL_GENRES = 60 * 60 * 1000L     // 1 jam, list genre nyaris statis
    }

    private var homeCache: CacheEntry<HomeResponse>? = null
    private val homeMutex = Mutex()
    private var homeInFlight: CompletableDeferred<HomeResponse>? = null
    private val searchCache = ConcurrentHashMap<String, CacheEntry<SearchResponse>>()
    private val detailCache = ConcurrentHashMap<String, CacheEntry<AnimeItem>>()
    private val episodesCache = ConcurrentHashMap<String, CacheEntry<List<EpisodeItem>>>()
    private val allEpisodesCache = ConcurrentHashMap<String, CacheEntry<List<EpisodeItem>>>()
    private val scheduleCache = ConcurrentHashMap<String, CacheEntry<List<AnimeItem>>>()
    private var genresCache: CacheEntry<List<GenreItem>>? = null

    /** Panggil ini dari pull-to-refresh kalau nanti mau nambahin fitur itu. */
    fun clearAllCache() {
        homeCache = null
        searchCache.clear()
        detailCache.clear()
        episodesCache.clear()
        allEpisodesCache.clear()
        scheduleCache.clear()
        genresCache = null
    }

    // ---- Home ----------------------------------------------------------
    fun getHome(forceRefresh: Boolean = false): Flow<Result<HomeResponse>> = flow {
        val cached = homeCache
        if (!forceRefresh && isFresh(cached, TTL_HOME)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val response = fetchHomeDeduped()
            emit(Result.Success(response))
        } catch (e: Exception) {
            // API lagi bermasalah tapi masih ada cache lama -> tampilin
            // daripada nge-blank-in layar. Lebih baik data agak basi
            // daripada error total.
            val cachedAfterFailure = homeCache
            if (cachedAfterFailure != null) {
                emit(Result.Success(cachedAfterFailure.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat beranda"))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Kalau ada beberapa pemanggil getHome() hampir bersamaan (misal warm-up
     * di MainActivity.onCreate() dan LoginViewModel.init() yang jalan
     * beriringan pas app baru dibuka), cukup satu yang beneran nembak
     * network -- pemanggil lain nunggu hasil yang sama. Ini penting justru
     * di momen paling kritis (cold start), bukan cuma buat ngirit kuota.
     */
    private suspend fun fetchHomeDeduped(): HomeResponse {
        val existing = homeMutex.withLock { homeInFlight }
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<HomeResponse>()
        homeMutex.withLock { homeInFlight = deferred }
        return try {
            // 5 panggilan paralel: data/home/list (buat "history" -> fallback
            // "Baru Ditambahkan" kalau 3/2/home/new kosong) + 4 endpoint
            // 3/2/home/* yang KONFIRMASI mapping 1:1 ke section HomeScreen.
            // "today" & "waiting" tetap null -- belum ketemu endpoint
            // padanannya di backend native.
            val response = kotlinx.coroutines.coroutineScope {
                val history = async { api.getHome().data?.history }
                val hot = async { api.getHomeHot().data?.movie }
                val new = async { api.getHomeNew().data?.movie }
                val popular = async { api.getHomePopular().data?.movie }
                val random = async { api.getHomeRandom().data?.movie }
                HomeResponse(
                    hot = hot.await(),
                    new = new.await() ?: history.await(),
                    today = null,
                    popular = popular.await(),
                    trailer = null,
                    random = random.await(),
                    waiting = null
                )
            }
            homeCache = CacheEntry(response, System.currentTimeMillis())
            deferred.complete(response)
            response
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            homeMutex.withLock { homeInFlight = null }
        }
    }

    // ---- Search ----------------------------------------------------------
    fun search(
        query: String = "",
        page: Int? = null,
        sort: String? = null,
        genreIn: String? = null,
        status: String? = null,
        type: String? = null,
        forceRefresh: Boolean = false
    ): Flow<Result<SearchResponse>> = flow {
        val key = listOf(query, page, sort, genreIn, status, type).joinToString("|")
        val cached = searchCache[key]
        if (!forceRefresh && isFresh(cached, TTL_SEARCH)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            // KONFIRMASI: 3/2/explore/movie?keyword=... beneran filter by
            // substring title/synonyms (bukan lagi diabaikan kayak param "q"
            // dulu). next_page tetap null -- backend native gak ngasih
            // cursor pagination kayak animeinweb dulu, jadi search cuma
            // 1 halaman buat sekarang (loadNextPage() belum ngefek).
            val raw = api.exploreMovie(
                keyword = query.ifBlank { null },
                page = page,
                genre = genreIn,
                status = status,
                type = type
            ).data
            val response = SearchResponse(
                query = query,
                page = page?.toString(),
                results = raw?.movie,
                next_page = null
            )
            searchCache[key] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal melakukan pencarian"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Detail ----------------------------------------------------------
    fun getDetail(id: String, forceRefresh: Boolean = false): Flow<Result<AnimeItem>> = flow {
        val cached = detailCache[id]
        if (!forceRefresh && isFresh(cached, TTL_DETAIL)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            // KONFIRMASI: 3/2/movie/detail/{id} balikin persis 1 objek movie.
            val response = api.getMovieDetail(id).data?.movie
                ?: throw NoSuchElementException("Anime dengan id=$id tidak ditemukan")
            detailCache[id] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat detail anime"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Episodes (single page) ------------------------------------------
    fun getEpisodes(
        id: String,
        page: Int? = null,
        forceRefresh: Boolean = false
    ): Flow<Result<List<EpisodeItem>>> = flow {
        val key = "$id:$page"
        val cached = episodesCache[key]
        if (!forceRefresh && isFresh(cached, TTL_EPISODES)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            // Backend native ANIMEIN gak paginasi -- 3/2/movie/episode/{id}
            // konfirmasi balikin SEMUA episode dalam 1x panggilan tanpa
            // parameter page. Jadi param `page` di sini diabaikan sepenuhnya
            // (dipertahankan di signature biar ViewModel pemanggil gak perlu
            // diubah), dan hasilnya sama aja mau page berapa pun diminta.
            val response = api.getEpisodes(id).data?.episode.orEmpty()
            episodesCache[key] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat daftar episode"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // Backend native gak paginasi (beda sama animeinweb dulu yang 30/halaman)
    // -- 1x panggilan udah dapet semua episode, jadi gak perlu loop lagi.
    // Fungsi ini dipertahankan (bukan langsung alias ke getEpisodes) supaya
    // pemanggil yang udah ada gak perlu diubah.
    fun getAllEpisodes(id: String, forceRefresh: Boolean = false): Flow<Result<List<EpisodeItem>>> = flow {
        val cached = allEpisodesCache[id]
        if (!forceRefresh && isFresh(cached, TTL_EPISODES)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val result = api.getEpisodes(id).data?.episode.orEmpty()
            allEpisodesCache[id] = CacheEntry(result, System.currentTimeMillis())
            emit(Result.Success(result))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat daftar episode"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Episode stream ----------------------------------------------------
    // SENGAJA TIDAK DI-CACHE: link stream biasanya signed URL dengan masa
    // berlaku pendek dari upstream. Kalau di-cache dan URL-nya udah expired,
    // video bakal gagal diputar meskipun "keliatan" ada datanya.
    fun getEpisodeStream(episodeId: String): Flow<Result<StreamResponse>> = flow {
        emit(Result.Loading)
        try {
            val raw = api.getEpisodeStream(episodeId).data
            val response = StreamResponse(
                episode = raw?.episode,
                episodeNext = raw?.episodeNext,
                servers = raw?.server
            )
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat link streaming"))
        }
    }.flowOn(Dispatchers.IO)

    // ---- Schedule ----------------------------------------------------------
    fun getSchedule(day: String, forceRefresh: Boolean = false): Flow<Result<List<AnimeItem>>> = flow {
        val cached = scheduleCache[day]
        if (!forceRefresh && isFresh(cached, TTL_SCHEDULE)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            // ScheduleViewModel ngirim day key Inggris ("monday"..) -- backend
            // native wajib bahasa Indonesia huruf besar (SENIN..MINGGU).
            val response = api.getSchedule(dayKeyToIndonesian(day)).data?.movie.orEmpty()
            scheduleCache[day] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat jadwal tayang"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- Genres ----------------------------------------------------------
    fun getGenres(forceRefresh: Boolean = false): Flow<Result<List<GenreItem>>> = flow {
        val cached = genresCache
        if (!forceRefresh && isFresh(cached, TTL_GENRES)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            // KONFIRMASI: 3/2/explore/genre balikin 32 genre lengkap.
            val response = api.exploreGenre().data.orEmpty()
            genresCache = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, e.localizedMessage ?: "Gagal memuat daftar genre"))
            }
        }
    }.flowOn(Dispatchers.IO)

    // Local DB - Favorites
    val favorites: Flow<List<FavoriteEntity>> = dao.getAllFavorites()

    fun isFavorite(animeId: String): Flow<Boolean> = dao.isFavoriteFlow(animeId)

    // Buat hapus langsung dari kartu Favorit di halaman Koleksi (swipe atau
    // tombol trash) -- gak butuh AnimeItem lengkap kayak toggleFavorite,
    // cukup id-nya doang.
    suspend fun removeFavorite(animeId: String) {
        dao.deleteFavorite(animeId)
    }

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
