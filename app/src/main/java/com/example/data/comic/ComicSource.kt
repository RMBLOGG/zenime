package com.example.data.comic

import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetail
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikListResponse

/** Satu tab daftar di layar Komik (mis. "Terbaru", "Populer", "Manhwa"). */
data class ComicTab(val id: String, val label: String)

/**
 * Adapter satu sumber komik. Semua sumber dipetakan ke model Bacakomik* yang
 * sudah dipakai UI, jadi layar Komik / Detail / Reader gak perlu tahu
 * sumbernya dari mana.
 *
 * Semua slug di sini POLOS (tanpa awalan sumber). Awalan ditambahkan/dibuang
 * oleh ComicRepository lewat ComicKey.
 */
interface ComicSource {
    val id: ComicSourceId
    val tabs: List<ComicTab>

    suspend fun browse(tabId: String, page: Int): BacakomikListResponse
    suspend fun search(query: String, page: Int): BacakomikListResponse
    suspend fun detail(slug: String): BacakomikDetail
    suspend fun chapter(comicSlug: String, chapterSlug: String): BacakomikChapterResponse
    suspend fun genres(): List<BacakomikGenreItem>
    suspend fun byGenre(genreSlug: String, page: Int): BacakomikListResponse
}
