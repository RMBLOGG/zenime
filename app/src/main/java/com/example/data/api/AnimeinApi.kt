package com.example.data.api

import com.example.data.model.AnimeinEnvelope
import com.example.data.model.AnimeinEpisodeListData
import com.example.data.model.AnimeinHomeData
import com.example.data.model.AnimeinMovieDetailData
import com.example.data.model.AnimeinMovieListData
import com.example.data.model.AnimeinStreamData
import com.example.data.model.GenreItem
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Ngomong LANGSUNG ke backend native aplikasi ANIMEIN (xyz-api.animein.net),
 * ditemuin lewat decompile classes*.dex dari animein-5-1-2.apk. Gantiin
 * DayynimeV5Api lama yang proxy ke animeinweb.com (down/503 per 22 Agu 2026).
 *
 * WAJIB: parameter "api_base_url" di Firebase Remote Config Console diisi
 * "https://xyz-api.animein.net/" (lihat RemoteConfigManager & NetworkModule).
 *
 * Status konfirmasi tiap endpoint per 23 Agu 2026 -- dites manual satu-satu
 * lewat browser sebelum dipasang di sini:
 */
interface AnimeinApi {

    // KONFIRMASI. Response: {"status":200,"error":false,"data":{"slider":[...],
    // "home_pos_manra":"bottom","history":[...]}}. Sekarang cuma dipetakan
    // ke "history" (lihat AnimeRepository.getHome()) -- section hot/new/
    // popular/random UDAH dipisah lewat 4 endpoint terpisah di bawah.
    @GET("data/home/list")
    suspend fun getHome(): AnimeinEnvelope<AnimeinHomeData>

    // KONFIRMASI SEMUA, 23 Agu 2026 -- 4 endpoint terpisah persis mapping
    // section HomeScreen (Sedang Tayang/Baru Ditambahkan/Terpopuler/
    // Rekomendasi). Belum ketemu padanan buat "today" (Update Hari Ini) &
    // "waiting" (Segera Tayang) -- kemungkinan emang gak ada section
    // terpisah buat itu di backend native ini.
    @GET("3/2/home/hot")
    suspend fun getHomeHot(): AnimeinEnvelope<AnimeinMovieListData>

    @GET("3/2/home/new")
    suspend fun getHomeNew(): AnimeinEnvelope<AnimeinMovieListData>

    @GET("3/2/home/popular")
    suspend fun getHomePopular(): AnimeinEnvelope<AnimeinMovieListData>

    @GET("3/2/home/random")
    suspend fun getHomeRandom(): AnimeinEnvelope<AnimeinMovieListData>

    // KONFIRMASI, 23 Agu 2026. Dites pake movie_id 6433 -> balikin PERSIS 1
    // objek movie (bukan list generik kayak data/movie/find yang ternyata
    // ngabaikan parameternya sama sekali -- makanya endpoint itu gak dipake
    // lagi). Response juga sekalian ngasih info episode pertama & "season"
    // (array, selalu kosong pas dites, struktur belum jelas -- lihat
    // AnimeinMovieDetailData).
    @GET("3/2/movie/detail/{movieId}")
    suspend fun getMovieDetail(@Path("movieId") movieId: String): AnimeinEnvelope<AnimeinMovieDetailData>

    // KONFIRMASI. Dites pake movie_id 6433 -> balikin 8 episode lengkap dalam
    // 1x panggilan, TANPA parameter page. Beda sama animeinweb yang paginasi
    // 30/halaman -- jadi di sini SENGAJA gak ada parameter page sama sekali.
    @GET("3/2/movie/episode/{movieId}")
    suspend fun getEpisodes(@Path("movieId") movieId: String): AnimeinEnvelope<AnimeinEpisodeListData>

    // KONFIRMASI. Dites pake episode id 318355 -> balikin 4 server "direct"
    // (360p/480p/720p/1080p), langsung mp4 siap pakai di ExoPlayer.
    @GET("3/2/episode/streamnew/{episodeId}")
    suspend fun getEpisodeStream(@Path("episodeId") episodeId: String): AnimeinEnvelope<AnimeinStreamData>

    // KONFIRMASI. Dites pake day=SENIN -> balikin 13 anime. Parameter day
    // WAJIB bahasa Indonesia huruf besar (SENIN..MINGGU), BUKAN bahasa
    // Inggris kayak animeinweb dulu. Konversi day key Inggris dari UI ada di
    // AnimeRepository.dayKeyToIndonesian().
    @GET("3/2/schedule/data")
    suspend fun getSchedule(@Query("day") day: String): AnimeinEnvelope<AnimeinMovieListData>

    // KONFIRMASI, 23 Agu 2026. Dites pake keyword=liar -> nemu "Liar Liar" &
    // "Liar Game", plus match substring di title/synonyms ("faMILIAR of Zero"
    // & "Death BiLLIARds" ikut kesangkut) -- jadi ini full-text SUBSTRING
    // match (bukan exact word), server yang nanganin, bukan kita.
    // PENTING: nama parameternya "keyword", BUKAN "q" -- "q" dulu diam-diam
    // diabaikan server (balikin listing generik gak difilter).
    @GET("3/2/explore/movie")
    suspend fun exploreMovie(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int? = null,
        @Query("genre") genre: String? = null,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null
    ): AnimeinEnvelope<AnimeinMovieListData>

    // KONFIRMASI, 23 Agu 2026. Balikin 32 genre lengkap (id, name, image,
    // group -- group-nya "Genre"/"Theme"/"Demographic", belum dipake tapi
    // tersedia kalau nanti mau dikelompokkan di UI).
    @GET("3/2/explore/genre")
    suspend fun exploreGenre(): AnimeinEnvelope<List<GenreItem>>
}
