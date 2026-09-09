package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CoinPackage(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "coin_amount") val coinAmount: Long,
    @Json(name = "bonus_coin") val bonusCoin: Long = 0,
    @Json(name = "price") val price: Long
) {
    /** Total ZCoin yang bakal diterima (pokok + bonus), buat ditampilin di kartu paket. */
    val totalCoin: Long get() = coinAmount + bonusCoin
}

@JsonClass(generateAdapter = true)
data class CoinPackagesResponse(
    @Json(name = "packages") val packages: List<CoinPackage> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CoinBalanceResponse(
    @Json(name = "balance") val balance: Long = 0,
    @Json(name = "message") val message: String? = null
)
