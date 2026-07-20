package org.gsgit.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.paint
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
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import org.gsgit.admin.ui.kyant.components.LiquidBottomTab
import org.gsgit.admin.ui.kyant.components.LiquidBottomTabs
import org.gsgit.admin.ui.kyant.components.LiquidToggle
import org.gsgit.admin.ui.liquid.LocalLiquidBackdrop
import org.gsgit.admin.ui.liquid.RegisterLiquidOverlay
import org.gsgit.admin.ui.theme.AdminTheme
import org.gsgit.admin.ui.theme.JetBrainsMono

// Дизайн-система повторяет каталог Kyant0/AndroidLiquidGlass:
//  * стеклянная панель с текстом = рецепт DialogContent:
//    colorControls(0.2, 1.5) + blur(16dp) + lens(24dp, 48dp, depthEffect) + FAFAFA 60%;
//  * кнопки ВНУТРИ стекла — обычные капсулы (сплошной акцент / полупрозрачный белый),
//    как в его диалоге, без стекла-в-стекле;
//  * нижний бар — LiquidBottomTabs с иконками, выбранную вкладку показывает
//    стеклянный индикатор, контент не перекрашивается вручную.

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
                shape = { RoundedRectangle(if (elevated) 48.dp else 32.dp) },
                effects = {
                    colorControls(brightness = 0.2f, saturation = 1.5f)
                    blur(16.dp.toPx())
                    lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = contentBackdrop,
                onDrawSurface = { drawRect(surface) },
            )
            .padding(horizontal = if (elevated) 24.dp else 18.dp, vertical = if (elevated) 22.dp else 16.dp),
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
    val filled = (accent || destructive) && enabled
    val background = when {
        destructive && enabled -> colors.error
        accent && enabled -> colors.accent
        else -> Color.White.copy(alpha = 0.35f)
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(Capsule())
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .height(46.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdminText(
            label,
            color = if (filled) Color.White else colors.textPrimary,
            fontSize = 14.sp,
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

/** Круглая кнопка-иконка для панелей: простая капсула, без стекла-в-стекле. */
@Composable
fun AdminIconAction(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(Capsule())
            .background(Color.White.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick)
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        AdminIcon(icon, description, Modifier.size(20.dp), tint = AdminTheme.colors.textPrimary)
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
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            AdminText(label, color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedRectangle(16.dp))
                .background(Color.White.copy(alpha = if (focused) 0.55f else 0.4f))
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
    val background = when {
        destructive -> colors.error
        selected -> colors.accent
        else -> Color.White.copy(alpha = 0.35f)
    }
    val foreground = if (selected || destructive) Color.White else colors.textPrimary
    Row(
        Modifier
            .clip(Capsule())
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 8.dp),
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
                .padding(28.dp)
                .widthIn(min = 280.dp, max = 520.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            elevated = true,
        ) {
            AdminText(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(18.dp))
            // Как в DialogContent Kyant: две равные капсулы — нейтральная и акцентная.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dismissLabel.isNotBlank()) {
                    AdminPillButton(dismissLabel, onDismissRequest, Modifier.weight(1f), accent = false)
                }
                AdminPillButton(confirmLabel, onConfirm, Modifier.weight(1f), enabled = confirmEnabled, destructive = destructive, accent = !destructive)
            }
        }
    }
}

@Composable
fun AdminPageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        AdminText(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                shape = { RoundedRectangle(32.dp) },
                effects = {
                    colorControls(brightness = 0.2f, saturation = 1.5f)
                    blur(16.dp.toPx())
                    lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Plain },
                exportedBackdrop = barBackdrop,
                onDrawSurface = { drawRect(colors.surface) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides barBackdrop) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    AdminText("Админ сервера", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AdminText("api.gsgit.org", color = colors.textMuted, fontSize = 10.sp, maxLines = 1)
                }
                AdminIconAction(AdminIcons.Refresh, "обновить", onRefresh, enabled = backend == Backend.GsGit)
                AdminIconAction(AdminIcons.Lock, "заблокировать", onLock)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Backend.entries.forEach { item -> AdminChip(item.name, backend == item) { onBackend(item) } }
            }
        }
    }
}

data class AdminNavItem(val section: Section, val label: String, val icon: ImageVector)

@Composable
fun AdminBottomBar(items: List<AdminNavItem>, selected: Section, onSelect: (Section) -> Unit) {
    val colors = AdminTheme.colors
    val selectedIndexState = rememberUpdatedState(items.indexOfFirst { it.section == selected }.coerceAtLeast(0))
    val iconTint = ColorFilter.tint(colors.textPrimary)
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
                // Контент вкладок одного цвета: выбранную показывает стеклянный
                // индикатор LiquidBottomTabs (сквозь него проступает акцентный слой).
                val painter = rememberVectorPainter(item.icon)
                LiquidBottomTab(onClick = { onSelect(item.section) }) {
                    Box(Modifier.size(24.dp).paint(painter, colorFilter = iconTint))
                    AdminText(item.label, color = colors.textPrimary, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
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
