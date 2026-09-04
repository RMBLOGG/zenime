package com.example.data.api

import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetailResponse
import com.example.data.model.BacakomikGenreListResponse
import com.example.data.model.BacakomikListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Manggil LANGSUNG ke API komik Sanka - endpoint bacakomik (sumber data:
 * bacakomik.my). Endpoint publik, tanpa apikey, base URL fixed (bukan
 * dynamic kayak DayynimeV5Api) karena ini backend pihak ketiga terpisah.
 *
 * Parameter "page" dikirim buat "Load More" -- response-nya (lihat
 * BacakomikListResponse) udah nyertain "hasNextPage"/"currentPage",
 * jadi API-nya emang didesain buat dipaginasi walau rakku (app lain
 * yang pertama pake API ini) kebetulan belum manfaatin field itu.
 */
interface ComicApi {

    @GET("bacakomik/latest")
    suspend fun getLatest(@Query("page") page: Int): BacakomikListResponse

    @GET("bacakomik/populer")
    suspend fun getPopular(@Query("page") page: Int): BacakomikListResponse

    // "query" di-percent-encode otomatis sama Retrofit (spasi -> %20)
    @GET("bacakomik/search/{query}")
    suspend fun searchComic(@Path("query") query: String, @Query("page") page: Int): BacakomikListResponse

    @GET("bacakomik/detail/{slug}")
    suspend fun getComicDetail(@Path("slug") slug: String): BacakomikDetailResponse

    // "chapterSlug" WAJIB slug lengkap dengan nomor chapter-nya, mis.
    // "nano-machine-chapter-1" (didapat dari field "slug" di dalam
    // BacakomikDetailResponse.detail.chapters, bukan slug komik polos)
    @GET("bacakomik/chapter/{chapterSlug}")
    suspend fun getComicChapter(@Path("chapterSlug") chapterSlug: String): BacakomikChapterResponse

    @GET("bacakomik/genres")
    suspend fun getGenres(): BacakomikGenreListResponse

    @GET("bacakomik/genre/{slug}")
    suspend fun getComicByGenre(@Path("slug") slug: String, @Query("page") page: Int): BacakomikListResponse

    companion object {
        const val BASE_URL = "https://www.sankavollerei.web.id/comic/"
    }
}
