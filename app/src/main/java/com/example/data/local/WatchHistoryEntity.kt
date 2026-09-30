package com.example.data.local

import androidx.room.Entity

// Satu baris per EPISODE (bukan per anime) -- PK gabungan animeId + episodeId.
@Entity(tableName = "watch_history", primaryKeys = ["animeId", "episodeId"])
data class WatchHistoryEntity(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episodeId: String,
    val episodeTitle: String?,
    val episodeIndex: String?,
    val progressMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
