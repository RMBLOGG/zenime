package com.example.data.repository

import com.example.data.api.AnichinApi
import com.example.data.api.AnichinNetwork
import com.example.data.common.Result
import com.example.data.model.AnichinAnimeDetail
import com.example.data.model.AnichinEpisodeDetail
import com.example.data.model.AnichinEpisodeRef
import com.example.data.model.AnichinGenre
import com.example.data.model.AnichinHomeResponse
import com.example.data.model.AnichinListResponse
import com.example.data.model.AnichinMedia
import com.example.data.model.AnichinPlayer
import com.example.data.model.AnichinVideoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AnichinRepository(
    private val api: AnichinApi = AnichinNetwork.api
) {

    // Semua fungsi balikin Flow<Result<T>> (Loading -> Success/Error) sama
    // kayak ComicRepository, jadi gampang dipakai di ViewModel yang ada.
    private fun <T> request(block: suspend () -> T): Flow<Result<T>> = flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(block()))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    fun getHome(page: Int = 1): Flow<Result<AnichinHomeResponse>> =
        request { api.getHome(if (page > 1) page else null).throwIfError { it.error } }

    fun search(query: String): Flow<Result<AnichinListResponse>> = request {
        // "/" di path bakal kena 404 di server, jadi diganti spasi
        api.search(query.trim().replace('/', ' ')).throwIfError { it.error }
    }

    fun getAnimeList(
        status: String? = null,
        type: String? = null,
        order: String? = null,
        extra: Map<String, String> = emptyMap()
    ): Flow<Result<AnichinListResponse>> = request {
        val params = buildMap<String, String> {
            status?.let { put("status", it) }
            type?.let { put("type", it) }
            order?.let { put("order", it) }
            putAll(extra)
        }
        api.getAnimeList(params).throwIfError { it.error }
    }

    fun getGenres(): Flow<Result<List<AnichinGenre>>> = request {
        val res = api.getGenres().throwIfError { it.error }
        // Server kadang ngirim daftar genre kedobel
        res.genres.orEmpty().distinctBy { it.slug }
    }

    fun getByGenre(slug: String, page: Int = 1): Flow<Result<AnichinListResponse>> = request {
        api.getByGenre(slug, if (page > 1) page else null).throwIfError { it.error }
    }

    fun getDetail(slug: String): Flow<Result<AnichinAnimeDetail>> = request {
        val res = api.getDetail(slug)
        val map = res.result ?: throw IllegalStateException(res.error ?: "Anime tidak ditemukan")
        AnichinAnimeDetail(
            name = map.str("name") ?: "Unknown",
            thumbnail = map.str("thumbnail"),
            genres = map.strList("genre"),
            rating = map.str("rating"),
            sinopsis = map.sinopsis(),
            episodes = map.episodes(),
            info = map.info(exclude = DETAIL_KEYS)
        )
    }

    fun getEpisode(slug: String): Flow<Result<AnichinEpisodeDetail>> = request {
        val res = api.getEpisode(slug)
        val map = res.result ?: throw IllegalStateException(res.error ?: "Episode tidak ditemukan")
        AnichinEpisodeDetail(
            name = map.str("name") ?: "Unknown",
            root = map.str("root")?.takeIf { it != "unknown" },
            thumbnail = map.str("thumbnail"),
            genres = map.strList("genre"),
            rating = map.str("rating"),
            sinopsis = map.sinopsis(),
            episodes = map.episodes(),
            players = map.players(),
            info = map.info(exclude = EPISODE_KEYS)
        )
    }

    /**
     * Link video langsung. Panggil fresh tiap mau nonton (link ada masa
     * berlakunya). Buat player: pilih dari [AnichinVideoSource.medias],
     * dan pakai AnichinNetwork.videoDataSourceFactory().
     */
    fun getVideoSource(slug: String): Flow<Result<AnichinVideoSource>> = request {
        api.getVideoSource(slug)
    }

    companion object {
        private val DETAIL_KEYS = setOf("name", "thumbnail", "genre", "rating", "sinopsis", "episode")
        private val EPISODE_KEYS = DETAIL_KEYS + setOf("players", "root")

        /**
         * Urutan kualitas OK.ru dari terendah ke tertinggi, dan label resolusinya:
         * mobile=144p, lowest=240p, low=360p, sd=480p, hd=720p, full=1080p.
         */
        val QUALITY_ORDER = listOf("mobile", "lowest", "low", "sd", "hd", "full", "quad", "ultra")

        private val QUALITY_LABELS = mapOf(
            "mobile" to "144p", "lowest" to "240p", "low" to "360p",
            "sd" to "480p", "hd" to "720p", "full" to "1080p",
            "quad" to "1440p", "ultra" to "2160p"
        )

        fun qualityRank(quality: String?): Int = QUALITY_ORDER.indexOf(quality.orEmpty().lowercase())

        fun qualityLabel(quality: String?): String =
            QUALITY_LABELS[quality.orEmpty().lowercase()] ?: quality.orEmpty().ifBlank { "Auto" }

        /** Non-premium maksimal 480p = "sd". */
        const val NON_PREMIUM_MAX_QUALITY = "sd"

        /**
         * Pilih media mp4 dengan kualitas tertinggi yang masih <= [maxQuality].
         * Non-premium (max 480p) -> maxQuality = NON_PREMIUM_MAX_QUALITY.
         */
        fun pickMedia(medias: List<AnichinMedia>, maxQuality: String = "ultra"): AnichinMedia? {
            val limit = qualityRank(maxQuality).let { if (it < 0) QUALITY_ORDER.lastIndex else it }
            return medias
                .filter { !it.url.isNullOrBlank() }
                .filter { qualityRank(it.quality) in 0..limit }
                .maxByOrNull { qualityRank(it.quality) }
        }
    }
}

// ---------- helper parsing Map dinamis ----------

private inline fun <T> T.throwIfError(error: (T) -> String?): T {
    error(this)?.let { throw IllegalStateException(it) }
    return this
}

private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

private fun Map<String, Any?>.strList(key: String): List<String> =
    (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()

// "sinopsis" bisa string (halaman episode) atau {paragraphs:[..], title:".."} (halaman detail)
private fun Map<String, Any?>.sinopsis(): String = when (val s = this["sinopsis"]) {
    is String -> s
    is Map<*, *> -> (s["paragraphs"] as? List<*>)?.mapNotNull { it as? String }
        ?.filter { it.isNotBlank() }?.joinToString("\n\n").orEmpty()
    else -> ""
}

private fun Map<String, Any?>.episodes(): List<AnichinEpisodeRef> =
    (this["episode"] as? List<*>).orEmpty().mapNotNull { item ->
        val m = item as? Map<*, *> ?: return@mapNotNull null
        val slug = m["slug"] as? String ?: return@mapNotNull null
        AnichinEpisodeRef(
            slug = slug,
            name = m["name"] as? String,
            subtitle = m["subtitle"] as? String,
            date = m["date"] as? String,
            episode = m["episode"]?.toString(),
            thumbnail = m["thumbnail"] as? String
        )
    }.distinctBy { it.slug }

// "players" berisi list {name,url}, atau {"error": "..."} kalau gagal
private fun Map<String, Any?>.players(): List<AnichinPlayer> =
    (this["players"] as? List<*>).orEmpty().mapNotNull { item ->
        val m = item as? Map<*, *> ?: return@mapNotNull null
        val url = m["url"] as? String ?: return@mapNotNull null
        AnichinPlayer(name = (m["name"] as? String).orEmpty(), url = url)
    }

private fun Map<String, Any?>.info(exclude: Set<String>): Map<String, String> =
    filterKeys { it !in exclude }
        .mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }
        .toMap()
