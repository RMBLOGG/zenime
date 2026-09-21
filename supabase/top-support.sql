-- ============================================================
-- TOP SUPPORT (donatur SociaBuzz) -- jalankan di Supabase SQL Editor
-- ============================================================
-- CEK DULU (asumsi: tabel `profiles` punya kolom firebase_uid & zenime_code):
--   select firebase_uid, zenime_code from public.profiles limit 3;
-- Kalau nama kolom/tabelnya beda, ganti di fungsi match_zenime_code di bawah.

create table if not exists public.donations (
  id            uuid primary key default gen_random_uuid(),
  external_id   text unique,                       -- ID transaksi dari SociaBuzz (anti-dobel)
  source        text not null default 'sociabuzz',
  supporter_name text not null default 'Anonim',
  amount        bigint not null default 0,
  message       text,
  firebase_uid  text,                              -- terisi kalau kode Zenime cocok
  created_at    timestamptz not null default now()
);
create index if not exists donations_firebase_uid_idx on public.donations (firebase_uid);
create index if not exists donations_name_idx on public.donations (lower(supporter_name));

-- Log payload mentah webhook (buat ngintip format asli dari SociaBuzz)
create table if not exists public.donation_webhook_logs (
  id          bigint generated always as identity primary key,
  received_at timestamptz not null default now(),
  payload     jsonb,
  headers     jsonb,
  note        text
);

-- RLS aktif tanpa policy = cuma service_role (Edge Function) yang bisa akses.
alter table public.donations enable row level security;
alter table public.donation_webhook_logs enable row level security;

grant select, insert, update, delete on public.donations to service_role;
grant select, insert, update, delete on public.donation_webhook_logs to service_role;
grant usage, select on all sequences in schema public to service_role;

-- Cari akun dari kode Zenime yang ditulis di teks (pesan/nama donatur).
-- Format kode gak diasumsikan: kode dicocokkan utuh sebagai "kata".
-- Kalau cocok ke tepat 1 akun -> balikin firebase_uid, selain itu NULL.
create or replace function public.match_zenime_code(p_text text)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_count int;
  v_uid   text;
begin
  if p_text is null or length(trim(p_text)) = 0 then
    return null;
  end if;

  select count(*), min(pr.firebase_uid::text)
    into v_count, v_uid
  from public.profiles pr
  where pr.zenime_code is not null
    and length(pr.zenime_code) >= 4
    and upper(p_text) ~ (
      '(^|[^A-Z0-9])'
      || regexp_replace(upper(pr.zenime_code), '([^A-Z0-9])', '\\\1', 'g')
      || '($|[^A-Z0-9])'
    );

  if v_count = 1 then
    return v_uid;
  end if;
  return null;
end;
$$;

-- Daftar Top Support: dijumlah per akun (kalau terhubung) atau per nama (kalau belum).
-- Donatur anonim tidak digabung -- tiap donasi anonim jadi baris sendiri.
create or replace function public.get_top_supporters(p_limit int default 20)
returns table (
  r_rank           bigint,
  r_display_name   text,
  r_avatar_url     text,
  r_username_color text,
  r_total_amount   bigint,
  r_donation_count bigint,
  r_is_linked      boolean
)
language sql
stable
security definer
set search_path = public
as $$
  with per_donation as (
    select
      d.id, d.firebase_uid, d.supporter_name, d.amount, d.created_at,
      case
        when d.firebase_uid is not null then 'uid:' || d.firebase_uid
        when lower(trim(d.supporter_name)) in ('', 'anonim', 'anonymous', 'seseorang', 'someone', 'guest', 'tamu')
          then 'anon:' || d.id::text
        else 'name:' || lower(trim(d.supporter_name))
      end as grp_key
    from public.donations d
    where d.amount > 0
  ),
  grouped as (
    select
      p.grp_key,
      max(p.firebase_uid) as g_uid,
      (array_agg(p.supporter_name order by p.created_at desc))[1] as g_name,
      sum(p.amount)::bigint as g_total,
      count(*)::bigint as g_count
    from per_donation p
    group by p.grp_key
  )
  select
    row_number() over (order by g.g_total desc, g.g_count desc, g.g_name)::bigint,
    coalesce(nullif(trim(cp.username), ''), nullif(trim(g.g_name), ''), 'Anonim'),
    cp.avatar_url,
    cp.username_color,
    g.g_total,
    g.g_count,
    (g.g_uid is not null)
  from grouped g
  left join public.chat_profiles cp on cp.firebase_uid = g.g_uid
  order by 1
  limit greatest(1, least(coalesce(p_limit, 20), 100));
$$;

revoke all on function public.match_zenime_code(text) from public;
revoke all on function public.get_top_supporters(int) from public;
grant execute on function public.match_zenime_code(text) to service_role;
grant execute on function public.get_top_supporters(int) to service_role;

-- ------------------------------------------------------------
-- UTILITAS ADMIN (jalankan manual kalau perlu)
-- ------------------------------------------------------------
-- Hubungkan donasi lama yang belum terhubung, kalau pesannya ada kodenya:
--   update public.donations
--      set firebase_uid = public.match_zenime_code(coalesce(message,'') || ' ' || supporter_name)
--    where firebase_uid is null
--      and public.match_zenime_code(coalesce(message,'') || ' ' || supporter_name) is not null;
--
-- Hubungkan donasi tertentu ke akun secara manual:
--   update public.donations set firebase_uid = '<FIREBASE_UID>' where id = '<DONATION_ID>';
--
-- Input donasi manual (mis. QRIS/Trakteer):
--   insert into public.donations (source, supporter_name, amount, firebase_uid)
--   values ('manual', 'Nama Donatur', 50000, null);
--
-- Hapus data test dari tombol "Test Notification":
--   delete from public.donations where supporter_name ilike '%test%';
