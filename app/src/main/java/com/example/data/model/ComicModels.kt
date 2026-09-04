package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * Model mentah buat response JSON dari API komik Sanka - BACAKOMIK
 * (https://www.sankavollerei.web.id/comic/bacakomik/...), sumber data
 * dari bacakomik.my. Endpoint publik, tanpa API key.
 */

// Dipakai buat /bacakomik/latest, /populer, /search/{query}, /genre/{slug} -
// bentuk JSON-nya sama persis (field "chapter"/"date"/"rating" kadang ada
// kadang enggak tergantung endpoint, makanya nullable semua).
@JsonClass(generateAdapter = true)
data class BacakomikListItem(
    val title: String = "",
    val slug: String = "",
    val cover: String? = null,
    val chapter: String? = null,
    val date: String? = null,
    val rating: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class BacakomikListResponse(
    val creator: String? = null,
    val success: Boolean? = null,
    val komikList: List<BacakomikListItem>? = null,
    val hasNextPage: Boolean? = null,
    val currentPage: Int? = null
)

@JsonClass(generateAdapter = true)
data class BacakomikGenreRef(
    val title: String = "",
    val slug: String = ""
)

// PENTING: field "title" di tiap chapter SELALU KOSONG ("") dari API ini -
// nomor chapter WAJIB diekstrak dari "slug" (mis. "nano-machine-chapter-325"
// -> "Chapter 325"), lihat extractChapterLabel() di ComicMappers.kt.
@JsonClass(generateAdapter = true)
data class BacakomikChapterRef(
    val title: String = "",
    val slug: String = "",
    val date: String? = null
)

@JsonClass(generateAdapter = true)
data class BacakomikDetail(
    val title: String? = null,
    val cover: String? = null,
    val rating: String? = null,
    val otherTitle: String? = null,
    val status: String? = null,
    val type: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val release: String? = null,
    val series: String? = null,
    val reader: String? = null,
    val synopsis: String? = null,
    val genres: List<BacakomikGenreRef>? = null,
    val chapters: List<BacakomikChapterRef>? = null
)

// Dipakai buat /bacakomik/detail/{slug}
@JsonClass(generateAdapter = true)
data class BacakomikDetailResponse(
    val creator: String? = null,
    val success: Boolean? = null,
    val detail: BacakomikDetail? = null
)

@JsonClass(generateAdapter = true)
data class BacakomikChapterNavigation(
    val next: String? = null,
    val prev: String? = null
)

// Dipakai buat /bacakomik/chapter/{chapterSlug} (baca gambar per chapter)
@JsonClass(generateAdapter = true)
data class BacakomikChapterResponse(
    val creator: String? = null,
    val success: Boolean? = null,
    val title: String? = null,
    val images: List<String>? = null,
    val navigation: BacakomikChapterNavigation? = null
)

@JsonClass(generateAdapter = true)
data class BacakomikGenreItem(
    val title: String = "",
    val slug: String = ""
)

// Dipakai buat /bacakomik/genres (list semua genre)
@JsonClass(generateAdapter = true)
data class BacakomikGenreListResponse(
    val creator: String? = null,
    val success: Boolean? = null,
    val genres: List<BacakomikGenreItem>? = null
)

/**
 * Ekstrak nomor/label chapter dari slug, karena field "title" di
 * BacakomikChapterRef selalu kosong dari API. Contoh:
 * "nano-machine-chapter-325" -> "Chapter 325".
 * "one-piece-chapter-1052-5" -> "Chapter 1052.5" (chapter selingan/desimal).
 */
fun extractChapterLabel(slug: String): String {
    val match = Regex("chapter-([0-9]+(?:-[0-9]+)?)$").find(slug)
        ?: return slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
    val raw = match.groupValues[1]
    val parts = raw.split("-")
    val numberLabel = if (parts.size == 2) "${parts[0]}.${parts[1]}" else parts[0]
    return "Chapter $numberLabel"
}
