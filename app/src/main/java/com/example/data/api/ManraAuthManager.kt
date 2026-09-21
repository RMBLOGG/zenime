package com.example.data.api

import android.content.Context
import com.example.data.model.RawEnvelope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.runBlocking

/**
 * Device-auth khusus buat endpoint Manra (data/manra/*), niru alur yang
 * ditemukan di StartActivity app Animein asli (hasil decompile APK):
 *
 *   1. Ambil token Firebase Cloud Messaging.
 *   2. POST 3/2/user/auth/device dengan field {token, apk} -- TANPA perlu
 *      username/password, ini murni device/guest auth.
 *   3. Respons-nya (diasumsikan sama bentuknya kayak respons auth/login &
 *      auth/google, yang paling jelas kebaca di decompile) berisi
 *      data.user.id dan data.user.key_client.
 *   4. Dua nilai itu + versi APK ditempelin ke SETIAP request data/manra/*
 *      (sebagai query param buat GET, field form buat POST) -- niru
 *      CommonParamsInterceptor yang di app asli jalan global ke semua
 *      request, tapi di sini sengaja dibatasin cuma ke path Manra biar gak
 *      ganggu endpoint lain yang udah jalan normal tanpa param ini.
 *
 * BELUM TERVERIFIKASI ke server asli -- terutama bentuk field response di
 * parseAuthResponse(). Kalau ternyata field-nya beda (atau ternyata bukan
 * dibungkus objek "user"), tinggal sesuaikan fungsi itu; sisanya (caching,
 * pemasangan ke request) sudah gak perlu diubah.
 */
object ManraAuthManager {

    private const val PREFS_NAME = "manra_auth"
    private const val KEY_ID_USER = "id_user"
    private const val KEY_KEY_CLIENT = "key_client"

    @Volatile private var cachedIdUser: String? = null
    @Volatile private var cachedKeyClient: String? = null
    @Volatile private var loadedFromPrefs = false
    @Volatile private var authAttemptedThisSession = false
    private val lock = Any()

    private fun appContext(): Context? = try {
        FirebaseApp.getInstance().applicationContext
    } catch (e: Exception) {
        null // FirebaseApp belum ke-init (mis. dipanggil kepagian) -- gagal diam-diam
    }

    private fun prefs() = appContext()?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadFromPrefsIfNeeded() {
        if (loadedFromPrefs) return
        val p = prefs() ?: return
        cachedIdUser = p.getString(KEY_ID_USER, null)
        cachedKeyClient = p.getString(KEY_KEY_CLIENT, null)
        loadedFromPrefs = true
    }

    private fun saveCache(idUser: String, keyClient: String) {
        cachedIdUser = idUser
        cachedKeyClient = keyClient
        prefs()?.edit()
            ?.putString(KEY_ID_USER, idUser)
            ?.putString(KEY_KEY_CLIENT, keyClient)
            ?.apply()
    }

    fun currentIdUser(): String? {
        loadFromPrefsIfNeeded()
        return cachedIdUser
    }

    fun currentKeyClient(): String? {
        loadFromPrefsIfNeeded()
        return cachedKeyClient
    }

    fun apkVersion(): String = try {
        val ctx = appContext()
        if (ctx != null) {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
        } else "-"
    } catch (e: Exception) {
        "-"
    }

    /**
     * Panggil dari thread background (interceptor OkHttp udah otomatis di
     * thread terpisah, jadi aman blocking di sini). Cuma beneran nembak
     * server sekali per proses app -- setelah berhasil, nilainya nempel di
     * SharedPreferences dan gak perlu device-auth ulang lagi.
     */
    fun ensureAuthBlocking() {
        loadFromPrefsIfNeeded()
        if (cachedIdUser != null && cachedKeyClient != null) return
        synchronized(lock) {
            if (cachedIdUser != null && cachedKeyClient != null) return
            if (authAttemptedThisSession) return
            authAttemptedThisSession = true
            try {
                val token = Tasks.await(FirebaseMessaging.getInstance().token)
                val params = mapOf(
                    "token" to token,
                    "apk" to apkVersion()
                )
                val envelope = runBlocking { NetworkModule.api.authDeviceRaw(params) }
                parseAuthResponse(envelope)
            } catch (e: Exception) {
                // Gagal diam-diam: request Manra berikutnya tetap jalan
                // tanpa id_user/key_client (kemungkinan ditolak server
                // dengan pesan generik, sama kayak sebelum device-auth ini
                // dipasang). authAttemptedThisSession tetap true biar gak
                // nyoba ulang berkali-kali tiap section Manra di-load.
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAuthResponse(env: RawEnvelope) {
        val data = env.data ?: return
        val userObj = (data["user"] as? Map<String, Any?>) ?: data
        val id = userObj["id"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val keyClient = userObj["key_client"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        if (id != null && keyClient != null) {
            saveCache(id, keyClient)
        }
    }
}
