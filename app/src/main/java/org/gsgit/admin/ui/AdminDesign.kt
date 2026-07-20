package org.gsgit.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import org.gsgit.admin.ui.kyant.components.LiquidBottomTab
import org.gsgit.admin.ui.kyant.components.LiquidBottomTabs
import org.gsgit.admin.ui.kyant.components.LiquidButton
import org.gsgit.admin.ui.kyant.components.LiquidToggle
import org.gsgit.admin.ui.liquid.LocalLiquidBackdrop
import org.gsgit.admin.ui.liquid.RegisterLiquidOverlay
import org.gsgit.admin.ui.theme.AdminTheme
import org.gsgit.admin.ui.theme.JetBrainsMono

internal val AdminControlRadius = 24.dp

@Composable
fun AdminText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AdminTheme.colors.textPrimary,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = 1.35.em,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        style = TextStyle(
            color = color,
            fontFamily = JetBrainsMono,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            textAlign = textAlign,
        ),
    )
}

@Composable
fun AdminIcon(imageVector: ImageVector, description: String?, modifier: Modifier = Modifier, tint: Color = AdminTheme.colors.textSecondary) {
    Image(rememberVectorPainter(imageVector), description, modifier, colorFilter = ColorFilter.tint(tint))
}

@Composable
fun AdminCard(modifier: Modifier = Modifier, elevated: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalLiquidBackdrop.current
    val contentBackdrop = rememberLayerBackdrop()
    Column(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(if (elevated) 48.dp else 32.dp) },
                effects = {
                    if (elevated) {
                        colorControls(brightness = 0.2f, saturation = 1.5f)
                        blur(16.dp.toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    } else {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    }
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = contentBackdrop,
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = if (elevated) 0.6f else 0.5f))
                },
            )
            .padding(if (elevated) 20.dp else 16.dp),
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides contentBackdrop) { content() }
    }
}

@Composable
fun AdminPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    accent: Boolean = true,
) {
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    val tint = when {
        !enabled -> colors.textMuted
        destructive -> colors.error
        accent -> colors.accent
        else -> colors.textSecondary
    }
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        isInteractive = enabled,
        tint = if (accent || destructive) tint else Color.Unspecified,
        surfaceColor = if (accent || destructive) Color.Unspecified else Color.White.copy(alpha = 0.3f),
    ) {
        AdminText(
            label,
            color = when {
                !enabled -> colors.textMuted
                accent || destructive -> Color.White
                else -> colors.textPrimary
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AdminTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    val tint = when {
        !enabled -> colors.textMuted
        destructive -> colors.error
        else -> colors.accent
    }
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        isInteractive = enabled,
        surfaceColor = Color.White.copy(alpha = 0.3f),
    ) {
        AdminText(label, color = tint, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun AdminTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            AdminText(label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(18.dp) },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) },
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = if (maxLines == 1) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && !placeholder.isNullOrBlank()) AdminText(placeholder, color = colors.textMuted)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = maxLines == 1,
                    minLines = minLines,
                    maxLines = maxLines,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(colors.accent),
                    textStyle = TextStyle(color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 1.35.em),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun AdminCheckRow(label: String, checked: Boolean, onToggle: () -> Unit, description: String? = null) {
    val colors = AdminTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidToggle({ checked }, { if (it != checked) onToggle() }, LocalLiquidBackdrop.current)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).clickable(onClick = onToggle).padding(vertical = 4.dp)) {
            AdminText(label, fontSize = 13.sp)
            if (!description.isNullOrBlank()) AdminText(description, color = colors.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun AdminChip(label: String, selected: Boolean = false, destructive: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = AdminTheme.colors
    val tint = when {
        destructive -> colors.error
        selected -> colors.accent
        else -> colors.textSecondary
    }
    LiquidButton(
        onClick = onClick ?: {},
        backdrop = LocalLiquidBackdrop.current,
        isInteractive = onClick != null,
        tint = if (selected || destructive) tint else Color.Unspecified,
        surfaceColor = if (selected || destructive) Color.Unspecified else Color.White.copy(alpha = 0.3f),
    ) {
        AdminText(
            label,
            color = if (selected || destructive) Color.White else tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun AdminKeyValue(key: String, value: String, valueColor: Color = AdminTheme.colors.textPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        AdminText(key, color = AdminTheme.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(0.42f))
        AdminText(value, color = valueColor, fontSize = 11.sp, modifier = Modifier.weight(0.58f))
    }
}

@Composable
fun AdminSectionLabel(text: String, modifier: Modifier = Modifier) {
    AdminText(text.uppercase(), modifier, color = AdminTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun AdminHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AdminTheme.colors.border))
}

private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

@Composable
fun AdminSpinner(label: String? = null, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(80); frame = (frame + 1) % spinnerFrames.size } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AdminText(spinnerFrames[frame], color = AdminTheme.colors.accent)
        if (!label.isNullOrBlank()) { Spacer(Modifier.width(6.dp)); AdminText(label, color = AdminTheme.colors.textMuted, fontSize = 11.sp) }
    }
}

@Composable
fun AdminDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String = "отмена",
    content: @Composable ColumnScope.() -> Unit,
) {
    RegisterLiquidOverlay(onDismiss = onDismissRequest) {
        AdminCard(
            Modifier
                .align(Alignment.Center)
                .padding(20.dp)
                .widthIn(min = 280.dp, max = 520.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            elevated = true,
        ) {
            AdminText("> $title", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(11.dp))
            content()
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (dismissLabel.isNotBlank()) AdminTextAction(dismissLabel, onDismissRequest)
                Spacer(Modifier.width(6.dp))
                AdminPillButton(confirmLabel, onConfirm, enabled = confirmEnabled, destructive = destructive, accent = !destructive)
            }
        }
    }
}

@Composable
fun AdminPageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AdminText("> $title", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        AdminText(subtitle, color = AdminTheme.colors.textMuted, fontSize = 11.sp)
    }
}

@Composable
fun AdminTopBar(onRefresh: () -> Unit, onLock: () -> Unit, backend: Backend, onBackend: (Backend) -> Unit) {
    val colors = AdminTheme.colors
    val sceneBackdrop = LocalLiquidBackdrop.current
    val barBackdrop = rememberLayerBackdrop()
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .drawBackdrop(
                backdrop = sceneBackdrop,
                shape = { RoundedRectangle(32.dp) },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = barBackdrop,
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) },
            ),
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides barBackdrop) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AdminText("> админ сервера", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    AdminText("api.gsgit.org", color = colors.textMuted, fontSize = 10.sp)
                }
                AdminTextAction("↻", onRefresh, enabled = backend == Backend.GsGit)
                AdminTextAction("[ замок ]", onLock)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Backend.entries.forEach { item -> AdminChip(item.name, backend == item) { onBackend(item) } }
            }
            Spacer(Modifier.height(6.dp))
            AdminHairline()
        }
    }
}

data class AdminNavItem(val section: Section, val label: String, val glyph: String)

@Composable
fun AdminBottomBar(items: List<AdminNavItem>, selected: Section, onSelect: (Section) -> Unit) {
    val colors = AdminTheme.colors
    val selectedIndex = items.indexOfFirst { it.section == selected }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
        LiquidBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = { index -> items.getOrNull(index)?.let { onSelect(it.section) } },
            backdrop = LocalLiquidBackdrop.current,
            tabsCount = items.size,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items.forEach { item ->
                val active = item.section == selected
                val tint = if (active) colors.accent else colors.textPrimary
                LiquidBottomTab(onClick = { onSelect(item.section) }) {
                    AdminText(item.glyph, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    AdminText(item.label, color = tint, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun AdminToast(message: String, modifier: Modifier = Modifier) {
    AdminCard(modifier.widthIn(max = 520.dp), elevated = true) {
        AdminText(message, fontSize = 11.sp)
    }
}
