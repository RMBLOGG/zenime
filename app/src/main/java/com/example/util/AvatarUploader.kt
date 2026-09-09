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

private const val AVATAR_BUCKET = "chat-avatars"
private const val MAX_DIMENSION_PX = 512
private const val JPEG_QUALITY = 82

/**
 * Upload foto profil buat Chat Global -- khusus user Premium (dicek di UI
 * sebelum manggil ini, lihat ChatViewModel.uploadAvatar). Gambar dikompres
 * dulu ke JPEG max 512px biar hemat kuota & storage, lalu di-PUT langsung
 * ke Supabase Storage lewat REST API (bukan pakai Supabase SDK, biar gak
 * nambah dependency besar cuma buat satu fitur ini).
 */
object AvatarUploader {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @return URL publik avatar yang baru diupload.
     * @throws Exception kalau baca gambar atau upload-nya gagal.
     */
    suspend fun uploadAvatar(context: Context, imageUri: Uri, firebaseUid: String): String =
        withContext(Dispatchers.IO) {
            val jpegBytes = compressImage(context.contentResolver, imageUri)
            val path = "$firebaseUid.jpg"
            val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$AVATAR_BUCKET/$path"

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
                        "Upload avatar gagal (${response.code}): ${response.body?.string()}"
                    )
                }
            }

            // Tambah query param cache-buster (?v=timestamp) biar Coil gak nampilin
            // avatar lama yang ke-cache pas user ganti foto ke path yang sama.
            "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$AVATAR_BUCKET/$path?v=${System.currentTimeMillis()}"
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
