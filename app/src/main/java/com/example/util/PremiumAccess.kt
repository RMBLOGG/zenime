package com.example.util

/** Jumlah episode TERBARU (index paling tinggi) yang dikunci buat non-premium. */
const val LOCKED_LATEST_EPISODES_COUNT = 2

/** Ekstrak angka index dari string index episode, misal "12" -> 12. */
fun episodeIndexValue(episodeIndex: String?): Int? =
    episodeIndex?.trim()?.toIntOrNull()
        ?: Regex("\\d+").find(episodeIndex.orEmpty())?.value?.toIntOrNull()

/**
 * Cari total episode (index tertinggi) dari daftar index string yang ada,
 * dipakai buat nentuin mana [LOCKED_LATEST_EPISODES_COUNT] episode paling
 * baru yang harus dikunci. Kalau gak ada index yang kebaca sama sekali,
 * fallback ke jumlah item di list.
 */
fun latestEpisodeIndex(episodeIndexes: List<String?>): Int =
    episodeIndexes.mapNotNull(::episodeIndexValue).maxOrNull() ?: episodeIndexes.size

/**
 * Semua episode LAMA bebas ditonton non-premium. Cuma
 * [LOCKED_LATEST_EPISODES_COUNT] episode paling baru (index tertinggi dari
 * [totalEpisodes]) yang dikunci khusus Premium. episodeIndex yang null atau
 * gak kebaca angka, atau [totalEpisodes] yang belum kebaca (<= 0), dianggap
 * TIDAK terkunci -- daripada salah lock gara-gara gagal parse / data belum siap.
 */
fun isEpisodeLocked(episodeIndex: String?, totalEpisodes: Int, isPremium: Boolean): Boolean {
    if (isPremium) return false
    if (totalEpisodes <= 0) return false
    val index = episodeIndexValue(episodeIndex) ?: return false
    return index > totalEpisodes - LOCKED_LATEST_EPISODES_COUNT
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
 * termasuk yang lama (unlocked) ataupun kualitasnya rendah.
 */
fun isDownloadAllowed(isPremium: Boolean): Boolean = isPremium
