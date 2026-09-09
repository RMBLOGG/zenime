package com.example.ui.screens.login

/**
 * Daftar poster tetap buat backdrop LoginScreen -- SENGAJA di-hardcode,
 * bukan diambil dari endpoint /home.
 *
 * Alasannya: LoginScreen adalah layar pertama yang dilihat user, termasuk
 * user yang BARU install (belum punya cache apa pun). Kalau backdrop-nya
 * nunggu getHome() dulu, user baru itu selalu lihat shimmer/kosong sampai
 * network selesai -- padahal poster di sini cuma dekorasi, bukan konten
 * yang harus selalu up-to-date.
 *
 * Dengan list tetap ini, Coil langsung mulai download begitu LoginScreen
 * dibuka, gak peduli status API/Remote Config/cache Room sama sekali.
 * Sumbernya CDN publik MyAnimeList (cdn.myanimelist.net) -- cuma nge-link
 * URL gambar, sama seperti cara AnimeRepository nge-link poster dari
 * sumber lain, bukan file yang di-bundle ke dalam APK.
 */
object LoginBackdropPosters {
    val urls = listOf(
        "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg", // Sousou no Frieren
        "https://cdn.myanimelist.net/images/anime/1540/155824l.jpg", // Re:Zero Season 4
        "https://cdn.myanimelist.net/images/anime/1208/94745l.jpg",  // Fullmetal Alchemist: Brotherhood
        "https://cdn.myanimelist.net/images/anime/1448/154111l.jpg", // JoJo's Bizarre Adventure: Steel Ball Run
        "https://cdn.myanimelist.net/images/anime/1935/127974l.jpg", // Steins;Gate
        "https://cdn.myanimelist.net/images/anime/1763/150638l.jpg", // Chainsaw Man Movie: Reze-hen
        "https://cdn.myanimelist.net/images/anime/1517/100633l.jpg", // Shingeki no Kyojin S3 Part 2
        "https://cdn.myanimelist.net/images/anime/3/72078l.jpg",     // Gintama°
        "https://cdn.myanimelist.net/images/anime/1245/116760l.jpg", // Gintama: The Final
        "https://cdn.myanimelist.net/images/anime/1337/99013l.jpg",  // Hunter x Hunter (2011)
        "https://cdn.myanimelist.net/images/anime/4/50361l.jpg",     // Gintama'
        "https://cdn.myanimelist.net/images/anime/1452/123686l.jpg", // Gintama': Enchousen
        "https://cdn.myanimelist.net/images/anime/1976/142016l.jpg", // Ginga Eiyuu Densetsu
        "https://cdn.myanimelist.net/images/anime/1455/146229l.jpg", // One Piece Fan Letter
        "https://cdn.myanimelist.net/images/anime/3/83528l.jpg",     // Gintama.
        "https://cdn.myanimelist.net/images/anime/1908/135431l.jpg", // Bleach: Sennen Kessen-hen
        "https://cdn.myanimelist.net/images/anime/1160/122627l.jpg", // Kaguya-sama: Ultra Romantic
        "https://cdn.myanimelist.net/images/anime/1085/114792l.jpg", // Fruits Basket: The Final
        "https://cdn.myanimelist.net/images/anime/1299/110774l.jpg", // Clannad: After Story
        "https://cdn.myanimelist.net/images/anime/10/73274l.jpg",    // Gintama
        "https://cdn.myanimelist.net/images/anime/1122/96435l.jpg",  // Koe no Katachi
        "https://cdn.myanimelist.net/images/anime/1088/135089l.jpg", // Code Geass R2
        "https://cdn.myanimelist.net/images/anime/1025/147458l.jpg", // Kusuriya no Hitorigoto S2
        "https://cdn.myanimelist.net/images/anime/3/88469l.jpg",     // 3-gatsu no Lion S2
        "https://cdn.myanimelist.net/images/anime/10/51723l.jpg"     // Gintama Movie 2
    )
}
