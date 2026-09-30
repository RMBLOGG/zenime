package com.example.data.api

import com.example.data.model.WmDetailResponse
import com.example.data.model.WmGenreResponse
import com.example.data.model.WmListResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** API komik Sanka - endpoint WESTMANGA (base URL sama dengan ComicApi). */
interface WestmangaApi {

    // kind: latest, popular, ongoing, completed, list, manga, manhwa, manhua,
    // az, za, added, colored, uncolored, projects, others
    @GET("westmanga/{kind}")
    suspend fun browse(@Path("kind") kind: String, @Query("page") page: Int): WmListResponse

    @GET("westmanga/search")
    suspend fun search(@Query("q") query: String, @Query("page") page: Int): WmListResponse

    @GET("westmanga/genres")
    suspend fun getGenres(): WmGenreResponse

    @GET("westmanga/genre/{id}")
    suspend fun getByGenre(@Path("id") id: String, @Query("page") page: Int): WmListResponse

    // ids dipisah koma, mis. "13,344"
    @GET("westmanga/genres-filter")
    suspend fun getByGenres(@Query("genres") ids: String, @Query("page") page: Int): WmListResponse

    @GET("westmanga/detail/{slug}")
    suspend fun getDetail(@Path("slug") slug: String): WmDetailResponse

    // Sengaja mentah (ResponseBody): lokasi array gambar di dalam "data"
    // dicari fleksibel lewat org.json (lihat WestmangaSource.findImages).
    @GET("westmanga/chapter/{slug}")
    suspend fun getChapterRaw(@Path("slug") slug: String): ResponseBody
}
