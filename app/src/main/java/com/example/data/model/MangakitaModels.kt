package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * Model mentah API Sanka - MANGAKITA
 * (https://www.sankavollerei.web.id/comic/mangakita/...). Semua nullable
 * karena beberapa endpoint ngirim field kosong ("" / tanpa chapters).
 */
@JsonClass(generateAdapter = true)
data class MkChapter(
    val title: String? = null,
    val time: String? = null,
    val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class MkItem(
    val title: String? = null,
    val slug: String? = null,
    val image: String? = null,
    val rating: String? = null,
    val status: String? = null,
    val type: String? = null,
    val latestChapter: String? = null,
    val chapters: List<MkChapter>? = null
)

@JsonClass(generateAdapter = true)
data class MkPagination(
    val currentPage: Int? = null,
    val hasNextPage: Boolean? = null
)

// Satu kelas buat /list, /projects, /daftar-manga, /genres/{slug}, /search --
// beda endpoint cuma beda nama field list-nya.
@JsonClass(generateAdapter = true)
data class MkListResponse(
    val pagination: MkPagination? = null,
    val mangaList: List<MkItem>? = null,
    val projects: List<MkItem>? = null,
    val results: List<MkItem>? = null
)

@JsonClass(generateAdapter = true)
data class MkHomeResponse(
    val popularToday: List<MkItem>? = null,
    val projectUpdates: List<MkItem>? = null,
    val latestReleases: List<MkItem>? = null
)

@JsonClass(generateAdapter = true)
data class MkRecommendationResponse(
    val recommendations: Map<String, List<MkItem>>? = null
)

@JsonClass(generateAdapter = true)
data class MkGenre(
    val name: String? = null,
    val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class MkGenreListResponse(
    val genres: List<MkGenre>? = null
)

@JsonClass(generateAdapter = true)
data class MkInfo(
    val status: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class MkDetailChapter(
    val title: String? = null,
    val slug: String? = null,
    val date: String? = null
)

@JsonClass(generateAdapter = true)
data class MkDetail(
    val title: String? = null,
    val image: String? = null,
    val rating: String? = null,
    val synopsis: String? = null,
    val info: MkInfo? = null,
    val genres: List<String>? = null,
    val chapters: List<MkDetailChapter>? = null
)

@JsonClass(generateAdapter = true)
data class MkDetailResponse(
    val details: MkDetail? = null
)

@JsonClass(generateAdapter = true)
data class MkNavigation(
    val prev: String? = null,
    val next: String? = null
)

@JsonClass(generateAdapter = true)
data class MkChapterResponse(
    val title: String? = null,
    val comicSlug: String? = null,
    val images: List<String>? = null,
    val navigation: MkNavigation? = null
)
