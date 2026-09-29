package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Model buat API Anichin (https://api.zenime.biz.id).
 * Semua field dibuat nullable/default karena hasil scraping bisa bolong,
 * dan tiap response bisa nyelipin field "error" kalau scrape-nya gagal.
 */

// ---- Kartu anime (dipakai di home, search, /anime, /genre/{slug}) ----

@JsonClass(generateAdapter = true)
data class AnichinCard(
    @Json(name = "title") val title: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "headline") val headline: String? = null,
    // "eps" cuma ada di kartu home; "status" cuma ada di search/anime/genre
    @Json(name = "eps") val eps: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "slug") val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class AnichinHomeSection(
    @Json(name = "section") val section: String? = null,
    @Json(name = "cards") val cards: List<AnichinCard>? = null
)

// GET /  (?page=)
@JsonClass(generateAdapter = true)
data class AnichinHomeResponse(
    @Json(name = "results") val results: List<AnichinHomeSection>? = null,
    @Json(name = "page") val page: Int? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "error") val error: String? = null
)

// GET /search/{query}, /anime, /genre/{slug}
@JsonClass(generateAdapter = true)
data class AnichinListResponse(
    @Json(name = "results") val results: List<AnichinCard>? = null,
    @Json(name = "query") val query: String? = null,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "page") val page: Int? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "error") val error: String? = null
)

// ---- Genre ----

@JsonClass(generateAdapter = true)
data class AnichinGenre(
    @Json(name = "name") val name: String? = null,
    @Json(name = "slug") val slug: String? = null
)

// GET /genres  (catatan: dari server kadang kedobel, makanya di repository di-distinct)
@JsonClass(generateAdapter = true)
data class AnichinGenresResponse(
    @Json(name = "genres") val genres: List<AnichinGenre>? = null,
    @Json(name = "total") val total: Int? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "error") val error: String? = null
)

// ---- Detail anime & episode ----
// Response mentahnya bentuknya dinamis (key info kayak "status", "studio",
// "durasi" dll bergantung situs sumber, dan "sinopsis" bisa object atau
// string), jadi diterima sebagai Map dulu lalu dirapikan di repository
// jadi AnichinAnimeDetail / AnichinEpisodeDetail.

@JsonClass(generateAdapter = true)
data class AnichinRawEnvelope(
    @Json(name = "result") val result: Map<String, Any?>? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "error") val error: String? = null
)

data class AnichinEpisodeRef(
    val slug: String,
    val name: String? = null,
    val subtitle: String? = null,
    val date: String? = null,
    val episode: String? = null,
    val thumbnail: String? = null
)

data class AnichinAnimeDetail(
    val name: String,
    val thumbnail: String?,
    val genres: List<String>,
    val rating: String?,
    val sinopsis: String,
    val episodes: List<AnichinEpisodeRef>,
    // Sisa field info dari situs sumber (status, studio, tipe, dll) key-nya lowercase_underscore
    val info: Map<String, String>
)

data class AnichinPlayer(val name: String, val url: String)

data class AnichinEpisodeDetail(
    val name: String,
    val root: String?,          // slug anime induknya (buat balik ke halaman detail)
    val thumbnail: String?,
    val genres: List<String>,
    val rating: String?,
    val sinopsis: String,
    val episodes: List<AnichinEpisodeRef>,
    val players: List<AnichinPlayer>,   // embed mirror (iframe), bukan link video langsung
    val info: Map<String, String>
)

// ---- Video ----

@JsonClass(generateAdapter = true)
data class AnichinMedia(
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "url") val url: String? = null
)

// GET /video-source/{slug}
@JsonClass(generateAdapter = true)
data class AnichinVideoSource(
    @Json(name = "title") val title: String? = null,
    @Json(name = "hls") val hls: String? = null,
    @Json(name = "medias") val medias: List<AnichinMedia>? = null
)
