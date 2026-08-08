package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
