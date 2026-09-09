package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.model.CoinPackage

class CoinRepository {

    private val api = SupabaseNetworkModule.api

    suspend fun getPackages(): Result<List<CoinPackage>> {
        return try {
            val response = api.getCoinPackages()
            Result.success(response.packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Saldo ZCoin user saat ini; dipanggil pas buka layar ZCoin & abis top up. */
    suspend fun getBalance(firebaseUid: String): Result<Long> {
        return try {
            val response = api.getCoinBalance(mapOf("firebase_uid" to firebaseUid))
            Result.success(response.balance)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
