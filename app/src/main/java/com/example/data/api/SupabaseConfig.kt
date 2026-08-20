package com.example.data.api

/**
 * Kredensial Supabase project Zenime (buat fitur Premium).
 *
 * PENTING: SUPABASE_ANON_KEY di sini WAJIB diisi manual sebelum fitur
 * Premium bisa jalan -- ambil dari Supabase Dashboard > Project Settings >
 * API > "anon public" key. Ini AMAN ditanam di app (beda dari
 * service_role key yang gak boleh pernah ada di client), karena anon key
 * emang didesain buat dipakai dari sisi client dan dibatasi lewat RLS +
 * Edge Function yang cuma nerima request tertentu.
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://lryvtlnozwixjnuwfexj.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxyeXZ0bG5vendpeGpudXdmZXhqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcxNTUzMDQsImV4cCI6MjEwMjczMTMwNH0.qMEvt6OGBYqlkMwXZNZmoAdUt0-5hBdcme5DMCR0dxw"

    /** Halaman storefront buat checkout pembayaran premium. */
    const val STOREFRONT_URL = "https://zenime.biz.id/beli-premium"
}
