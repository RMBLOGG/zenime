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

private const val MAX_DIMENSION_PX = 512
private const val JPEG_QUALITY = 82

/**
 * Upload foto clan ke Cloudinary lewat unsigned upload preset (gak butuh
 * API secret di client -- itu yang bikin "unsigned" ini aman ditaro di
 * kode Android, beda sama API secret yang harus tetap rahasia di server).
 * Preset `Zenime` udah dibikin di dashboard Cloudinary (mode Unsigned).
 */
object ClanPhotoUploader {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @param pathKey identitas unik buat public_id di Cloudinary -- pas
     *   bikin clan baru, clan ID-nya belum ada, jadi dari CreateClanScreen
     *   kirim `"${firebaseUid}-${System.currentTimeMillis()}"`. Abis
     *   clan-nya jadi, dari ManageClanScreen kirim `clanId` biasa.
     * @return secure_url (HTTPS) hasil upload dari Cloudinary.
     * @throws Exception kalau baca gambar atau upload-nya gagal.
     */
    suspend fun uploadClanPhoto(context: Context, imageUri: Uri, pathKey: String): String =
        withContext(Dispatchers.IO) {
            val jpegBytes = compressImage(context.contentResolver, imageUri)

            // Cloudinary butuh file beneran (bukan cuma byte array) buat multipart,
            // jadi ditulis dulu ke cache directory sementara.
            val tempFile = File.createTempFile("clan_photo", ".jpg", context.cacheDir)
            tempFile.writeBytes(jpegBytes)

            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    .addFormDataPart("public_id", "clan-photos/$pathKey")
                    .addFormDataPart(
                        "file",
                        "$pathKey.jpg",
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
                        throw IllegalStateException("Upload foto clan gagal (${response.code}): $bodyString")
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
