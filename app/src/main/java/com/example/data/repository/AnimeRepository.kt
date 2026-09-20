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
import com.example.data.model.CuplixItem
import com.example.data.model.CuplixPage
import com.example.data.model.GalleryImage
import com.example.data.model.GalleryKind
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
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
        private const val CUPLIX_PAGE_SIZE = 30            // sama dengan limit yang dipakai app Animein
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
    private val cuplixItemAdapter by lazy { rawMoshi.adapter(CuplixItem::class.java) }

    private val dayKeyToApiDay = mapOf(
        "monday" to "SENIN", "tuesday" to "SELASA", "wednesday" to "RABU",
        "thursday" to "KAMIS", "friday" to "JUMAT", "saturday" to "SABTU",
        "sunday" to "MINGGU"
    )

    @Suppress("UNCHECKED_CAST")
    private fun asMap(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    // Array PHP kadang terkirim sebagai object ber-key angka ({"0":{..},"1":{..}}),
    // bukan [..] -- dua-duanya diterima (app Animein juga begitu: arrFlexible).
    @Suppress("UNCHECKED_CAST")
    private fun asMapList(value: Any?): List<Map<String, Any?>> = when (value) {
        is List<*> -> value.filterIsInstance<Map<String, Any?>>()
        is Map<*, *> ->
            if (value.isNotEmpty() && value.keys.all { it is String && it.toIntOrNull() != null }) {
                value.values.filterIsInstance<Map<String, Any?>>()
            } else {
                emptyList()
            }
        else -> emptyList()
    }

    /**
     * Pesan galat untuk tab detail: menyebut penyebab yang bisa dibedakan
     * (kode HTTP / format data) supaya gampang didiagnosis dari layar.
     */
    private fun tabError(e: Throwable, fallback: String): String = when (e) {
        is retrofit2.HttpException -> "$fallback (server membalas HTTP ${e.code()})."
        is com.squareup.moshi.JsonDataException -> "$fallback (format data dari server tidak sesuai)."
        is com.squareup.moshi.JsonEncodingException -> "$fallback (respons server bukan JSON yang valid)."
        else -> friendlyErrorMessage(e, fallback)
    }

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
                // Section tambahan dari data/home/list (Episode Baru, Jadwal Hari
                // Ini, Paling Dinanti). Gagal -> section-nya saja yang hilang;
                // TIDAK ikut menggagalkan beranda.
                val extrasDef = async { runCatching { fetchHomeExtras() } }
                val results = listOf(hotDef, newDef, popularDef, randomDef).awaitAll()
                val extras = extrasDef.await().getOrNull()
                // Kalau SEMUA section gagal, anggap request gagal total (biar
                // fallback ke cache lama / pesan error jalan kayak biasa).
                if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
                HomeResponse(
                    hot = results[0].getOrNull(),
                    new = results[1].getOrNull(),
                    today = extras?.today?.takeIf { it.isNotEmpty() },
                    popular = results[2].getOrNull(),
                    trailer = null,
                    // "Jas Por Yu": home/random cuma memberi ~3 item, sedangkan
                    // data/home/list (limit=10) memberi lebih banyak -- pakai yang
                    // paling banyak isinya.
                    random = listOfNotNull(results[3].getOrNull(), extras?.random)
                        .maxByOrNull { it.size }
                        ?.takeIf { it.isNotEmpty() },
                    waiting = extras?.waiting?.takeIf { it.isNotEmpty() },
                    update = extras?.update?.takeIf { it.isNotEmpty() },
                    updateEpisodeLabels = extras?.updateLabels?.takeIf { it.isNotEmpty() }
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

    // ---- Section tambahan Beranda (data/home/list) -----------------------
    private data class HomeExtras(
        val update: List<AnimeItem>,
        val updateLabels: Map<String, String>,
        val today: List<AnimeItem>,
        val waiting: List<AnimeItem>,
        val random: List<AnimeItem>
    )

    private suspend fun fetchHomeExtras(): HomeExtras {
        val day = todayApiDay()
        val data = api.getHomeListRaw(limit = 10, day = day).data

        fun movieList(key: String): List<AnimeItem> =
            asMapList(data?.get(key))
                .mapNotNull { it.toModelOrNull(animeItemAdapter) }
                .filter { it.id.isNotBlank() }

        // "Episode Baru": item-nya berformat film biasa. Label "Episode 23"
        // dicari dari field mana pun yang isinya nomor episode (nama field-nya
        // belum pasti), jadi dibaca dari map mentahnya.
        val updateRaw = asMapList(data?.get("update"))
        val labels = LinkedHashMap<String, String>()
        updateRaw.forEach { raw ->
            val id = raw["id"]?.toString().orEmpty()
            val label = episodeLabelOf(raw)
            if (id.isNotBlank() && label != null) labels[id] = label
        }

        var today = movieList("today")
        if (today.isEmpty()) {
            // Cadangan: jadwal hari ini dari endpoint jadwal yang sudah terbukti jalan.
            today = runCatching { movieMaps(api.getScheduleRaw(day)) }.getOrDefault(emptyList())
        }

        return HomeExtras(
            update = movieList("update"),
            updateLabels = labels,
            today = today,
            waiting = movieList("waiting"),
            random = movieList("random")
        )
    }

    private fun todayApiDay(): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Jakarta"))
        return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "SENIN"
            java.util.Calendar.TUESDAY -> "SELASA"
            java.util.Calendar.WEDNESDAY -> "RABU"
            java.util.Calendar.THURSDAY -> "KAMIS"
            java.util.Calendar.FRIDAY -> "JUMAT"
            java.util.Calendar.SATURDAY -> "SABTU"
            else -> "MINGGU"
        }
    }

    private val episodeTextRegex = Regex("(?:episode|eps?)\\.?\\s*\\d+(?:[.,]\\d+)?", RegexOption.IGNORE_CASE)
    private val episodeKeyRegex = Regex(
        "(?:episode|eps?|episode_(?:index|number|num|no)|(?:last|latest|new)_episode)",
        RegexOption.IGNORE_CASE
    )

    /** Cari nomor episode di item mentah; null kalau tidak ada. */
    private fun episodeLabelOf(raw: Map<String, Any?>): String? {
        // 1) sudah berbentuk teks "Episode 23" / "Eps 23"
        raw.values.forEach { v ->
            if (v is String && episodeTextRegex.matches(v.trim())) return v.trim()
        }
        // 2) field bernama episode/eps/episode_index yang isinya angka saja
        //    (id_episode sengaja tidak ikut: itu ID, bukan nomor)
        for ((k, v) in raw) {
            if (!episodeKeyRegex.matches(k)) continue
            val number = when (v) {
                is String -> v.trim().takeIf { s -> s.isNotEmpty() && s.all { c -> c.isDigit() || c == '.' } }
                is Number -> if (v.toDouble() % 1.0 == 0.0) v.toLong().toString() else v.toString()
                else -> null
            }
            if (number != null) return "Episode $number"
        }
        return null
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
    // Sumber daftar "Semua": true = explore/movie dengan keyword kosong,
    // false = cadangan home/popular. Ditentukan sekali di halaman 0 supaya
    // halaman berikutnya tidak tercampur dari dua sumber berbeda.
    @Volatile
    private var browseViaKeyword: Boolean? = null

    private suspend fun browseAll(sort: String?, page: Int): RawEnvelope? {
        if (browseViaKeyword == null || page == 0) {
            val viaKeyword = runCatching {
                api.exploreByKeyword(keyword = "", sort = sort, page = page)
            }.getOrNull()
            browseViaKeyword = viaKeyword != null && movieMaps(viaKeyword).isNotEmpty()
            if (browseViaKeyword == true) return viaKeyword
        }
        return if (browseViaKeyword == true) {
            api.exploreByKeyword(keyword = "", sort = sort, page = page)
        } else {
            runCatching { api.getHomeSection("popular", page) }.getOrNull()
        }
    }

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
            // "Semua" tanpa kata kunci/genre/tipe: dulu langsung kosong ("Anime
            // Tidak Ditemukan"). Sekarang tampil daftar jelajah.
            else -> browseAll(apiSort, apiPage)
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

    // ---- Cuplix (klip pendek gaya scroll) -----------------------------------
    /**
     * Satu batch Cuplix. [seenIds] = id yang sudah tampil (dikirim sebagai
     * key_id_fyp), [cursors] = kursor cursor_* dari batch sebelumnya. Bentuk
     * respons: data.fyp = [...]; kursor (kalau ada) dibaca dari key cursor_*
     * di data.
     */
    suspend fun getCuplixPage(
        sort: String,
        seenIds: List<String>,
        cursors: Map<String, String>
    ): Result<CuplixPage> = withContext(Dispatchers.IO) {
        try {
            val params = LinkedHashMap<String, String>()
            params["limit"] = CUPLIX_PAGE_SIZE.toString()
            params["sort"] = sort
            params.putAll(cursors)
            if (seenIds.isNotEmpty()) params["key_id_fyp"] = seenIds.takeLast(300).joinToString(",")

            val env = api.getCuplixRaw(params)
            val items = firstList(env.data, "fyp")
                .mapNotNull { it.toModelOrNull(cuplixItemAdapter) }
                .filter { it.id.isNotBlank() && !it.idEpisode.isNullOrBlank() }

            val serverCursors = LinkedHashMap<String, String>()
            env.data?.forEach { (k, v) ->
                if (k.startsWith("cursor_")) cursorValue(v)?.let { serverCursors[k] = it }
            }
            Result.Success(
                CuplixPage(
                    items = items,
                    cursors = if (serverCursors.isNotEmpty()) serverCursors else cursors,
                    hasMore = items.size >= CUPLIX_PAGE_SIZE
                )
            )
        } catch (e: Exception) {
            Result.Error(e, friendlyErrorMessage(e, "Gagal memuat Cuplix"))
        }
    }

    // ---- Tab halaman detail: Cuplix / Cover / Poster / Season -------------
    /**
     * Cuplix milik satu anime (data/movie/fyp/list_new). Item-nya sama dengan
     * Cuplix beranda. [page] mulai dari 0 di app aslinya -- pemanggil yang
     * menangani kemungkinan API mulai dari 1.
     */
    suspend fun getMovieCuplixPage(
        movieId: String,
        page: Int,
        type: String = "NEW"
    ): Result<CuplixPage> = withContext(Dispatchers.IO) {
        try {
            val env = api.getMovieCuplixRaw(
                mapOf("id_movie" to movieId, "page" to page.toString(), "type" to type)
            )
            val items = firstList(env.data, "fyp")
                .mapNotNull { it.toModelOrNull(cuplixItemAdapter) }
                .filter { it.id.isNotBlank() && !it.idEpisode.isNullOrBlank() }
            Result.Success(CuplixPage(items = items, cursors = emptyMap(), hasMore = items.isNotEmpty()))
        } catch (e: Exception) {
            Result.Error(e, tabError(e, "Gagal memuat Cuplix anime ini"))
        }
    }

    /** Galeri cover/poster kiriman user untuk satu anime. */
    suspend fun getMovieGallery(
        kind: GalleryKind,
        movieId: String,
        page: Int
    ): Result<List<GalleryImage>> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf("id_movie" to movieId, "page" to page.toString())
            val env = when (kind) {
                GalleryKind.COVER -> api.getMovieCoverRaw(params)
                GalleryKind.POSTER -> api.getMoviePosterRaw(params)
            }
            val key = if (kind == GalleryKind.COVER) "cover" else "poster"
            val images = firstList(env.data, key).mapNotNull { raw ->
                val id = raw["id"].asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val image = raw["image"].asText()?.takeIf { it.isNotBlank() }
                val url = when {
                    image == null -> null
                    image.startsWith("http") -> image
                    image.startsWith("/") -> "https://xyz-api.animein.net$image"
                    else -> null
                }
                GalleryImage(
                    id = id,
                    imageUrl = url,
                    points = raw["point"].asText(),
                    username = raw["username"].asText()?.takeIf { it.isNotBlank() },
                    isPro = raw["is_pro"].asText().let { it == "1" || it.equals("true", true) }
                )
            }.filter { it.imageUrl != null }
            Result.Success(images)
        } catch (e: Exception) {
            val label = if (kind == GalleryKind.COVER) "cover" else "poster"
            Result.Error(e, tabError(e, "Gagal memuat $label"))
        }
    }

    /**
     * Season lain dari anime ini. Tidak ada endpoint sendiri -- datanya ikut
     * di respons detail (app Animein membacanya lewat MovieUser.getMovieSeasons()).
     * Nama field-nya belum pasti, jadi dicari key mana pun yang mengandung
     * "season" dan berisi list.
     */
    suspend fun getSeasons(movieId: String): Result<List<AnimeItem>> = withContext(Dispatchers.IO) {
        try {
            val data = api.getDetailRaw(movieId).data
            val inner = data?.let { d -> asMap(d["movie"]) }
            val list = findSeasonList(data) ?: findSeasonList(inner)
            val seasons = asMapList(list)
                .mapNotNull { it.toModelOrNull(animeItemAdapter) }
                .filter { it.id.isNotBlank() }
            Result.Success(seasons)
        } catch (e: Exception) {
            Result.Error(e, tabError(e, "Gagal memuat season"))
        }
    }

    private fun findSeasonList(map: Map<String, Any?>?): List<*>? =
        map?.entries
            ?.firstOrNull { (k, v) -> k.contains("season", ignoreCase = true) && v is List<*> }
            ?.value as? List<*>

    private fun Any?.asText(): String? = when (this) {
        null -> null
        is String -> this
        is Double -> if (this % 1.0 == 0.0) toLong().toString() else toString()
        is Number -> toString()
        is Boolean -> toString()
        else -> null
    }

    // Moshi membaca angka JSON sebagai Double ("77" -> 77.0); rapikan lagi.
    private fun cursorValue(v: Any?): String? = when (v) {
        is String -> v
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Number -> v.toString()
        else -> null
    }

    /**
     * URL video direct untuk memutar klip: dipilih kualitas terdekat 480p
     * (cepat mulai, dan sama dengan batas kualitas non-premium). Klip cuma
     * bisa dari server bertipe "direct".
     */
    suspend fun getCuplixVideoUrl(episodeId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val direct = fetchStream(episodeId).servers.orEmpty()
                .filter { !it.link.isNullOrBlank() && it.type.equals("direct", ignoreCase = true) }
            val best = direct.minByOrNull { abs((qualityValueP(it.quality) ?: 480) - 480) }
            val link = best?.link
            if (link.isNullOrBlank()) {
                Result.Error(
                    IllegalStateException("Tidak ada server direct"),
                    "Klip ini tidak punya video yang bisa diputar."
                )
            } else {
                Result.Success(link)
            }
        } catch (e: Exception) {
            Result.Error(e, friendlyErrorMessage(e, "Gagal memuat video klip"))
        }
    }

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
