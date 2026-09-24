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
 * Upload foto profil -- dipakai buat Profil & Chat Global, BEBAS semua user
 * (gak perlu Premium; beda sama [BannerUploader] yang tetap khusus Premium).
 * Gambar dikompres dulu ke JPEG max 512px, lalu diupload ke Cloudinary lewat
 * unsigned upload preset `Zenime` (pola PERSIS sama kayak [ClanPhotoUploader],
 * cuma folder public_id-nya beda: "avatars/{firebaseUid}-{timestamp}" bukan
 * "clan-photos/...").
 *
 * public_id-nya UNIK per upload (nempel timestamp), BUKAN fixed per user.
 * Sebelumnya public_id fixed ("avatars/{uid}") sehingga bergantung ke setting
 * "Overwrite" + "Unique filename" di preset Cloudinary buat bisa dipakai
 * berkali-kali -- kalau preset-nya belum diatur pas, upload ke-3 dst bentrok
 * sama asset lama (bug "foto cuma bisa diganti 2x"). Public_id unik = setiap
 * upload pasti berhasil bikin asset baru, gak pernah bentrok lagi, gak
 * gantung setting preset apapun. Konsekuensinya: asset lama TETAP ada di
 * storage Cloudinary (gak keganti/kehapus otomatis) -- biasanya gak masalah
 * buat foto profil yang ukurannya kecil, tapi kalau storage jadi perhatian,
 * perlu ditambah cleanup terpisah (hapus asset lama pakai Admin API).
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
     * @return secure_url (HTTPS) hasil upload dari Cloudinary.
     * @throws Exception kalau baca gambar atau upload-nya gagal.
     */
    suspend fun uploadAvatar(context: Context, imageUri: Uri, firebaseUid: String): String =
        withContext(Dispatchers.IO) {
            val jpegBytes = compressImage(context.contentResolver, imageUri)

            // Cloudinary butuh file beneran (bukan cuma byte array) buat multipart,
            // jadi ditulis dulu ke cache directory sementara.
            val tempFile = File.createTempFile("avatar", ".jpg", context.cacheDir)
            tempFile.writeBytes(jpegBytes)

            // public_id DIBIKIN UNIK per upload (timestamp) -- BUKAN fixed per user lagi.
            // Sebelumnya public_id fixed ("avatars/{uid}") jadi upload ke-3+ bentrok
            // sama asset lama & gagal/gak ke-overwrite kalau setting "Overwrite" +
            // "Unique filename" di preset Cloudinary belum pas (banyak yang ngeluh
            // foto profil cuma bisa diganti 2x). Public_id unik = gak pernah bentrok
            // lagi, gak gantung sama setting preset apapun.
            val uniquePublicId = "avatars/$firebaseUid-${System.currentTimeMillis()}"

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
                        throw IllegalStateException("Upload avatar gagal (${response.code}): $bodyString")
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
