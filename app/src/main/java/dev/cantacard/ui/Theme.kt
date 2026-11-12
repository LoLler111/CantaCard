package dev.cantacard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Brand = Color(0xFF006A60)
private val BrandLight = Color(0xFF7DD5C7)

private val LightColors = lightColorScheme(
    primary = Brand,
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFF8FAF8),
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    background = Color(0xFF0F1514),
    surface = Color(0xFF0F1514),
)

/** Tema dell'app: colori dinamici su Android 12+, altrimenti la palette del marchio. */
@Composable
fun CantaCardTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
