package com.example.data.comic

/**
 * Sumber komik yang tersedia di Zenime. Semuanya lewat API Sanka
 * (https://www.sankavollerei.web.id/comic/{source}/...).
 */
enum class ComicSourceId(val id: String, val label: String) {
    BACAKOMIK("bacakomik", "BacaKomik"),
    MANGAKITA("mangakita", "Mangakita"),
    WESTMANGA("westmanga", "Westmanga")
}

/**
 * Kunci komik = slug yang diberi awalan sumber, supaya slug yang sama di dua
 * sumber berbeda gak bentrok di Room (favorit / progress baca) maupun di
 * route navigasi.
 *
 * BacaKomik sengaja TIDAK diberi awalan, jadi data favorit & progress yang
 * sudah tersimpan sebelum fitur multi-sumber tetap valid tanpa migrasi DB.
 *   "nano-machine"            -> BacaKomik
 *   "mangakita~one-piece"     -> Mangakita
 *   "westmanga~solo-leveling" -> Westmanga
 */
object ComicKey {
    private const val SEP = "~"

    fun encode(source: ComicSourceId, slug: String): String =
        if (source == ComicSourceId.BACAKOMIK) slug else "${source.id}$SEP$slug"

    fun decode(key: String): Pair<ComicSourceId, String> {
        val idx = key.indexOf(SEP)
        if (idx > 0) {
            val prefix = key.substring(0, idx)
            val source = ComicSourceId.values().firstOrNull { it.id == prefix }
            if (source != null) return source to key.substring(idx + 1)
        }
        return ComicSourceId.BACAKOMIK to key
    }
}
