package com.example

import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.api.AnnouncementPopup
import com.example.data.api.GithubUpdateChecker
import com.example.data.api.NetworkModule
import com.example.data.api.RemoteConfigManager
import com.example.data.download.EpisodeDownloadManager
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.ZenimeDatabase
import com.example.data.repository.AnimeRepository
import com.example.data.repository.ComicRepository
import com.example.notifications.setupAnnouncementNotifications
import com.example.ui.navigation.ZenimeAppNavHost
import com.example.ui.screens.announcement.AnnouncementPopupHost
import com.example.ui.screens.maintenance.MaintenanceScreen
import com.example.ui.screens.update.ForceUpdateScreen
import com.example.ui.theme.ZenimeTheme
import com.example.util.ApkDownloader
import com.example.util.DownloadState
import com.example.util.PipController
import androidx.compose.runtime.collectAsState
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        // popup_id yang udah ditutup user di "sesi" app ini -- dipakai buat
        // popup dengan popup_repeat = true. Sengaja di companion (level
        // proses), bukan field Activity: tetap kesimpan pas layar diputar /
        // Activity dibuat ulang, tapi kereset begitu app di-kill lalu
        // dibuka lagi -- itulah yang bikin popup muncul lagi tiap app dibuka.
        var sessionSeenPopupId by mutableStateOf("")
    }

    // null = belum kelar ngecek GitHub Releases (tampilin blank sebentar),
    // true = ada release lebih baru dari versionName APK ini -> app
    // diblokir total, cuma ForceUpdateScreen yang di-compose (ZenimeAppNavHost
    // sama sekali gak dipanggil, jadi gak ada cara "skip" balik ke app),
    // false = versi udah paling baru (atau repo belum ada release/fetch
    // gagal), lanjut app seperti biasa.
    private var needsUpdate by mutableStateOf<Boolean?>(null)

    // true = "maintenance_mode" aktif di Firebase Remote Config -> app
    // diblokir total, cuma MaintenanceScreen yang di-compose (dicek PALING
    // DULUAN, sebelum needsUpdate -- kalau server lagi maintenance gak ada
    // gunanya lanjut ngecek update atau prefetch homepage).
    private var isMaintenanceMode by mutableStateOf(false)

    // Pop up pengumuman dari Remote Config ("popup_enabled" ON di Console).
    // null = saklar OFF / gak ada isi. Diupdate pas app start DAN real-time
    // lewat listener di onStart (lihat di bawah).
    private var announcementPopup by mutableStateOf<AnnouncementPopup?>(null)

    // Registrasi listener real-time Remote Config -- dipasang di onStart,
    // dilepas di onStop supaya gak bocor dan gak jalan pas app di background.
    private var configListener: ConfigUpdateListenerRegistration? = null

    // Info release terbaru dari GitHub (tag + link APK + changelog), diisi
    // bareng needsUpdate. Cuma valid kalau needsUpdate == true.
    private var latestUpdateInfo by mutableStateOf<GithubUpdateChecker.UpdateInfo?>(null)

    // versionName APK yang lagi jalan sekarang, buat ditampilin di chip
    // versi "v{current} -> v{latest}" di ForceUpdateScreen.
    private var currentVersionName by mutableStateOf("")

    // Downloader APK update, di-scope ke Activity ini (bukan singleton)
    // supaya coroutine polling progress-nya ikut mati kalau Activity-nya
    // kelar. Cuma dipakai kalau needsUpdate == true.
    private val apkDownloader by lazy { ApkDownloader(applicationContext) }

    // Dialog izin notifikasi Android 13+ (POST_NOTIFICATIONS) -- gak
    // ngeblok apa-apa kalau user nolak, cuma berarti notif pengumuman
    // gak bakal muncul (fitur lain tetep jalan normal).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Siapin channel notifikasi + subscribe device ini ke topic
        // pengumuman rilis (lihat ANNOUNCEMENT_TOPIC), lalu minta izin
        // runtime-nya kalau di Android 13+ dan belum pernah dikasih.
        setupAnnouncementNotifications(applicationContext)
        requestNotificationPermissionIfNeeded()

        val database = ZenimeDatabase.getInstance(this)
        val userPrefs = UserPreferencesRepository(this)
        val downloadManager = EpisodeDownloadManager(
            appContext = applicationContext,
            dao = database.zenimeDao()
        )
        val repository = AnimeRepository(
            api = NetworkModule.api,
            dao = database.zenimeDao(),
            userPrefs = userPrefs,
            downloadManager = downloadManager
        )
        val comicRepository = ComicRepository(api = NetworkModule.comicApi, dao = database.zenimeDao())

        // Nyambungin lagi polling progress buat download yang masih
        // QUEUED/DOWNLOADING dari sesi sebelumnya (system DownloadManager-nya
        // sendiri tetep jalan terus di background walau app kemarin di-kill).
        repository.reconcileActiveDownloads()

        // Mulai narik data homepage (buat poster backdrop LoginScreen dan
        // konten HomeScreen) sesegera mungkin, sebelum compose pertama kali
        // ke-render -- supaya poster udah nyampe/lagi keburu kecache pas
        // LoginScreen tampil, bukan mulai fetch baru pas layar itu dibuka.
        // Dipanggil sekali di awal onCreate, lalu dipanggil ULANG tiap user
        // pencet "Coba Sekarang" di MaintenanceScreen (lihat setContent di
        // bawah) -- forceRefresh() motong cache 1 jam Remote Config supaya
        // status maintenance_mode yang baru aja di-publish di Console
        // langsung kebaca, bukan nunggu sampai app di-force-close.
        suspend fun checkAppState(isRetry: Boolean) {
            if (isRetry) {
                RemoteConfigManager.forceRefresh()
            } else {
                RemoteConfigManager.refresh()
            }

            isMaintenanceMode = RemoteConfigManager.isMaintenanceMode()
            announcementPopup = RemoteConfigManager.currentPopup()
            if (isMaintenanceMode) {
                // Server lagi diperbaiki -- gak ada gunanya ngecek update
                // atau prefetch homepage dulu, MaintenanceScreen bakal
                // nge-block semuanya.
                needsUpdate = false
                return
            }

            // Cek release terbaru LANGSUNG ke GitHub (bukan Firebase Remote
            // Config lagi) -- gak ada cache/throttle, tiap app dibuka pasti
            // hit GitHub. Kalau repo belum ada release atau fetch gagal
            // (offline dll), checkForUpdate() balikin null -> anggap aman,
            // JANGAN block user.
            val currentVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
            } catch (_: Exception) {
                "999999" // gagal baca versi sendiri -- jangan sampai nge-block orang
            }
            currentVersionName = currentVersion
            val update = GithubUpdateChecker.checkForUpdate(currentVersion)
            latestUpdateInfo = update
            needsUpdate = update != null

            // Kalau lagi diblokir force update, gak perlu buang-buang request
            // buat prefetch homepage -- toh ZenimeAppNavHost gak bakal di-compose.
            if (needsUpdate != true) {
                repository.getHome().collect { }
            }
        }

        // isRetry: true selalu (bukan cuma pas tombol retry) -- SENGAJA
        // motong cache 1 jam RemoteConfigManager.refresh() di sini. Kalau
        // pakai refresh() biasa, app yang di-UPDATE (bukan install ulang)
        // bakal ngewarisin cache Remote Config dari sesi sebelumnya dan
        // BISA SKIP FETCH BARU sampai 1 jam -- artinya toggle
        // maintenance_mode di Console gak langsung kepake pas app dibuka.
        // forceRefresh() di cold start mahalnya cuma 1 request tambahan
        // per buka app (jauh di bawah quota Remote Config), harga yang
        // wajar buat kill-switch yang harus REAL-TIME.
        lifecycleScope.launch { checkAppState(isRetry = true) }

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
                when {
                    isMaintenanceMode -> {
                        MaintenanceScreen(
                            title = RemoteConfigManager.maintenanceTitle(),
                            message = RemoteConfigManager.maintenanceMessage(),
                            onRetry = {
                                lifecycleScope.launch { checkAppState(isRetry = true) }
                            }
                        )
                    }
                    needsUpdate == true -> {
                        val downloadState by apkDownloader.state.collectAsState()
                        val downloadUrl = latestUpdateInfo?.downloadUrl.orEmpty()
                        ForceUpdateScreen(
                            currentVersion = currentVersionName,
                            latestVersion = latestUpdateInfo?.tagName.orEmpty().trimStart('v', 'V'),
                            releaseNotes = latestUpdateInfo?.releaseBody.orEmpty(),
                            downloadState = downloadState,
                            onDownloadClick = {
                                apkDownloader.startDownload(downloadUrl)
                            },
                            onInstallClick = {
                                (downloadState as? DownloadState.Downloaded)?.let {
                                    apkDownloader.launchInstall(it.fileUri)
                                }
                            },
                            onRetryClick = {
                                apkDownloader.startDownload(downloadUrl)
                            }
                        )
                    }
                    // false = versi aman & gak maintenance -> app normal.
                    // null = masih ngecek Remote Config/GitHub -> blank
                    // sebentar (biasanya cuma sekejap, gak pakai splash
                    // animasi lagi supaya gak dobel).
                    needsUpdate == false -> {
                        // Box(fillMaxSize) supaya pop up pengumuman bisa jadi
                        // overlay di ATAS seluruh app (scrim + kartu beranimasi).
                        // Sengaja di dalam cabang ini -- jadi gak pernah muncul
                        // di atas MaintenanceScreen / ForceUpdateScreen.
                        Box(modifier = Modifier.fillMaxSize()) {
                            ZenimeAppNavHost(repository = repository, comicRepository = comicRepository)

                            // popup_repeat = false: dedupe permanen lewat DataStore
                            // (null awalnya = belum kebaca, popup ditahan dulu biar
                            // gak kedip). popup_repeat = true: dedupe cuma selama
                            // sesi app ini (memori), jadi muncul lagi tiap app dibuka.
                            val savedSeenPopupId by userPrefs.lastSeenPopupIdFlow
                                .collectAsStateWithLifecycle(initialValue = null as String?)
                            val popup = announcementPopup
                            val seenId = if (popup?.repeat == true) sessionSeenPopupId else savedSeenPopupId
                            AnnouncementPopupHost(
                                popup = popup,
                                lastSeenId = seenId,
                                onDismiss = { id ->
                                    sessionSeenPopupId = id
                                    lifecycleScope.launch { userPrefs.setLastSeenPopupId(id) }
                                }
                            )
                        }
                    }
                    else -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Real-time: begitu kamu Publish perubahan popup_* di Firebase
        // Console, Firebase nge-push ke app yang lagi kebuka -- popup
        // muncul/hilang tanpa user perlu restart app.
        configListener?.remove()
        configListener = RemoteConfigManager.listenRealtime {
            announcementPopup = RemoteConfigManager.currentPopup()
        }
    }

    override fun onStop() {
        configListener?.remove()
        configListener = null
        super.onStop()
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
