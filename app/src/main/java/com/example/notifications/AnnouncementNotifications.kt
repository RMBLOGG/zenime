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

const val ANNOUNCEMENT_CHANNEL_ID = "announcements"

/**
 * Bikin notification channel (wajib buat Android 8+, aman dipanggil
 * berkali-kali -- createNotificationChannel dengan ID yang sama gak
 * ngapa-ngapain kalau udah ada) dan subscribe device ini ke topic
 * pengumuman. Dipanggil sekali tiap MainActivity.onCreate.
 *
 * TIDAK termasuk minta izin runtime POST_NOTIFICATIONS (Android 13+) --
 * itu harus lewat ActivityResultLauncher di Activity, lihat MainActivity.
 */
fun setupAnnouncementNotifications(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            ANNOUNCEMENT_CHANNEL_ID,
            "Pengumuman & Update",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Info rilis versi baru dan pengumuman dari Zenime"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    // subscribeToTopic idempoten -- aman dipanggil tiap app dibuka walau
    // devicenya udah subscribe dari sebelumnya.
    FirebaseMessaging.getInstance().subscribeToTopic(ANNOUNCEMENT_TOPIC)
}
