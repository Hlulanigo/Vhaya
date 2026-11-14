package com.wayfare.ride.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayfare.ride.R

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val WayfareMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

enum class AppThemeMode(val key: String, val label: String) {
    DARK("dark", "Dark"),
    SYSTEM("system", "System"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

@Immutable
data class WayfareExtendedColors(
    val routeHighlight: Color,
    val mapRoad: Color,
    val mapRoadMajor: Color,
    val mapWater: Color,
    val mapPark: Color,
    val mapBlock: Color,
    val destinationMarker: Color,
    val brandHighlight: Color,
    val crimsonAccent: Color,
    val crimsonContainer: Color,
    val positive: Color,
    val warning: Color,
    val subtleBorder: Color,
    val walletCardStart: Color,
    val walletCardEnd: Color,
    val walletCardBorder: Color,
    val walletOnCard: Color,
    val walletAccent: Color,
    val heatLow: Color,
    val heatMid: Color,
    val heatHigh: Color
)

private val LightExtendedColors = WayfareExtendedColors(
    routeHighlight = Color(0xFF52A5FF),
    mapRoad = Color(0xFFFFFFFF),
    mapRoadMajor = Color(0xFFD3DDF0),
    mapWater = Color(0xFFBDD6FA),
    mapPark = Color(0xFFDCE9F4),
    mapBlock = Color(0xFFEAF0FA),
    destinationMarker = Color(0xFFB45D27),
    brandHighlight = Color(0xFFDCE6FF),
    crimsonAccent = Color(0xFFD92B3A),
    crimsonContainer = Color(0xFFFFE5E8),
    positive = Color(0xFF0E8A6E),
    warning = Color(0xFFC97110),
    subtleBorder = Color(0xFFD3DDF0),
    walletCardStart = Color(0xFF1652E6),
    walletCardEnd = Color(0xFF0A2156),
    walletCardBorder = Color(0xFF52A5FF),
    walletOnCard = Color(0xFFFFFFFF),
    walletAccent = Color(0xFF8CC5FF),
    heatLow = Color(0xFFF59E0B),
    heatMid = Color(0xFFEA580C),
    heatHigh = Color(0xFFDC2626)
)

private val DarkExtendedColors = WayfareExtendedColors(
    routeHighlight = Color(0xFF7CB8FF),
    mapRoad = Color(0xFF1D2A42),
    mapRoadMajor = Color(0xFF2B3D5E),
    mapWater = Color(0xFF142A4A),
    mapPark = Color(0xFF142438),
    mapBlock = Color(0xFF0D1526),
    destinationMarker = Color(0xFFF0A16B),
    brandHighlight = Color(0xFF193268),
    crimsonAccent = Color(0xFFFF6B78),
    crimsonContainer = Color(0xFF5C1720),
    positive = Color(0xFF34D399),
    warning = Color(0xFFF5B249),
    subtleBorder = Color(0xFF283754),
    walletCardStart = Color(0xFF183166),
    walletCardEnd = Color(0xFF091225),
    walletCardBorder = Color(0xFF426ED0),
    walletOnCard = Color(0xFFF3F7FF),
    walletAccent = Color(0xFF7CB8FF),
    heatLow = Color(0xFFF5B249),
    heatMid = Color(0xFFFF6D3B),
    heatHigh = Color(0xFFFF2E54)
)

val LocalWayfareColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1652E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0D255E),
    secondary = Color(0xFFE7EEF9),
    onSecondary = Color(0xFF0F1B33),
    secondaryContainer = Color(0xFFDCE6FF),
    onSecondaryContainer = Color(0xFF0D255E),
    tertiary = Color(0xFFD92B3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5E8),
    onTertiaryContainer = Color(0xFF610D16),
    background = Color(0xFFF4F7FC),
    onBackground = Color(0xFF0F1B33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1B33),
    surfaceVariant = Color(0xFFE7EEF9),
    onSurfaceVariant = Color(0xFF53627C),
    outline = Color(0xFFD3DDF0),
    outlineVariant = Color(0xFFE4ECF8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5C93FF),
    onPrimary = Color(0xFF061430),
    primaryContainer = Color(0xFF193268),
    onPrimaryContainer = Color(0xFFEAF1FF),
    secondary = Color(0xFF1A263E),
    onSecondary = Color(0xFFEFF4FF),
    secondaryContainer = Color(0xFF1B3469),
    onSecondaryContainer = Color(0xFFEAF1FF),
    tertiary = Color(0xFFFF6B78),
    onTertiary = Color(0xFF3E040B),
    tertiaryContainer = Color(0xFF5C1720),
    onTertiaryContainer = Color(0xFFFFE5E8),
    background = Color(0xFF090F1C),
    onBackground = Color(0xFFF1F5FF),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFF1F5FF),
    surfaceVariant = Color(0xFF1A263E),
    onSurfaceVariant = Color(0xFFA2B3D1),
    outline = Color(0xFF283754),
    outlineVariant = Color(0xFF1B2740),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val WayfareTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val WayfareShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WayfareTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalWayfareColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WayfareTypography,
            shapes = WayfareShapes,
            content = content
        )
    }
}
