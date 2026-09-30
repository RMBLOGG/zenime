package com.example.util

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Gambar komik (cover + halaman chapter) dari CDN sumber. Coil bawaan kirim
 * User-Agent "okhttp/..." dan http:// diblok Android (cleartext), jadi
 * sering kosong. Di web Flask aman karena browser kirim UA normal +
 * referrerpolicy="no-referrer" -- di sini ditiru: UA browser, TANPA Referer.
 */
private const val COMIC_IMG_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

fun normalizeComicImageUrl(url: String?): String? {
    val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        u.startsWith("//") -> "https:$u"
        u.startsWith("http://") -> "https://" + u.removePrefix("http://")
        else -> u
    }
}

fun comicImageRequest(context: Context, url: String?): ImageRequest =
    ImageRequest.Builder(context)
        .data(normalizeComicImageUrl(url))
        .addHeader("User-Agent", COMIC_IMG_UA)
        .crossfade(true)
        .build()

/**
 * ImageLoader khusus halaman chapter. Coil bawaan pakai OkHttp dengan timeout
 * 10 detik, jadi di sinyal lemah (halaman manhwa bisa ratusan KB - beberapa MB)
 * gambar gampang "gagal" padahal cuma lambat. Di sini timeout dilonggarkan
 * dan koneksi yang putus di tengah jalan dicoba ulang otomatis oleh OkHttp.
 */
@Volatile
private var comicLoader: ImageLoader? = null

fun comicImageLoader(context: Context): ImageLoader =
    comicLoader ?: synchronized(ComicImageLock) {
        comicLoader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
            }
            .crossfade(true)
            .build()
            .also { comicLoader = it }
    }

private object ComicImageLock
