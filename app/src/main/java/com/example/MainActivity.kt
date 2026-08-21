package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ads.AdManager
import com.example.data.api.NetworkModule
import com.example.data.api.RemoteConfigManager
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.ZenimeDatabase
import com.example.data.repository.AnimeRepository
import com.example.ui.navigation.ZenimeAppNavHost
import com.example.ui.screens.update.ForceUpdateScreen
import com.example.ui.theme.ZenimeTheme
import com.example.util.ApkDownloader
import com.example.util.DownloadState
import com.example.util.PipController
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // null = belum kelar ngecek Remote Config (tampilin blank sebentar),
    // true = versionCode APK ini di bawah minimum -> app diblokir total,
    // cuma ForceUpdateScreen yang di-compose (ZenimeAppNavHost sama sekali
    // gak dipanggil, jadi gak ada cara "skip" balik ke app),
    // false = versi aman, lanjut app seperti biasa.
    private var needsUpdate by mutableStateOf<Boolean?>(null)

    // Downloader APK update, di-scope ke Activity ini (bukan singleton)
    // supaya coroutine polling progress-nya ikut mati kalau Activity-nya
    // kelar. Cuma dipakai kalau needsUpdate == true.
    private val apkDownloader by lazy { ApkDownloader(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init Unity Ads sedini mungkin biar interstitial udah siap kepake
        // pas user pertama kali buka PlayerScreen.
        AdManager.initialize(this)

        val database = ZenimeDatabase.getInstance(this)
        val userPrefs = UserPreferencesRepository(this)
        val repository = AnimeRepository(
            api = NetworkModule.api,
            dao = database.zenimeDao(),
            userPrefs = userPrefs
        )

        // Mulai narik data homepage (buat poster backdrop LoginScreen dan
        // konten HomeScreen) sesegera mungkin, sebelum compose pertama kali
        // ke-render -- supaya poster udah nyampe/lagi keburu kecache pas
        // LoginScreen tampil, bukan mulai fetch baru pas layar itu dibuka.
        lifecycleScope.launch {
            // Ambil base URL terbaru + min_version_code dari Firebase Remote
            // Config dulu (kalau ada koneksi), baru mulai request API pertama
            // supaya langsung pakai base URL yang sesuai.
            RemoteConfigManager.refresh()

            // Bandingin versionCode APK yang lagi jalan sekarang vs minimum
            // yang di-set admin di Console. minVersionCode() == 0 artinya
            // parameter belum di-set -> force update mati (semua versi boleh).
            val currentVersionCode = try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                PackageInfoCompat.getLongVersionCode(packageInfo)
            } catch (_: Exception) {
                Long.MAX_VALUE // gagal baca versi sendiri -- jangan sampai nge-block orang
            }
            val minVersionCode = RemoteConfigManager.minVersionCode()
            needsUpdate = minVersionCode > 0 && currentVersionCode < minVersionCode

            // Kalau lagi diblokir force update, gak perlu buang-buang request
            // buat prefetch homepage -- toh ZenimeAppNavHost gak bakal di-compose.
            if (needsUpdate != true) {
                repository.getHome().collect { }
            }
        }

        setContent {
            val themeMode by userPrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = "DARK")
            val dynamicColor by userPrefs.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = false)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "SYSTEM" -> isSystemInDarkTheme()
                else -> true // Default DARK
            }

            ZenimeTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor
            ) {
                when (needsUpdate) {
                    true -> {
                        val downloadState by apkDownloader.state.collectAsState()
                        ForceUpdateScreen(
                            message = RemoteConfigManager.updateMessage().ifBlank {
                                "Versi aplikasi ini sudah tidak didukung. Update dulu ke versi terbaru untuk lanjut menonton."
                            },
                            downloadState = downloadState,
                            onDownloadClick = {
                                apkDownloader.startDownload(RemoteConfigManager.updateDownloadUrl())
                            },
                            onInstallClick = {
                                (downloadState as? DownloadState.Downloaded)?.let {
                                    apkDownloader.launchInstall(it.fileUri)
                                }
                            },
                            onRetryClick = {
                                apkDownloader.startDownload(RemoteConfigManager.updateDownloadUrl())
                            }
                        )
                    }
                    // false = versi aman -> app normal. null = masih ngecek
                    // Remote Config -> blank sebentar (biasanya cuma sekejap,
                    // gak pakai splash animasi lagi supaya gak dobel).
                    false -> ZenimeAppNavHost(repository = repository)
                    null -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    // Dipanggil sistem pas user ninggalin app (tekan Home, swipe ke recent
    // apps, dll) -- BUKAN pas nekan back. Momen paling pas buat auto-masuk
    // PiP kalau lagi di PlayerScreen, biar video gak keputus pas user
    // ngecek notifikasi/app lain sebentar.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.requestEnter(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.setInPipMode(isInPictureInPictureMode)
    }
}
