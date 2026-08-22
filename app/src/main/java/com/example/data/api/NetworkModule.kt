package com.example.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object NetworkModule {

    // Dipakai hanya sebagai base URL "dummy" saat inisialisasi Retrofit.
    // URL sebenarnya di-rewrite tiap request oleh interceptor di bawah,
    // berdasarkan RemoteConfigManager.currentBaseUrl().
    //
    // PENTING: sejak pindah ke AnimeinApi (backend native ANIMEIN), value
    // parameter "api_base_url" di Firebase Remote Config Console WAJIB
    // diganti ke "https://xyz-api.animein.net/" -- kalau masih nunjuk ke
    // wrapper DayynimeV5/animeinweb lama, semua request bakal 404 karena
    // path endpoint-nya (data/home/list, 3/2/movie/episode/{id}, dst) beda
    // total sama path lama (homepage, anime/{id}, dst).
    private const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/api/"

    /**
     * Interceptor yang mengganti scheme/host/port/base-path tiap request
     * dengan base URL terbaru dari RemoteConfigManager, tanpa perlu
     * membuat ulang instance Retrofit.
     */
    private val dynamicBaseUrlInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val newBaseString = RemoteConfigManager.currentBaseUrl()

        // null artinya belum ada base URL yang bisa dipakai -- baik karena
        // api_base_url dikosongin di Firebase Console (kill-switch sengaja),
        // maupun karena app belum pernah berhasil fetch Remote Config sama
        // sekali. Gagalin request dengan error yang jelas, JANGAN fallback
        // ke placeholder atau base URL bawaan kode -- gak ada base URL yang
        // di-hardcode di app ini sama sekali.
        if (newBaseString == null) {
            throw java.io.IOException(
                "Server sedang tidak tersedia. Coba lagi nanti."
            )
        }

        val newBase = newBaseString.toHttpUrlOrNull()
        if (newBase == null) {
            chain.proceed(original)
        } else {
            val originalUrl = original.url
            // Ambil path & query yang dituju Retrofit relatif terhadap PLACEHOLDER_BASE_URL,
            // lalu tempel di atas base path dari Remote Config.
            val placeholderPath = PLACEHOLDER_BASE_URL.toHttpUrlOrNull()!!.encodedPath
            val relativePath = originalUrl.encodedPath.removePrefix(placeholderPath)

            val newUrlBuilder = newBase.newBuilder()
            val combinedPath = (newBase.encodedPath.trimEnd('/') + "/" + relativePath.trimStart('/'))
            newUrlBuilder.encodedPath(combinedPath)
            newUrlBuilder.encodedQuery(originalUrl.encodedQuery)

            val newRequest = original.newBuilder()
                .url(newUrlBuilder.build())
                .build()
            chain.proceed(newRequest)
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor { chain ->
                // Referer animeinweb.com DIHAPUS -- itu spesifik buat proxy
                // lama yang udah 503, gak relevan buat xyz-api.animein.net.
                // Test manual lewat browser ke xyz-api.animein.net jalan
                // tanpa header custom sama sekali, jadi ini sekadar jaga-jaga
                // (UA generik) bukan requirement yang kekonfirmasi.
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val api: AnimeinApi by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnimeinApi::class.java)
    }
}
