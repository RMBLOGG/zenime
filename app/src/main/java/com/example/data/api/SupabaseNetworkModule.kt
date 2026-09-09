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
 */
object SupabaseNetworkModule {

    private val apiKeyInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(request)
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

    val api: ZenimeSupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl("${SupabaseConfig.SUPABASE_URL}/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ZenimeSupabaseApi::class.java)
    }
}
