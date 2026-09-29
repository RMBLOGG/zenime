package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.api.SupabaseNetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val PremiumCheckBlue = Color(0xFF3897F0)

/**
 * Warna centang berdasarkan role (developer/admin/moderator) -- default
 * merah/hijau/ungu, atau `badge_color` custom kalau developer nyetel sendiri.
 * Sama persis kayak aturan di Chat Global & Clan.
 */
fun roleCheckColor(role: String?, badgeColorHex: String?): Color? {
    if (role == null) return null
    val hex = badgeColorHex ?: when (role) {
        "developer" -> "#E53935"
        "admin" -> "#43A047"
        "moderator" -> "#8E24AA"
        else -> null
    }
    return hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
}

/**
 * Cache role in-memory buat semua layar yang nampilin centang. Layar cukup
 * manggil [request] per uid; request-nya dikumpulin ~60ms lalu ditarik
 * SEKALI JALAN (`in.(...)`), jadi list panjang gak bikin request per baris.
 * Lookup yang gagal (network) dilepas biar dicoba lagi lain waktu.
 */
object RoleBadgeCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val requested = mutableSetOf<String>()
    private val pending = mutableSetOf<String>()
    private var flushing = false

    private val _colors = MutableStateFlow<Map<String, Color>>(emptyMap())

    /** uid -> warna centang role. Uid tanpa role gak ada di map ini. */
    val colors: StateFlow<Map<String, Color>> = _colors.asStateFlow()

    fun request(uid: String) {
        if (uid.isBlank()) return
        synchronized(lock) {
            if (!requested.add(uid)) return
            pending.add(uid)
            if (flushing) return
            flushing = true
        }
        scope.launch { flushLoop() }
    }

    private suspend fun flushLoop() {
        while (true) {
            delay(60)
            val batch = synchronized(lock) {
                if (pending.isEmpty()) {
                    flushing = false
                    return
                }
                pending.toList().also { pending.clear() }
            }
            batch.chunked(100).forEach { chunk ->
                val rows = runCatching {
                    SupabaseNetworkModule.adminApi.getRolesForUids(
                        firebaseUidIn = "in.(${chunk.joinToString(",")})"
                    )
                }.getOrNull()
                if (rows == null) {
                    synchronized(lock) { requested.removeAll(chunk.toSet()) }
                } else {
                    val found = rows.mapNotNull { row ->
                        roleCheckColor(row.role, row.badgeColor)?.let { row.firebaseUid to it }
                    }.toMap()
                    if (found.isNotEmpty()) _colors.value = _colors.value + found
                }
            }
        }
    }
}

/**
 * Centang di samping username. Role menang atas Premium (satu centang aja):
 * warna role kalau punya role, biru kalau cuma Premium, kosong kalau
 * keduanya bukan.
 */
@Composable
fun UserCheckBadge(
    firebaseUid: String?,
    isPremium: Boolean,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val uid = firebaseUid.orEmpty()
    LaunchedEffect(uid) { RoleBadgeCache.request(uid) }
    val colors by RoleBadgeCache.colors.collectAsState()
    val roleColor = colors[uid]
    val tint = roleColor ?: if (isPremium) PremiumCheckBlue else null
    if (tint != null) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = if (roleColor != null) "Role" else "Premium",
            tint = tint,
            modifier = modifier.size(size)
        )
    }
}
