package dev.cantacard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colori del marchio CantaCard: verde acqua profondo con accento corallo. */
object Brand {
    val Teal950 = Color(0xFF042F2C)
    val Teal900 = Color(0xFF0B4F4A)
    val Teal700 = Color(0xFF0F766E)
    val Teal500 = Color(0xFF14B8A6)
    val Teal300 = Color(0xFF5EEAD4)
    val Coral = Color(0xFFFF7A59)
    val CoralLight = Color(0xFFFF9F85)
    val Sun = Color(0xFFFFC857)
}

private val LightColors = lightColorScheme(
    primary = Brand.Teal700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF3EC),
    onPrimaryContainer = Brand.Teal900,
    secondary = Brand.Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3DA),
    onSecondaryContainer = Color(0xFF5C1A08),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF14201E),
    surface = Color(0xFFF4F7F6),
    onSurface = Color(0xFF14201E),
    surfaceVariant = Color(0xFFE3ECEA),
    onSurfaceVariant = Color(0xFF4A5A57),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFB),
    surfaceContainer = Color(0xFFEEF3F2),
    surfaceContainerHigh = Color(0xFFE8EFEE),
    outline = Color(0xFF8A9A97),
    outlineVariant = Color(0xFFD3DEDB),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Brand.Teal300,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0F4F49),
    onPrimaryContainer = Color(0xFFCDF3EC),
    secondary = Brand.CoralLight,
    onSecondary = Color(0xFF4A1405),
    secondaryContainer = Color(0xFF6B2A17),
    onSecondaryContainer = Color(0xFFFFE3DA),
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFE2ECEA),
    surface = Color(0xFF0B1211),
    onSurface = Color(0xFFE2ECEA),
    surfaceVariant = Color(0xFF233130),
    onSurfaceVariant = Color(0xFFB4C4C1),
    surfaceContainerLowest = Color(0xFF070D0C),
    surfaceContainerLow = Color(0xFF111A19),
    surfaceContainer = Color(0xFF16201F),
    surfaceContainerHigh = Color(0xFF1E2A29),
    outline = Color(0xFF6E807D),
    outlineVariant = Color(0xFF2E3D3B),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Etichetta di sezione: piccola, maiuscola, spaziata. */
val SectionLabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Tema dell'app. Palette fissa del marchio (niente colori dinamici di sistema), così CantaCard
 * ha la stessa faccia su ogni telefono.
 */
@Composable
fun CantaCardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Colore dell'intestazione: verde pieno del marchio (come la schermata di avvio), più scuro col tema scuro. */
val headerColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Brand.Teal900 else Brand.Teal700

/** Testo e icone leggibili sopra [background]. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.55f) Color(0xFF111111) else Color.White

fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction)

fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction)
