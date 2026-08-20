package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PremiumPackage(
    @Json(name = "id") val id: String,
    @Json(name = "label") val label: String,
    @Json(name = "duration_text") val durationText: String,
    @Json(name = "price") val price: Long,
    @Json(name = "badge") val badge: String? = null
)

@JsonClass(generateAdapter = true)
data class PremiumPackagesResponse(
    @Json(name = "packages") val packages: List<PremiumPackage> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ZenimeCodeResponse(
    @Json(name = "zenime_code") val zenimeCode: String? = null,
    @Json(name = "message") val message: String? = null
)
