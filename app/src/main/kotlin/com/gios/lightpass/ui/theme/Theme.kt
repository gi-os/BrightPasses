package com.gios.lightpass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monochrome, high-contrast — reads well on the Light Phone's grayscale panel.
private val MonoLight = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF303030),
)
private val MonoDark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFFCFCFCF),
)

private val BigType = Typography(
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LightPassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonoDark,  // always black — matches the Light Phone panel
        typography = BigType,
        content = content,
    )
}
