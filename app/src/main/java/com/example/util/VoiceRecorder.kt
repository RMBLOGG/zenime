package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Wrapper tipis di atas MediaRecorder buat rekam pesan suara (VN) di Chat
 * Global. File direkam ke cache dir app sendiri (bukan storage publik),
 * format M4A/AAC bitrate rendah biar ukurannya kecil & hemat kuota pas
 * diupload ke Supabase Storage lewat VoiceNoteUploader.
 *
 * Satu instance = satu sesi rekam. Bikin instance baru tiap mulai rekam.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    /** @return file tujuan rekaman. @throws Exception kalau MediaRecorder gagal start (mis. izin mikrofon belum ada). */
    fun start(): File {
        val file = File(context.cacheDir, "vn_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(64_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            mr.release()
            file.delete()
            throw e
        }
        recorder = mr
        outputFile = file
        startedAtMs = System.currentTimeMillis()
        return file
    }

    /** Hentiin rekaman & simpen file-nya. @return durasi rekaman dalam detik (min. 1), atau null kalau gagal/kependekan. */
    fun stop(): Int? {
        val mr = recorder ?: return null
        val elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000).toInt()
        return try {
            mr.stop()
            elapsedSec.coerceAtLeast(1)
        } catch (e: Exception) {
            // MediaRecorder.stop() bisa throw kalau rekamannya kependekan
            // (< ~1 detik) atau gak ada audio yang kerekam sama sekali.
            outputFile?.delete()
            null
        } finally {
            mr.release()
            recorder = null
        }
    }

    /** Batalin rekaman yang lagi jalan & hapus file-nya (dipanggil pas user pencet cancel). */
    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // Diem aja -- lagi mau dibuang juga file-nya.
        } finally {
            recorder?.release()
            recorder = null
        }
        outputFile?.delete()
        outputFile = null
    }

    fun currentFile(): File? = outputFile
}
