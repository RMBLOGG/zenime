package com.example.data.comic

import com.example.data.api.WestmangaApi
import com.example.data.model.BacakomikChapterNavigation
import com.example.data.model.BacakomikChapterRef
import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetail
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikGenreRef
import com.example.data.model.BacakomikListItem
import com.example.data.model.BacakomikListResponse
import com.example.data.model.WmChapter
import com.example.data.model.WmItem
import com.example.data.model.WmListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WestmangaSource(private val api: WestmangaApi) : ComicSource {

    override val id = ComicSourceId.WESTMANGA

    // id tab = nama endpoint /westmanga/{id}
    override val tabs = listOf(
        ComicTab("latest", "Terbaru"),
        ComicTab("popular", "Populer"),
        ComicTab("ongoing", "Ongoing"),
        ComicTab("completed", "Tamat"),
        ComicTab("list", "Semua"),
        ComicTab("manga", "Manga"),
        ComicTab("manhwa", "Manhwa"),
        ComicTab("manhua", "Manhua"),
        ComicTab("az", "A-Z"),
        ComicTab("za", "Z-A"),
        ComicTab("added", "Baru Ditambah"),
        ComicTab("colored", "Berwarna"),
        ComicTab("uncolored", "Hitam Putih"),
        ComicTab("projects", "Project"),
        ComicTab("others", "Lainnya")
    )

    override suspend fun browse(tabId: String, page: Int): BacakomikListResponse =
        api.browse(tabId, page).toList()

    override suspend fun search(query: String, page: Int): BacakomikListResponse =
        api.search(query, page).toList()

    override suspend fun detail(slug: String): BacakomikDetail {
        val d = api.getDetail(slug).data ?: throw IllegalStateException("Detail komik tidak ditemukan")
        val chapters = d.chapters.orEmpty()
            .filter { !it.slug.isNullOrBlank() }
            .distinctBy { it.slug }
            .map {
                BacakomikChapterRef(
                    title = "Chapter ${it.number.orEmpty()}".trim(),
                    slug = it.slug.orEmpty(),
                    date = it.updatedAt?.formatted?.let(::dateOnly) ?: it.createdAt?.formatted?.let(::dateOnly)
                )
            }
        return BacakomikDetail(
            title = d.title,
            cover = d.cover,
            rating = d.rating?.takeIf { it > 0 }?.toString(),
            status = d.status,
            type = typeOf(d.countryId),
            author = d.author,
            synopsis = d.sinopsis,
            genres = d.genres.orEmpty().mapNotNull { g ->
                g.name?.let { BacakomikGenreRef(title = it, slug = g.id?.toString().orEmpty()) }
            },
            chapters = chapters
        )
    }

    override suspend fun chapter(comicSlug: String, chapterSlug: String): BacakomikChapterResponse {
        val raw = withContext(Dispatchers.IO) { api.getChapterRaw(chapterSlug).string() }
        val data = JSONObject(raw).optJSONObject("data")
            ?: throw IllegalStateException("Respons chapter tidak valid")
        val images = findImages(data) ?: throw IllegalStateException("Gambar chapter tidak ditemukan")

        // Daftar chapter ikut di respons (terbaru dulu) -> prev/next dihitung dari sini.
        val chapters = data.optJSONArray("chapters")
        val currentId = data.optLong("id", -1L)
        var idx = -1
        var slugs = emptyList<Pair<String, String>>() // slug to number
        if (chapters != null) {
            slugs = (0 until chapters.length()).mapNotNull { i ->
                chapters.optJSONObject(i)?.let { it.optString("slug") to it.optString("number") }
            }
            for (i in 0 until chapters.length()) {
                val c = chapters.optJSONObject(i) ?: continue
                if (c.optLong("id", -2L) == currentId || c.optString("slug") == chapterSlug) { idx = i; break }
            }
        }
        val next = if (idx > 0) slugs.getOrNull(idx - 1)?.first else null
        val prev = if (idx >= 0) slugs.getOrNull(idx + 1)?.first else null
        val comicTitle = data.optJSONObject("content")?.optString("title").orEmpty()
        val number = slugs.getOrNull(idx)?.second.orEmpty()
        return BacakomikChapterResponse(
            success = true,
            title = "$comicTitle Chapter $number".trim().takeIf { comicTitle.isNotBlank() || number.isNotBlank() },
            images = images,
            navigation = BacakomikChapterNavigation(next = next?.ifBlank { null }, prev = prev?.ifBlank { null })
        )
    }

    override suspend fun genres(): List<BacakomikGenreItem> =
        api.getGenres().data.orEmpty().mapNotNull { g ->
            val id = g.id ?: return@mapNotNull null
            BacakomikGenreItem(title = g.name.orEmpty(), slug = id.toString())
        }

    // genreSlug = id genre; boleh beberapa id dipisah koma ("13,344") -> genres-filter.
    override suspend fun byGenre(genreSlug: String, page: Int): BacakomikListResponse =
        (if (genreSlug.contains(',')) api.getByGenres(genreSlug, page) else api.getByGenre(genreSlug, page)).toList()

    // ---- mapper ----

    private fun typeOf(country: String?): String? = when (country) {
        "KR" -> "Manhwa"
        "CN" -> "Manhua"
        "JP" -> "Manga"
        else -> null
    }

    // "30 Sep 2026 03:25" -> "30 Sep 2026"
    private fun dateOnly(formatted: String): String = formatted.split(" ").take(3).joinToString(" ")

    private fun WmItem.toListItem(): BacakomikListItem? {
        val s = slug?.takeIf { it.isNotBlank() } ?: return null
        val t = title?.takeIf { it.isNotBlank() } ?: return null
        val newest: WmChapter? = lastChapters.orEmpty().maxByOrNull { it.updatedAt?.time ?: 0L }
        return BacakomikListItem(
            title = t,
            slug = s,
            cover = cover,
            chapter = newest?.number?.let { "Chapter $it" },
            date = newest?.updatedAt?.formatted?.let(::dateOnly),
            rating = rating?.takeIf { it > 0 }?.toString(),
            type = typeOf(countryId)
        )
    }

    private fun WmListResponse.toList(): BacakomikListResponse {
        val current = pagination?.currentPage ?: 1
        val last = pagination?.lastPage ?: 1
        return BacakomikListResponse(
            success = true,
            komikList = data.orEmpty().mapNotNull { it.toListItem() }.distinctBy { it.slug },
            hasNextPage = current < last,
            currentPage = current
        )
    }

    // Cari array URL gambar di struktur JSON yang lokasinya belum pasti.
    private fun findImages(node: Any?): List<String>? {
        when (node) {
            is JSONObject -> {
                for (k in listOf("images", "image", "pages", "chapter_images")) {
                    urlList(node.optJSONArray(k))?.let { return it }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findImages(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                urlList(node)?.let { return it }
                for (i in 0 until node.length()) {
                    findImages(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    private fun urlList(arr: JSONArray?): List<String>? {
        if (arr == null || arr.length() == 0) return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.opt(i) as? String ?: return null
            if (!s.startsWith("http")) return null
            out.add(s)
        }
        return out
    }
}
