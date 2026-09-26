package com.example.data.realtime

import com.example.data.api.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Event yang datang dari Supabase Realtime buat tabel `user_roles`. */
sealed class RoleRealtimeEvent {
    data class Upserted(val firebaseUid: String, val role: String, val badgeColor: String?) : RoleRealtimeEvent()
    data class Removed(val firebaseUid: String) : RoleRealtimeEvent()
}

/**
 * Client Supabase Realtime (Postgres Changes) khusus tabel `user_roles` --
 * begitu developer kasih/cabut role lewat Panel Admin (INSERT/UPDATE/DELETE
 * di tabel ini), semua device yang lagi buka Chat Global langsung dapet
 * push ganti warna badge-nya, TANPA nunggu refresh manual atau reopen chat.
 *
 * Struktur & protokolnya sengaja disalin persis dari [com.example.ui.screens.chat.ChatRealtimeClient]
 * (Phoenix Channel via OkHttp WebSocket) -- cuma beda topic/table & bentuk
 * event-nya, biar konsisten satu pola realtime di seluruh app.
 *
 * SYARAT DI SISI SUPABASE (dashboard/SQL, bukan kode) -- WAJIB biar ini jalan:
 * - `ALTER PUBLICATION supabase_realtime ADD TABLE user_roles;`
 * - RLS SELECT `user_roles` emang udah public (true) dari admin_panel_schema.sql,
 *   jadi gak perlu diapa-apain lagi -- broadcast-nya ikut RLS itu.
 */
class RoleRealtimeClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    private val topic = "realtime:public:user_roles"

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldRun = false
    private var retryAttempt = 0
    private var refCounter = 0

    private fun nextRef(): String = (++refCounter).toString()

    fun events(scope: CoroutineScope): Flow<RoleRealtimeEvent> = callbackFlow {
        shouldRun = true
        retryAttempt = 0
        connect(scope, this)
        awaitClose {
            shouldRun = false
            heartbeatJob?.cancel()
            reconnectJob?.cancel()
            webSocket?.close(1000, "bye")
            webSocket = null
        }
    }

    private fun connect(scope: CoroutineScope, producer: ProducerScope<RoleRealtimeEvent>) {
        val wsUrl = SupabaseConfig.SUPABASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${SupabaseConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                retryAttempt = 0
                joinChannel(ws)
                startHeartbeat(scope, ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleFrame(text, producer)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect(scope, producer)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (shouldRun) scheduleReconnect(scope, producer)
            }
        })
    }

    private fun joinChannel(ws: WebSocket) {
        val config = JSONObject().put(
            "postgres_changes",
            JSONArray()
                .put(JSONObject().put("event", "INSERT").put("schema", "public").put("table", "user_roles"))
                .put(JSONObject().put("event", "UPDATE").put("schema", "public").put("table", "user_roles"))
                .put(JSONObject().put("event", "DELETE").put("schema", "public").put("table", "user_roles"))
        )
        val join = JSONObject()
            .put("topic", topic)
            .put("event", "phx_join")
            .put("payload", JSONObject().put("config", config))
            .put("ref", nextRef())
        ws.send(join.toString())
    }

    private fun startHeartbeat(scope: CoroutineScope, ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(25_000L)
                val heartbeat = JSONObject()
                    .put("topic", "phoenix")
                    .put("event", "heartbeat")
                    .put("payload", JSONObject())
                    .put("ref", nextRef())
                ws.send(heartbeat.toString())
            }
        }
    }

    private fun handleFrame(text: String, producer: ProducerScope<RoleRealtimeEvent>) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return

            val payload = json.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return

            when (data.optString("type")) {
                "INSERT", "UPDATE" -> {
                    val record = data.optJSONObject("record") ?: return
                    val uid = record.optString("firebase_uid").takeIf { it.isNotBlank() } ?: return
                    val role = record.optString("role").takeIf { it.isNotBlank() } ?: return
                    val badgeColor = if (record.isNull("badge_color")) null else record.optString("badge_color")
                    producer.trySend(RoleRealtimeEvent.Upserted(uid, role, badgeColor))
                }
                "DELETE" -> {
                    val oldRecord = data.optJSONObject("old_record") ?: return
                    val uid = oldRecord.optString("firebase_uid").takeIf { it.isNotBlank() } ?: return
                    producer.trySend(RoleRealtimeEvent.Removed(uid))
                }
            }
        } catch (e: Exception) {
            // Frame lain (phx_reply buat join/heartbeat, dll) -- aman diabaikan.
        }
    }

    private fun scheduleReconnect(scope: CoroutineScope, producer: ProducerScope<RoleRealtimeEvent>) {
        if (!shouldRun) return
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        val delayMs = min(30_000L, 1000L * (1L shl min(retryAttempt, 5)))
        retryAttempt++
        reconnectJob = scope.launch {
            delay(delayMs)
            if (shouldRun) connect(scope, producer)
        }
    }
}
