package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.api.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val BANNER_BUCKET = "chat-banners"

// Banner ditampilin lebar (bukan bulat kayak avatar), jadi dibatasi lewat sisi
// terpanjang yang lebih besar dari avatar (1280px) biar tetap tajam pas
// di-crop lebar penuh layar, tapi ukuran filenya tetap wajar.
private const val MAX_DIMENSION_PX = 1280
private const val JPEG_QUALITY = 82

/**
 * Upload foto banner profil -- khusus user Premium (dicek di UI sebelum
 * manggil ini, dan diulang lagi di ProfileViewModel.uploadBanner biar gak
 * bisa dilewatin). Sama persis polanya kayak [AvatarUploader]: kompres ke
 * JPEG dulu, lalu PUT langsung ke Supabase Storage lewat REST API (bucket
 * terpisah "chat-banners" -- WAJIB dibikin public dulu di Supabase dashboard,
 * caranya sama kayak waktu bikin bucket "chat-avatars").
 */
object BannerUploader {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @return URL publik banner yang baru diupload.
     * @throws Exception kalau baca gambar atau upload-nya gagal.
     */
    suspend fun uploadBanner(context: Context, imageUri: Uri, firebaseUid: String): String =
        withContext(Dispatchers.IO) {
            val jpegBytes = compressImage(context.contentResolver, imageUri)
            val path = "$firebaseUid.jpg"
            val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$BANNER_BUCKET/$path"

            val request = Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                .header("x-upsert", "true")
                .post(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "Upload banner gagal (${response.code}): ${response.body?.string()}"
                    )
                }
            }

            // Cache-buster (?v=timestamp) biar Coil gak nampilin banner lama
            // yang ke-cache pas user ganti banner ke path yang sama.
            "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$BANNER_BUCKET/$path?v=${System.currentTimeMillis()}"
        }

    private fun compressImage(resolver: ContentResolver, uri: Uri): ByteArray {
        val original = resolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException("Gagal membaca gambar")

        val scaled = scaleDown(original, MAX_DIMENSION_PX)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (scaled !== original) original.recycle()
        scaled.recycle()
        return output.toByteArray()
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
