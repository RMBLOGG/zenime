package com.example.data.repository

import com.example.data.comic.ComicKey
import com.example.data.comic.ComicSource
import com.example.data.comic.ComicSourceId
import com.example.data.comic.ComicTab
import com.example.data.common.Result
import com.example.data.local.ComicFavoriteEntity
import com.example.data.local.ComicReadingProgressEntity
import com.example.data.local.ZenimeDao
import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetail
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikListResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository komik multi-sumber (BacaKomik, Mangakita, Westmanga).
 *
 * Slug komik yang keluar dari repository ini SUDAH berupa "kunci" (lihat
 * ComicKey): BacaKomik polos, sumber lain diberi awalan "sumber~". Kunci itu
 * yang dipakai UI, route navigasi, dan Room -- jadi favorit/progress antar
 * sumber gak saling bentrok. Slug chapter tetap polos.
 */
class ComicRepository(
    private val sources: List<ComicSource>,
    private val dao: ZenimeDao
) {

    private data class CacheEntry<T>(val data: T, val timestamp: Long)

    private fun isFresh(entry: CacheEntry<*>?, ttlMillis: Long): Boolean =
        entry != null && (System.currentTimeMillis() - entry.timestamp) < ttlMillis

    companion object {
        private const val TTL_LIST = 5 * 60 * 1000L      // 5 menit, cuma buat page 1
        private const val TTL_DETAIL = 30 * 60 * 1000L   // 30 menit, detail komik jarang berubah
        private const val TTL_GENRES = 60 * 60 * 1000L   // 1 jam, list genre nyaris statis
    }

    val availableSources: List<ComicSource> = sources

    private fun sourceOf(id: ComicSourceId): ComicSource =
        sources.first { it.id == id }

    fun tabsOf(id: ComicSourceId): List<ComicTab> = sourceOf(id).tabs

    // Cache CUMA buat page 1 tiap (sumber, tab) -- paling sering diminta
    // ulang. Page 2+ ("Load More") sengaja selalu fetch fresh.
    private val listPage1Cache = ConcurrentHashMap<String, CacheEntry<BacakomikListResponse>>()
    private val detailCache = ConcurrentHashMap<String, CacheEntry<BacakomikDetail>>()
    private val genresCache = ConcurrentHashMap<ComicSourceId, CacheEntry<List<BacakomikGenreItem>>>()

    // Tambahin awalan sumber ke slug tiap komik di hasil list.
    private fun BacakomikListResponse.withKeys(source: ComicSourceId): BacakomikListResponse =
        copy(komikList = komikList?.map { it.copy(slug = ComicKey.encode(source, it.slug)) })

    // ---- List per tab ----

    fun browse(
        sourceId: ComicSourceId,
        tabId: String,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Flow<Result<BacakomikListResponse>> = flow {
        val source = sourceOf(sourceId)
        val cacheKey = "${sourceId.id}/$tabId"
        if (page == 1) {
            val cached = listPage1Cache[cacheKey]
            if (!forceRefresh && isFresh(cached, TTL_LIST)) {
                emit(Result.Success(cached!!.data))
                return@flow
            }
            emit(Result.Loading)
            try {
                val res = source.browse(tabId, page).withKeys(sourceId)
                listPage1Cache[cacheKey] = CacheEntry(res, System.currentTimeMillis())
                emit(Result.Success(res))
            } catch (e: Exception) {
                if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
            }
        } else {
            emit(Result.Loading)
            try {
                emit(Result.Success(source.browse(tabId, page).withKeys(sourceId)))
            } catch (e: Exception) {
                emit(Result.Error(e))
            }
        }
    }

    // Dipakai Beranda (kartu komik) -- tetap BacaKomik seperti sebelumnya.
    fun getLatest(page: Int = 1, forceRefresh: Boolean = false): Flow<Result<BacakomikListResponse>> =
        browse(ComicSourceId.BACAKOMIK, "latest", page, forceRefresh)

    fun getPopular(page: Int = 1, forceRefresh: Boolean = false): Flow<Result<BacakomikListResponse>> =
        browse(ComicSourceId.BACAKOMIK, "popular", page, forceRefresh)

    // Search sengaja gak di-cache -- query berubah-ubah tiap ketikan.
    fun search(
        sourceId: ComicSourceId,
        query: String,
        page: Int = 1
    ): Flow<Result<BacakomikListResponse>> = flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(sourceOf(sourceId).search(query, page).withKeys(sourceId)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    // ---- Detail & chapter ----

    // comicKey = kunci komik (lihat ComicKey); BacaKomik lama (tanpa awalan) tetap jalan.
    fun getDetail(comicKey: String, forceRefresh: Boolean = false): Flow<Result<BacakomikDetail>> = flow {
        val (sourceId, slug) = ComicKey.decode(comicKey)
        val cached = detailCache[comicKey]
        if (!forceRefresh && isFresh(cached, TTL_DETAIL)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val detail = sourceOf(sourceId).detail(slug)
            detailCache[comicKey] = CacheEntry(detail, System.currentTimeMillis())
            emit(Result.Success(detail))
        } catch (e: Exception) {
            if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
        }
    }

    // Chapter (halaman baca) sengaja gak di-cache. comicKey dibutuhin karena
    // sumber tertentu (Mangakita) butuh slug komik buat bikin URL chapter.
    fun getChapter(comicKey: String, chapterSlug: String): Flow<Result<BacakomikChapterResponse>> = flow {
        emit(Result.Loading)
        try {
            val (sourceId, comicSlug) = ComicKey.decode(comicKey)
            emit(Result.Success(sourceOf(sourceId).chapter(comicSlug, chapterSlug)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    // ---- Genre ----

    fun getGenres(sourceId: ComicSourceId, forceRefresh: Boolean = false): Flow<Result<List<BacakomikGenreItem>>> = flow {
        val cached = genresCache[sourceId]
        if (!forceRefresh && isFresh(cached, TTL_GENRES)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val list = sourceOf(sourceId).genres()
            genresCache[sourceId] = CacheEntry(list, System.currentTimeMillis())
            emit(Result.Success(list))
        } catch (e: Exception) {
            if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
        }
    }

    fun getByGenre(
        sourceId: ComicSourceId,
        genreSlug: String,
        page: Int = 1
    ): Flow<Result<BacakomikListResponse>> = flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(sourceOf(sourceId).byGenre(genreSlug, page).withKeys(sourceId)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    // Comic Favorites (bookmark) -- comicSlug di sini = kunci komik.
    val comicFavorites: Flow<List<ComicFavoriteEntity>> = dao.getAllComicFavorites()

    fun isComicFavorite(comicSlug: String): Flow<Boolean> = dao.isComicFavoriteFlow(comicSlug)

    suspend fun removeComicFavorite(comicSlug: String) {
        dao.deleteComicFavorite(comicSlug)
    }

    suspend fun toggleComicFavorite(
        slug: String,
        title: String,
        cover: String?,
        status: String?,
        isCurrentlyFavorite: Boolean
    ) {
        if (isCurrentlyFavorite) {
            dao.deleteComicFavorite(slug)
        } else {
            dao.insertComicFavorite(
                ComicFavoriteEntity(slug = slug, title = title, cover = cover, status = status)
            )
        }
    }

    // Comic Reading Progress ("Lanjutkan Baca") -- comicSlug = kunci komik.
    val comicReadingProgress: Flow<List<ComicReadingProgressEntity>> = dao.getAllComicProgress()

    fun getComicProgress(comicSlug: String): Flow<ComicReadingProgressEntity?> =
        dao.getComicProgressFlow(comicSlug)

    suspend fun getComicProgressOnce(comicSlug: String): ComicReadingProgressEntity? =
        dao.getComicProgressOnce(comicSlug)

    suspend fun saveComicProgress(
        comicSlug: String,
        comicTitle: String,
        comicCover: String?,
        chapterSlug: String,
        chapterLabel: String?,
        scrollItemIndex: Int,
        scrollItemOffset: Int
    ) {
        dao.upsertComicProgress(
            ComicReadingProgressEntity(
                comicSlug = comicSlug,
                comicTitle = comicTitle,
                comicCover = comicCover,
                chapterSlug = chapterSlug,
                chapterLabel = chapterLabel,
                scrollItemIndex = scrollItemIndex,
                scrollItemOffset = scrollItemOffset
            )
        )
    }

    suspend fun deleteComicProgress(comicSlug: String) {
        dao.deleteComicProgress(comicSlug)
    }
}
