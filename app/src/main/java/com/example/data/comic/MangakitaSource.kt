package com.example.data.comic

import com.example.data.api.MangakitaApi
import com.example.data.model.BacakomikChapterNavigation
import com.example.data.model.BacakomikChapterRef
import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetail
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikGenreRef
import com.example.data.model.BacakomikListItem
import com.example.data.model.BacakomikListResponse
import com.example.data.model.MkItem
import com.example.data.model.MkListResponse
import java.net.URLEncoder

class MangakitaSource(private val api: MangakitaApi) : ComicSource {

    override val id = ComicSourceId.MANGAKITA

    override val tabs = listOf(
        ComicTab("latest", "Terbaru"),
        ComicTab("popular", "Populer"),
        ComicTab("releases", "Rilis Terbaru"),
        ComicTab("projects", "Project"),
        ComicTab("all", "Semua"),
        ComicTab("recommend", "Rekomendasi"),
        ComicTab("ongoing", "Ongoing"),
        ComicTab("completed", "Tamat"),
        ComicTab("manga", "Manga"),
        ComicTab("manhwa", "Manhwa"),
        ComicTab("manhua", "Manhua")
    )

    override suspend fun browse(tabId: String, page: Int): BacakomikListResponse = when (tabId) {
        "latest" -> api.getList("update", null, null, page).toList()
        "popular" -> api.getList("popular", null, null, page).toList()
        "ongoing" -> api.getList(null, "ongoing", null, page).toList()
        "completed" -> api.getList(null, "completed", null, page).toList()
        "manga", "manhwa", "manhua" -> api.getList(null, null, tabId, page).toList()
        "projects" -> (if (page > 1) api.getProjectsPaged(page) else api.getProjects()).toList()
        "all" -> (if (page > 1) api.getAllPaged(page) else api.getAll()).toList()
        // Endpoint tanpa paginasi -- cuma halaman 1.
        "releases" -> flatList(api.getHome().latestReleases, page)
        "recommend" -> flatList(api.getRecommendations().recommendations?.values?.flatten(), page)
        else -> BacakomikListResponse(success = true, komikList = emptyList(), hasNextPage = false, currentPage = page)
    }

    override suspend fun search(query: String, page: Int): BacakomikListResponse {
        // Query dikirim polos; Retrofit yang meng-encode (spasi -> %20).
        return (if (page > 1) api.searchPaged(query, page) else api.search(query)).toList()
    }

    override suspend fun detail(slug: String): BacakomikDetail {
        val d = api.getDetail(slug).details ?: throw IllegalStateException("Detail komik tidak ditemukan")
        val chapters = d.chapters.orEmpty()
            .filter { !it.slug.isNullOrBlank() }
            .distinctBy { it.slug }
            .map {
                BacakomikChapterRef(
                    title = it.title.orEmpty(),
                    slug = it.slug.orEmpty(),
                    date = it.date?.take(10)
                )
            }
        // API kadang nyelipin "Chapter 0" palsu -- buang kalau ada chapter asli.
        val real = chapters.filter { !it.title.trim().equals("Chapter 0", ignoreCase = true) }
        return BacakomikDetail(
            title = d.title,
            cover = d.image,
            rating = d.rating?.takeIf { it.isNotBlank() && it != "0.00" },
            status = d.info?.status?.takeIf { it.isNotBlank() },
            type = d.info?.type?.takeIf { it.isNotBlank() },
            synopsis = d.synopsis,
            genres = d.genres.orEmpty().map { BacakomikGenreRef(title = it, slug = it) },
            chapters = real.ifEmpty { chapters }
        )
    }

    override suspend fun chapter(comicSlug: String, chapterSlug: String): BacakomikChapterResponse {
        // Format slug untuk /chapter/:slug belum pasti -> coba beberapa bentuk.
        val base = chapterSlug.substringBeforeLast('.', chapterSlug)
        val enc = URLEncoder.encode("$comicSlug/$chapterSlug", "UTF-8")
        val candidates = listOf(enc, "$comicSlug/$chapterSlug", chapterSlug, base, "$comicSlug-$base", "$comicSlug-$chapterSlug")
        var lastError: Exception? = null
        for (cand in candidates.distinct()) {
            try {
                val r = api.getChapter(cand)
                val images = r.images.orEmpty()
                if (images.isEmpty()) continue
                // Gambar pertama biasanya cover (wp-content/uploads), bukan halaman chapter.
                val pages = images.filter { !it.contains("/wp-content/uploads/") }.ifEmpty { images }
                return BacakomikChapterResponse(
                    success = true,
                    title = r.title,
                    images = pages,
                    navigation = BacakomikChapterNavigation(
                        next = lastPart(r.navigation?.next),
                        prev = lastPart(r.navigation?.prev)
                    )
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Chapter tidak bisa dimuat")
    }

    override suspend fun genres(): List<BacakomikGenreItem> =
        api.getGenres().genres.orEmpty()
            .filter { !it.slug.isNullOrBlank() }
            .map { BacakomikGenreItem(title = it.name.orEmpty(), slug = it.slug.orEmpty()) }

    override suspend fun byGenre(genreSlug: String, page: Int): BacakomikListResponse =
        (if (page > 1) api.getByGenrePaged(genreSlug, page) else api.getByGenre(genreSlug)).toList()

    // ---- mapper ----

    private fun lastPart(path: String?): String? =
        path?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    private fun MkItem.toListItem(): BacakomikListItem? {
        val s = slug?.takeIf { it.isNotBlank() } ?: return null
        val t = title?.takeIf { it.isNotBlank() } ?: return null
        val chapterList = chapters.orEmpty().filter { !it.title.isNullOrBlank() }
        val latest = chapterList.firstOrNull()?.title
            ?: latestChapter?.takeIf { it.isNotBlank() }?.let { "Chapter $it" }
        return BacakomikListItem(
            title = t,
            slug = s,
            cover = image,
            chapter = latest,
            date = chapterList.firstOrNull()?.time?.take(10),
            rating = rating?.takeIf { it.isNotBlank() && it != "0.00" },
            type = type?.takeIf { it.isNotBlank() }
        )
    }

    private fun MkListResponse.toList(): BacakomikListResponse {
        val raw = mangaList ?: projects ?: results ?: emptyList()
        return BacakomikListResponse(
            success = true,
            komikList = raw.mapNotNull { it.toListItem() }.distinctBy { it.slug },
            hasNextPage = pagination?.hasNextPage ?: false,
            currentPage = pagination?.currentPage ?: 1
        )
    }

    private fun flatList(items: List<MkItem>?, page: Int): BacakomikListResponse =
        BacakomikListResponse(
            success = true,
            komikList = items.orEmpty().mapNotNull { it.toListItem() }.distinctBy { it.slug },
            hasNextPage = false,
            currentPage = page
        )
}
