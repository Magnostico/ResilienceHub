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
    primaryContainer = OledBlack,
    onPrimaryContainer = SafetyAmberBright,
    secondary = TacticalCyan,
    onSecondary = Color.Black,
    secondaryContainer = OledBlack,
    onSecondaryContainer = TacticalCyan,
    background = OledBlack,
    onBackground = Color.White,
    surface = OledBlack,
    onSurface = Color.White,
    surfaceVariant = OledBlack,
    onSurfaceVariant = Slate400,
    outline = OledBorder,
    error = AlertRed,
    onError = Color.Black
)

// Tactical Dark Theme
val TacticalDarkColorScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = Slate950,
    primaryContainer = Slate800,
    onPrimaryContainer = SafetyAmberBright,
    secondary = TacticalCyan,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = TacticalCyan,
    background = Slate950,
    onBackground = Slate200,
    surface = Slate900,
    onSurface = Slate200,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    error = AlertRed,
    onError = Color.White
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
            2, 3 -> AlertRed
            else -> SafetyAmber
        }
    } else {
        when (severity) {
            0 -> OperationalGreen.copy(alpha = 0.5f)
            1 -> WarningYellow.copy(alpha = 0.6f)
            2, 3 -> AlertRed.copy(alpha = 0.7f)
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
