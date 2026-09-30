-- Jalankan di Supabase SQL Editor SEBELUM install versi app yang baru.
-- Tujuan: riwayat tontonan disimpan per EPISODE (sebelumnya 1 baris per anime).
--
-- PERHATIAN: RPC `upsert_watch_history` yang lama gak ada di repo ini, jadi
-- definisinya ditulis ulang di bawah dengan asumsi nama argumen = key JSON
-- yang dikirim app (firebase_uid, anime_id, ...). Cek dulu function lama kamu
-- (Database > Functions) -- kalau signature/return type-nya beda, hapus versi
-- lama biar gak numpuk jadi 2 overload.

-- 1. Ganti primary key: (firebase_uid, anime_id) -> + episode_id
alter table public.user_watch_history
    drop constraint if exists user_watch_history_pkey;

alter table public.user_watch_history
    add constraint user_watch_history_pkey
    primary key (firebase_uid, anime_id, episode_id);

-- 2. RPC upsert: 1 baris per episode, last_updated selalu di-set now()
drop function if exists public.upsert_watch_history(
    text, text, text, text, text, text, text, bigint, bigint
);

create or replace function public.upsert_watch_history(
    firebase_uid text,
    anime_id text,
    anime_title text,
    poster_url text,
    episode_id text,
    episode_title text,
    episode_index text,
    progress_ms bigint,
    duration_ms bigint
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.user_watch_history (
        firebase_uid, anime_id, anime_title, poster_url,
        episode_id, episode_title, episode_index,
        progress_ms, duration_ms, last_updated
    ) values (
        firebase_uid, anime_id, anime_title, poster_url,
        episode_id, episode_title, episode_index,
        progress_ms, duration_ms, now()
    )
    on conflict on constraint user_watch_history_pkey do update set
        anime_title   = excluded.anime_title,
        poster_url    = excluded.poster_url,
        episode_title = excluded.episode_title,
        episode_index = excluded.episode_index,
        progress_ms   = excluded.progress_ms,
        duration_ms   = excluded.duration_ms,
        last_updated  = now();
end;
$$;

grant execute on function public.upsert_watch_history(
    text, text, text, text, text, text, text, bigint, bigint
) to anon;
