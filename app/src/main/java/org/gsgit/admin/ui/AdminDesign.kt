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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import org.gsgit.admin.ui.kyant.components.GlassBottomTabBar
import org.gsgit.admin.ui.kyant.components.GlassTabItem
import org.gsgit.admin.ui.kyant.components.LiquidToggle
import org.gsgit.admin.ui.kyant.utils.InteractiveHighlight
import org.gsgit.admin.ui.liquid.LocalLiquidBackdrop
import org.gsgit.admin.ui.liquid.RegisterLiquidOverlay
import org.gsgit.admin.ui.theme.AdminFont
import org.gsgit.admin.ui.theme.AdminTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// Тёмная сцена по исходникам Kyant0/AndroidLiquidGlass:
//  * панель с текстом = тёмный вариант его DialogContent:
//    colorControls(0, 1.5) + blur(8dp) + lens(24dp, 48dp, depthEffect) + поверхность 121212;
//  * все кнопки — объёмные стеклянные капсулы (линза + блик + тени + окантовка);
//  * нижний бар — GlassBottomTabBar, портирован из GlassFiles: парящая
//    капсула с усиленной линзой, индикатор резкий.

// Полупрозрачные поверхности стеклянных контролов: на тёмных обоях цвет
// должен пропускать фон, иначе стекло читается как сплошной пластик.
private val NeutralGlassSurface = Color.White.copy(alpha = 0.12f)
private const val TintedGlassAlpha = 0.6f

// Высота плавающего хрома: под него контент получает contentPadding,
// чтобы списки проезжали под кромками и плавно размывались.
private val AdminTopChromeHeight = 112.dp
private val AdminBottomChromeClearance = 98.dp

/** Вставки для полноэкранных списков: контент проходит под шапкой и баром. */
@Composable
fun adminScreenPadding(): PaddingValues {
    val system = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = system.calculateTopPadding() + AdminTopChromeHeight,
        bottom = system.calculateBottomPadding() + AdminBottomChromeClearance,
    )
}

/** Вставки для панелей с собственной шапкой (операции): только нижняя кромка. */
@Composable
fun adminPanelPadding(): PaddingValues {
    val system = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = system.calculateBottomPadding() + AdminBottomChromeClearance,
    )
}

/** Отступ фиксированного контента от верхней кромки (под плавающей шапкой). */
@Composable
fun adminTopChromeInset(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AdminTopChromeHeight

// Прогрессивный блюр кромки: у края размытие максимальное, к центру экрана
// alpha-маска сводит его в ноль (рецепт ProgressiveBlurContent из каталога
// Kyant). Никаких заливок — только размытие того, что проезжает под кромкой.
private const val EdgeBlurMaskShader = """
uniform shader content;

uniform float2 size;
uniform float topEdge;

half4 main(float2 coord) {
    float a = topEdge > 0.5
        ? smoothstep(size.y, size.y * 0.35, coord.y)
        : smoothstep(0.0, size.y * 0.65, coord.y);
    return content.eval(coord) * a;
}"""

@Composable
fun AdminEdgeBlur(topEdge: Boolean, modifier: Modifier = Modifier) {
    val backdrop = LocalLiquidBackdrop.current
    val system = WindowInsets.systemBars.asPaddingValues()
    val stripHeight =
        if (topEdge) system.calculateTopPadding() + 64.dp
        else system.calculateBottomPadding() + 56.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(stripHeight)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(10.dp.toPx())
                    runtimeShaderEffect("AlphaMask", EdgeBlurMaskShader, "content") {
                        setFloatUniform("size", size.width, size.height)
                        setFloatUniform("topEdge", if (topEdge) 1f else 0f)
                    }
                },
                // "Plain": без блика и теней — только размытие кромки.
                highlight = null,
                shadow = null,
            ),
    )
}

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
            fontFamily = AdminFont,
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
                    colorControls(brightness = 0f, saturation = 1.5f)
                    blur(8.dp.toPx())
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

/**
 * Стеклянная капсула с объёмом: линза + Ambient-блик + внешняя и внутренняя
 * тени + тонкая белая окантовка (рецепт GlassFab из GlassFiles). Физика
 * нажатия — как у LiquidButton Kyant.
 */
@Composable
private fun AdminGlassCapsule(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    surfaceColor: Color = NeutralGlassSurface,
    height: Dp = 46.dp,
    horizontalPadding: Dp = 18.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }
    Row(
        modifier
            .drawBackdrop(
                backdrop = LocalLiquidBackdrop.current,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Ambient },
                shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.3f)) },
                innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.3f) },
                layerBlock = if (enabled) {
                    {
                        val width = size.width
                        val heightPx = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)
                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                        val maxDragScale = 4.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / heightPx).fastCoerceAtMost(1f)
                        scaleY = scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (heightPx / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                    drawRect(Color.White.copy(alpha = 0.18f), style = Stroke(width = 0.8.dp.toPx()))
                },
            )
            .clip(Capsule())
            // Обратную связь даёт физика сжатия капсулы, стандартная индикация не нужна.
            .clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)
            .then(
                if (enabled) Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier)
                else Modifier
            )
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
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
    val filled = (accent || destructive) && enabled
    val tint = if (destructive) colors.error else colors.accent
    AdminGlassCapsule(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.55f),
        enabled = enabled,
        surfaceColor = if (filled) tint.copy(alpha = TintedGlassAlpha) else NeutralGlassSurface,
    ) {
        AdminText(
            label,
            color = if (enabled) Color.White else colors.textMuted,
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
        AdminText(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

/** Круглая стеклянная кнопка-иконка для панелей. */
@Composable
fun AdminIconAction(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    val tint = colors.accent
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(10.dp.toPx(), 20.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Ambient },
                shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.25f)) },
                innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.25f) },
                onDrawSurface = {
                    drawRect(if (active) tint.copy(alpha = TintedGlassAlpha) else NeutralGlassSurface)
                    drawRect(Color.White.copy(alpha = 0.16f), style = Stroke(width = 0.8.dp.toPx()))
                },
            )
            .clip(Capsule())
            .clickable(enabled = enabled, onClick = onClick)
            .size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        AdminIcon(icon, description, Modifier.size(20.dp), tint = Color.White)
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
            AdminText(label, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedRectangle(16.dp))
                .background(Color.White.copy(alpha = if (focused) 0.16f else 0.09f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = if (maxLines == 1) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && !placeholder.isNullOrBlank()) AdminText(placeholder, color = colors.textMuted, fontSize = 14.sp)
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
                    textStyle = TextStyle(color = colors.textPrimary, fontFamily = AdminFont, fontSize = 14.sp, lineHeight = 1.35.em),
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
            AdminText(label, fontSize = 14.sp)
            if (!description.isNullOrBlank()) AdminText(description, color = colors.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun AdminChip(label: String, selected: Boolean = false, destructive: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = AdminTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    val tint = when {
        destructive -> colors.error
        selected -> colors.accent
        else -> Color.Unspecified
    }
    Row(
        Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(8.dp.toPx(), 16.dp.toPx())
                },
                highlight = { Highlight.Ambient },
                shadow = { Shadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.22f)) },
                innerShadow = { InnerShadow(radius = 3.dp, alpha = 0.22f) },
                onDrawSurface = {
                    drawRect(if (tint.isSpecified) tint.copy(alpha = TintedGlassAlpha) else NeutralGlassSurface)
                    drawRect(Color.White.copy(alpha = 0.14f), style = Stroke(width = 0.6.dp.toPx()))
                },
            )
            .clip(Capsule())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdminText(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
fun AdminKeyValue(key: String, value: String, valueColor: Color = AdminTheme.colors.textPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        AdminText(key, color = AdminTheme.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(0.42f))
        AdminText(value, color = valueColor, fontSize = 12.sp, modifier = Modifier.weight(0.58f))
    }
}

@Composable
fun AdminSectionLabel(text: String, modifier: Modifier = Modifier) {
    AdminText(text.uppercase(), modifier, color = AdminTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
        if (!label.isNullOrBlank()) { Spacer(Modifier.width(6.dp)); AdminText(label, color = AdminTheme.colors.textMuted, fontSize = 12.sp) }
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
            AdminText(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(18.dp))
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
        AdminText(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        AdminText(subtitle, color = AdminTheme.colors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
fun AdminTopBar(
    onRefresh: () -> Unit,
    onLock: () -> Unit,
    onOperations: () -> Unit,
    operationsActive: Boolean,
    backend: Backend,
    onBackend: (Backend) -> Unit,
) {
    val colors = AdminTheme.colors
    // Без подложки: заголовок и стеклянные кнопки лежат прямо на обоях.
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                AdminText("Админ сервера", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AdminText("api.gsgit.org", color = colors.textMuted, fontSize = 11.sp, maxLines = 1)
            }
            AdminIconAction(AdminIcons.Refresh, "обновить", onRefresh, enabled = backend == Backend.GsGit)
            AdminIconAction(AdminIcons.Settings, "операции", onOperations, active = operationsActive)
            AdminIconAction(AdminIcons.Lock, "заблокировать", onLock)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Backend.entries.forEach { item -> AdminChip(item.name, backend == item) { onBackend(item) } }
        }
    }
}

data class AdminNavItem(val section: Section, val label: String, val icon: ImageVector)

@Composable
fun AdminBottomBar(items: List<AdminNavItem>, selected: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    val rawIndex = items.indexOfFirst { it.section == selected }
    // Когда открыт раздел вне бара (операции), индикатор остаётся на последней вкладке.
    var lastIndex by rememberSaveable { mutableIntStateOf(0) }
    if (rawIndex >= 0 && rawIndex != lastIndex) lastIndex = rawIndex
    val tabs = remember(items) { items.map { GlassTabItem(it.icon, it.label) } }
    GlassBottomTabBar(
        backdrop = LocalLiquidBackdrop.current,
        selectedTab = if (rawIndex >= 0) rawIndex else lastIndex,
        onTabSelected = { index -> items.getOrNull(index)?.let { onSelect(it.section) } },
        tabs = tabs,
        modifier = modifier.navigationBarsPadding(),
    )
}

@Composable
fun AdminToast(message: String, modifier: Modifier = Modifier) {
    AdminCard(modifier.widthIn(max = 520.dp), elevated = true) {
        AdminText(message, fontSize = 13.sp)
    }
}
