// Edge Function: sociabuzz-webhook
// Deploy dengan "Verify JWT" = OFF (SociaBuzz tidak bawa JWT Supabase).
//
// Secret yang dibutuhkan:
//   SOCIABUZZ_WEBHOOK_TOKEN  -> isi kolom "Webhook Token" dari SociaBuzz (sbwhook-...)
// Opsional:
//   LOG_RAW      -> "1" = simpan payload mentah (email donatur disamarkan) ke donation_webhook_logs.
//                   Default "0": cuma catatan singkat (note) yang disimpan.
//   ACCEPT_TEST  -> "1" = "Test Notifikasi" dari SociaBuzz ikut disimpan sebagai donasi.
//                   Default "0": notifikasi test dilewati.
//
// Webhook URL di SociaBuzz:
//   https://<project-ref>.supabase.co/functions/v1/sociabuzz-webhook?token=<SOCIABUZZ_WEBHOOK_TOKEN>
//
// Format payload SociaBuzz (dari Test Notifikasi), semua di level atas:
//   id, amount, amount_settled, currency, currency_settled, supporter,
//   email_supporter, message, created_at, item{}, vote{}, level{}, content{}, media_url, media_type
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);
const WEBHOOK_TOKEN = Deno.env.get("SOCIABUZZ_WEBHOOK_TOKEN") ?? "";
const LOG_RAW = (Deno.env.get("LOG_RAW") ?? "0") === "1";
const ACCEPT_TEST = (Deno.env.get("ACCEPT_TEST") ?? "0") === "1";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

async function sha256Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Payload bisa JSON atau form-urlencoded.
async function parseBody(req: Request): Promise<Record<string, unknown>> {
  const raw = (await req.text()).trim();
  if (!raw) return {};
  if (raw.startsWith("{")) {
    try {
      return JSON.parse(raw);
    } catch {
      // lanjut coba form-urlencoded
    }
  }
  const obj: Record<string, unknown> = {};
  new URLSearchParams(raw).forEach((v, k) => {
    obj[k] = v;
  });
  return obj;
}

function toAmount(v: unknown): number {
  if (typeof v === "number") return Math.round(v);
  if (typeof v !== "string") return 0;
  const s = v.trim();
  if (/^\d+([.,]\d{1,2})$/.test(s)) return Math.round(parseFloat(s.replace(",", ".")));
  const digits = s.replace(/[^\d]/g, "");
  return digits ? parseInt(digits, 10) : 0;
}

// Samarkan data sensitif sebelum masuk log.
function redact(body: Record<string, unknown>): Record<string, unknown> {
  const copy: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(body)) {
    copy[k] = /token|secret|password|email/i.test(k) ? "***" : v;
  }
  return copy;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ ok: true, message: "sociabuzz-webhook aktif (gunakan POST)" });
  }
  if (!WEBHOOK_TOKEN) {
    return json({ ok: false, error: "SOCIABUZZ_WEBHOOK_TOKEN belum di-set" }, 500);
  }

  const body = await parseBody(req);
  // Jaga-jaga kalau suatu saat datanya dibungkus di dalam "data".
  const src: Record<string, unknown> =
    body.data && typeof body.data === "object" ? (body.data as Record<string, unknown>) : body;

  const headerLog: Record<string, string> = {};
  req.headers.forEach((v, k) => {
    headerLog[k] = /authorization|token|cookie|apikey/i.test(k) ? "***" : v;
  });
  const writeLog = async (note: string) => {
    await supabase.from("donation_webhook_logs").insert({
      payload: LOG_RAW ? redact(body) : null,
      headers: LOG_RAW ? headerLog : null,
      note,
    });
  };

  // Token boleh datang lewat query ?token=, header, atau body.
  const auth = req.headers.get("authorization") ?? "";
  const provided = [
    new URL(req.url).searchParams.get("token"),
    req.headers.get("x-webhook-token"),
    req.headers.get("x-sociabuzz-token"),
    req.headers.get("x-token"),
    auth.replace(/^Bearer\s+/i, ""),
    body["token"] as string | undefined,
    body["webhook_token"] as string | undefined,
  ].filter((t): t is string => typeof t === "string" && t.length > 0);

  if (!provided.some((t) => safeEqual(t, WEBHOOK_TOKEN))) {
    // Catat cuma kalau lagi debug (LOG_RAW) supaya request sampah gak menuhin tabel.
    if (LOG_RAW) await writeLog("UNAUTHORIZED");
    return json({ ok: false, error: "unauthorized" }, 401);
  }

  const supporterName = String(src.supporter ?? "").trim() || "Anonim";
  const email = String(src.email_supporter ?? "").trim();
  const message = String(src.message ?? "").trim();
  // amount_settled = nominal dalam IDR (amount bisa dalam mata uang donatur, mis. USD).
  const amount = toAmount(src.amount_settled) || toAmount(src.amount);
  const donationId = src.id !== undefined && src.id !== null ? String(src.id).trim() : "";

  // Notifikasi test dari tombol "Test Notifikasi" -- jangan dihitung sebagai donasi.
  const isTest = /@example\.com$/i.test(email) || /hanya test notifikasi/i.test(message);
  if (isTest && !ACCEPT_TEST) {
    await writeLog("SKIPPED_TEST");
    return json({ ok: true, skipped: "test" });
  }

  if (amount <= 0) {
    await writeLog("NO_AMOUNT (payload tidak dikenali)");
    return json({ ok: false, error: "amount tidak ditemukan" });
  }

  // ID donasi dari SociaBuzz dipakai apa adanya (anti-dobel kalau webhook dikirim ulang).
  const externalId = donationId
    ? `sociabuzz:${donationId}`
    : `sociabuzz:fp:${await sha256Hex(`${supporterName}|${amount}|${message}|${Math.floor(Date.now() / 60000)}`)}`;

  // Waktu donasi asli dari SociaBuzz; kalau gak valid, pakai waktu sekarang (default tabel).
  const createdMs = Date.parse(String(src.created_at ?? ""));
  const createdAt = Number.isNaN(createdMs) ? null : new Date(createdMs).toISOString();

  // Hubungkan ke akun Zenime lewat kode di pesan (atau di nama).
  let firebaseUid: string | null = null;
  const { data: matched, error: matchError } = await supabase.rpc("match_zenime_code", {
    p_text: `${message} ${supporterName}`,
  });
  if (!matchError && typeof matched === "string" && matched) firebaseUid = matched;

  const { error } = await supabase.from("donations").upsert(
    {
      external_id: externalId,
      source: "sociabuzz",
      supporter_name: supporterName,
      amount,
      message: message || null,
      firebase_uid: firebaseUid,
      ...(createdAt ? { created_at: createdAt } : {}),
    },
    { onConflict: "external_id", ignoreDuplicates: true },
  );
  if (error) {
    await writeLog(`DB_ERROR ${error.message}`);
    return json({ ok: false, error: error.message }, 500);
  }

  await writeLog(`OK name=${supporterName} amount=${amount} linked=${firebaseUid !== null}`);
  return json({ ok: true, linked: firebaseUid !== null });
});
