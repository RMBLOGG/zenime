package com.example.util

/**
 * Episode 1-3 GRATIS buat non-premium (trial), episode 4 ke atas
 * (termasuk yang paling baru) yang dikunci -- premium wajib buat lanjut
 * nonton lebih jauh dari situ.
 */
private val FREE_EPISODE_NUMBERS = 1..3

fun isEpisodeLocked(episodeIndex: String?, isPremium: Boolean): Boolean {
    if (isPremium) return false
    val number = episodeIndex?.trim()?.toIntOrNull() ?: return false
    return number !in FREE_EPISODE_NUMBERS
}

/** Kualitas maksimal (dalam "p", misal 480 = 480p) yang boleh diputer non-premium. */
const val NON_PREMIUM_MAX_QUALITY_P = 480

/** Ekstrak angka resolusi dari label kualitas server, misal "1080p" -> 1080. */
fun qualityValueP(quality: String?): Int? =
    Regex("\\d+").find(quality.orEmpty())?.value?.toIntOrNull()

/**
 * Kualitas dianggap locked cuma kalau angkanya kebaca DAN di atas batas.
 * Label yang gak punya angka (misal "HD", "Auto") dibiarin lolos apa
 * adanya -- daripada salah lock gara-gara gagal parse.
 */
fun isQualityLocked(quality: String?, isPremium: Boolean): Boolean {
    if (isPremium) return false
    val value = qualityValueP(quality) ?: return false
    return value > NON_PREMIUM_MAX_QUALITY_P
}
