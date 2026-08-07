package com.example.data.api

import com.example.data.model.AnimeItem
import com.example.data.model.EpisodeItem
import com.example.data.model.GenreItem
import com.example.data.model.HomeResponse
import com.example.data.model.SearchResponse
import com.example.data.model.StreamResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DayynimeV5Api {

    @GET("homepage")
    suspend fun getHome(): HomeResponse

    @GET("search")
    suspend fun search(
        @Query("q") keyword: String = "",
        @Query("page") page: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("genre_in") genreIn: String? = null,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null
    ): SearchResponse

    @GET("anime/{id}")
    suspend fun getDetail(@Path("id") id: String): AnimeItem

    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(
        @Path("id") id: String,
        @Query("page") page: Int? = null
    ): List<EpisodeItem>

    @GET("episode/{episodeId}/stream")
    suspend fun getEpisodeStream(@Path("episodeId") episodeId: String): StreamResponse

    @GET("schedule")
    suspend fun getSchedule(@Query("day") day: String): List<AnimeItem>

    @GET("genres")
    suspend fun getGenres(): List<GenreItem>
}
