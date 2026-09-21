-- Salin profiles.user_number -> chat_profiles.user_number biar "#ID" di Chat Global
-- bisa dibaca lewat PostgREST biasa (chat_profiles udah publik), TANPA Edge Function.
-- profiles sengaja TIDAK dibuka ke publik karena isinya ada zenime_code.
-- Jalanin sekali di Supabase SQL Editor.

alter table public.chat_profiles add column if not exists user_number bigint;

-- Backfill user yang udah ada
update public.chat_profiles cp
set user_number = p.user_number
from public.profiles p
where p.firebase_uid = cp.firebase_uid
  and p.user_number is not null;

-- Kalau user_number di profiles berubah / baru diisi -> ikut ke chat_profiles
create or replace function public.sync_chat_profile_user_number()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  update public.chat_profiles
  set user_number = new.user_number
  where firebase_uid = new.firebase_uid;
  return new;
end $$;

drop trigger if exists trg_sync_user_number on public.profiles;
create trigger trg_sync_user_number
after insert or update of user_number on public.profiles
for each row execute function public.sync_chat_profile_user_number();

-- Kalau baris chat_profiles dibuat belakangan -> isi dari profiles
create or replace function public.fill_chat_profile_user_number()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.user_number is null then
    select user_number into new.user_number
    from public.profiles where firebase_uid = new.firebase_uid;
  end if;
  return new;
end $$;

drop trigger if exists trg_fill_user_number on public.chat_profiles;
create trigger trg_fill_user_number
before insert on public.chat_profiles
for each row execute function public.fill_chat_profile_user_number();

-- Suruh PostgREST refresh schema cache biar kolom baru langsung kebaca API
notify pgrst, 'reload schema';

-- CEK HASIL (jalanin terpisah): harusnya user_number terisi, bukan null semua
-- select firebase_uid, user_number from public.chat_profiles
-- where user_number is not null limit 5;
