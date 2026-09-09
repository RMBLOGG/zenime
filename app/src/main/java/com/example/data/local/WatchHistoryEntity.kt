package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val animeId: String,
    val animeTitle: String,
    val posterUrl: String?,
    val episodeId: String,
    val episodeTitle: String?,
    val episodeIndex: String?,
    val progressMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
