package com.example.util

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Jembatan kecil antara PlayerScreen (Compose) dan MainActivity buat fitur
 * Picture-in-Picture. PlayerScreen nulis ke sini pas dia aktif/lagi nonton;
 * MainActivity baca ini pas user pindah app (onUserLeaveHint) buat mutusin
 * apakah perlu auto-masuk PiP atau nggak. Dibikin object (bukan lewat
 * ViewModel/CompositionLocal) soalnya yang butuh akses ada di dua dunia
 * berbeda -- Activity (framework) dan Composable -- yang gak gampang saling
 * suntik lewat DI biasa di app ini.
 */
object PipController {

    // True selama PlayerScreen ada di layar -- dipakai MainActivity buat
    // mutusin apakah auto-enter PiP pas user pindah app (tekan Home / swipe
    // recent apps). Di luar PlayerScreen, ini selalu false jadi PiP gak
    // ke-trigger di halaman lain.
    private val _canEnterPip = MutableStateFlow(false)

    // Rasio aspek video saat ini, dikirim ke PictureInPictureParams biar
    // jendela PiP-nya proporsional (gak gepeng/kepotong) sesuai video yang
    // lagi diputar.
    private val _aspectRatio = MutableStateFlow(Rational(16, 9))

    // True selagi Activity BENERAN lagi dalam mode PiP. PlayerScreen pakai
    // ini buat nyembunyiin overlay kontrol custom (gak ada gunanya di
    // jendela kecil, dan sistem yang gambar kontrol play/pause/close-nya).
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode

    fun setCanEnterPip(enabled: Boolean) {
        _canEnterPip.value = enabled
    }

    fun setAspectRatio(width: Int, height: Int) {
        // PiP di Android cuma nerima rasio antara 1:2.39 dan 2.39:1 --
        // di luar itu system bakal nolak/nge-crash PictureInPictureParams.
        if (width <= 0 || height <= 0) return
        val ratio = width.toFloat() / height.toFloat()
        if (ratio < 1f / 2.39f || ratio > 2.39f) return
        _aspectRatio.value = Rational(width, height)
    }

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    /**
     * Coba masuk PiP kalau lagi di PlayerScreen. Dipanggil dari
     * MainActivity.onUserLeaveHint() (pas user pindah app) atau dari tombol
     * PiP manual di kontrol player. Aman dipanggil kapan pun -- no-op kalau
     * kondisinya gak memenuhi (bukan di player, device gak support PiP, dll).
     */
    fun requestEnter(activity: Activity) {
        if (!_canEnterPip.value) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(_aspectRatio.value)
            .build()

        runCatching { activity.enterPictureInPictureMode(params) }
    }
}
