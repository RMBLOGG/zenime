package com.example.util

import com.example.data.api.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private const val VOICE_NOTE_BUCKET = "chat-voice-notes"

/**
 * Upload pesan suara (VN) Chat Global -- khusus user Premium (dicek di
 * ChatViewModel.sendVoiceNote sebelum manggil ini, sama kayak pola
 * AvatarUploader/uploadAvatar). File di-PUT langsung ke Supabase Storage
 * lewat REST API (bukan SDK), path-nya dipisah per firebase_uid biar rapi.
 *
 * SYARAT DI SISI SUPABASE (dashboard, bukan kode):
 * - Bikin bucket Storage baru namanya "chat-voice-notes", set Public.
 * - Policy INSERT/UPDATE buat bucket ini cukup dibuka ke anon (sama kayak
 *   bucket "chat-avatars" yang udah ada), karena app kirim pake anon key.
 */
object VoiceNoteUploader {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @return URL publik file suara yang baru diupload.
     * @throws Exception kalau upload-nya gagal.
     */
    suspend fun uploadVoiceNote(file: File, firebaseUid: String): String =
        withContext(Dispatchers.IO) {
            val path = "$firebaseUid/${System.currentTimeMillis()}.m4a"
            val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/$VOICE_NOTE_BUCKET/$path"

            val request = Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                .header("x-upsert", "true")
                .post(file.asRequestBody("audio/mp4".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "Upload pesan suara gagal (${response.code}): ${response.body?.string()}"
                    )
                }
            }

            "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$VOICE_NOTE_BUCKET/$path"
        }
}
