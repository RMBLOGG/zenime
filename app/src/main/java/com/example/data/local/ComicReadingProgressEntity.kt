package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Satu baris per komik (bukan per chapter) -- "Lanjutkan Baca" cuma perlu
// tahu chapter TERAKHIR yang dibaca buat komik itu, jadi upsert selalu
// nimpa baris lama tiap kali user pindah/scroll chapter.
@Entity(tableName = "comic_reading_progress")
data class ComicReadingProgressEntity(
    @PrimaryKey val comicSlug: String,
    val comicTitle: String,
    val comicCover: String?,
    val chapterSlug: String,
    val chapterLabel: String?,
    val scrollItemIndex: Int = 0,
    val scrollItemOffset: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
