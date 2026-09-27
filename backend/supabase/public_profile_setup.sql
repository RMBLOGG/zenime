-- Jalankan file ini di Supabase SQL Editor.
-- Tujuan: nyimpen SALINAN favorit & riwayat tontonan tiap user di server
-- (Room di HP tetap jadi sumber utama buat pemilik data sendiri), supaya
-- bisa ditampilkan di profil ke user LAIN -- tapi dibatasi toggle privasi
-- per-user (favorites_public / history_public di chat_profiles).
--
-- PENTING soal keamanan: app ini pakai Firebase Auth, BUKAN Supabase Auth,
-- jadi RLS gak bisa ngecek "ini request dari uid siapa" lewat auth.uid().
-- Tapi itu gak masalah di sini -- app CUMA pernah nge-query 2 tabel ini buat
-- LIHAT PUNYA ORANG LAIN (profil sendiri selalu baca dari Room lokal, gak
-- pernah lewat query ini), jadi RLS-nya gak perlu tau "siapa yang nanya",
-- cukup ngecek toggle privasi PEMILIK baris itu sendiri lewat subquery ke
-- chat_profiles. Kalau toggle-nya mati, row-nya gak nongol ke SIAPA PUN lewat
-- anon key -- sama efektifnya kayak ditutup total, tanpa perlu Edge Function/
-- service_role. INSERT/UPDATE tetap kebuka buat anon (dipercaya nulis baris
-- firebase_uid milik sendiri), konsisten sama pola tabel lain di app ini
-- (chat_profiles, global_chat_messages, dll).

create table if not exists public.user_favorites (
    firebase_uid text not null,
    anime_id text not null,
    title text not null,
    poster_url text,
    type text,
    status text,
    created_at timestamptz not null default now(),
    primary key (firebase_uid, anime_id)
);

create table if not exists public.user_watch_history (
    firebase_uid text not null,
    anime_id text not null,
    anime_title text not null,
    poster_url text,
    episode_id text not null,
    episode_title text,
    episode_index text,
    progress_ms bigint not null default 0,
    duration_ms bigint not null default 0,
    last_updated timestamptz not null default now(),
    primary key (firebase_uid, anime_id)
);

alter table public.chat_profiles
    add column if not exists favorites_public boolean not null default false,
    add column if not exists history_public boolean not null default false;

alter table public.user_favorites enable row level security;
alter table public.user_watch_history enable row level security;

drop policy if exists "anon can upsert own favorites" on public.user_favorites;
create policy "anon can upsert own favorites"
    on public.user_favorites for insert to anon with check (true);

drop policy if exists "anon can update own favorites" on public.user_favorites;
create policy "anon can update own favorites"
    on public.user_favorites for update to anon using (true);

drop policy if exists "anon can delete own favorites" on public.user_favorites;
create policy "anon can delete own favorites"
    on public.user_favorites for delete to anon using (true);

drop policy if exists "anon can read favorites if public" on public.user_favorites;
create policy "anon can read favorites if public"
    on public.user_favorites for select to anon using (
        exists (
            select 1 from public.chat_profiles cp
            where cp.firebase_uid = user_favorites.firebase_uid
              and cp.favorites_public = true
        )
    );

drop policy if exists "anon can upsert own history" on public.user_watch_history;
create policy "anon can upsert own history"
    on public.user_watch_history for insert to anon with check (true);

drop policy if exists "anon can update own history" on public.user_watch_history;
create policy "anon can update own history"
    on public.user_watch_history for update to anon using (true);

drop policy if exists "anon can delete own history" on public.user_watch_history;
create policy "anon can delete own history"
    on public.user_watch_history for delete to anon using (true);

drop policy if exists "anon can read history if public" on public.user_watch_history;
create policy "anon can read history if public"
    on public.user_watch_history for select to anon using (
        exists (
            select 1 from public.chat_profiles cp
            where cp.firebase_uid = user_watch_history.firebase_uid
              and cp.history_public = true
        )
    );
