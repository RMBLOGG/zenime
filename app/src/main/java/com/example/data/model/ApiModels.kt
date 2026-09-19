package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AnimeItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "synonyms") val synonyms: String? = null,
    @Json(name = "synopsis") val synopsis: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "year") val year: String? = null,
    @Json(name = "day") val day: String? = null,
    @Json(name = "views") val views: String? = null,
    @Json(name = "favorites") val favorites: String? = null,
    @Json(name = "image_poster") val image_poster: String? = null,
    @Json(name = "image_cover") val image_cover: String? = null,
    @Json(name = "aired_start") val aired_start: String? = null,
    @Json(name = "time") val time: String? = null,
    @Json(name = "key_time") val key_time: String? = null
)

@JsonClass(generateAdapter = true)
data class HomeResponse(
    @Json(name = "hot") val hot: List<AnimeItem>? = null,
    @Json(name = "new") val new: List<AnimeItem>? = null,
    @Json(name = "today") val today: List<AnimeItem>? = null,
    @Json(name = "popular") val popular: List<AnimeItem>? = null,
    @Json(name = "trailer") val trailer: List<AnimeItem>? = null,
    @Json(name = "random") val random: List<AnimeItem>? = null,
    @Json(name = "waiting") val waiting: List<AnimeItem>? = null
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "query") val query: String? = null,
    @Json(name = "page") val page: String? = null,
    @Json(name = "results") val results: List<AnimeItem>? = null,
    @Json(name = "next_page") val next_page: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenreItem(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "value") val value: String? = null,
    // API baru (explore/genre) ngasih grouping genre lewat field ini (mis.
    // "Genre", "Tema", dll) -- belum dipakai UI, disiapin buat nanti.
    @Json(name = "group") val group: String? = null
) {
    fun getDisplayName(): String = name ?: title ?: value ?: slug ?: "Unknown"

    // API baru (explore/movie_genre) filter pakai id_genre numerik, bukan
    // slug -- makanya id diprioritaskan duluan sekarang.
    fun getFilterValue(): String = id ?: slug ?: value ?: name ?: ""
}

@JsonClass(generateAdapter = true)
data class EpisodeItem(
    @Json(name = "id") val id: String,
    @Json(name = "id_movie") val id_movie: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "index") val index: String? = null,
    @Json(name = "is_new") val is_new: String? = null,
    @Json(name = "key_time") val key_time: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "views") val views: String? = null
) {
    /**
     * Upstream ngasih path relatif buat thumbnail episode (beda sama
     * image_cover/image_poster anime yang udah full URL). Host asetnya
     * xyz-api.animein.net -- disimpulkan dari pola image_cover yang
     * pakai prefix /assets_xyz/ di response homepage/detail.
     */
    val resolvedImageUrl: String?
        get() {
            val raw = image?.takeIf { it.isNotBlank() } ?: return null
            return if (raw.startsWith("http")) raw else "https://xyz-api.animein.net$raw"
        }
}

@JsonClass(generateAdapter = true)
data class StreamServer(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "server_id") val server_id: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeDetail(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "index") val index: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamResponse(
    @Json(name = "episode") val episode: EpisodeDetail? = null,
    // Nama field JSON dari API baru beda ("episode_next"/"server"), tapi
    // nama properti Kotlin dipertahankan sama biar UI (PlayerScreen dkk)
    // gak perlu diubah sama sekali.
    @Json(name = "episode_next") val episodeNext: EpisodeDetail? = null,
    @Json(name = "server") val servers: List<StreamServer>? = null
)

/**
 * Amplop respons mentah API baru (Animein raw, base path /3/2/...):
 * {"status": 200, "error": false, "data": {...}}.
 * "data" isinya beda-beda tiap endpoint (kadang key "movie", kadang
 * "episode", kadang "genre", dll) makanya ditampung sebagai Map dulu,
 * baru di-parse manual di AnimeRepository -- sama kayak pola
 * first_list()/clean_movie() di referensi Flask, supaya gak gampang
 * crash kalau upstream ngasih bentuk yang agak beda dari dugaan.
 */
@JsonClass(generateAdapter = true)
data class RawEnvelope(
    @Json(name = "status") val status: Int? = null,
    @Json(name = "error") val error: Boolean? = null,
    @Json(name = "data") val data: Map<String, Any?>? = null
)

/**
 * Satu Cuplix (klip pendek). Video-nya BUKAN file terpisah: klip adalah
 * potongan waktu (milidetik) dari sebuah episode -- [timeStart]..[timeEnd].
 * Semua field dari API berupa string.
 */
@JsonClass(generateAdapter = true)
data class CuplixItem(
    @Json(name = "id") val id: String,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "url_thumbnail") val urlThumbnail: String? = null,
    @Json(name = "id_episode") val idEpisode: String? = null,
    @Json(name = "id_movie") val idMovie: String? = null,
    @Json(name = "count_views") val countViews: String? = null,
    @Json(name = "count_likes") val countLikes: String? = null,
    @Json(name = "count_comments") val countComments: String? = null,
    @Json(name = "anime") val anime: String? = null,
    @Json(name = "episode") val episode: String? = null,
    @Json(name = "time_start") val timeStart: String? = null,
    @Json(name = "time_end") val timeEnd: String? = null,
    @Json(name = "username") val username: String? = null
) {
    val startMs: Long get() = timeStart?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    /** 0 = tanpa batas akhir (putar sampai video habis). */
    val endMs: Long
        get() {
            val end = timeEnd?.toLongOrNull() ?: 0L
            return if (end > startMs) end else 0L
        }
}

/** Satu batch Cuplix + kursor untuk batch berikutnya. */
data class CuplixPage(
    val items: List<CuplixItem>,
    val cursors: Map<String, String>,
    val hasMore: Boolean
)
