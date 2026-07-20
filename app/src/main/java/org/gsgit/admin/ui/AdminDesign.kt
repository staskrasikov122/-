package org.gsgit.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
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

// Радиусы дизайн-системы: стеклянный хром — капсулы, карточки контента — 24,
// поля ввода — 14. Жидкое стекло (lens) применяется только к плавающему хрому:
// кнопкам, барам, тумблерам. Контент лежит на матовых frosted-панелях.
internal val AdminCardRadius = 24.dp
internal val AdminFieldRadius = 14.dp

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
    val surface = if (elevated) colors.surfaceElevated else colors.surface
    val backdrop = LocalLiquidBackdrop.current
    val contentBackdrop = rememberLayerBackdrop()
    Column(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(AdminCardRadius) },
                effects = {
                    vibrancy()
                    blur(if (elevated) 16.dp.toPx() else 10.dp.toPx())
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = contentBackdrop,
                onDrawSurface = { drawRect(surface) },
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
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
    val filled = accent || destructive
    val tint = if (destructive) colors.error else colors.accent
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        isInteractive = enabled,
        tint = if (filled && enabled) tint else Color.Unspecified,
        surfaceColor = if (filled && enabled) Color.Unspecified else Color.White.copy(alpha = 0.5f),
    ) {
        AdminText(
            label,
            color = when {
                !enabled -> colors.textMuted
                filled -> Color.White
                else -> colors.textPrimary
            },
            fontSize = 13.sp,
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
    val tint = when {
        !enabled -> colors.textMuted
        destructive -> colors.error
        else -> colors.accent
    }
    Row(
        modifier
            .clip(Capsule())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AdminText(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

/** Компактная стеклянная кнопка для панелей (верхний бар). */
@Composable
fun AdminBarAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(10.dp.toPx(), 20.dp.toPx())
                },
                highlight = { Highlight.Plain },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.45f)) },
            )
            .clip(Capsule())
            .clickable(enabled = enabled, onClick = onClick)
            .height(38.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        AdminText(label, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedRectangle(AdminFieldRadius)
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            AdminText(label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.55f))
                .border(1.dp, if (focused) colors.accent.copy(alpha = 0.55f) else colors.border, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
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
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onToggle).padding(vertical = 4.dp)) {
            AdminText(label, fontSize = 13.sp)
            if (!description.isNullOrBlank()) AdminText(description, color = colors.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun AdminChip(label: String, selected: Boolean = false, destructive: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = AdminTheme.colors
    val shape = Capsule()
    val background = when {
        destructive -> colors.error
        selected -> colors.accent
        else -> Color.White.copy(alpha = 0.55f)
    }
    val foreground = if (selected || destructive) Color.White else colors.textSecondary
    Row(
        Modifier
            .clip(shape)
            .background(background)
            .then(if (selected || destructive) Modifier else Modifier.border(1.dp, colors.border, shape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdminText(label, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
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
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (dismissLabel.isNotBlank()) AdminTextAction(dismissLabel, onDismissRequest)
                Spacer(Modifier.width(8.dp))
                AdminPillButton(confirmLabel, onConfirm, enabled = confirmEnabled, destructive = destructive, accent = !destructive)
            }
        }
    }
}

@Composable
fun AdminPageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AdminText("> $title", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        AdminText(subtitle, color = AdminTheme.colors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
fun AdminTopBar(onRefresh: () -> Unit, onLock: () -> Unit, backend: Backend, onBackend: (Backend) -> Unit) {
    val colors = AdminTheme.colors
    val sceneBackdrop = LocalLiquidBackdrop.current
    val barBackdrop = rememberLayerBackdrop()
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .drawBackdrop(
                backdrop = sceneBackdrop,
                shape = { RoundedRectangle(28.dp) },
                effects = {
                    vibrancy()
                    blur(12.dp.toPx())
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = barBackdrop,
                onDrawSurface = { drawRect(colors.surface) },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides barBackdrop) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    AdminText("> админ сервера", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AdminText("api.gsgit.org", color = colors.textMuted, fontSize = 10.sp, maxLines = 1)
                }
                AdminBarAction("↻", onRefresh, enabled = backend == Backend.GsGit)
                AdminBarAction("замок", onLock)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Backend.entries.forEach { item -> AdminChip(item.name, backend == item) { onBackend(item) } }
            }
        }
    }
}

data class AdminNavItem(val section: Section, val label: String, val glyph: String)

@Composable
fun AdminBottomBar(items: List<AdminNavItem>, selected: Section, onSelect: (Section) -> Unit) {
    val colors = AdminTheme.colors
    val selectedIndexState = rememberUpdatedState(items.indexOfFirst { it.section == selected }.coerceAtLeast(0))
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        LiquidBottomTabs(
            // Стабильная лямбда: новая на каждую рекомпозицию сбрасывает внутреннее
            // состояние LiquidBottomTabs, и индикатор "залипает" на чужой вкладке.
            selectedTabIndex = remember { { selectedIndexState.value } },
            onTabSelected = { index -> items.getOrNull(index)?.let { onSelect(it.section) } },
            backdrop = LocalLiquidBackdrop.current,
            tabsCount = items.size,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items.forEach { item ->
                val active = item.section == selected
                val tint = if (active) colors.accent else colors.textSecondary
                LiquidBottomTab(onClick = { onSelect(item.section) }) {
                    AdminText(item.glyph, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    AdminText(item.label, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun AdminToast(message: String, modifier: Modifier = Modifier) {
    AdminCard(modifier.widthIn(max = 520.dp), elevated = true) {
        AdminText(message, fontSize = 12.sp)
    }
}
