package com.example.data.repository

import com.example.data.api.DayynimeV5Api
import com.example.data.common.Result
import com.example.data.download.EpisodeDownloadManager
import com.example.data.local.DownloadedEpisodeEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WatchHistoryEntity
import com.example.data.local.ZenimeDao
import com.example.data.model.AnimeItem
import com.example.data.model.EpisodeDetail
import com.example.data.model.EpisodeItem
import com.example.data.model.GenreItem
import com.example.data.model.HomeResponse
import com.example.data.model.RawEnvelope
import com.example.data.model.SearchResponse
import com.example.util.friendlyErrorMessage
import com.example.data.model.StreamResponse
import com.example.data.model.StreamServer
import com.example.util.qualityValueP
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class AnimeRepository(
    private val api: DayynimeV5Api,
    private val dao: ZenimeDao,
    val userPrefs: UserPreferencesRepository,
    private val downloadManager: EpisodeDownloadManager
) {

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

    // ---- Parsing amplop API baru --------------------------------------
    // API baru balikin {"status","error","data":{...}} dengan bentuk "data"
    // beda-beda tiap endpoint. RawEnvelope nampung "data" sebagai Map dulu
    // (lewat adapter Any bawaan Moshi), baru di-navigasi & di-convert manual
    // ke model yang udah ada di sini -- sama kayak first_list()/clean_movie()
    // di referensi Flask, biar gak gampang crash kalau bentuknya sedikit
    // meleset dari dugaan.
    private val rawMoshi: Moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }
    private val animeItemAdapter by lazy { rawMoshi.adapter(AnimeItem::class.java) }
    private val episodeItemAdapter by lazy { rawMoshi.adapter(EpisodeItem::class.java) }
    private val episodeDetailAdapter by lazy { rawMoshi.adapter(EpisodeDetail::class.java) }
    private val genreItemAdapter by lazy { rawMoshi.adapter(GenreItem::class.java) }
    private val streamServerAdapter by lazy { rawMoshi.adapter(StreamServer::class.java) }

    private val dayKeyToApiDay = mapOf(
        "monday" to "SENIN", "tuesday" to "SELASA", "wednesday" to "RABU",
        "thursday" to "KAMIS", "friday" to "JUMAT", "saturday" to "SABTU",
        "sunday" to "MINGGU"
    )

    @Suppress("UNCHECKED_CAST")
    private fun asMap(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun asMapList(value: Any?): List<Map<String, Any?>> =
        (value as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    /** Ambil list dari data[key]; kalau gak ada, pakai list pertama yang ketemu di data. */
    private fun firstList(data: Map<String, Any?>?, key: String): List<Map<String, Any?>> {
        if (data == null) return emptyList()
        asMapList(data[key]).let { if (it.isNotEmpty() || data[key] is List<*>) return it }
        for (v in data.values) {
            if (v is List<*>) return asMapList(v)
        }
        return emptyList()
    }

    private fun <T> Map<String, Any?>.toModelOrNull(adapter: com.squareup.moshi.JsonAdapter<T>): T? =
        try {
            adapter.fromJsonValue(this)
        } catch (e: Exception) {
            null
        }

    private fun movieMaps(env: RawEnvelope): List<AnimeItem> =
        firstList(env.data, "movie").mapNotNull { it.toModelOrNull(animeItemAdapter) }
            .filter { it.id.isNotBlank() }

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
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat beranda")))
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
            // API baru cuma punya section hot/new/popular/random (gak ada
            // today/trailer/waiting kayak API lama) -- ditembak paralel
            // biar beranda gak lebih lambat dari sebelumnya. Section yang
            // gak ada tetap null, aman karena HomeScreen udah pakai ?.let
            // buat nampilin tiap section (otomatis kesembunyi kalau null).
            val response = coroutineScope {
                val hotDef = async { runCatching { movieMaps(api.getHomeSection("hot")) } }
                val newDef = async { runCatching { movieMaps(api.getHomeSection("new")) } }
                val popularDef = async { runCatching { movieMaps(api.getHomeSection("popular")) } }
                val randomDef = async { runCatching { movieMaps(api.getHomeSection("random")) } }
                val results = listOf(hotDef, newDef, popularDef, randomDef).awaitAll()
                // Kalau SEMUA section gagal, anggap request gagal total (biar
                // fallback ke cache lama / pesan error jalan kayak biasa).
                if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
                HomeResponse(
                    hot = results[0].getOrNull(),
                    new = results[1].getOrNull(),
                    today = null,
                    popular = results[2].getOrNull(),
                    trailer = null,
                    random = results[3].getOrNull(),
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
            val response = runSearch(query, page, sort, genreIn, status, type)
            searchCache[key] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal melakukan pencarian")))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * API baru gak punya 1 endpoint pencarian gabungan (keyword+genre+
     * status+type+sort sekaligus kayak API lama) -- tiap dimensi filter
     * endpoint-nya sendiri:
     *   keyword -> explore/movie, genre -> explore/movie_genre,
     *   tipe -> explore/movie_type. "status" (Ongoing/Completed) malah
     *   gak ada endpoint filter-nya sama sekali di API baru.
     *
     * Jadi: satu filter jadi "endpoint utama" (prioritas: keyword > genre >
     * tipe), sisanya (termasuk status, yang emang gak ada endpoint-nya sama
     * sekali) diterapkan manual di sisi app dari hasil endpoint utama itu --
     * biar UI filter yang udah ada tetap kepake dan beberapa filter bisa
     * dikombinasi sekaligus, walau gak sekuat query gabungan di server dulu.
     * Kalau gak ada satupun dari keyword/genre/tipe yang dipilih, gak ada
     * endpoint yang cocok buat "tampilkan semua" -- balikin kosong dulu,
     * sama kayak halaman /cari di web referensi yang minta user ngetik dulu.
     */
    private suspend fun runSearch(
        query: String,
        page: Int?,
        sort: String?,
        genreIn: String?,
        status: String?,
        type: String?
    ): SearchResponse {
        val apiPage = page ?: 0
        val apiSort = sort?.takeIf { it == "views" || it == "alphabet" }

        val env: RawEnvelope? = when {
            query.isNotBlank() -> api.exploreByKeyword(keyword = query, sort = apiSort, page = apiPage)
            !genreIn.isNullOrBlank() -> api.exploreByGenre(idGenre = genreIn, sort = apiSort, page = apiPage)
            !type.isNullOrBlank() -> api.exploreByType(type = type, sort = apiSort, page = apiPage)
            else -> null
        }

        if (env == null) {
            return SearchResponse(query = query, page = apiPage.toString(), results = emptyList(), next_page = null)
        }

        var movies = movieMaps(env)
        val hasMoreRaw = movies.isNotEmpty()

        // Filter tambahan di sisi app buat dimensi yang bukan endpoint utama.
        if (!status.isNullOrBlank()) {
            movies = movies.filter { it.status?.trim()?.equals(status, ignoreCase = true) == true }
        }
        if (!type.isNullOrBlank() && query.isNotBlank()) {
            // type cuma jadi endpoint utama kalau keyword kosong -- kalau
            // keyword yang jadi utama, type diterapkan manual di sini.
            movies = movies.filter { it.type?.equals(type, ignoreCase = true) == true }
        }

        return SearchResponse(
            query = query,
            page = apiPage.toString(),
            results = movies,
            next_page = if (hasMoreRaw) apiPage + 1 else null
        )
    }

    // ---- Detail ----------------------------------------------------------
    fun getDetail(id: String, forceRefresh: Boolean = false): Flow<Result<AnimeItem>> = flow {
        val cached = detailCache[id]
        if (!forceRefresh && isFresh(cached, TTL_DETAIL)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val env = api.getDetailRaw(id)
            // Kadang "data" langsung berisi field-field anime-nya (flat),
            // kadang dibungkus lagi di data.movie -- dicoba dua-duanya sama
            // kayak fallback di web referensi.
            val movieMap = env.data?.let { d -> asMap(d["movie"]) ?: d }
            val response = movieMap?.toModelOrNull(animeItemAdapter)
                ?: throw IllegalStateException("Detail anime kosong dari server")
            detailCache[id] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat detail anime")))
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
            val response = fetchEpisodePage(id, page ?: 0)
            episodesCache[key] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat daftar episode")))
            }
        }
    }.flowOn(Dispatchers.IO)

    // movie/episode di API baru dipaginasi upstream mulai dari page=0 (bukan
    // pola null-lalu-1 kayak API lama). Buat anime yang episode-nya banyak
    // (One Piece dkk bisa 1000+), loop semua halaman di sini sampe ketemu
    // halaman kosong.
    fun getAllEpisodes(id: String, forceRefresh: Boolean = false): Flow<Result<List<EpisodeItem>>> = flow {
        val cached = allEpisodesCache[id]
        if (!forceRefresh && isFresh(cached, TTL_EPISODES)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val allEpisodes = mutableListOf<EpisodeItem>()
            var epPage = 0
            val MAX_EPISODE_PAGES = 60 // ~1800 episode, jauh di atas anime terpanjang yang ada
            while (epPage <= MAX_EPISODE_PAGES) {
                val pageResult = fetchEpisodePage(id, epPage)
                if (pageResult.isEmpty()) break
                allEpisodes.addAll(pageResult)
                epPage++
            }
            val result = allEpisodes.toList()
            allEpisodesCache[id] = CacheEntry(result, System.currentTimeMillis())
            emit(Result.Success(result))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat daftar episode")))
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchEpisodePage(id: String, page: Int): List<EpisodeItem> {
        val env = api.getEpisodesRaw(id, page = page)
        return firstList(env.data, "episode").mapNotNull { it.toModelOrNull(episodeItemAdapter) }
            .filter { it.id.isNotBlank() }
    }

    // ---- Episode stream ----------------------------------------------------
    // SENGAJA TIDAK DI-CACHE: link stream biasanya signed URL dengan masa
    // berlaku pendek dari upstream. Kalau di-cache dan URL-nya udah expired,
    // video bakal gagal diputar meskipun "keliatan" ada datanya.
    fun getEpisodeStream(episodeId: String): Flow<Result<StreamResponse>> = flow {
        emit(Result.Loading)
        try {
            val response = fetchStream(episodeId)
            emit(Result.Success(response))
        } catch (e: Exception) {
            emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat link streaming")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * "episode" & "episode_next" upstream kadang bukan object (mis. `false`
     * kalau gak ada episode berikutnya) -- asMap() balikin null diam-diam
     * buat kasus gitu, gak crash, sama kayak isinstance(nxt, dict) di
     * referensi Flask.
     */
    private suspend fun fetchStream(episodeId: String): StreamResponse {
        val data = api.getStreamRaw(episodeId).data
        val episode = asMap(data?.get("episode"))?.toModelOrNull(episodeDetailAdapter)
        val nextMap = asMap(data?.get("episode_next"))
        val episodeNext = if (nextMap != null && nextMap["id"] != null) {
            nextMap.toModelOrNull(episodeDetailAdapter)
        } else null
        val servers = firstList(data, "server")
            .mapNotNull { it.toModelOrNull(streamServerAdapter) }
            .filter { !it.link.isNullOrBlank() }
        return StreamResponse(episode = episode, episodeNext = episodeNext, servers = servers)
    }

    // ---- Schedule ----------------------------------------------------------
    fun getSchedule(day: String, forceRefresh: Boolean = false): Flow<Result<List<AnimeItem>>> = flow {
        val cached = scheduleCache[day]
        if (!forceRefresh && isFresh(cached, TTL_SCHEDULE)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            // API baru minta kode hari Indonesia (SENIN..MINGGU), sedangkan
            // ScheduleViewModel masih ngirim key Inggris (monday..sunday) --
            // ditranslate di sini biar ViewModel gak perlu diubah.
            val apiDay = dayKeyToApiDay[day.lowercase()] ?: day.uppercase()
            val env = api.getScheduleRaw(apiDay)
            val response = movieMaps(env)
            scheduleCache[day] = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat jadwal tayang")))
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
            val env = api.getGenresRaw()
            val response = firstList(env.data, "genre").mapNotNull { it.toModelOrNull(genreItemAdapter) }
            genresCache = CacheEntry(response, System.currentTimeMillis())
            emit(Result.Success(response))
        } catch (e: Exception) {
            if (cached != null) {
                emit(Result.Success(cached.data))
            } else {
                emit(Result.Error(e, friendlyErrorMessage(e, "Gagal memuat daftar genre")))
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

    // ---- Downloads (nonton offline, premium only) -------------------------
    val allDownloads: Flow<List<DownloadedEpisodeEntity>> = downloadManager.allDownloads

    fun downloadsForAnime(animeId: String): Flow<List<DownloadedEpisodeEntity>> =
        downloadManager.downloadsForAnime(animeId)

    fun downloadForEpisode(episodeId: String): Flow<DownloadedEpisodeEntity?> =
        downloadManager.downloadForEpisode(episodeId)

    suspend fun localDownloadedFile(episodeId: String) = downloadManager.localFileFor(episodeId)

    fun reconcileActiveDownloads() = downloadManager.reconcileActiveDownloads()

    suspend fun deleteEpisodeDownload(episodeId: String) = downloadManager.deleteDownload(episodeId)

    /**
     * Enqueue download episode buat offline. SENGAJA fetch stream link yang
     * BARU di sini (bukan pakai StreamResponse yang mungkin udah dipegang
     * ViewModel dari sebelumnya) -- link dari upstream itu signed URL yang
     * cepet expired, jadi selalu diambil pas-pasan sama waktu download-nya
     * dimulai, gak peduli caller-nya PlayerScreen atau episode list di
     * DetailScreen.
     *
     * Kualitas yang diambil otomatis yang PALING TINGGI dari server yang
     * tersedia -- gating premium buat fitur download ini sendiri (siapa
     * yang boleh mijit tombolnya) dicek di UI pakai isPremium, BUKAN di sini.
     */
    /**
     * Ambil daftar pilihan kualitas download yang tersedia buat satu episode.
     * Fetch fresh dari server (bukan cache) -- link masing-masing opsi cuma
     * valid sebentar, jadi dipanggil pas dialog pilih kualitas dibuka, dan
     * link yang dipilih user harus langsung dipakai buat enqueueEpisodeDownload
     * tanpa fetch ulang.
     *
     * Di-dedupe per label kualitas (kalau ada beberapa server dengan kualitas
     * sama, cuma yang pertama muncul yang dipakai) dan diurutkan dari
     * tertinggi ke terendah biar enak dipilih user.
     */
    suspend fun getDownloadQualityOptions(episodeId: String): Result<List<StreamServer>> {
        return try {
            val stream = fetchStream(episodeId)
            val options = stream.servers.orEmpty()
                .filter { !it.link.isNullOrBlank() }
                .distinctBy { it.quality }
                .sortedByDescending { qualityValueP(it.quality) ?: -1 }

            if (options.isEmpty()) {
                Result.Error(
                    IllegalStateException("Tidak ada server yang tersedia"),
                    "Tidak ada pilihan kualitas yang tersedia untuk episode ini"
                )
            } else {
                Result.Success(options)
            }
        } catch (e: Exception) {
            Result.Error(e, friendlyErrorMessage(e, "Gagal memuat pilihan kualitas"))
        }
    }

    /**
     * Mulai download episode dengan server/kualitas yang SUDAH dipilih user
     * (dari getDownloadQualityOptions). Gating premium (siapa yang boleh
     * mijit tombolnya) dicek di UI pakai isDownloadAllowed, BUKAN di sini.
     */
    suspend fun enqueueEpisodeDownload(
        episodeId: String,
        animeId: String,
        animeTitle: String,
        posterUrl: String?,
        episodeTitle: String?,
        episodeIndex: String?,
        server: StreamServer,
        episodeThumbnailUrl: String? = null
    ): Result<Unit> {
        val link = server.link
        if (link.isNullOrBlank()) {
            return Result.Error(
                IllegalStateException("Link kosong"),
                "Link download untuk kualitas ini tidak valid"
            )
        }

        val outcome = downloadManager.startDownload(
            episodeId = episodeId,
            animeId = animeId,
            animeTitle = animeTitle,
            posterUrl = posterUrl,
            episodeTitle = episodeTitle,
            episodeIndex = episodeIndex,
            quality = server.quality,
            videoUrl = link,
            episodeThumbnailUrl = episodeThumbnailUrl
        )

        return outcome.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { e -> Result.Error(e, friendlyErrorMessage(e, "Gagal memulai download")) }
        )
    }
}
