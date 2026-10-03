package com.example.util

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy

/**
 * Konfigurasi player yang lebih tahan jaringan lemot (WiFi/Indihome yang
 * lagi jelek). Default ExoPlayer cuma nge-buffer ~50 detik max dan mulai
 * muter di 2,5 detik, timeout 8 detik, retry 3x -- gampang banget stuck
 * kalau speed turun sebentar.
 */
@OptIn(UnstableApi::class)
object PlayerConfig {

    fun loadControl(): LoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 6_000
            )
            // Batas memori buffer biar HP RAM kecil gak OOM.
            .setTargetBufferBytes(48 * 1024 * 1024)
            .setBackBuffer(15_000, false)
            .build()

    /** Retry lebih banyak sebelum player nyerah dan nampilin error. */
    fun errorPolicy() = DefaultLoadErrorHandlingPolicy(6)

    fun applyTimeouts(factory: DefaultHttpDataSource.Factory): DefaultHttpDataSource.Factory =
        factory
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(false)
}
