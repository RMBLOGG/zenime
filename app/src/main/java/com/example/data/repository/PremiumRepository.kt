package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.local.PremiumStatusCache
import com.example.data.model.PremiumPackage

class PremiumRepository(
    // Nullable & default null biar caller lama (yang belum punya Context
    // gampang diakses, misal dari tempat yang cuma pegang Application-less
    // scope) tetap kompilasi -- tanpa cache, checkPremiumStatus tetap jalan
    // seperti biasa, cuma gak ada fallback offline.
    private val statusCache: PremiumStatusCache? = null
) {

    private val api = SupabaseNetworkModule.api

    suspend fun getPackages(): Result<List<PremiumPackage>> {
        return try {
            val response = api.getPremiumPackages()
            Result.success(response.packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Ambil zenime_code user; server yang generate otomatis kalau belum ada. */
    suspend fun getZenimeCode(firebaseUid: String): Result<String> {
        return try {
            val response = api.getZenimeCode(mapOf("firebase_uid" to firebaseUid))
            val code = response.zenimeCode
            if (code != null) {
                Result.success(code)
            } else {
                Result.failure(IllegalStateException(response.message ?: "Gagal mengambil kode akun"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ambil zenime_code + user_number (ID urut ala Aniku) sekaligus dalam
     * satu request -- dipakai khusus di kartu profil Beranda. Screen lain
     * (Premium/Coin) yang cuma butuh kode tetap pakai [getZenimeCode].
     */
    suspend fun getProfileIdentity(firebaseUid: String): Result<Pair<String?, Long?>> {
        return try {
            val response = api.getZenimeCode(mapOf("firebase_uid" to firebaseUid))
            Result.success(response.zenimeCode to response.userNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cek status premium user; dipanggil sebelum nonton buat gating.
     * Setiap sukses, hasilnya ditulis ke [statusCache] (kalau ada) buat
     * jadi fallback pas nanti gagal cek karena offline -- lihat
     * [PremiumStatusCache] buat aturan masa berlaku & expiresAt-nya.
     */
    suspend fun checkPremiumStatus(firebaseUid: String): Result<PremiumStatus> {
        return try {
            val response = api.checkPremiumStatus(mapOf("firebase_uid" to firebaseUid))
            statusCache?.save(response.isPremium, response.expiresAt)
            Result.success(PremiumStatus(response.isPremium, response.expiresAt))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class PremiumStatus(
    val isPremium: Boolean,
    val expiresAt: String?
)
