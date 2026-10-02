package com.example.data.api

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Token premium bertanda tangan SERVER (edge function zenime-premium-token).
 *
 * Ini inti anti-mod: keputusan "boleh nonton donghua atau nggak" diambil server,
 * bukan flag isPremium di app. APK mod yang maksa isPremium = true tetap gak
 * punya token valid (server cuma ngasih ke akun yang beneran premium), jadi
 * endpoint episode/video-source donghua nolak. Token cuma disimpan di memori
 * (gak ditulis ke disk) dan umurnya pendek.
 */
object PremiumTokenManager {

    @Volatile private var token: String? = null
    @Volatile private var tokenUid: String? = null
    @Volatile private var expiresAtMs: Long = 0L
    private val mutex = Mutex()

    fun invalidate() {
        token = null
        tokenUid = null
        expiresAtMs = 0L
    }

    /** Token valid, atau null kalau belum login / bukan premium / gagal ambil. */
    suspend fun get(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        cached(user.uid)?.let { return it }
        return mutex.withLock {
            cached(user.uid)?.let { return@withLock it }
            try {
                val idToken = user.getIdToken(false).await()?.token ?: return@withLock null
                val res = SupabaseNetworkModule.api.getPremiumToken("Bearer $idToken")
                val t = res.token
                if (res.isPremium && !t.isNullOrBlank()) {
                    token = t
                    tokenUid = user.uid
                    expiresAtMs = System.currentTimeMillis() + (res.expiresIn ?: 1800L) * 1000L
                    t
                } else {
                    invalidate()
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun cached(uid: String): String? {
        val t = token ?: return null
        // Sisakan 60 detik biar gak kepake pas udah mau kadaluarsa.
        return if (tokenUid == uid && System.currentTimeMillis() < expiresAtMs - 60_000L) t else null
    }
}
