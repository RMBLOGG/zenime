package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.BuildConfig
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener

/**
 * Wrapper tipis di atas Unity LevelPlay SDK buat nampilin interstitial video ad
 * sebelum user mulai nonton episode.
 *
 * MIGRASI (lihat riwayat chat): sebelumnya pakai legacy `com.unity3d.ads.UnityAds`
 * langsung ke placement Waterfall di Unity Cloud (Game ID). Per kebijakan Unity,
 * placement baru di Unity Cloud sekarang otomatis Bidding, dan SDK legacy nggak
 * bisa konsumsi placement Bidding (error "adMarkup is missing; objectId is
 * missing"). Solusinya migrasi ke LevelPlay SDK (App Key + Ad Unit ID, didaftarin
 * lewat platform.ironsrc.com, bukan cloud.unity.com lagi).
 */
object AdManager {

    private const val TAG = "AdManager"

    private var isInitialized = false
    private var interstitialAd: LevelPlayInterstitialAd? = null
    private var isInterstitialLoaded = false

    // Retry load kalau gagal (no-fill, timeout, dll), backoff naik tiap
    // gagal berturut-turut, di-cap biar nggak nunggu kelamaan. Tanpa ini,
    // sekali load gagal, iklan SELAMANYA nggak ke-refresh lagi karena
    // satu-satunya pemicu load ulang lain cuma abis show sukses.
    private val retryDelaysMs = longArrayOf(10_000L, 30_000L, 60_000L, 120_000L)
    private var retryAttempt = 0
    private val retryHandler = Handler(Looper.getMainLooper())
    private var isLoadInFlight = false

    // DEBUG SEMENTARA: nampilin Toast tiap ada kejadian penting soal iklan
    // (gagal load, gagal tampil, skip karena belum siap), lengkap sama pesan
    // errornya, biar kelihatan langsung di layar HP tanpa perlu adb/logcat.
    // Matiin lagi (set false) begitu udah selesai diagnosa.
    private const val DEBUG_ADS_TOAST = true
    private var appContext: Context? = null

    private fun debugToast(message: String) {
        if (!DEBUG_ADS_TOAST) return
        val ctx = appContext ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, "[Ads] $message", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Panggil sekali di awal (misal di MainActivity.onCreate) sebelum ada
     * placement yang di-load/ditampilin.
     */
    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        appContext = context.applicationContext
        if (isInitialized) {
            onReady?.invoke()
            return
        }

        val initRequest = LevelPlayInitRequest.Builder(BuildConfig.LEVELPLAY_APP_KEY).build()

        LevelPlay.init(
            context.applicationContext,
            initRequest,
            object : LevelPlayInitListener {
                override fun onInitSuccess(configuration: LevelPlayConfiguration) {
                    Log.d(TAG, "LevelPlay initialized")
                    isInitialized = true
                    debugToast("LevelPlay initialized (appKey=${BuildConfig.LEVELPLAY_APP_KEY})")
                    createAndLoadInterstitial()
                    onReady?.invoke()
                }

                override fun onInitFailed(error: LevelPlayInitError) {
                    Log.e(TAG, "LevelPlay init gagal: $error")
                    debugToast("Init GAGAL: $error")
                }
            }
        )
    }

    private fun createAndLoadInterstitial() {
        if (interstitialAd == null) {
            interstitialAd = LevelPlayInterstitialAd(BuildConfig.LEVELPLAY_INTERSTITIAL_AD_UNIT_ID)
            interstitialAd?.setListener(object : LevelPlayInterstitialAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    isLoadInFlight = false
                    isInterstitialLoaded = true
                    retryAttempt = 0
                    debugToast("Interstitial berhasil di-load, siap ditampilin")
                }

                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    isLoadInFlight = false
                    isInterstitialLoaded = false
                    Log.e(TAG, "Gagal load interstitial: $error")
                    debugToast("Gagal load: $error")
                    scheduleRetry()
                }

                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}

                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    Log.e(TAG, "Gagal nampilin interstitial: $error")
                    debugToast("Gagal tampil: $error")
                    onShowFinished()
                }

                override fun onAdClicked(adInfo: LevelPlayAdInfo) {}

                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    onShowFinished()
                }

                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
            })
        }
        loadInterstitial()
    }

    /** Preload iklan interstitial supaya siap ditampilin instan pas dibutuhin. */
    private fun loadInterstitial() {
        if (!isInitialized || isLoadInFlight) return
        isLoadInFlight = true
        interstitialAd?.loadAd()
    }

    private fun scheduleRetry() {
        val delay = retryDelaysMs[retryAttempt.coerceAtMost(retryDelaysMs.lastIndex)]
        retryAttempt++
        Log.d(TAG, "Retry load interstitial dalam ${delay / 1000}s (percobaan ke-$retryAttempt)")
        retryHandler.postDelayed({ loadInterstitial() }, delay)
    }

    private var pendingOnAdFinished: (() -> Unit)? = null

    private fun onShowFinished() {
        isInterstitialLoaded = false
        // Siapin iklan berikutnya buat episode selanjutnya.
        loadInterstitial()
        val callback = pendingOnAdFinished
        pendingOnAdFinished = null
        callback?.invoke()
    }

    /**
     * Tampilin interstitial video ad kalau udah siap. [onAdFinished] selalu
     * dipanggil tepat sekali -- baik iklannya sukses tampil, di-skip user,
     * gagal tampil, atau memang belum ada iklan yang siap (network/timeout) --
     * supaya alur nonton video TETEP LANJUT walau iklan gagal, bukan malah
     * nge-block user selamanya.
     */
    fun showInterstitial(activity: Activity, onAdFinished: () -> Unit) {
        val ad = interstitialAd
        if (!isInitialized || !isInterstitialLoaded || ad == null || !ad.isAdReady) {
            Log.d(TAG, "Interstitial belum siap, skip nampilin iklan")
            debugToast("Skip: iklan belum siap (initialized=$isInitialized, loaded=$isInterstitialLoaded)")
            // Jaga-jaga kalau kebetulan lagi nggak ada retry yang jalan
            // (misal masih nunggu backoff), coba siapin lagi dari sini juga.
            if (isInitialized && !isLoadInFlight) loadInterstitial()
            onAdFinished()
            return
        }

        pendingOnAdFinished = onAdFinished
        ad.showAd(activity)
    }
}
