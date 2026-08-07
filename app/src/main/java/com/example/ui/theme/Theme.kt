package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ZenimePrimary,
    onPrimary = ZenimeOnPrimary,
    primaryContainer = ZenimePrimaryContainer,
    onPrimaryContainer = ZenimeOnPrimaryContainer,
    secondary = ZenimeSecondary,
    onSecondary = ZenimeOnSecondary,
    secondaryContainer = ZenimeSecondaryContainer,
    onSecondaryContainer = ZenimeOnSecondaryContainer,
    tertiary = ZenimeTertiary,
    onTertiary = ZenimeOnTertiary,
    background = ZenimeBackgroundDark,
    onBackground = ZenimeOnSurfaceDark,
    surface = ZenimeSurfaceDark,
    onSurface = ZenimeOnSurfaceDark,
    surfaceVariant = ZenimeSurfaceVariantDark,
    onSurfaceVariant = ZenimeOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = ZenimePrimary,
    onPrimary = ZenimeOnPrimary,
    primaryContainer = ZenimePrimaryContainer,
    onPrimaryContainer = ZenimeOnPrimaryContainer,
    secondary = ZenimeSecondary,
    onSecondary = ZenimeOnSecondary,
    secondaryContainer = ZenimeSecondaryContainer,
    onSecondaryContainer = ZenimeOnSecondaryContainer,
    tertiary = ZenimeTertiary,
    onTertiary = ZenimeOnTertiary,
    background = ZenimeBackgroundLight,
    onBackground = ZenimeOnSurfaceLight,
    surface = ZenimeSurfaceLight,
    onSurface = ZenimeOnSurfaceLight,
    surfaceVariant = ZenimeSurfaceVariantLight,
    onSurfaceVariant = ZenimeOnSurfaceVariantLight
)

@Composable
fun ZenimeTheme(
    darkTheme: Boolean = true, // Default dark theme as requested
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
