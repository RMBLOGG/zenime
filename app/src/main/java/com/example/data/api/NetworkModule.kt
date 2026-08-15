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
                val request = chain.request().newBuilder()
                    .header("Referer", "https://animeinweb.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

    val api: DayynimeV5Api by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DayynimeV5Api::class.java)
    }
}
