package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.model.PremiumPackage

class PremiumRepository {

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
}
