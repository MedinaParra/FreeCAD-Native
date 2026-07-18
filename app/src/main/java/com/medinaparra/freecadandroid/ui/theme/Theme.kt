package com.medinaparra.freecadandroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElegantDarkPrimary,
    onPrimary = ElegantDarkOnPrimary,
    secondary = ElegantDarkSecondary,
    background = ElegantDarkBackground,
    surface = ElegantDarkBackground,
    onBackground = ElegantDarkOnSurface,
    onSurface = ElegantDarkOnSurface,
    onSurfaceVariant = ElegantDarkOnSurfaceVariant,
    outline = ElegantDarkOutline,
    error = ElegantDarkError
)

private val LightColorScheme = lightColorScheme(
    primary = ElegantDarkPrimary,
    onPrimary = ElegantDarkOnPrimary,
    secondary = ElegantDarkSecondary,
    background = ElegantDarkBackground,
    surface = ElegantDarkBackground,
    onBackground = ElegantDarkOnSurface,
    onSurface = ElegantDarkOnSurface,
    onSurfaceVariant = ElegantDarkOnSurfaceVariant,
    outline = ElegantDarkOutline,
    error = ElegantDarkError
)

@Composable
fun FreeCadAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
