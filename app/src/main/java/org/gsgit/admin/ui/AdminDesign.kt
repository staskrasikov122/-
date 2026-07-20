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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.isSpecified
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
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import org.gsgit.admin.ui.liquid.KyantInteractiveHighlight
import org.gsgit.admin.ui.liquid.KyantLiquidBottomTab
import org.gsgit.admin.ui.liquid.KyantLiquidBottomTabs
import org.gsgit.admin.ui.liquid.KyantLiquidToggle
import org.gsgit.admin.ui.liquid.LiquidGlassStyle
import org.gsgit.admin.ui.liquid.LocalLiquidBackdrop
import org.gsgit.admin.ui.liquid.RegisterLiquidOverlay
import org.gsgit.admin.ui.liquid.liquidGlass
import org.gsgit.admin.ui.theme.AdminTheme
import org.gsgit.admin.ui.theme.JetBrainsMono
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

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
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop,
                LiquidGlassStyle(
                    cornerRadius = if (elevated) 32.dp else 24.dp,
                    blurRadius = if (elevated) 14.dp else 8.dp,
                    refractionHeight = if (elevated) 28.dp else 18.dp,
                    refractionAmount = if (elevated) 44.dp else 30.dp,
                    surfaceColor = if (elevated) Color(0xA61A1C22) else Color(0x70101418),
                ),
            )
            .padding(if (elevated) 20.dp else 16.dp),
        content = content,
    )
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
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { KyantInteractiveHighlight(scope) }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.42f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                layerBlock = if (enabled) {
                    {
                        val width = size.width
                        val height = size.height
                        val progress = highlight.pressProgress
                        val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)
                        val maxOffset = size.minDimension
                        val offset = highlight.offset
                        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                        val dragScale = 4.dp.toPx() / size.height
                        val angle = atan2(offset.y, offset.x)
                        scaleX = scale + dragScale * abs(cos(angle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
                        scaleY = scale + dragScale * abs(sin(angle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
                    }
                } else null,
                highlight = { Highlight.Plain },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = if (destructive) 0.38f else 0.22f))
                    }
                    drawRect(Color(0x52101418))
                },
            )
            .clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier.then(highlight.modifier).then(highlight.gestureModifier) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AdminText(label, color = if (enabled) colors.textPrimary else colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
    Box(
        modifier
            .liquidGlass(
                backdrop,
                LiquidGlassStyle(18.dp, 3.dp, 8.dp, 14.dp, Color(0x38101418), tint.copy(alpha = 0.25f)),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { AdminText(label, color = tint, fontWeight = FontWeight.Medium, maxLines = 1) }
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
                .liquidGlass(
                    backdrop,
                    LiquidGlassStyle(18.dp, 4.dp, 10.dp, 18.dp, Color(0x54101418)),
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
        KyantLiquidToggle({ checked }, { if (it != checked) onToggle() }, LocalLiquidBackdrop.current)
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
    val modifier = Modifier
        .liquidGlass(
            LocalLiquidBackdrop.current,
            LiquidGlassStyle(16.dp, 2.dp, 7.dp, 12.dp, if (selected) tint.copy(alpha = 0.24f) else Color(0x38101418), tint.copy(alpha = 0.18f)),
        )
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 10.dp, vertical = 6.dp)
    Box(modifier) { AdminText(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
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
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .liquidGlass(LocalLiquidBackdrop.current, LiquidGlassStyle(24.dp, 10.dp, 12.dp, 20.dp, Color(0x52101418))),
    ) {
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

data class AdminNavItem(val section: Section, val label: String, val glyph: String)

@Composable
fun AdminBottomBar(items: List<AdminNavItem>, selected: Section, onSelect: (Section) -> Unit) {
    val colors = AdminTheme.colors
    val selectedIndex = items.indexOfFirst { it.section == selected }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
        KyantLiquidBottomTabs(
            selectedTabIndex = { selectedIndex },
            onTabSelected = { index -> items.getOrNull(index)?.let { onSelect(it.section) } },
            backdrop = LocalLiquidBackdrop.current,
            tabsCount = items.size,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items.forEach { item ->
                val active = item.section == selected
                val tint = if (active) colors.accent else colors.textPrimary
                KyantLiquidBottomTab(onClick = { onSelect(item.section) }) {
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
