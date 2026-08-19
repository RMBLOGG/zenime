package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "DARK", "LIGHT", "SYSTEM"
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality") // "720p", "1080p", "480p", "360p"
        val AUTO_SKIP_INTRO = booleanPreferencesKey("auto_skip_intro")
        val AUTO_SKIP_OUTRO = booleanPreferencesKey("auto_skip_outro")

        // Kustomisasi Hero Banner Carousel di Beranda
        val HERO_STYLE = stringPreferencesKey("hero_style") // "FULL_BLEED", "CARD_PEEK", "MINIMAL"
        val HERO_AUTOPLAY = booleanPreferencesKey("hero_autoplay")
        val HERO_INTERVAL_MS = intPreferencesKey("hero_interval_ms")
        val HERO_ITEM_COUNT = intPreferencesKey("hero_item_count")
        val HERO_SOURCE = stringPreferencesKey("hero_source") // "AUTO", "HOT", "POPULAR", "RANDOM"
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "DARK"
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: false
    }

    val defaultQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_QUALITY] ?: "720p"
    }

    // Default ON, tapi sekarang dua-duanya bisa diatur sendiri-sendiri di Pengaturan.
    val autoSkipIntroFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SKIP_INTRO] ?: true
    }

    val autoSkipOutroFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SKIP_OUTRO] ?: true
    }

    // Default ON, interval 4.5 detik, 6 item, sumber otomatis, gaya Full
    // Bleed -- sama persis kayak perilaku hardcoded
    // FullBleedHeroBannerCarousel sebelumnya, biar behavior gak berubah
    // buat user yang belum pernah sentuh pengaturan ini.
    val heroStyleFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.HERO_STYLE] ?: "FULL_BLEED"
    }

    val heroAutoplayFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HERO_AUTOPLAY] ?: true
    }

    val heroIntervalMsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.HERO_INTERVAL_MS] ?: 4500
    }

    val heroItemCountFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.HERO_ITEM_COUNT] ?: 6
    }

    val heroSourceFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.HERO_SOURCE] ?: "AUTO"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_QUALITY] = quality
        }
    }

    suspend fun setAutoSkipIntro(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SKIP_INTRO] = enabled
        }
    }

    suspend fun setAutoSkipOutro(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SKIP_OUTRO] = enabled
        }
    }

    suspend fun setHeroStyle(style: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HERO_STYLE] = style
        }
    }

    suspend fun setHeroAutoplay(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HERO_AUTOPLAY] = enabled
        }
    }

    suspend fun setHeroIntervalMs(intervalMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HERO_INTERVAL_MS] = intervalMs
        }
    }

    suspend fun setHeroItemCount(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HERO_ITEM_COUNT] = count
        }
    }

    suspend fun setHeroSource(source: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HERO_SOURCE] = source
        }
    }
}
