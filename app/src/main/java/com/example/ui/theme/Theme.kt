package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pure OLED Crisis Mode Color Scheme (#000000 maximizes OLED battery lifespan during disasters)
val OledCrisisColorScheme = darkColorScheme(
    primary = SafetyAmberBright,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF18181B),
    onPrimaryContainer = SafetyAmberBright,
    secondary = TacticalCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0C2434),
    onSecondaryContainer = TacticalCyan,
    background = OledBlack,
    onBackground = Color.White,
    surface = OledBlack,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121214),
    onSurfaceVariant = Slate200,
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFF87171),
    onError = Color.Black,
    errorContainer = Color(0xFF3B0A0A),
    onErrorContainer = Color(0xFFFECACA)
)

// Tactical Dark Theme (High-Contrast Readability)
val TacticalDarkColorScheme = darkColorScheme(
    primary = SafetyAmberBright,
    onPrimary = Slate950,
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = SafetyAmberBright,
    secondary = TacticalCyan,
    onSecondary = Slate950,
    secondaryContainer = Color(0xFF0F2942),
    onSecondaryContainer = Color(0xFF7DD3FC),
    background = Slate950,
    onBackground = Color.White,
    surface = Slate900,
    onSurface = Color.White,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    outline = Color(0xFF475569),
    outlineVariant = Slate700,
    error = Color(0xFFF87171),
    onError = Color.Black,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA)
)

val TacticalLightColorScheme = lightColorScheme(
    primary = EmergencyOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = AlertRed,
    onError = Color.White
)

/**
 * Função utilitária para fornecer bordas de alto contraste (#000000 OLED com bordas Verde, Amarelo ou Vermelho)
 */
fun getCrisisBorderColor(severity: Int, isCrisisMode: Boolean): Color {
    return if (isCrisisMode) {
        when (severity) {
            0 -> OperationalGreen
            1 -> WarningYellow
            2, 3 -> Color(0xFFF87171)
            else -> SafetyAmberBright
        }
    } else {
        when (severity) {
            0 -> OperationalGreen.copy(alpha = 0.65f)
            1 -> WarningYellow.copy(alpha = 0.8f)
            2, 3 -> Color(0xFFF87171).copy(alpha = 0.85f)
            else -> Slate700
        }
    }
}

@Composable
fun ResilienceHubTheme(
    isCrisisMode: Boolean = false,
    crisisMode: Boolean = isCrisisMode,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val activeCrisisMode = isCrisisMode || crisisMode
    val colorScheme = when {
        activeCrisisMode -> OledCrisisColorScheme
        darkTheme -> TacticalDarkColorScheme
        else -> TacticalLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    ResilienceHubTheme(darkTheme = darkTheme, content = content)
}
