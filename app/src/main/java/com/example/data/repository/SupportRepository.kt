package com.example.data.repository

import com.example.data.api.SupabaseNetworkModule
import com.example.data.model.TopSupporter

class SupportRepository {

    private val api = SupabaseNetworkModule.api

    /** Ambil daftar Top Support (donatur SociaBuzz), sudah terurut dari nominal terbesar. */
    suspend fun getTopSupporters(): Result<List<TopSupporter>> {
        return try {
            Result.success(api.getTopSupporters().supporters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
