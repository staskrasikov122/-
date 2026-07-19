package org.gsgit.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.gsgit.admin.R

val TerminalGreen = Color(0xFF2EE66B)
val TerminalBackground = Color(0xFF050605)
val TerminalSurface = Color(0xFF0B0E0C)
val TerminalSurfaceHigh = Color(0xFF121713)
val TerminalBorder = Color(0xFF263029)
val TerminalText = Color(0xFFE5ECE6)
val TerminalMuted = Color(0xFF8E9A91)
val TerminalRed = Color(0xFFFF5D73)
val TerminalAmber = Color(0xFFFFC857)

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val AdminColors = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBackground,
    secondary = TerminalGreen,
    background = TerminalBackground,
    onBackground = TerminalText,
    surface = TerminalSurface,
    onSurface = TerminalText,
    surfaceVariant = TerminalSurfaceHigh,
    onSurfaceVariant = TerminalMuted,
    outline = TerminalBorder,
    error = TerminalRed,
    onError = TerminalBackground,
)

private val AdminTypography = Typography(
    displaySmall = TextStyle(JetBrainsMono, FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(JetBrainsMono, FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(JetBrainsMono, FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(JetBrainsMono, FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(JetBrainsMono, FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(JetBrainsMono, FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(JetBrainsMono, FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(JetBrainsMono, FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun GsGitAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdminColors,
        typography = AdminTypography,
        content = content,
    )
}
