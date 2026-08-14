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
 * Wrapper tipis di atas Unity Ads SDK buat nampilin REWARDED video ad
 * (wajib ditonton penuh, tanpa tombol skip) sebelum user mulai nonton
 * episode.
 *
 * Placement ID diambil dari dashboard Unity Cloud (Monetization > Apps >
 * Zenime > Network Placement ID).
 */
object AdManager {

    private const val TAG = "AdManager"
    private const val PLACEMENT_REWARDED = "Rewarded_Android"

    // Set true kalau lagi development/testing biar cuma dapet iklan dummy
    // (nggak generate uang beneran, aman dari resiko akun ke-flag karena
    // klik/impresi berulang dari device sendiri). Production: false.
    private var testMode = true

    private var isInitialized = false
    private var isRewardedLoaded = false
    private var appContext: Context? = null

    // Toast debug sementara buat diagnosis kenapa iklan gak muncul, tanpa
    // perlu logcat/adb. Aman dibiarin nyala pas testMode=true; nanti tinggal
    // di-nonaktifin lagi setelah masalahnya ketemu.
    private var debugToasts = true

    private fun debugToast(context: Context, message: String) {
        if (!debugToasts) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, "[AdManager] $message", Toast.LENGTH_LONG).show()
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
                    debugToast(context, "Init sukses, mulai load iklan")
                    isInitialized = true
                    loadRewarded()
                    onReady?.invoke()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    Log.e(TAG, "Unity Ads init failed: $error - $message")
                    debugToast(context, "Init GAGAL: $error - $message")
                }
            }
        )
    }

    /** Preload rewarded ad supaya siap ditampilin instan pas dibutuhin. */
    fun loadRewarded() {
        if (!isInitialized) return
        UnityAds.load(
            PLACEMENT_REWARDED,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    isRewardedLoaded = true
                    appContext?.let { debugToast(it, "Iklan berhasil di-load, siap tampil") }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    isRewardedLoaded = false
                    Log.e(TAG, "Gagal load rewarded ad: $error - $message")
                    appContext?.let { debugToast(it, "Load GAGAL: $error - $message") }
                }
            }
        )
    }

    /**
     * Tampilin rewarded video ad (wajib nonton penuh, gak ada tombol skip)
     * kalau udah siap. [onAdFinished] dipanggil dengan [earnedReward] = true
     * cuma kalau iklannya beneran ditonton sampai selesai (state COMPLETED).
     *
     * Kalau iklan gagal/belum siap (misal lagi no-fill), [onAdFinished]
     * tetap dipanggil dengan earnedReward = false supaya video TETEP BISA
     * DITONTON -- bukan malah nge-block user selamanya gara-gara sistem
     * iklan lagi bermasalah.
     */
    fun showRewarded(activity: Activity, onAdFinished: (earnedReward: Boolean) -> Unit) {
        if (!isInitialized || !isRewardedLoaded) {
            Log.d(TAG, "Rewarded ad belum siap, skip nampilin iklan")
            debugToast(activity, "Skip: belum siap (init=$isInitialized, loaded=$isRewardedLoaded)")
            onAdFinished(false)
            return
        }

        var finished = false
        fun finishOnce(earnedReward: Boolean) {
            if (!finished) {
                finished = true
                isRewardedLoaded = false
                // Siapin iklan berikutnya buat episode selanjutnya.
                loadRewarded()
                onAdFinished(earnedReward)
            }
        }

        UnityAds.show(
            activity,
            PLACEMENT_REWARDED,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    Log.e(TAG, "Gagal nampilin rewarded ad: $error - $message")
                    debugToast(activity, "Show GAGAL: $error - $message")
                    finishOnce(false)
                }

                override fun onUnityAdsShowStart(placementId: String?) {}

                override fun onUnityAdsShowClick(placementId: String?) {}

                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    state: UnityAds.UnityAdsShowCompletionState?
                ) {
                    val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                    Log.d(TAG, "Rewarded ad selesai dengan state: $state")
                    finishOnce(completed)
                }
            }
        )
    }
}
