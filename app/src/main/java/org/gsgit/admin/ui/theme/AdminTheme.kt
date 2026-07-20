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
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceElevated = Color(0xFF141414),
    border = Color(0xFF1F1F1F),
    accent = Color(0xFFA8D982),
    accentDim = Color(0xFF6B8C54),
    textPrimary = Color(0xFFE0E0E0),
    textSecondary = Color(0xFF999999),
    textMuted = Color(0xFF5C5C5C),
    warning = Color(0xFFE5C07B),
    error = Color(0xFFE06C75),
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
