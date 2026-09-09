package com.example.data.realtime

import com.example.data.api.SupabaseConfig
import com.example.data.model.ChatMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

/** Event yang datang dari Supabase Realtime buat tabel `global_chat_messages`. */
sealed class ChatRealtimeEvent {
    data class Inserted(val message: ChatMessage) : ChatRealtimeEvent()
    data class Deleted(val id: Long) : ChatRealtimeEvent()
}

/**
 * Client Supabase Realtime (Postgres Changes) khusus Chat Global -- gantiin
 * polling PostgREST tiap 3 detik yang sebelumnya bikin egress boros.
 *
 * Sengaja ngomong langsung protokol Phoenix Channel via OkHttp WebSocket
 * (bukan pakai SDK resmi supabase-kt/Ktor), karena project ini udah pakai
 * OkHttp+Retrofit buat semua network call -- nambah stack Ktor cuma buat
 * chat gak sepadan.
 *
 * Alur:
 * 1. Connect ke wss://<project>.supabase.co/realtime/v1/websocket
 * 2. `phx_join` ke topic dengan config `postgres_changes` (INSERT + DELETE)
 *    buat tabel `global_chat_messages`.
 * 3. Server push event "postgres_changes" tiap ada baris baru/kehapus.
 * 4. Heartbeat tiap 25 detik (Supabase nutup koneksi kalau diem >30 detik).
 * 5. Auto-reconnect pakai backoff kalau socket ketutup/error.
 *
 * SYARAT DI SISI SUPABASE (dashboard, bukan kode):
 * - Tabel `global_chat_messages` HARUS didaftarkan ke publication realtime
 *   (Database > Replication, atau `ALTER PUBLICATION supabase_realtime ADD
 *   TABLE global_chat_messages;`).
 * - RLS SELECT policy di tabel itu yang nentuin baris mana yang di-broadcast
 *   ke tiap koneksi -- karena ini chat publik, policy SELECT-nya emang
 *   harus terbuka buat anon/semua user.
 */
class ChatRealtimeClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val chatMessageAdapter = moshi.adapter(ChatMessage::class.java)

    private val topic = "realtime:public:global_chat_messages"

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldRun = false
    private var retryAttempt = 0
    private var refCounter = 0

    private fun nextRef(): String = (++refCounter).toString()

    /**
     * Flow event realtime (Inserted/Deleted). Connect otomatis pas
     * di-collect, dan disconnect otomatis pas collector-nya berhenti
     * (viewModelScope dibatalin, dsb) lewat awaitClose.
     */
    fun events(scope: CoroutineScope): Flow<ChatRealtimeEvent> = callbackFlow {
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

    private fun connect(scope: CoroutineScope, producer: ProducerScope<ChatRealtimeEvent>) {
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
                .put(
                    JSONObject()
                        .put("event", "INSERT")
                        .put("schema", "public")
                        .put("table", "global_chat_messages")
                )
                .put(
                    JSONObject()
                        .put("event", "DELETE")
                        .put("schema", "public")
                        .put("table", "global_chat_messages")
                )
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

    private fun handleFrame(text: String, producer: ProducerScope<ChatRealtimeEvent>) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return

            val payload = json.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return

            when (data.optString("type")) {
                "INSERT" -> {
                    val record = data.optJSONObject("record") ?: return
                    chatMessageAdapter.fromJson(record.toString())?.let {
                        producer.trySend(ChatRealtimeEvent.Inserted(it))
                    }
                }
                "DELETE" -> {
                    val oldRecord = data.optJSONObject("old_record") ?: return
                    val id = oldRecord.optLong("id", -1L)
                    if (id != -1L) producer.trySend(ChatRealtimeEvent.Deleted(id))
                }
            }
        } catch (e: Exception) {
            // Frame lain (phx_reply buat join/heartbeat, dll) -- aman diabaikan.
        }
    }

    private fun scheduleReconnect(scope: CoroutineScope, producer: ProducerScope<ChatRealtimeEvent>) {
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
