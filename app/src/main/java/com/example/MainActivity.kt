package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.NetworkModule
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.ZenimeDatabase
import com.example.data.repository.AnimeRepository
import com.example.ui.navigation.ZenimeAppNavHost
import com.example.ui.theme.ZenimeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = ZenimeDatabase.getInstance(this)
        val userPrefs = UserPreferencesRepository(this)
        val repository = AnimeRepository(
            api = NetworkModule.api,
            dao = database.zenimeDao(),
            userPrefs = userPrefs
        )

        setContent {
            val themeMode by userPrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = "DARK")
            val dynamicColor by userPrefs.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = false)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "SYSTEM" -> isSystemInDarkTheme()
                else -> true // Default DARK
            }

            ZenimeTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor
            ) {
                ZenimeAppNavHost(repository = repository)
            }
        }
    }
}
