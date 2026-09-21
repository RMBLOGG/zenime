package com.example.data.api

import com.example.data.model.RawEnvelope
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * API baru: Animein raw API (base path /3/2/...), bukan lagi wrapper lama.
 * Semua endpoint balikin amplop {"status","error","data":{...}} -- makanya
 * return type-nya seragam RawEnvelope, lalu di-parse manual per bentuk
 * "data" masing-masing endpoint di AnimeRepository.
 *
 * CATATAN: base host ("https://xyz-api.animein.net" atau apa pun host API
 * barunya) diatur lewat Firebase Remote Config (parameter "api_base_url"),
 * BUKAN di sini -- lihat RemoteConfigManager. Path di bawah ini sudah
 * termasuk prefix "3/2/" sesuai referensi.
 */
interface DayynimeV5Api {

    // ---- Beranda: home/{hot,new,popular,random} -> data.movie: [...] ----
    @GET("3/2/home/{section}")
    suspend fun getHomeSection(
        @Path("section") section: String,
        @Query("page") page: Int? = null
    ): RawEnvelope

    // ---- Pencarian & jelajah (tiap filter endpoint sendiri-sendiri) ----
    @GET("3/2/explore/movie")
    suspend fun exploreByKeyword(
        @Query("keyword") keyword: String,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null
    ): RawEnvelope

    @GET("3/2/explore/movie_genre")
    suspend fun exploreByGenre(
        @Query("id_genre") idGenre: String,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null
    ): RawEnvelope

    @GET("3/2/explore/movie_type")
    suspend fun exploreByType(
        @Query("type") type: String,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null
    ): RawEnvelope

    @GET("3/2/explore/movie_year")
    suspend fun exploreByYear(
        @Query("year") year: String,
        @Query("season") season: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null
    ): RawEnvelope

    @GET("3/2/explore/movie_studio")
    suspend fun exploreByStudio(
        @Query("studio") studio: String,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null
    ): RawEnvelope

    @GET("3/2/explore/genre")
    suspend fun getGenresRaw(): RawEnvelope

    // ---- Detail & episode anime ----
    @GET("3/2/movie/detail/{id}")
    suspend fun getDetailRaw(@Path("id") id: String): RawEnvelope

    @GET("3/2/movie/episode/{id}")
    suspend fun getEpisodesRaw(
        @Path("id") id: String,
        @Query("page") page: Int? = null,
        @Query("search") search: String? = null
    ): RawEnvelope

    // ---- Link streaming episode ----
    @GET("3/2/episode/streamnew/{id}")
    suspend fun getStreamRaw(@Path("id") id: String): RawEnvelope

    // ---- Manra (cerita interaktif / visual novel buatan pengguna) ----
    // "data/..." tanpa prefix 3/2. Daftar: page (mulai 1), limit, sort=popular.
    @GET("data/manra/list")
    suspend fun getManraListRaw(@QueryMap params: Map<String, String>): RawEnvelope

    // Query: id_manra
    @GET("data/manra/detail")
    suspend fun getManraDetailRaw(@QueryMap params: Map<String, String>): RawEnvelope

    // Query: id_manra_chapter, id_last_line (lanjutan setelah pilihan)
    @GET("data/manra/chapter/play")
    suspend fun getManraChapterPlayRaw(@QueryMap params: Map<String, String>): RawEnvelope

    // Field: id_manra, id_manra_chapter, id_manra_chapter_line, id_manra_chapter_line_choose.
    // Kemungkinan butuh sesi login Animein (belum terverifikasi).
    @FormUrlEncoded
    @POST("data/manra/chapter/choose")
    suspend fun manraChapterChoose(@FieldMap params: Map<String, String>): RawEnvelope

    // ---- Tab halaman detail: Cuplix / Cover / Poster per anime ----
    // Cuplix per anime ("data/..." tanpa prefix 3/2). Query: id_movie, page, type.
    // Item-nya sama dengan Cuplix beranda (FypMapper.toFyp di app Animein).
    @GET("data/movie/fyp/list_new")
    suspend fun getMovieCuplixRaw(@QueryMap params: Map<String, String>): RawEnvelope

    // Galeri kiriman user. Query: id_movie, page. Item: id, image, point,
    // username, is_pro, rank, ...
    @GET("3/2/movie_cover/data")
    suspend fun getMovieCoverRaw(@QueryMap params: Map<String, String>): RawEnvelope

    @GET("3/2/movie_poster/data")
    suspend fun getMoviePosterRaw(@QueryMap params: Map<String, String>): RawEnvelope

    // ---- Cuplix (di API disebut "fyp"): klip pendek buatan user ----
    // Path "data/..." TIDAK pakai prefix "3/2/". Query: limit, sort
    // (scroll_likes | scroll_new | scroll_old), key_id_fyp (id yang sudah
    // tampil, dipisah koma) dan kursor cursor_* dari server bila ada.
    @GET("data/fyp2/list_scroll")
    suspend fun getCuplixRaw(@QueryMap params: Map<String, String>): RawEnvelope

    // ---- Beranda gabungan Animein: data/home/list ----
    // Path "data/..." TIDAK pakai prefix "3/2/". Respons data berisi array:
    // slider, history, update (= "Episode Baru"), hot, new, today, popular,
    // waiting, random. "day" = SENIN..MINGGU / RANDOM (dipakai buat "today").
    @GET("data/home/list")
    suspend fun getHomeListRaw(
        @Query("limit") limit: Int = 10,
        @Query("day") day: String
    ): RawEnvelope

    // ---- Jadwal rilis: schedule/data?day=SENIN.. ----
    @GET("3/2/schedule/data")
    suspend fun getScheduleRaw(
        @Query("day") day: String,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null
    ): RawEnvelope
}
