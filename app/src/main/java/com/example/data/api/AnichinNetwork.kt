package com.example.data.api

import androidx.annotation.OptIn as UnstableOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Klien HTTP khusus API Anichin. Terpisah dari NetworkModule (yang dynamic
 * base URL lewat Remote Config) karena ini backend sendiri dengan base URL dari parameter Remote Config "anichin_base_url".
 */
object AnichinNetwork {

    // Link video OK.ru nolak (HTTP 400) kalau User-Agent-nya bukan browser --
    // termasuk User-Agent bawaan ExoPlayer. Referer juga dibutuhkan.
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    const val VIDEO_REFERER = "https://ok.ru/"

    // Thumbnail dari API berbentuk path relatif ("/wp-content/uploads/...") -- host-nya
    // ikut situs sumber. Default di bawah, lalu otomatis diperbarui dari field "source"
    // di tiap response (jadi kalau situs sumber ganti domain, gak perlu update app).
    @Volatile
    var sourceBase: String = "https://anichin.moe"
        private set

    fun updateSourceBase(source: String?) {
        val uri = runCatching { java.net.URI(source ?: return) }.getOrNull() ?: return
        val scheme = uri.scheme ?: return
        val host = uri.host ?: return
        sourceBase = "$scheme://$host"
    }

    /** Ubah path relatif dari API jadi URL absolut yang bisa dimuat Coil. */
    fun imageUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> sourceBase + path
        else -> "$sourceBase/$path"
    }

    // Base URL dummy buat inisialisasi Retrofit. Host asli diganti tiap
    // request oleh dynamicBaseUrlInterceptor dari RemoteConfigManager.
    private const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"

    private val dynamicBaseUrlInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val base = RemoteConfigManager.currentAnichinBaseUrl()
            ?: throw com.example.util.ApiUnavailableException(
                "Server sedang tidak tersedia. Coba lagi nanti."
            )
        val newBase = base.toHttpUrlOrNull() ?: return@Interceptor chain.proceed(original)

        val placeholderPath = PLACEHOLDER_BASE_URL.toHttpUrlOrNull()!!.encodedPath
        val relativePath = original.url.encodedPath.removePrefix(placeholderPath)
        val combinedPath = newBase.encodedPath.trimEnd('/') + "/" + relativePath.trimStart('/')

        val newUrl = newBase.newBuilder()
            .encodedPath(combinedPath)
            .encodedQuery(original.url.encodedQuery)
            .build()
        chain.proceed(original.newBuilder().url(newUrl).build())
    }

    // Donghua khusus Premium. Endpoint episode/ dan video-source/ dijaga di SERVER
    // (backend/anichin-api/premium_guard.py). App cuma nempelin token premium
    // bertanda tangan server; kalau gak ada/ditolak, server yang nolak.
    private val premiumTokenInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val segs = request.url.pathSegments
        val guarded = segs.size >= 2 && segs[segs.size - 2].let { it == "episode" || it == "video-source" }
        if (!guarded) return@Interceptor chain.proceed(request)

        val token = kotlinx.coroutines.runBlocking { PremiumTokenManager.get() }
        val first = chain.proceed(
            if (token != null) request.newBuilder().header("Authorization", "Bearer $token").build() else request
        )
        if (first.code != 401 || token == null) return@Interceptor first

        // Token ditolak (kadaluarsa / secret diputar) -> buang, ambil baru, coba sekali lagi.
        first.close()
        PremiumTokenManager.invalidate()
        val fresh = kotlinx.coroutines.runBlocking { PremiumTokenManager.get() }
        chain.proceed(
            if (fresh != null) request.newBuilder().header("Authorization", "Bearer $fresh").build() else request
        )
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(premiumTokenInterceptor)
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
            .baseUrl(PLACEHOLDER_BASE_URL)
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
