package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comic_favorites")
data class ComicFavoriteEntity(
    @PrimaryKey val slug: String,
    val title: String,
    val cover: String?,
    val status: String?,
    val timestamp: Long = System.currentTimeMillis()
)
