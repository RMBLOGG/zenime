package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

private const val CLOUDINARY_CLOUD_NAME = "jbtwhnrb"
private const val CLOUDINARY_UPLOAD_PRESET = "Zenime"
private const val CLOUDINARY_UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload"

// Banner ditampilin lebar (bukan bulat kayak avatar), jadi dibatasi lewat sisi
// terpanjang yang lebih besar dari avatar (1280px) biar tetap tajam pas
// di-crop lebar penuh layar, tapi ukuran filenya tetap wajar.
private const val MAX_DIMENSION_PX = 1280
private const val JPEG_QUALITY = 82

/**
 * Upload foto banner profil -- khusus user Premium (dicek di UI sebelum
 * manggil ini, dan diulang lagi di ProfileViewModel.uploadBanner biar gak
 * bisa dilewatin). Sama persis polanya kayak [AvatarUploader]/[ClanPhotoUploader]:
 * kompres ke JPEG dulu, lalu upload ke Cloudinary lewat unsigned upload
 * preset `Zenime`, public_id "banners/{firebaseUid}-{timestamp}" (UNIK per
 * upload, bukan fixed lagi -- lihat komentar lengkap di [AvatarUploader],
 * ini fix buat bug "banner cuma bisa diganti 2x").
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
     * @return secure_url (HTTPS) hasil upload dari Cloudinary.
     * @throws Exception kalau baca gambar atau upload-nya gagal.
     */
    suspend fun uploadBanner(context: Context, imageUri: Uri, firebaseUid: String): String =
        withContext(Dispatchers.IO) {
            val jpegBytes = compressImage(context.contentResolver, imageUri)

            val tempFile = File.createTempFile("banner", ".jpg", context.cacheDir)
            tempFile.writeBytes(jpegBytes)

            // public_id unik per upload -- lihat komentar di AvatarUploader.uploadAvatar.
            val uniquePublicId = "banners/$firebaseUid-${System.currentTimeMillis()}"

            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("public_id", uniquePublicId)
                    .addFormDataPart(
                        "file",
                        "$firebaseUid.jpg",
                        tempFile.asRequestBody("image/jpeg".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url(CLOUDINARY_UPLOAD_URL)
                    .post(requestBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (!response.isSuccessful || bodyString == null) {
                        throw IllegalStateException("Upload banner gagal (${response.code}): $bodyString")
                    }
                    val json = JSONObject(bodyString)
                    json.optString("secure_url").takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Response Cloudinary gak ada secure_url: $bodyString")
                }
            } finally {
                tempFile.delete()
            }
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
