package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ads.AdManager
import com.example.data.api.NetworkModule
import com.example.data.api.RemoteConfigManager
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.ZenimeDatabase
import com.example.data.repository.AnimeRepository
import com.example.ui.navigation.ZenimeAppNavHost
import com.example.ui.theme.ZenimeTheme
import com.example.util.PipController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
            // Ambil base URL terbaru dari Firebase Remote Config dulu (kalau
            // ada koneksi), baru mulai request API pertama supaya langsung
            // pakai base URL yang sesuai.
            RemoteConfigManager.refresh()
            repository.getHome().collect { }
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
                ZenimeAppNavHost(repository = repository)
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
