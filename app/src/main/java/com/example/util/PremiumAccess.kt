package com.example.util

/**
 * Semua episode BEBAS ditonton non-premium (gak ada batas trial 1-3
 * lagi) -- akses nonton bukan lagi benefit Premium. Benefit Premium yang
 * masih berlaku: bebas iklan ([PlayerScreen] cek isPremium langsung),
 * resolusi di atas [NON_PREMIUM_MAX_QUALITY_P] (lihat isQualityLocked),
 * dan download offline (lihat isDownloadAllowed).
 */
fun isEpisodeLocked(episodeIndex: String?, isPremium: Boolean): Boolean = false

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

/**
 * Fitur download buat nonton offline khusus premium -- non-premium sama
 * sekali gak boleh download episode manapun, gak peduli episode itu
 * termasuk yang gratis (1-3) ataupun kualitasnya rendah.
 */
fun isDownloadAllowed(isPremium: Boolean): Boolean = isPremium
