package app.kurenai.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.kurenai.core.model.Palette
import app.kurenai.core.model.ThemeMode
import app.kurenai.core.model.ThemeSettings

data class KurenaiColors(
    val readerBackground: Color,
    val cardGlass: Color,
    val accentSecondary: Color,
    val success: Color,
    val warning: Color,
)

val LocalKurenaiColors = staticCompositionLocalOf {
    KurenaiColors(
        readerBackground = Color.Black,
        cardGlass = Color(0x99FFFFFF),
        accentSecondary = KurenaiPalette.Gold,
        success = KurenaiPalette.Sage,
        warning = KurenaiPalette.Gold,
    )
}

private val DarkColors = darkColorScheme(
    primary = KurenaiPalette.CrimsonLight,
    onPrimary = KurenaiPalette.Ink,
    primaryContainer = KurenaiPalette.CrimsonDark,
    onPrimaryContainer = KurenaiPalette.Cream,
    secondary = KurenaiPalette.Gold,
    onSecondary = KurenaiPalette.Ink,
    secondaryContainer = Color(0xFF4A3425),
    onSecondaryContainer = Color(0xFFFFE0B7),
    tertiary = KurenaiPalette.Latte,
    onTertiary = KurenaiPalette.Ink,
    background = KurenaiPalette.Espresso,
    onBackground = KurenaiPalette.Cream,
    surface = KurenaiPalette.Coffee,
    onSurface = KurenaiPalette.Cream,
    surfaceVariant = KurenaiPalette.Mocha,
    onSurfaceVariant = KurenaiPalette.Latte,
    outline = Color(0xFF9A7F77),
    outlineVariant = Color(0xFF4B373B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surfaceDim = Color(0xFF140D0F),
    surfaceBright = Color(0xFF4A363A),
    surfaceContainerLowest = KurenaiPalette.Ink,
    surfaceContainerLow = Color(0xFF211619),
    surfaceContainer = KurenaiPalette.Coffee,
    surfaceContainerHigh = KurenaiPalette.Mocha,
    surfaceContainerHighest = Color(0xFF453136),
    inverseSurface = KurenaiPalette.Cream,
    inverseOnSurface = KurenaiPalette.Ink,
    inversePrimary = KurenaiPalette.CrimsonDark,
)

private val LightColors = lightColorScheme(
    primary = KurenaiPalette.CrimsonDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADD),
    onPrimaryContainer = Color(0xFF40000A),
    secondary = Color(0xFF7A5B2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF291800),
    tertiary = Color(0xFF6B5A54),
    onTertiary = Color.White,
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A18),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A18),
    surfaceVariant = Color(0xFFF4DED8),
    onSurfaceVariant = Color(0xFF52443F),
    outline = Color(0xFF84736D),
    outlineVariant = Color(0xFFD7C2BC),
    surfaceDim = Color(0xFFE8D6D0),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFF9ECE7),
    surfaceContainerHigh = Color(0xFFF2E2DC),
    surfaceContainerHighest = Color(0xFFEBD9D3),
    inverseSurface = Color(0xFF362E2C),
    inverseOnSurface = Color(0xFFFBEEE9),
    inversePrimary = KurenaiPalette.CrimsonLight,
)

@Composable
fun KurenaiTheme(
    theme: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = when (theme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val base = when {
        theme.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val scheme: ColorScheme = when {
        theme.amoled && dark -> base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF161012),
        )
        theme.palette == Palette.COFFEE && dark -> base.copy(
            primary = KurenaiPalette.Gold,
            primaryContainer = Color(0xFF4A3425),
            background = Color(0xFF120E0C),
            surface = Color(0xFF1D1512),
        )
        else -> base
    }
    val extras = KurenaiColors(
        readerBackground = if (dark) Color.Black else Color(0xFFFAF6F2),
        cardGlass = if (dark) Color(0x661B1214) else Color(0x8CFFFFFF),
        accentSecondary = scheme.secondary,
        success = KurenaiPalette.Sage,
        warning = KurenaiPalette.Gold,
    )
    CompositionLocalProvider(LocalKurenaiColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KurenaiTypography,
            shapes = KurenaiShapes,
            content = content,
        )
    }
}
