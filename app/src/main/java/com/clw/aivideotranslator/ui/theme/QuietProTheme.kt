package com.clw.aivideotranslator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.clw.aivideotranslator.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF2457D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEFF),
    onPrimaryContainer = Color(0xFF163A8F),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF667085),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFEF3F2),
    onErrorContainer = Color(0xFF912018),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF0B2459),
    primaryContainer = Color(0xFF183B77),
    onPrimaryContainer = Color(0xFFDCE6FF),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF101722),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF151B26),
    onSurfaceVariant = Color(0xFFB9C7E8),
    outline = Color(0xFF667085),
    error = Color(0xFFF97066),
    errorContainer = Color(0xFF3B1517),
    onErrorContainer = Color(0xFFFDA29B),
)

// UI font candidate. Subtitle/export font profiles stay evidence-gated and are not derived from this.
private val UiArabic = FontFamily(
    Font(R.font.tv1_noto_sans_arabic_regular, weight = FontWeight.Normal),
)

private val QuietTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = UiArabic,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
internal fun QuietProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = QuietTypography,
        content = content,
    )
}
