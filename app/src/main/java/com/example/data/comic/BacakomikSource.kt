package com.example.data.comic

import com.example.data.api.ComicApi
import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetail
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikListResponse

/** Sumber asli (yang sudah ada sebelumnya): cuma dibungkus ke ComicSource. */
class BacakomikSource(private val api: ComicApi) : ComicSource {

    override val id = ComicSourceId.BACAKOMIK

    override val tabs = listOf(
        ComicTab("latest", "Terbaru"),
        ComicTab("popular", "Populer")
    )

    override suspend fun browse(tabId: String, page: Int): BacakomikListResponse =
        if (tabId == "popular") api.getPopular(page) else api.getLatest(page)

    override suspend fun search(query: String, page: Int): BacakomikListResponse =
        api.searchComic(query, page)

    override suspend fun detail(slug: String): BacakomikDetail =
        api.getComicDetail(slug).detail ?: throw IllegalStateException("Detail komik tidak ditemukan")

    override suspend fun chapter(comicSlug: String, chapterSlug: String): BacakomikChapterResponse =
        api.getComicChapter(chapterSlug)

    override suspend fun genres(): List<BacakomikGenreItem> =
        api.getGenres().genres ?: emptyList()

    override suspend fun byGenre(genreSlug: String, page: Int): BacakomikListResponse =
        api.getComicByGenre(genreSlug, page)
}
