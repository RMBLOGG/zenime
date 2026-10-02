package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Cache status premium terakhir yang berhasil diambil dari server, buat
 * fallback pas [PremiumGate] gagal cek live (misal lagi offline).
 *
 * SENGAJA gak dipakai buat nge-bypass premium selamanya -- dua lapis
 * proteksi:
 *  1. [expiresAt]: tanggal premium user itu sendiri habis (dari server).
 *     Kalau udah lewat, cache dianggap non-premium walau [isPremium]
 *     tersimpan true -- jadi user yang premiumnya abis TETAP keblokir
 *     walau offline, gak perlu koneksi buat mastiin itu.
 *  2. [CACHE_TTL_MS]: cache cuma valid dipakai sebagai fallback offline
 *     selama beberapa hari sejak sukses cek terakhir ([lastCheckedAt]).
 *     Ini jaga-jaga kalau ada revoke manual dari admin (refund/chargeback)
 *     yang gak tercermin di [expiresAt] lama. Lewat dari TTL ini, gagal
 *     cek = tetap Blocked (behavior lama).
 *
 * Reuse Context.dataStore yang sama kayak [UserPreferencesRepository]
 * (satu file preferences per app, bukan per-fitur).
 */
class PremiumStatusCache(private val context: Context) {

    private object Keys {
        val IS_PREMIUM = booleanPreferencesKey("premium_cache_is_premium")
        val EXPIRES_AT = stringPreferencesKey("premium_cache_expires_at")
        val LAST_CHECKED_AT = longPreferencesKey("premium_cache_last_checked_at")
        val SIG = stringPreferencesKey("premium_cache_sig")
    }

    companion object {
        // Maksimal umur cache buat dipakai sebagai fallback offline.
        private val CACHE_TTL_MS = java.time.Duration.ofDays(3).toMillis()

        // Cache ditandatangani HMAC pakai key AndroidKeyStore (gak bisa diekspor dari
        // device). Edit manual file DataStore (root / adb restore) jadi percuma:
        // tanda tangan gak cocok -> cache dianggap gak ada -> non-premium.
        private const val KEY_ALIAS = "zenime_premium_cache_hmac"

        private fun hmacKey(): javax.crypto.SecretKey {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
            val gen = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                "AndroidKeyStore"
            )
            gen.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN
                ).build()
            )
            return gen.generateKey()
        }

        private fun sign(isPremium: Boolean, expiresAt: String?, checkedAt: Long): String = try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(hmacKey())
            mac.doFinal("$isPremium|${expiresAt.orEmpty()}|$checkedAt".toByteArray())
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /** Simpan hasil sukses cek premium terbaru dari server. */
    suspend fun save(isPremium: Boolean, expiresAt: String?) {
        val now = System.currentTimeMillis()
        val sig = sign(isPremium, expiresAt, now)
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_PREMIUM] = isPremium
            if (expiresAt != null) {
                prefs[Keys.EXPIRES_AT] = expiresAt
            } else {
                prefs.remove(Keys.EXPIRES_AT)
            }
            prefs[Keys.LAST_CHECKED_AT] = now
            prefs[Keys.SIG] = sig
        }
    }

    /**
     * Status premium buat dipakai SAAT OFFLINE ATAU JARINGAN GAGAL. Balikin
     * null kalau cache gak ada, udah kadaluarsa TTL-nya, atau kalau
     * [expiresAt] tersimpan udah lewat -- di semua kasus itu pemanggil
     * WAJIB anggap Blocked (jangan default ke true).
     */
    suspend fun getValidOfflineStatus(): Boolean? {
        val prefs = context.dataStore.data.first()
        val isPremium = prefs[Keys.IS_PREMIUM] ?: return null
        val lastCheckedAt = prefs[Keys.LAST_CHECKED_AT] ?: return null

        if (!isPremium) return false

        // Lapis 0: tanda tangan harus cocok, kalau gak (diedit / cache versi lama) = gak valid.
        val storedSig = prefs[Keys.SIG].orEmpty()
        val expectedSig = sign(true, prefs[Keys.EXPIRES_AT], lastCheckedAt)
        if (storedSig.isEmpty() || expectedSig.isEmpty() ||
            !java.security.MessageDigest.isEqual(storedSig.toByteArray(), expectedSig.toByteArray())
        ) return null

        // Lapis 2: cache basi (lebih lama dari TTL sejak sukses cek terakhir).
        val cacheAge = System.currentTimeMillis() - lastCheckedAt
        if (cacheAge > CACHE_TTL_MS) return null

        // Lapis 1: expiresAt asli si user udah lewat -> non-premium,
        // walaupun cache-nya sendiri masih "segar".
        val expiresAtIso = prefs[Keys.EXPIRES_AT]
        if (expiresAtIso != null) {
            val expiresAt = try {
                Instant.parse(expiresAtIso)
            } catch (e: Exception) {
                null
            }
            if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
                return false
            }
        }

        return true
    }
}
