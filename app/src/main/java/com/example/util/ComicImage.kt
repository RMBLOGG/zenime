package com.example.util

import android.content.Context
import coil.request.ImageRequest

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
