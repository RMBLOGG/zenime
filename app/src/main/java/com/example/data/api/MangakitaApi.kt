package com.example.data.api

import com.example.data.model.MkChapterResponse
import com.example.data.model.MkDetailResponse
import com.example.data.model.MkGenreListResponse
import com.example.data.model.MkHomeResponse
import com.example.data.model.MkListResponse
import com.example.data.model.MkRecommendationResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API komik Sanka - endpoint MANGAKITA (base URL sama dengan ComicApi).
 * Halaman 1 dipanggil TANPA segmen page (sesuai definisi endpoint ":page?"),
 * halaman 2+ pakai varian "...Paged".
 */
interface MangakitaApi {

    @GET("mangakita/home")
    suspend fun getHome(): MkHomeResponse

    // order / status / type = filter opsional; nilai valid tergantung API.
    @GET("mangakita/list")
    suspend fun getList(
        @Query("order") order: String?,
        @Query("status") status: String?,
        @Query("type") type: String?,
        @Query("page") page: Int
    ): MkListResponse

    @GET("mangakita/projects")
    suspend fun getProjects(): MkListResponse

    @GET("mangakita/projects/{page}")
    suspend fun getProjectsPaged(@Path("page") page: Int): MkListResponse

    @GET("mangakita/daftar-manga")
    suspend fun getAll(): MkListResponse

    @GET("mangakita/daftar-manga/{page}")
    suspend fun getAllPaged(@Path("page") page: Int): MkListResponse

    @GET("mangakita/genres")
    suspend fun getGenres(): MkGenreListResponse

    @GET("mangakita/genres/{slug}")
    suspend fun getByGenre(@Path("slug") slug: String): MkListResponse

    @GET("mangakita/genres/{slug}/{page}")
    suspend fun getByGenrePaged(@Path("slug") slug: String, @Path("page") page: Int): MkListResponse

    @GET("mangakita/rekomendasi")
    suspend fun getRecommendations(): MkRecommendationResponse

    @GET("mangakita/search/{query}")
    suspend fun search(@Path("query") query: String): MkListResponse

    @GET("mangakita/search/{query}/{page}")
    suspend fun searchPaged(@Path("query") query: String, @Path("page") page: Int): MkListResponse

    @GET("mangakita/detail/{slug}")
    suspend fun getDetail(@Path("slug") slug: String): MkDetailResponse

    // encoded = true: "%2F" di slug gabungan (comic%2Fchapter) harus lewat
    // apa adanya, jangan di-encode ulang jadi %252F.
    @GET("mangakita/chapter/{slug}")
    suspend fun getChapter(@Path("slug", encoded = true) slug: String): MkChapterResponse
}
