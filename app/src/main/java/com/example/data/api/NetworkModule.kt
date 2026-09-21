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
            throw com.example.util.ApiUnavailableException(
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

    /**
     * Nempelin id_user/key_client/apk_ver ke request data/manra/... --
     * niru CommonParamsInterceptor app Animein asli (hasil decompile),
     * tapi sengaja dibatasin cuma ke path Manra (bukan global kayak
     * aslinya) biar endpoint lain yang udah jalan normal gak keganggu.
     * Nilainya didapat dari ManraAuthManager (device-auth otomatis, gak
     * perlu login manual) -- lihat komentar di file itu.
     */
    private val manraParamsInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        if (!request.url.encodedPath.contains("data/manra")) {
            return@Interceptor chain.proceed(request)
        }

        ManraAuthManager.ensureAuthBlocking()
        val idUser = ManraAuthManager.currentIdUser()
        val keyClient = ManraAuthManager.currentKeyClient()
        val apkVer = ManraAuthManager.apkVersion()

        val newRequestBuilder = request.newBuilder()

        if (request.method == "GET" || request.method == "DELETE") {
            val urlBuilder = request.url.newBuilder()
                .addQueryParameter("apk_ver", apkVer)
            if (keyClient != null) urlBuilder.addQueryParameter("key_client", keyClient)
            if (idUser != null) urlBuilder.addQueryParameter("id_user", idUser)
            newRequestBuilder.url(urlBuilder.build())
        } else {
            val body = request.body
            if (body is okhttp3.FormBody) {
                val newBody = okhttp3.FormBody.Builder()
                for (i in 0 until body.size) {
                    newBody.addEncoded(body.encodedName(i), body.encodedValue(i))
                }
                newBody.addEncoded("apk_ver", apkVer)
                if (keyClient != null) newBody.addEncoded("key_client", keyClient)
                if (idUser != null) newBody.addEncoded("id_user", idUser)
                newRequestBuilder.method(request.method, newBody.build())
            }
            // Kalau body-nya bukan FormBody (mis. multipart), dibiarin apa
            // adanya -- belum ada request Manra yang butuh multipart.
        }

        chain.proceed(newRequestBuilder.build())
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Server sumber kadang lambat / memutus koneksi sesaat. GET itu aman
            // diulang, jadi coba sekali lagi kalau koneksinya terputus. Timeout
            // sengaja TIDAK diulang (nunggu 2x lipat cuma bikin user makin lama).
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: java.io.IOException) {
                    val retryable = request.method == "GET" &&
                        e !is java.net.SocketTimeoutException &&
                        e !is com.example.util.ApiUnavailableException
                    if (!retryable) throw e
                    Thread.sleep(800)
                    chain.proceed(request)
                }
            }
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(manraParamsInterceptor)
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
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // server sumber sering lambat menjawab
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

    // KOMIK: client terpisah, khusus buat API komik Sanka (bacakomik.my).
    // Base URL-nya FIXED (bukan dynamic dari Remote Config kayak okHttpClient
    // di atas), jadi gak perlu dynamicBaseUrlInterceptor sama sekali.
    private val comicOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val comicApi: ComicApi by lazy {
        Retrofit.Builder()
            .baseUrl(ComicApi.BASE_URL)
            .client(comicOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ComicApi::class.java)
    }
}
