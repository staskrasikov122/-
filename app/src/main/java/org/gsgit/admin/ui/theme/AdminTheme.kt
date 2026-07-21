package org.gsgit.admin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.gsgit.admin.R
import org.gsgit.admin.data.GlassSettingsStore

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

// Тёмная палитра под обои: поверхности — 121212 в духе тёмной темы Kyant,
// акцент 0091FF из его каталога, текст белый с шагами прозрачности.
val AdminDarkColors = AdminColors(
    background = Color(0xFF0E0508),
    surface = Color(0x59121212),
    surfaceElevated = Color(0x80121212),
    border = Color(0x21FFFFFF),
    accent = Color(0xFF0091FF),
    accentDim = Color(0xFF0074CC),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xB3FFFFFF),
    textMuted = Color(0x80FFFFFF),
    warning = Color(0xFFFFB340),
    error = Color(0xFFFF453A),
)

val LocalAdminColors = compositionLocalOf { AdminDarkColors }

object AdminTheme {
    val colors: AdminColors
        @Composable @ReadOnlyComposable get() = LocalAdminColors.current
}

// Inter — открытый аналог SF Pro (OFL), даёт эппловскую типографику.
val AdminFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Composable
fun GsGitAdminTheme(content: @Composable () -> Unit) {
    // Динамический акцент из настроек стекла; derivedStateOf — рекомпозиция
    // темы только при фактической смене цвета, а не любого параметра.
    val accentArgb by remember { derivedStateOf { GlassSettingsStore.state.value.accentColor } }
    val accent = Color(accentArgb)
    CompositionLocalProvider(
        LocalAdminColors provides AdminDarkColors.copy(accent = accent, accentDim = accent.copy(alpha = 0.8f)),
    ) {
        Box(Modifier.fillMaxSize().background(AdminDarkColors.background)) { content() }
    }
}
