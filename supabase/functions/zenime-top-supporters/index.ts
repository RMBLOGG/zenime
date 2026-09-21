// Edge Function: zenime-top-supporters
// Deploy dengan "Verify JWT" = OFF (sama seperti zenime-list-packages).
// GET /functions/v1/zenime-top-supporters?limit=20
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  const raw = Number(new URL(req.url).searchParams.get("limit") ?? "20");
  const limit = Number.isFinite(raw) ? Math.min(Math.max(Math.trunc(raw), 1), 100) : 20;

  const { data, error } = await supabase.rpc("get_top_supporters", { p_limit: limit });
  if (error) return json({ supporters: [], message: error.message }, 500);

  // firebase_uid sengaja TIDAK dikirim ke app -- cuma data tampilan.
  // deno-lint-ignore no-explicit-any
  const supporters = (data ?? []).map((r: any) => ({
    rank: Number(r.r_rank),
    name: r.r_display_name,
    avatar_url: r.r_avatar_url,
    username_color: r.r_username_color,
    total_amount: Number(r.r_total_amount),
    donation_count: Number(r.r_donation_count),
    is_linked: Boolean(r.r_is_linked),
  }));
  return json({ supporters });
});
