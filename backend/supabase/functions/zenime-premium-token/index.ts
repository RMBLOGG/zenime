// Edge Function: zenime-premium-token
//
// Verifikasi Firebase ID Token ASLI user, cek status premium-nya (pakai function
// zenime-check-premium yang sudah ada), lalu kalau premium kasih token HS256 umur
// pendek yang cuma bisa dibuat server. Token ini yang dicek anichin-api
// (backend/anichin-api/premium_guard.py) sebelum ngasih link video donghua.
//
// Env yang dibutuhin (set di edge-runtime Supabase self-host):
//   PREMIUM_TOKEN_SECRET  string acak panjang (>= 32 char), SAMA persis dengan di anichin-api
//   FIREBASE_PROJECT_ID   opsional, default zenime-609d0
// Deploy: sama kayak function Clan (Authorization-nya Firebase ID Token, bukan JWT Supabase,
// jadi verify_jwt untuk function ini harus dimatiin).

import { createRemoteJWKSet, jwtVerify } from "https://esm.sh/jose@5.9.6";

const PROJECT_ID = Deno.env.get("FIREBASE_PROJECT_ID") ?? "zenime-609d0";
const SECRET = Deno.env.get("PREMIUM_TOKEN_SECRET") ?? "";
const TTL_SECONDS = 30 * 60;

const JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const b64url = (data: Uint8Array | string) => {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

async function signHs256(payload: Record<string, unknown>, secret: string): Promise<string> {
  const head = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = b64url(JSON.stringify(payload));
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${head}.${body}`));
  return `${head}.${body}.${b64url(new Uint8Array(sig))}`;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ message: "method not allowed" }, 405);
  if (SECRET.length < 32) return json({ message: "server belum dikonfigurasi" }, 500);

  const idToken = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  let uid = "";
  try {
    const { payload } = await jwtVerify(idToken, JWKS, {
      issuer: `https://securetoken.google.com/${PROJECT_ID}`,
      audience: PROJECT_ID,
    });
    uid = String(payload.sub ?? "");
    if (!uid) throw new Error("no sub");
  } catch {
    return json({ message: "token tidak valid" }, 401);
  }

  const base = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const res = await fetch(`${base}/functions/v1/zenime-check-premium`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: anon, Authorization: `Bearer ${anon}` },
    body: JSON.stringify({ firebase_uid: uid }),
  });
  if (!res.ok) return json({ message: "gagal cek premium" }, 502);
  const status = await res.json();
  if (!status.is_premium) return json({ is_premium: false, token: null, expires_in: 0 });

  const exp = Math.floor(Date.now() / 1000) + TTL_SECONDS;
  const token = await signHs256({ sub: uid, prem: 1, exp }, SECRET);
  return json({ is_premium: true, token, expires_in: TTL_SECONDS });
});
