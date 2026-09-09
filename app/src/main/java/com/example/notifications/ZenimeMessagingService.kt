package com.example.notifications

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Kalau app lagi BACKGROUND/ketutup total, notifikasi dengan payload
 * "notification" (bukan cuma "data") ditampilin OTOMATIS sama sistem
 * Android sendiri -- onMessageReceived di sini SAMA SEKALI GAK
 * dipanggil buat kasus itu.
 *
 * onMessageReceived cuma jalan pas app lagi kebuka/foreground -- makanya
 * di sini kita bikin manual notification-nya sendiri, biar tetep muncul
 * kayak notif biasa walau lagi buka Zenime.
 */
class ZenimeMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        val notification = NotificationCompat.Builder(this, ANNOUNCEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_announcement)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Channel-nya udah pasti dibikin dari setupAnnouncementNotifications
        // pas app pertama dibuka -- tapi jaga-jaga kalau service ini
        // somehow ke-trigger sebelum itu (harusnya gak mungkin, tapi
        // notify() bakal silent-fail di beberapa OEM kalau channel belum ada).
        setupAnnouncementNotifications(this)

        NotificationManagerCompat.from(this).notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }

    // Gak perlu simpen/kirim token ke server manapun -- pengumuman
    // dikirim ke TOPIC (release_updates), bukan ke token per-device,
    // jadi token baru otomatis ke-cover pas subscribeToTopic dipanggil
    // lagi di onCreate MainActivity berikutnya.
    override fun onNewToken(token: String) = Unit
}
