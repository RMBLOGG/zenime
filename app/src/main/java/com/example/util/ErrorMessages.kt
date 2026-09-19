package com.example.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException

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
        // Kill-switch Remote Config (api_base_url kosong): pesannya sudah ramah.
        is ApiUnavailableException -> e.message ?: "Server sedang tidak tersedia. Coba lagi nanti."

        // Nama host tidak ketemu: internet mati, atau DNS diblokir.
        is UnknownHostException ->
            "Tidak bisa terhubung ke server. Periksa internetmu, atau server sedang gangguan. (kode: dns)"

        // Tersambung tapi server tidak menjawab tepat waktu.
        is SocketTimeoutException ->
            "Server terlalu lama merespons, mungkin sedang gangguan. Coba lagi sebentar lagi. (kode: timeout)"

        is SSLException ->
            "Koneksi aman ke server gagal. Coba lagi nanti. (kode: ssl)"

        is ConnectException ->
            "Server tidak bisa dijangkau. Coba lagi nanti. (kode: connect)"

        // Koneksi diputus di tengah jalan (mis. ERR_CONNECTION_ABORTED / reset).
        is IOException ->
            "Koneksi ke server terputus. Server sedang gangguan atau jaringanmu tidak stabil. Coba lagi nanti. (kode: putus)"

        // Server sumber menolak / error di sisinya.
        is HttpException -> when (e.code()) {
            403, 429 -> "Server sumber sedang membatasi akses. Coba lagi nanti. (kode: HTTP ${e.code()})"
            in 500..599 -> "Server sumber sedang gangguan. Coba lagi nanti. (kode: HTTP ${e.code()})"
            else -> fallback
        }

        else -> fallback
    }
}

/**
 * Dilempar ketika API sengaja dimatikan lewat Remote Config (api_base_url kosong).
 * Kelas terpisah supaya tidak salah dibaca sebagai "tidak ada internet".
 */
class ApiUnavailableException(message: String) : IOException(message)
