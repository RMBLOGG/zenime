package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Model mentah API Sanka - WESTMANGA
 * (https://www.sankavollerei.web.id/comic/westmanga/...). Respons dibungkus
 * "data"; list dipaginasi lewat pagination.current_page / last_page.
 */
@JsonClass(generateAdapter = true)
data class WmTime(
    val time: Long? = null,
    val formatted: String? = null
)

@JsonClass(generateAdapter = true)
data class WmChapter(
    val id: Long? = null,
    val number: String? = null,
    val slug: String? = null,
    @Json(name = "updated_at") val updatedAt: WmTime? = null,
    @Json(name = "created_at") val createdAt: WmTime? = null
)

@JsonClass(generateAdapter = true)
data class WmItem(
    val id: Long? = null,
    val title: String? = null,
    val slug: String? = null,
    val cover: String? = null,
    @Json(name = "country_id") val countryId: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val lastChapters: List<WmChapter>? = null
)

@JsonClass(generateAdapter = true)
data class WmPagination(
    @Json(name = "current_page") val currentPage: Int? = null,
    @Json(name = "last_page") val lastPage: Int? = null
)

@JsonClass(generateAdapter = true)
data class WmListResponse(
    val data: List<WmItem>? = null,
    val pagination: WmPagination? = null
)

@JsonClass(generateAdapter = true)
data class WmGenre(
    val id: Long? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class WmGenreResponse(
    val data: List<WmGenre>? = null
)

@JsonClass(generateAdapter = true)
data class WmDetail(
    val title: String? = null,
    val sinopsis: String? = null,
    val cover: String? = null,
    val author: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    @Json(name = "country_id") val countryId: String? = null,
    val genres: List<WmGenre>? = null,
    val chapters: List<WmChapter>? = null
)

@JsonClass(generateAdapter = true)
data class WmDetailResponse(
    val data: WmDetail? = null
)
