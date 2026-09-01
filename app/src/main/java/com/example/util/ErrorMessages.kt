package com.example.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Ubah exception jadi pesan yang enak dibaca user -- JANGAN PERNAH
 * tampilin `e.localizedMessage` mentah-mentah ke UI. Exception jaringan
 * (misal request ke backend gagal connect) localizedMessage-nya sering
 * kebawa detail teknis kayak IP/port internal server (contoh:
 * "Failed to connect to /203.175.11.166:5001"), yang gak seharusnya
 * kelihatan sama user awam dan gak perlu juga buat mereka.
 *
 * [fallback] dipakai buat exception non-jaringan (parsing, dll) yang
 * gak dikenali di sini -- isi dengan teks spesifik ke konteks
 * pemanggilnya (mis. "Gagal memuat jadwal tayang"), BUKAN pesan generik
 * exception itu sendiri.
 */
fun friendlyErrorMessage(e: Throwable, fallback: String): String {
    return when (e) {
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is IOException -> "Gak ada koneksi internet. Periksa jaringan kamu dan coba lagi."
        else -> fallback
    }
}
