package com.example.util

/** Jumlah episode gratis (episode 1 s/d nilai ini) buat non-premium. */
const val FREE_EPISODE_LIMIT = 4

/**
 * Episode 1 s/d [FREE_EPISODE_LIMIT] BEBAS ditonton non-premium (trial).
 * Episode selanjutnya cuma bisa ditonton member Premium. episodeIndex
 * yang null atau gak kebaca angka dianggap TIDAK terkunci -- daripada
 * salah lock gara-gara gagal parse.
 */
fun isEpisodeLocked(episodeIndex: String?, isPremium: Boolean): Boolean {
    if (isPremium) return false
    val index = episodeIndex?.trim()?.toIntOrNull()
        ?: Regex("\\d+").find(episodeIndex.orEmpty())?.value?.toIntOrNull()
        ?: return false
    return index > FREE_EPISODE_LIMIT
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

/**
 * Fitur download buat nonton offline khusus premium -- non-premium sama
 * sekali gak boleh download episode manapun, gak peduli episode itu
 * termasuk yang gratis (1-4) ataupun kualitasnya rendah.
 */
fun isDownloadAllowed(isPremium: Boolean): Boolean = isPremium
