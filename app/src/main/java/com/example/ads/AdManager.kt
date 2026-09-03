package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Wrapper tipis di atas Google Mobile Ads SDK (AdMob) buat nampilin:
 * - Interstitial video ad sebelum user mulai nonton episode (non-premium).
 * - Rewarded video ad buat "tonton iklan = unlock 1 episode" (dipanggil
 *   manual dari UI, bukan otomatis kayak interstitial).
 *
 * MIGRASI (lihat riwayat chat): sebelumnya pakai Unity LevelPlay/ironSource
 * mediation. Pindah ke AdMob langsung karena proses approval LevelPlay
 * kelamaan. Public API (`showInterstitial`) sengaja dipertahanin sama
 * kayak sebelumnya (signature identik) supaya caller (PlayerScreen.kt,
 * MainActivity.kt) gak perlu diubah.
 */
object AdManager {

    private const val TAG = "AdManager"

    private var isInitialized = false
    private var appContext: Context? = null

    // ---- Interstitial ----
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoadInFlight = false
    private var interstitialRetryAttempt = 0
    private var pendingInterstitialOnFinished: (() -> Unit)? = null

    // ---- Rewarded ----
    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoadInFlight = false
    private var rewardedRetryAttempt = 0
    private var pendingRewardedOnFinished: (() -> Unit)? = null
    private var rewardEarnedThisShow = false

    // Retry load kalau gagal (no-fill, timeout, dll), backoff naik tiap
    // gagal berturut-turut, di-cap biar nggak nunggu kelamaan. Tanpa ini,
    // sekali load gagal, iklan SELAMANYA nggak ke-refresh lagi karena
    // satu-satunya pemicu load ulang lain cuma abis show sukses.
    private val retryDelaysMs = longArrayOf(10_000L, 30_000L, 60_000L, 120_000L)
    private val retryHandler = Handler(Looper.getMainLooper())

    // DEBUG SEMENTARA: nampilin Toast tiap ada kejadian penting soal iklan
    // (gagal load, gagal tampil, skip karena belum siap), lengkap sama pesan
    // errornya, biar kelihatan langsung di layar HP tanpa perlu adb/logcat.
    // Matiin lagi (set false) begitu udah selesai diagnosa.
    private const val DEBUG_ADS_TOAST = false

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

        MobileAds.initialize(context.applicationContext) { status ->
            Log.d(TAG, "AdMob initialized: ${status.adapterStatusMap}")
            isInitialized = true
            debugToast("AdMob initialized")
            loadInterstitial()
            loadRewarded()
            onReady?.invoke()
        }
    }

    // ===================== INTERSTITIAL =====================

    private fun loadInterstitial() {
        val ctx = appContext ?: return
        if (!isInitialized || isInterstitialLoadInFlight || interstitialAd != null) return
        isInterstitialLoadInFlight = true

        InterstitialAd.load(
            ctx,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isInterstitialLoadInFlight = false
                    interstitialRetryAttempt = 0
                    interstitialAd = ad
                    debugToast("Interstitial berhasil di-load, siap ditampilin")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isInterstitialLoadInFlight = false
                    interstitialAd = null
                    Log.e(TAG, "Gagal load interstitial: $error")
                    debugToast("Gagal load interstitial: ${error.message}")
                    scheduleRetry(isRewarded = false)
                }
            }
        )
    }

    private fun onInterstitialShowFinished() {
        interstitialAd = null
        // Siapin iklan berikutnya buat episode selanjutnya.
        loadInterstitial()
        val callback = pendingInterstitialOnFinished
        pendingInterstitialOnFinished = null
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
        if (!isInitialized || ad == null) {
            Log.d(TAG, "Interstitial belum siap, skip nampilin iklan")
            debugToast("Skip: interstitial belum siap")
            if (isInitialized && !isInterstitialLoadInFlight) loadInterstitial()
            onAdFinished()
            return
        }

        pendingInterstitialOnFinished = onAdFinished
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onInterstitialShowFinished()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Gagal nampilin interstitial: $error")
                debugToast("Gagal tampil: ${error.message}")
                onInterstitialShowFinished()
            }
        }
        ad.show(activity)
    }

    // ===================== REWARDED =====================

    private fun loadRewarded() {
        val ctx = appContext ?: return
        if (!isInitialized || isRewardedLoadInFlight || rewardedAd != null) return
        isRewardedLoadInFlight = true

        RewardedAd.load(
            ctx,
            BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isRewardedLoadInFlight = false
                    rewardedRetryAttempt = 0
                    rewardedAd = ad
                    debugToast("Rewarded berhasil di-load, siap ditampilin")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isRewardedLoadInFlight = false
                    rewardedAd = null
                    Log.e(TAG, "Gagal load rewarded: $error")
                    debugToast("Gagal load rewarded: ${error.message}")
                    scheduleRetry(isRewarded = true)
                }
            }
        )
    }

    private fun onRewardedShowFinished() {
        rewardedAd = null
        // Siapin rewarded ad berikutnya buat episode/permintaan selanjutnya.
        loadRewarded()
        val callback = pendingRewardedOnFinished
        pendingRewardedOnFinished = null
        callback?.invoke()
    }

    /** Cek dari UI apakah rewarded ad siap ditampilin (buat show/hide tombol "Tonton Iklan"). */
    fun isRewardedReady(): Boolean = rewardedAd != null

    /**
     * Tampilin rewarded video ad buat unlock 1 episode. [onRewardEarned]
     * dipanggil HANYA kalau user nonton iklan sampai selesai (dapet reward
     * `unlock_episode`). [onAdFinished] selalu dipanggil tepat sekali di
     * akhir -- sukses dapet reward, di-skip sebelum selesai, gagal tampil,
     * atau memang belum ada iklan yang siap -- supaya UI (misal loading
     * state di tombol) bisa direset dengan aman.
     */
    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdFinished: () -> Unit
    ) {
        val ad = rewardedAd
        if (!isInitialized || ad == null) {
            Log.d(TAG, "Rewarded belum siap, skip nampilin iklan")
            debugToast("Skip: rewarded belum siap")
            if (isInitialized && !isRewardedLoadInFlight) loadRewarded()
            onAdFinished()
            return
        }

        pendingRewardedOnFinished = onAdFinished
        rewardEarnedThisShow = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onRewardedShowFinished()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Gagal nampilin rewarded: $error")
                debugToast("Gagal tampil: ${error.message}")
                onRewardedShowFinished()
            }
        }
        ad.show(activity) { rewardItem ->
            // Dipanggil pas user udah nonton cukup lama buat dianggap "earned"
            // (item reward "unlock_episode", jumlah 1 -- lihat AdMob dashboard).
            Log.d(TAG, "Reward earned: ${rewardItem.type} x${rewardItem.amount}")
            rewardEarnedThisShow = true
            onRewardEarned()
        }
    }

    // ===================== RETRY (SHARED) =====================

    private fun scheduleRetry(isRewarded: Boolean) {
        val attempt = if (isRewarded) rewardedRetryAttempt else interstitialRetryAttempt
        val delay = retryDelaysMs[attempt.coerceAtMost(retryDelaysMs.lastIndex)]
        if (isRewarded) rewardedRetryAttempt++ else interstitialRetryAttempt++
        val label = if (isRewarded) "rewarded" else "interstitial"
        Log.d(TAG, "Retry load $label dalam ${delay / 1000}s")
        retryHandler.postDelayed(
            { if (isRewarded) loadRewarded() else loadInterstitial() },
            delay
        )
    }
}
