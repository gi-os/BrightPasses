package com.gios.lightpass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MonoDark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFFBBBBBB),
)

@Composable
fun LightPassTheme(content: @Composable () -> Unit) {
    val fam = remember { akkuratFamilyOrDefault() }
    val type = Typography(
        titleLarge = TextStyle(fontFamily = fam, fontSize = 26.sp, fontWeight = FontWeight.Light),
        bodyLarge = TextStyle(fontFamily = fam, fontSize = 18.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = fam, fontSize = 15.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = fam, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 2.4.sp),
        labelSmall = TextStyle(fontFamily = fam, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp),
    )
    MaterialTheme(colorScheme = MonoDark, typography = type, content = content)
}
