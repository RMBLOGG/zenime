package com.example

import android.app.Application

/**
 * Cuma buat nyimpen [applicationContext] yang bisa diakses dari mana aja
 * (termasuk dari repository yang gak dikasih Context lewat constructor) --
 * dipake [com.example.data.repository.PublicProfileRepository] buat nampilin
 * Toast pas sync Favorit/Riwayat ke Supabase gagal, biar errornya kelihatan
 * LANGSUNG di HP tanpa perlu adb/Logcat/PC.
 */
class ZenimeApp : Application() {
    companion object {
        lateinit var instance: ZenimeApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
