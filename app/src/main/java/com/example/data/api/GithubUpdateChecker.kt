package com.example.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cek update terbaru LANGSUNG ke GitHub Releases API (bukan Firebase
 * Remote Config) -- jadi gak ada masalah cache/throttle 1 jam kayak
 * RemoteConfigManager. Tiap app dibuka, ini beneran hit GitHub, dapet
 * apa adanya release paling baru yang ke-publish di repo.
 *
 * Cara pakai: publish GitHub Release baru di repo [REPO_OWNER]/[REPO_NAME]
 * dengan tag versi (mis. "v1.1" atau "1.1", boleh pakai prefix "v" atau
 * enggak) dan attach file .apk sebagai asset pertama di release itu --
 * itu yang bakal jadi link download.
 */
object GithubUpdateChecker {

    private const val REPO_OWNER = "RMBLOGG"
    private const val REPO_NAME = "zenime"

    data class UpdateInfo(
        val tagName: String,
        val downloadUrl: String,
        val releaseBody: String
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ambil release terbaru dari GitHub. Return null kalau gagal (offline,
     * repo belum punya release sama sekali -> GitHub balikin 404, dll) --
     * caller HARUS anggap null sebagai "gak ada update" (jangan block app),
     * bukan sebagai error yang nge-block user.
     */
    private suspend fun fetchLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null // termasuk 404 kalau belum ada release

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            if (tagName.isBlank()) return@withContext null

            val assets = json.optJSONArray("assets")
            val downloadUrl = if (assets != null && assets.length() > 0) {
                assets.getJSONObject(0).optString("browser_download_url", "")
            } else ""

            UpdateInfo(
                tagName = tagName,
                downloadUrl = downloadUrl,
                releaseBody = json.optString("body", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Bandingin dua versi segmen-per-segmen (mis. "1.2.10" vs "1.3"). */
    private fun isNewer(latest: String, current: String): Boolean {
        fun parse(v: String) = v.trimStart('v', 'V')
            .substringBefore('-')
            .split(".")
            .map { it.toIntOrNull() ?: 0 }

        val latestParts = parse(latest)
        val currentParts = parse(current)
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Cek apakah ada update dibanding [currentVersionName] (versionName APK
     * yang lagi jalan). Return null kalau gak ada release publik di repo
     * atau fetch gagal (offline dll) -- di kedua kasus itu app harus lanjut
     * jalan normal, BUKAN dianggap wajib update.
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? {
        val latest = fetchLatestRelease() ?: return null
        return if (isNewer(latest.tagName, currentVersionName)) latest else null
    }
}
