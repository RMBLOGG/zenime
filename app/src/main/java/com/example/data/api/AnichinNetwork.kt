package com.example.data.api

import androidx.annotation.OptIn as UnstableOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Klien HTTP khusus API Anichin. Terpisah dari NetworkModule (yang dynamic
 * base URL lewat Remote Config) karena ini backend sendiri dengan base URL fixed.
 */
object AnichinNetwork {

    // Link video OK.ru nolak (HTTP 400) kalau User-Agent-nya bukan browser --
    // termasuk User-Agent bawaan ExoPlayer. Referer juga dibutuhkan.
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    const val VIDEO_REFERER = "https://ok.ru/"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // GET aman diulang sekali kalau koneksi putus (timeout sengaja gak diulang)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: java.io.IOException) {
                    val retryable = request.method == "GET" &&
                        e !is java.net.SocketTimeoutException
                    if (!retryable) throw e
                    Thread.sleep(800)
                    chain.proceed(request)
                }
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(20, TimeUnit.SECONDS)
            // endpoint scraping bisa 5-8 detik, kasih napas panjang
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val api: AnichinApi by lazy {
        Retrofit.Builder()
            .baseUrl(AnichinApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnichinApi::class.java)
    }

    /**
     * Factory buat ExoPlayer/Media3 yang muter link dari /video-source.
     * Pakai: ProgressiveMediaSource.Factory(AnichinNetwork.videoDataSourceFactory())
     *           .createMediaSource(MediaItem.fromUri(url))
     */
    @UnstableOptIn(UnstableApi::class)
    fun videoDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to VIDEO_REFERER))
}
