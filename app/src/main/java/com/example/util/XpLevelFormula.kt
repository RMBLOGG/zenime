package com.example.util

/**
 * Formula level XP nonton, SAMA PERSIS kayak yang dihitung di RPC `add_watch_xp`
 * (Supabase). Kalau formula di server diubah, ubah juga di sini biar progress
 * bar di client gak salah tampil.
 *
 * total_xp_needed(N) = 100 * N * (N-1)  -- kurva landai, level awal cepet naik.
 */
object XpLevelFormula {

    /** Total XP kumulatif minimum buat mencapai level [n]. Level 1 = 0 XP. */
    fun totalXpForLevel(n: Int): Long = 100L * n * (n - 1)

    /** Info progress buat ditampilin di progress bar: XP di level ini, kebutuhan level ini & berikutnya. */
    fun progress(totalXp: Long, level: Int): XpProgressInfo {
        val currentLevelFloor = totalXpForLevel(level)
        val nextLevelFloor = totalXpForLevel(level + 1)
        val xpIntoLevel = (totalXp - currentLevelFloor).coerceAtLeast(0)
        val xpNeededForLevel = (nextLevelFloor - currentLevelFloor).coerceAtLeast(1)
        return XpProgressInfo(
            xpIntoLevel = xpIntoLevel,
            xpNeededForLevel = xpNeededForLevel,
            fraction = (xpIntoLevel.toFloat() / xpNeededForLevel.toFloat()).coerceIn(0f, 1f)
        )
    }
}

data class XpProgressInfo(
    val xpIntoLevel: Long,
    val xpNeededForLevel: Long,
    val fraction: Float
)
