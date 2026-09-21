// Edge Function: sociabuzz-webhook
// Deploy dengan "Verify JWT" = OFF (SociaBuzz tidak bawa JWT Supabase).
//
// Secret yang dibutuhkan:
//   SOCIABUZZ_WEBHOOK_TOKEN  -> string acak buatanmu sendiri
//   LOG_RAW                  -> "1" (default) simpan payload mentah ke donation_webhook_logs;
//                               set "0" kalau format sudah fix.
//
// Webhook URL di SociaBuzz:
//   https://<project-ref>.supabase.co/functions/v1/sociabuzz-webhook?token=<SOCIABUZZ_WEBHOOK_TOKEN>
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);
const WEBHOOK_TOKEN = Deno.env.get("SOCIABUZZ_WEBHOOK_TOKEN") ?? "";
const LOG_RAW = (Deno.env.get("LOG_RAW") ?? "1") === "1";

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
  if (raw.startsWith("{") || raw.startsWith("[")) {
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? { items: parsed } : parsed;
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

// Ratakan objek bersarang (maks 3 level), key jadi lowercase, nilai pertama menang.
function flatten(obj: unknown, out: Record<string, unknown> = {}, depth = 0): Record<string, unknown> {
  if (obj && typeof obj === "object" && !Array.isArray(obj) && depth < 3) {
    for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
      const key = k.toLowerCase();
      if (v && typeof v === "object" && !Array.isArray(v)) {
        flatten(v, out, depth + 1);
      } else if (!(key in out)) {
        out[key] = v;
      }
    }
  }
  return out;
}

function pick(flat: Record<string, unknown>, keys: string[]): unknown {
  for (const k of keys) {
    const v = flat[k];
    if (v !== undefined && v !== null && String(v).trim() !== "") return v;
  }
  return undefined;
}

function toAmount(v: unknown): number {
  if (typeof v === "number") return Math.round(v);
  if (typeof v !== "string") return 0;
  const s = v.trim();
  if (/^\d+([.,]\d{1,2})$/.test(s)) return Math.round(parseFloat(s.replace(",", ".")));
  const digits = s.replace(/[^\d]/g, "");
  return digits ? parseInt(digits, 10) : 0;
}

function redact(body: Record<string, unknown>): Record<string, unknown> {
  const copy: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(body)) copy[k] = /token|secret|password/i.test(k) ? "***" : v;
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
  const flat = flatten(body);

  const headerLog: Record<string, string> = {};
  req.headers.forEach((v, k) => {
    headerLog[k] = /authorization|token|cookie|apikey/i.test(k) ? "***" : v;
  });
  const writeLog = async (note: string) => {
    if (!LOG_RAW) return;
    await supabase.from("donation_webhook_logs").insert({
      payload: redact(body),
      headers: headerLog,
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
    flat["token"] as string | undefined,
    flat["webhook_token"] as string | undefined,
  ].filter((t): t is string => typeof t === "string" && t.length > 0);

  if (!provided.some((t) => safeEqual(t, WEBHOOK_TOKEN))) {
    await writeLog("UNAUTHORIZED");
    return json({ ok: false, error: "unauthorized" }, 401);
  }

  // Lewati transaksi yang belum/tidak berhasil dibayar.
  const status = String(pick(flat, ["status", "payment_status", "transaction_status"]) ?? "");
  if (/pending|unpaid|expire|fail|cancel|refund/i.test(status)) {
    await writeLog(`SKIPPED status=${status}`);
    return json({ ok: true, skipped: true });
  }

  const supporterName =
    String(
      pick(flat, [
        "supporter", "supporter_name", "donator_name", "donatur", "donor_name",
        "donor", "name", "from", "sender", "display_name", "nama",
      ]) ?? "",
    ).trim() || "Anonim";
  const amount = toAmount(
    pick(flat, ["amount", "nominal", "total", "total_amount", "gross_amount", "donation_amount", "price", "value"]),
  );
  const message = String(
    pick(flat, ["message", "note", "notes", "pesan", "comment", "supporter_message", "description"]) ?? "",
  ).trim();

  if (amount <= 0) {
    await writeLog("NO_AMOUNT (payload tidak dikenali)");
    return json({ ok: false, error: "amount tidak ditemukan" });
  }

  // ID transaksi: ID spesifik dipakai apa adanya; ID generik digabung isi donasi
  // (biar ID milik supporter gak bikin donasi berbeda dianggap dobel).
  const specificId = pick(flat, ["transaction_id", "order_id", "donation_id", "trx_id", "invoice", "invoice_id"]);
  const genericId = pick(flat, ["id", "reference", "reference_id"]);
  let externalId: string;
  if (specificId !== undefined) {
    externalId = `sociabuzz:${String(specificId)}`;
  } else if (genericId !== undefined) {
    externalId = `sociabuzz:fp:${await sha256Hex(`${genericId}|${supporterName}|${amount}|${message}`)}`;
  } else {
    const minute = Math.floor(Date.now() / 60000);
    externalId = `sociabuzz:fp:${await sha256Hex(`${supporterName}|${amount}|${message}|${minute}`)}`;
  }

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
