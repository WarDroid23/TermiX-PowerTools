package com.example.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

data class ConsoleColors(
    val background: Color,
    val text: Color,
    val primary: Color,
    val error: Color,
    val success: Color,
    val info: Color,
    val warning: Color,
    val cardBackground: Color,
    val cursorColor: Color
) {
    val fontFamily: FontFamily = FontFamily.Monospace
}

fun getColorsForTheme(theme: TerminalTheme): ConsoleColors {
    return when (theme) {
        TerminalTheme.IMMERSIVE -> ConsoleColors(
            background = Color(0xFF0F1115),
            text = Color(0xFFE6E1E5),
            primary = Color(0xFFD0BCFF),
            error = Color(0xFFFF5F56),
            success = Color(0xFF4ADE80),
            info = Color(0xFF7CDBF3),
            warning = Color(0xFFFFBD2E),
            cardBackground = Color(0xFF1C1B1F),
            cursorColor = Color(0xFFD0BCFF)
        )
        TerminalTheme.MATRIX -> ConsoleColors(
            background = Color(0xFF040C04),
            text = Color(0xFF44FF44),
            primary = Color(0xFF00FF00),
            error = Color(0xFFFF3333),
            success = Color(0xFF88FF88),
            info = Color(0xFF33B3FF),
            warning = Color(0xFFFFCC00),
            cardBackground = Color(0xFF0C240E),
            cursorColor = Color(0xFF00FF00)
        )
        TerminalTheme.AMBER -> ConsoleColors(
            background = Color(0xFF140B00),
            text = Color(0xFFFFB000),
            primary = Color(0xFFFF8000),
            error = Color(0xFFFF4444),
            success = Color(0xFFFFC04D),
            info = Color(0xFFFFD480),
            warning = Color(0xFFFF9900),
            cardBackground = Color(0xFF2B1700),
            cursorColor = Color(0xFFFFB000)
        )
        TerminalTheme.CYBERPUNK -> ConsoleColors(
            background = Color(0xFF0A0211),
            text = Color(0xFF00FFCC),
            primary = Color(0xFFFF007F),
            error = Color(0xFFF92672),
            success = Color(0xFF00FF66),
            info = Color(0xFF66D9EF),
            warning = Color(0xFFFD971F),
            cardBackground = Color(0xFF220836),
            cursorColor = Color(0xFFFF007F)
        )
        TerminalTheme.MONOKAI -> ConsoleColors(
            background = Color(0xFF1E1E1E),
            text = Color(0xFFF8F8F2),
            primary = Color(0xFFA6E22E),
            error = Color(0xFFF92672),
            success = Color(0xFFA6E22E),
            info = Color(0xFF66D9EF),
            warning = Color(0xFFE6DB74),
            cardBackground = Color(0xFF2E2E2E),
            cursorColor = Color(0xFFF8F8F2)
        )
        TerminalTheme.SNOW -> ConsoleColors(
            background = Color(0xFFF2F5F8),
            text = Color(0xFF1E2F3B),
            primary = Color(0xFF0055B3),
            error = Color(0xFFCC0000),
            success = Color(0xFF008822),
            info = Color(0xFF006699),
            warning = Color(0xFF997300),
            cardBackground = Color(0xFFE1E7EE),
            cursorColor = Color(0xFF0055B3)
        )
    }
}
