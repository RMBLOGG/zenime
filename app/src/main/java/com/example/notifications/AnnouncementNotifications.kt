package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Semua device yang subscribe topic ini bakal nerima pengumuman yang
 * dikirim ke topic tersebut lewat FCM -- baik manual dari Firebase
 * Console maupun otomatis dari GitHub Actions pas ada release baru
 * (lihat .github/workflows/notify-release.yml di root repo).
 */
const val ANNOUNCEMENT_TOPIC = "release_updates"

/**
 * Topic buat notif episode terbaru. Dikirim otomatis dari GitHub Actions
 * (lihat .github/workflows/check-new-episode.yml) tiap ada anime yang
 * key_time-nya berubah dibanding snapshot terakhir -- blast ke semua
 * device yang subscribe, gak dibedain per-anime/per-favorite.
 */
const val NEW_EPISODE_TOPIC = "new_episode_updates"

const val ANNOUNCEMENT_CHANNEL_ID = "announcements"

/**
 * Channel terpisah dari pengumuman/release, biar user bisa matiin salah
 * satu doang lewat pengaturan notifikasi Android kalau mau (misal males
 * notif episode tapi tetep mau tau ada versi baru, atau sebaliknya).
 */
const val NEW_EPISODE_CHANNEL_ID = "new_episode"

/**
 * Bikin notification channel (wajib buat Android 8+, aman dipanggil
 * berkali-kali -- createNotificationChannel dengan ID yang sama gak
 * ngapa-ngapain kalau udah ada) dan subscribe device ini ke topic-topic
 * pengumuman & episode terbaru. Dipanggil sekali tiap MainActivity.onCreate.
 *
 * TIDAK termasuk minta izin runtime POST_NOTIFICATIONS (Android 13+) --
 * itu harus lewat ActivityResultLauncher di Activity, lihat MainActivity.
 */
fun setupAnnouncementNotifications(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val announcementChannel = NotificationChannel(
            ANNOUNCEMENT_CHANNEL_ID,
            "Pengumuman & Update",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Info rilis versi baru dan pengumuman dari Zenime"
        }
        manager?.createNotificationChannel(announcementChannel)

        val newEpisodeChannel = NotificationChannel(
            NEW_EPISODE_CHANNEL_ID,
            "Episode Terbaru",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notif setiap ada episode baru yang rilis"
        }
        manager?.createNotificationChannel(newEpisodeChannel)
    }

    // subscribeToTopic idempoten -- aman dipanggil tiap app dibuka walau
    // devicenya udah subscribe dari sebelumnya.
    FirebaseMessaging.getInstance().subscribeToTopic(ANNOUNCEMENT_TOPIC)
    FirebaseMessaging.getInstance().subscribeToTopic(NEW_EPISODE_TOPIC)
}
