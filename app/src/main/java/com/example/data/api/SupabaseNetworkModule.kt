package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit terpisah khusus buat manggil Supabase Edge Functions (fitur
 * Premium). Base URL-nya TETAP (beda dari [NetworkModule] yang base
 * URL-nya dinamis dari Remote Config buat API anime), karena Supabase
 * project-nya emang satu dan gak butuh mekanisme kill-switch/rotasi.
 *
 * Semua Edge Function yang dipanggil dari sini butuh header `apikey`
 * dengan anon key -- itu yang dilakuin interceptor di bawah, jadi
 * masing-masing pemanggil (lihat [ZenimeSupabaseApi]) gak perlu nambahin
 * header manual satu-satu.
 *
 * CATATAN (fitur Clan): endpoint clan yang nyentuh identitas user (create,
 * join, donate, dst) butuh header Authorization isi Firebase ID Token ASLI
 * per-request -- BUKAN dari interceptor global ini (interceptor ini cuma
 * isi anon key). Header Authorization per-request dikirim lewat parameter
 * @Header di [ZenimeClanApi], diisi dari [com.example.data.repository.ClanRepository].
 */
object SupabaseNetworkModule {

    private val apiKeyInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")

        // PENTING: kalau request UDAH bawa header Authorization sendiri (ini yang
        // dipakai endpoint Clan buat nitipin Firebase ID Token lewat @Header di
        // ZenimeClanApi), JANGAN ditimpa. `.header()` OkHttp itu replace, bukan
        // nambah -- kalau baris ini dihilangin, token asli bakal ke-overwrite diam-diam
        // jadi anon key dan semua endpoint Clan bakal selalu ditolak 401.
        if (original.header("Authorization") == null) {
            builder.header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
        }

        chain.proceed(builder.build())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    // Diekspos (bukan private lagi) biar interface API lain (ZenimeClanApi)
    // bisa numpang instance Retrofit yang sama tanpa duplikat konfigurasi.
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("${SupabaseConfig.SUPABASE_URL}/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val api: ZenimeSupabaseApi by lazy {
        retrofit.create(ZenimeSupabaseApi::class.java)
    }

    val clanApi: ZenimeClanApi by lazy {
        retrofit.create(ZenimeClanApi::class.java)
    }
}
