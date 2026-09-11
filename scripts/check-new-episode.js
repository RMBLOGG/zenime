// Dipanggil dari workflow .github/workflows/check-new-episode.yml
//
// Alurnya:
// 1. Fetch /schedule?day=<X> buat 7 hari (SENIN..MINGGU) dari API Zenime,
//    digabung jadi satu daftar semua anime ongoing.
// 2. Ambil snapshot terakhir tiap anime dari tabel anime_episode_snapshot
//    di Supabase.
// 3. Anime yang key_time-nya beda dari snapshot -> ada episode baru ->
//    kirim notif FCM (topic new_episode_updates) + update snapshotnya.
// 4. Anime yang belum ada di snapshot sama sekali -> insert diem-diem
//    (gak dikirim notif), supaya run pertama kali gak nge-spam semua
//    anime ongoing sekaligus.

const admin = require("firebase-admin");

const API_BASE_URL = process.env.API_BASE_URL;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const FIREBASE_SERVICE_ACCOUNT = process.env.FIREBASE_SERVICE_ACCOUNT;

const DAYS = ["SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU"];
const TOPIC = "new_episode_updates";

function requireEnv(name, value) {
  if (!value) {
    console.error(`Env var ${name} kosong/belum di-set sebagai secret.`);
    process.exit(1);
  }
}

requireEnv("API_BASE_URL", API_BASE_URL);
requireEnv("SUPABASE_URL", SUPABASE_URL);
requireEnv("SUPABASE_SERVICE_ROLE_KEY", SUPABASE_SERVICE_ROLE_KEY);
requireEnv("FIREBASE_SERVICE_ACCOUNT", FIREBASE_SERVICE_ACCOUNT);

async function fetchAllOngoing() {
  const byId = new Map();

  for (const day of DAYS) {
    const url = `${API_BASE_URL.replace(/\/$/, "")}/schedule?day=${encodeURIComponent(day)}`;
    const res = await fetch(url);
    if (!res.ok) {
      console.error(`Gagal fetch schedule day=${day}: HTTP ${res.status}`);
      continue;
    }
    const list = await res.json();
    for (const anime of list) {
      if (anime && anime.id) {
        byId.set(String(anime.id), anime);
      }
    }
  }

  return Array.from(byId.values());
}

async function fetchSnapshotMap() {
  const url = `${SUPABASE_URL}/rest/v1/anime_episode_snapshot?select=anime_id,key_time`;
  const res = await fetch(url, {
    headers: {
      apikey: SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
    },
  });
  if (!res.ok) {
    throw new Error(`Gagal ambil snapshot dari Supabase: HTTP ${res.status}`);
  }
  const rows = await res.json();
  const map = new Map();
  for (const row of rows) {
    map.set(String(row.anime_id), row.key_time);
  }
  return map;
}

async function upsertSnapshot(rows) {
  if (rows.length === 0) return;
  const url = `${SUPABASE_URL}/rest/v1/anime_episode_snapshot`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      apikey: SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates",
    },
    body: JSON.stringify(rows),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Gagal upsert snapshot: HTTP ${res.status} ${text}`);
  }
}

function normalizeImageUrl(url) {
  if (typeof url !== "string" || url.trim() === "") return null;
  try {
    const parsed = new URL(url.trim());
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      return null;
    }
    // API sumbernya kadang ngasih path dobel-slash (mis. ".net//assets/..."),
    // yang valid buat browser tapi ditolak sama validator ketat punya
    // Firebase (segmen path kosong dianggap invalid). Rapiin di sini.
    parsed.pathname = parsed.pathname.replace(/\/{2,}/g, "/");
    return parsed.toString();
  } catch {
    return null;
  }
}

async function sendEpisodeNotification(anime) {
  const title = anime.title || "Anime";
  const candidateImage = anime.image_poster || anime.image_cover;
  const normalizedImage = normalizeImageUrl(candidateImage);

  const notification = {
    title: title,
    body: "Episode terbaru sudah rilis, buruan nonton!",
  };

  // Cuma masukin key imageUrl kalau beneran valid -- kalau key-nya ada
  // tapi isinya undefined/kosong, Firebase nolak seluruh pesannya
  // (bukan cuma skip gambarnya doang), jadi mending di-omit total.
  if (normalizedImage) {
    notification.imageUrl = normalizedImage;
  }

  const message = {
    notification,
    android: {
      // Route ke channel "new_episode" biar user bisa atur notif ini
      // terpisah dari channel "announcements" di pengaturan Android.
      notification: {
        channelId: "new_episode",
      },
    },
    topic: TOPIC,
  };

  try {
    const response = await admin.messaging().send(message);
    console.log(`Notif terkirim buat "${title}":`, response);
  } catch (error) {
    console.error(`Gagal kirim notif buat "${title}":`, error);
  }
}

async function main() {
  const serviceAccount = JSON.parse(FIREBASE_SERVICE_ACCOUNT);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });

  const [ongoingList, snapshotMap] = await Promise.all([
    fetchAllOngoing(),
    fetchSnapshotMap(),
  ]);

  console.log(`Total anime ongoing dari API: ${ongoingList.length}`);
  console.log(`Total anime tersimpan di snapshot: ${snapshotMap.size}`);

  const toUpsert = [];
  let notifiedCount = 0;

  for (const anime of ongoingList) {
    const id = String(anime.id);
    const previousKeyTime = snapshotMap.get(id);
    const currentKeyTime = anime.key_time || null;

    const isNewAnime = previousKeyTime === undefined;
    const hasChanged = !isNewAnime && previousKeyTime !== currentKeyTime;

    if (hasChanged) {
      notifiedCount += 1;
      // Kirim dulu, baru masukin ke antrian upsert setelah semua diproses.
      // eslint-disable-next-line no-await-in-loop
      await sendEpisodeNotification(anime);
    }

    if (isNewAnime || hasChanged) {
      toUpsert.push({
        anime_id: id,
        title: anime.title || "",
        key_time: currentKeyTime,
        poster_url: anime.image_poster || anime.image_cover || null,
        updated_at: new Date().toISOString(),
      });
    }
  }

  await upsertSnapshot(toUpsert);

  console.log(`Selesai. ${notifiedCount} anime dikirim notif episode baru.`);
}

main().catch((error) => {
  console.error("Script gagal jalan:", error);
  process.exit(1);
});
