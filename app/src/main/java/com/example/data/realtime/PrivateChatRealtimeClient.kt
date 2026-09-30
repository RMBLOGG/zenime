package com.example.data.realtime

import com.example.data.api.SupabaseConfig
import com.example.data.model.PrivateMessage
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

/**
 * Supabase Realtime (Postgres Changes) buat DM: cuma dengerin INSERT di
 * `private_messages` yang `recipient_uid`-nya = user ini (filter server-side),
 * jadi pesan yang DIKIRIM sendiri gak perlu lewat sini (udah ada dari
 * response POST). Pola koneksi sama kayak [ChatRealtimeClient].
 *
 * SYARAT: tabel udah didaftarin ke publication realtime (sudah diurus
 * private_chat_setup.sql).
 */
class PrivateChatRealtimeClient(
    private val myUid: String,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(PrivateMessage::class.java)

    private val topic = "realtime:public:private_messages:$myUid"

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldRun = false
    private var retryAttempt = 0
    private var refCounter = 0

    private fun nextRef(): String = (++refCounter).toString()

    fun incoming(scope: CoroutineScope): Flow<PrivateMessage> = callbackFlow {
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

    private fun connect(scope: CoroutineScope, producer: ProducerScope<PrivateMessage>) {
        val wsUrl = SupabaseConfig.SUPABASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${SupabaseConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"

        webSocket = okHttpClient.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
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
            JSONArray().put(
                JSONObject()
                    .put("event", "INSERT")
                    .put("schema", "public")
                    .put("table", "private_messages")
                    .put("filter", "recipient_uid=eq.$myUid")
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
                ws.send(
                    JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", nextRef())
                        .toString()
                )
            }
        }
    }

    private fun handleFrame(text: String, producer: ProducerScope<PrivateMessage>) {
        try {
            val json = JSONObject(text)
            if (json.optString("event") != "postgres_changes") return
            val data = json.optJSONObject("payload")?.optJSONObject("data") ?: return
            if (data.optString("type") != "INSERT") return
            val record = data.optJSONObject("record") ?: return
            adapter.fromJson(record.toString())?.let { producer.trySend(it) }
        } catch (e: Exception) {
            // Frame lain (phx_reply, dll) -- aman diabaikan.
        }
    }

    private fun scheduleReconnect(scope: CoroutineScope, producer: ProducerScope<PrivateMessage>) {
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
