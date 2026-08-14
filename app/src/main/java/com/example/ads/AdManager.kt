package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
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
    private const val PLACEMENT_INTERSTITIAL = "Interstitial_Android"

    // Set true kalau lagi development/testing biar cuma dapet iklan dummy
    // (nggak generate uang beneran, aman dari resiko akun ke-flag karena
    // klik/impresi berulang dari device sendiri). Production: false.
    private var testMode = false

    private var isInitialized = false
    private var isInterstitialLoaded = false

    fun setTestMode(enabled: Boolean) {
        testMode = enabled
    }

    /**
     * Panggil sekali di awal (misal di MainActivity.onCreate) sebelum ada
     * placement yang di-load/ditampilin.
     */
    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
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
                    loadInterstitial()
                    onReady?.invoke()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    Log.e(TAG, "Unity Ads init failed: $error - $message")
                }
            }
        )
    }

    /** Preload iklan interstitial supaya siap ditampilin instan pas dibutuhin. */
    fun loadInterstitial() {
        if (!isInitialized) return
        UnityAds.load(
            PLACEMENT_INTERSTITIAL,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    isInterstitialLoaded = true
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    isInterstitialLoaded = false
                    Log.e(TAG, "Gagal load interstitial: $error - $message")
                }
            }
        )
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
