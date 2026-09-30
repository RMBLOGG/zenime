package com.example.data.api

import com.example.data.model.AnichinGenresResponse
import com.example.data.model.AnichinHomeResponse
import com.example.data.model.AnichinListResponse
import com.example.data.model.AnichinRawEnvelope
import com.example.data.model.AnichinVideoSource
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * API Anichin (scraper Flask di VPS sendiri). Base URL dari Remote Config (anichin_base_url), pakai HTTPS
 * (Caddy + SSL otomatis), jadi gak butuh setting cleartext.
 *
 * Endpoint scraping langsung ke situs sumber, jadi lumayan lambat
 * (video-source bisa ~8 detik) -- timeout klien diatur di AnichinNetwork.
 */
interface AnichinApi {

    @GET("/")
    suspend fun getHome(@Query("page") page: Int? = null): AnichinHomeResponse

    @GET("search/{query}")
    suspend fun search(@Path("query") query: String): AnichinListResponse

    // Daftar anime dengan filter bebas: status, type, order, dll (nama query
    // param mengikuti filter situs sumber), mis. mapOf("status" to "Ongoing", "order" to "update")
    @GET("anime")
    suspend fun getAnimeList(@QueryMap params: Map<String, String> = emptyMap()): AnichinListResponse

    @GET("genres")
    suspend fun getGenres(): AnichinGenresResponse

    @GET("genre/{slug}")
    suspend fun getByGenre(
        @Path("slug") slug: String,
        @Query("page") page: Int? = null
    ): AnichinListResponse

    // Detail anime (slug anime, TANPA kata "episode")
    @GET("{slug}")
    suspend fun getDetail(@Path("slug") slug: String): AnichinRawEnvelope

    @GET("episode/{slug}")
    suspend fun getEpisode(@Path("slug") slug: String): AnichinRawEnvelope

    // Link video langsung (mp4 per kualitas + hls). Link-nya ada masa berlaku
    // ("expires"), jadi WAJIB ambil fresh tiap mau nonton, jangan disimpan.
    @GET("video-source/{slug}")
    suspend fun getVideoSource(@Path("slug") slug: String): AnichinVideoSource
}
