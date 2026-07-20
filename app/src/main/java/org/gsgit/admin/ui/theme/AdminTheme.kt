package org.gsgit.admin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.gsgit.admin.R

@Immutable
data class AdminColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val accent: Color,
    val accentDim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val warning: Color,
    val error: Color,
)

val AdminDarkColors = AdminColors(
    background = Color(0xFFE9F2F6),
    surface = Color(0x99FAFAFA),
    surfaceElevated = Color(0xA6FAFAFA),
    border = Color(0x1A15202B),
    accent = Color(0xFF0088FF),
    accentDim = Color(0xFF0066CC),
    textPrimary = Color(0xFF0F1720),
    textSecondary = Color(0xFF344054),
    textMuted = Color(0xFF5B6B7C),
    warning = Color(0xFF9A5A00),
    error = Color(0xFFC6283D),
)

val LocalAdminColors = compositionLocalOf { AdminDarkColors }

object AdminTheme {
    val colors: AdminColors
        @Composable @ReadOnlyComposable get() = LocalAdminColors.current
}

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

// Совместимые имена цветов для data/UI кода. Источник значений совпадает с GsGit AI/GitHub UI.
val TerminalGreen = AdminDarkColors.accent
val TerminalBackground = AdminDarkColors.background
val TerminalSurface = AdminDarkColors.surface
val TerminalSurfaceHigh = AdminDarkColors.surfaceElevated
val TerminalBorder = AdminDarkColors.border
val TerminalText = AdminDarkColors.textPrimary
val TerminalMuted = AdminDarkColors.textSecondary
val TerminalRed = AdminDarkColors.error
val TerminalAmber = AdminDarkColors.warning

@Composable
fun GsGitAdminTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAdminColors provides AdminDarkColors) {
        Box(Modifier.fillMaxSize().background(AdminDarkColors.background)) { content() }
    }
}
