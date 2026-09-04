package com.example.data.repository

import com.example.data.api.ComicApi
import com.example.data.common.Result
import com.example.data.model.BacakomikChapterResponse
import com.example.data.model.BacakomikDetail
import com.example.data.model.BacakomikGenreItem
import com.example.data.model.BacakomikListResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

class ComicRepository(private val api: ComicApi) {

    private data class CacheEntry<T>(val data: T, val timestamp: Long)

    private fun isFresh(entry: CacheEntry<*>?, ttlMillis: Long): Boolean =
        entry != null && (System.currentTimeMillis() - entry.timestamp) < ttlMillis

    companion object {
        private const val TTL_LIST = 5 * 60 * 1000L      // 5 menit, cuma buat page 1
        private const val TTL_DETAIL = 30 * 60 * 1000L   // 30 menit, detail komik jarang berubah
        private const val TTL_GENRES = 60 * 60 * 1000L   // 1 jam, list genre nyaris statis
    }

    // Cache CUMA buat page 1 -- itu yang paling sering diminta ulang (tiap
    // buka Home/tab). Page 2+ (hasil "Load More") sengaja selalu fetch fresh,
    // gak worth nyimpen tiap kombinasi halaman di memori.
    private var latestPage1Cache: CacheEntry<BacakomikListResponse>? = null
    private var popularPage1Cache: CacheEntry<BacakomikListResponse>? = null
    private val detailCache = ConcurrentHashMap<String, CacheEntry<BacakomikDetail>>()
    private var genresCache: CacheEntry<List<BacakomikGenreItem>>? = null

    fun getLatest(page: Int = 1, forceRefresh: Boolean = false): Flow<Result<BacakomikListResponse>> = flow {
        if (page == 1) {
            val cached = latestPage1Cache
            if (!forceRefresh && isFresh(cached, TTL_LIST)) {
                emit(Result.Success(cached!!.data))
                return@flow
            }
            emit(Result.Loading)
            try {
                val res = api.getLatest(page)
                latestPage1Cache = CacheEntry(res, System.currentTimeMillis())
                emit(Result.Success(res))
            } catch (e: Exception) {
                if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
            }
        } else {
            emit(Result.Loading)
            try {
                emit(Result.Success(api.getLatest(page)))
            } catch (e: Exception) {
                emit(Result.Error(e))
            }
        }
    }

    fun getPopular(page: Int = 1, forceRefresh: Boolean = false): Flow<Result<BacakomikListResponse>> = flow {
        if (page == 1) {
            val cached = popularPage1Cache
            if (!forceRefresh && isFresh(cached, TTL_LIST)) {
                emit(Result.Success(cached!!.data))
                return@flow
            }
            emit(Result.Loading)
            try {
                val res = api.getPopular(page)
                popularPage1Cache = CacheEntry(res, System.currentTimeMillis())
                emit(Result.Success(res))
            } catch (e: Exception) {
                if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
            }
        } else {
            emit(Result.Loading)
            try {
                emit(Result.Success(api.getPopular(page)))
            } catch (e: Exception) {
                emit(Result.Error(e))
            }
        }
    }

    // Search sengaja gak di-cache -- query berubah-ubah tiap ketikan (walau
    // udah di-debounce di ViewModel), gak worth nyimpen semua kombinasinya.
    fun search(query: String, page: Int = 1): Flow<Result<BacakomikListResponse>> = flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(api.searchComic(query, page)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    fun getDetail(slug: String, forceRefresh: Boolean = false): Flow<Result<BacakomikDetail>> = flow {
        val cached = detailCache[slug]
        if (!forceRefresh && isFresh(cached, TTL_DETAIL)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val detail = api.getComicDetail(slug).detail
            if (detail != null) {
                detailCache[slug] = CacheEntry(detail, System.currentTimeMillis())
                emit(Result.Success(detail))
            } else {
                emit(Result.Error(IllegalStateException("Detail komik tidak ditemukan")))
            }
        } catch (e: Exception) {
            if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
        }
    }

    // Chapter (halaman baca) sengaja gak di-cache -- biasanya cuma dibuka
    // sekali per kunjungan, gak worth makan memori buat nyimpen array URL gambar.
    fun getChapter(chapterSlug: String): Flow<Result<BacakomikChapterResponse>> = flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(api.getComicChapter(chapterSlug)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    fun getGenres(forceRefresh: Boolean = false): Flow<Result<List<BacakomikGenreItem>>> = flow {
        val cached = genresCache
        if (!forceRefresh && isFresh(cached, TTL_GENRES)) {
            emit(Result.Success(cached!!.data))
            return@flow
        }
        emit(Result.Loading)
        try {
            val list = api.getGenres().genres ?: emptyList()
            genresCache = CacheEntry(list, System.currentTimeMillis())
            emit(Result.Success(list))
        } catch (e: Exception) {
            if (cached != null) emit(Result.Success(cached.data)) else emit(Result.Error(e))
        }
    }

    fun getByGenre(genreSlug: String, page: Int = 1): Flow<Result<BacakomikListResponse>> = flow {
        emit(Result.Loading)
        try {
            emit(Result.Success(api.getComicByGenre(genreSlug, page)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }
}
