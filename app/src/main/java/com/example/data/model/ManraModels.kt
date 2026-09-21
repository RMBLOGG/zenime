package com.example.data.model

/**
 * Manra = cerita interaktif bergaya visual novel buatan pengguna Animein:
 * latar gambar + karakter (NPC) + dialog + pilihan. Model di bawah dibuat
 * manual dari map JSON mentah (lihat AnimeRepository), bukan lewat Moshi,
 * karena bentuk field-nya dibaca dari kode app Animein dan belum terverifikasi
 * dengan respons asli.
 */
data class ManraItem(
    val id: String,
    val title: String,
    val genres: String?,
    val status: String?,
    val mode: String?,
    val synopsis: String?,
    val author: String?,
    /** Teks siap tampil dari server, mis. "4.964 plays" / "341 reads". */
    val viewsText: String?,
    val likesText: String?,
    val imagePoster: String?,
    val imageCover: String?,
    val chapterCount: Int?
)

data class ManraPage(
    val items: List<ManraItem>,
    val hasNext: Boolean
)

data class ManraChapter(
    val id: String,
    val sequence: String?,
    val title: String,
    val release: String?,
    val views: String?,
    val likes: String?,
    val imageUrl: String?,
    val isFinished: Boolean,
    val isLocked: Boolean
)

data class ManraCast(
    val id: String,
    val name: String,
    val role: String?,
    val likes: String?,
    val imageUrl: String?
)

data class ManraDetail(
    val item: ManraItem,
    val chapters: List<ManraChapter>,
    val casts: List<ManraCast>
)

data class ManraChoice(
    val id: String,
    val text: String,
    val flag: String?
)

/**
 * Satu baris adegan. [type] salah satu dari: NARRATION, NPC_DIALOG, NPC_ACTION,
 * NPC_ONLY, BG_ONLY, VIDEO_SHORT, ATMOSPHERIC, CHOICE/CHOOSE.
 */
data class ManraLine(
    val id: String,
    val type: String,
    val text: String?,
    val imageBg: String?,
    val videoUrl: String?,
    val npcImage: String?,
    val npcName: String?,
    val choices: List<ManraChoice>
) {
    val isChoice: Boolean
        get() = choices.isNotEmpty() || type == "CHOICE" || type == "CHOOSE"
}
