package com.example.data.api

/**
 * Kredensial Supabase project Zenime (buat fitur Premium & ZCoin).
 *
 * PENTING: SUPABASE_ANON_KEY di sini WAJIB diisi manual sebelum fitur
 * Premium bisa jalan -- ambil dari Supabase Dashboard > Project Settings >
 * API > "anon public" key. Ini AMAN ditanam di app (beda dari
 * service_role key yang gak boleh pernah ada di client), karena anon key
 * emang didesain buat dipakai dari sisi client dan dibatasi lewat RLS +
 * Edge Function yang cuma nerima request tertentu.
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://supabase.zenime.biz.id"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiwiaWF0IjoxNzkwMTc4Njg0LCJleHAiOjE5NDc4NTg2ODR9.SH96iiwN4sQciG-8iIvO1bOFqEt58glG07z3AVYVrxE"

    /** Halaman storefront buat checkout pembayaran premium. */
    const val STOREFRONT_URL = "https://zenime.biz.id/beli-premium"

    /**
     * Halaman storefront khusus buat pembeli luar negeri (mis. Malaysia)
     * yang QRIS Sakurupiah di [STOREFRONT_URL] tidak kebaca e-wallet/bank
     * mereka -- pakai QRIS pribadi merchant, diverifikasi manual oleh admin.
     */
    const val MANUAL_STOREFRONT_URL = "https://zenime.biz.id/bayar-manual"

    /**
     * Halaman storefront buat checkout top up ZCoin.
     */
    const val COIN_STOREFRONT_URL = "https://zenime.biz.id/top-up-coin"

    /** Versi "bayar dari luar negeri" buat top up ZCoin. */
    const val COIN_MANUAL_STOREFRONT_URL = "https://zenime.biz.id/coin-bayar-manual"
}
