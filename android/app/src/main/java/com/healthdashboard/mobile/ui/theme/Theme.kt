package com.healthdashboard.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF155EEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2A63),
    secondary = Color(0xFF0A7E5C),
    secondaryContainer = Color(0xFFD5F6EA),
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF0F8),
    outline = Color(0xFFD3DCE8),
)

private val AppTypography = Typography()

@Composable
fun HealthDashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
