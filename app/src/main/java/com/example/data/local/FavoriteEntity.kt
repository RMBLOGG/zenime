package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String?,
    val type: String?,
    val status: String?,
    val timestamp: Long = System.currentTimeMillis()
)
