# Setup Top Support (SociaBuzz) -- urutan kerja

## 1. Cek dulu kolom profil (SQL Editor)
```sql
select firebase_uid, zenime_code from public.profiles limit 3;
```
Kalau error / nama kolom beda, kabari -- yang perlu diganti cuma fungsi `match_zenime_code`.

## 2. Jalankan `top-support.sql`
Paste seluruh isinya di SQL Editor -> Run.

## 3. Deploy 2 Edge Function (dua-duanya "Verify JWT" = OFF)
- `sociabuzz-webhook`        -> isi dari `functions/sociabuzz-webhook/index.ts`
- `zenime-top-supporters`    -> isi dari `functions/zenime-top-supporters/index.ts`

## 4. Tambah secret di Edge Functions -> Secrets
- `SOCIABUZZ_WEBHOOK_TOKEN` = string acak panjang buatanmu (contoh: 32 karakter huruf+angka)

## 5. Pasang webhook di SociaBuzz
TRIBE -> Edit & Settings -> Integrations -> Webhook:
- Activate Webhook Integration: ON
- Webhook URL:
  `https://lryvtlnozwixjnuwfexj.supabase.co/functions/v1/sociabuzz-webhook?token=ISI_TOKEN_KAMU`
- Webhook Token: isi token yang sama
- Webhook HTTP Test Response: isi `200` kalau kolomnya minta kode HTTP
- Klik "Test Notification"

## 6. Cek payload asli
```sql
select received_at, note, payload from public.donation_webhook_logs order by id desc limit 5;
select * from public.donations order by created_at desc limit 5;
```
Kirim hasil `payload` ke saya supaya parsernya bisa dikunci ke field yang pasti.
Kalau `note` = `NO_AMOUNT`, artinya nama field nominalnya belum dikenali.

## 7. Bersihkan data test
```sql
delete from public.donations where supporter_name ilike '%test%';
```
Setelah format aman, set secret `LOG_RAW` = `0` supaya payload mentah tidak terus disimpan.
