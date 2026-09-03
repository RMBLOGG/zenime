package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.BuildConfig
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Wrapper tipis di atas Unity Ads SDK buat nampilin interstitial video ad
 * sebelum user mulai nonton episode.
 *
 * Placement ID diambil dari dashboard Unity Cloud (Monetization > Apps >
 * Zenime > Network Placement ID).
 */
object AdManager {

    private const val TAG = "AdManager"
    // TEMP TESTING: ganti ke placement baru (dibikin lewat "Create placement"
    // di dashboard, tanpa milih Bidding) buat mastiin ini fix error
    // "adMarkup is missing; objectId is missing". Kalau kebukti jalan,
    // archive placement lama & ganti nilai ini balik ke "Interstitial_Android".
    private const val PLACEMENT_INTERSTITIAL = "Interstitial_Android_v2"

    // Set true kalau lagi development/testing biar cuma dapet iklan dummy
    // (nggak generate uang beneran, aman dari resiko akun ke-flag karena
    // klik/impresi berulang dari device sendiri). Production: false.
    private var testMode = false

    private var isInitialized = false
    private var isInterstitialLoaded = false

    // Retry load kalau gagal (no-fill, timeout, dll), backoff naik tiap
    // gagal berturut-turut, di-cap biar nggak nunggu kelamaan. Tanpa ini,
    // sekali load gagal, iklan SELAMANYA nggak ke-refresh lagi karena
    // satu-satunya pemicu loadInterstitial() lain cuma abis show sukses.
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

    fun setTestMode(enabled: Boolean) {
        testMode = enabled
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

        UnityAds.initialize(
            context.applicationContext,
            BuildConfig.UNITY_ADS_GAME_ID,
            testMode,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "Unity Ads initialized")
                    isInitialized = true
                    debugToast("Unity Ads initialized (gameId=${BuildConfig.UNITY_ADS_GAME_ID}, testMode=$testMode)")
                    loadInterstitial()
                    onReady?.invoke()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    Log.e(TAG, "Unity Ads init failed: $error - $message")
                    debugToast("Init GAGAL: $error - $message")
                }
            }
        )
    }

    /** Preload iklan interstitial supaya siap ditampilin instan pas dibutuhin. */
    fun loadInterstitial() {
        if (!isInitialized || isLoadInFlight) return
        isLoadInFlight = true
        UnityAds.load(
            PLACEMENT_INTERSTITIAL,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    isLoadInFlight = false
                    isInterstitialLoaded = true
                    retryAttempt = 0
                    debugToast("Interstitial berhasil di-load, siap ditampilin")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    isLoadInFlight = false
                    isInterstitialLoaded = false
                    Log.e(TAG, "Gagal load interstitial: $error - $message")
                    debugToast("Gagal load: $error - $message")
                    scheduleRetry()
                }
            }
        )
    }

    private fun scheduleRetry() {
        val delay = retryDelaysMs[retryAttempt.coerceAtMost(retryDelaysMs.lastIndex)]
        retryAttempt++
        Log.d(TAG, "Retry load interstitial dalam ${delay / 1000}s (percobaan ke-$retryAttempt)")
        retryHandler.postDelayed({ loadInterstitial() }, delay)
    }

    /**
     * Tampilin interstitial video ad kalau udah siap. [onAdFinished] selalu
     * dipanggil tepat sekali -- baik iklannya sukses tampil, di-skip user,
     * gagal tampil, atau memang belum ada iklan yang siap (network/timeout) --
     * supaya alur nonton video TETEP LANJUT walau iklan gagal, bukan malah
     * nge-block user selamanya.
     */
    fun showInterstitial(activity: Activity, onAdFinished: () -> Unit) {
        if (!isInitialized || !isInterstitialLoaded) {
            Log.d(TAG, "Interstitial belum siap, skip nampilin iklan")
            debugToast("Skip: iklan belum siap (initialized=$isInitialized, loaded=$isInterstitialLoaded)")
            // Jaga-jaga kalau kebetulan lagi nggak ada retry yang jalan
            // (misal masih nunggu backoff), coba siapin lagi dari sini juga.
            if (isInitialized && !isLoadInFlight) loadInterstitial()
            onAdFinished()
            return
        }

        var finished = false
        fun finishOnce() {
            if (!finished) {
                finished = true
                isInterstitialLoaded = false
                // Siapin iklan berikutnya buat episode selanjutnya.
                loadInterstitial()
                onAdFinished()
            }
        }

        UnityAds.show(
            activity,
            PLACEMENT_INTERSTITIAL,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e(TAG, "Gagal nampilin interstitial: $error - $message")
                    debugToast("Gagal tampil: $error - $message")
                    finishOnce()
                }

                override fun onUnityAdsShowStart(placementId: String?) {}

                override fun onUnityAdsShowClick(placementId: String?) {}

                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    state: UnityAds.UnityAdsShowCompletionState?
                ) {
                    finishOnce()
                }
            }
        )
    }
}
