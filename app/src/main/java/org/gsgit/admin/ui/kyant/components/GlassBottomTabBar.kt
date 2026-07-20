package org.gsgit.admin.ui.kyant.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.gsgit.admin.ui.kyant.utils.DampedDragAnimation
import org.gsgit.admin.ui.kyant.utils.InteractiveHighlight
import org.gsgit.admin.ui.theme.AdminFont
import kotlin.math.abs
import kotlin.math.sign

data class GlassTabItem(val icon: ImageVector, val label: String)

private val BarHeight = 62.dp
private val CapsuleHeight = 52.dp
private val AccentColor = Color(0xFF0091FF)
private val InactiveColor = Color(0xFF999999)

/**
 * Портированный GlassBottomTabBar из GlassFiles: парящая стеклянная капсула
 * с усиленной линзой (32/48dp + хроматическая аберрация), лёгким blur=2 и
 * тонкой заливкой 13% — сквозь бар видно искажённые обои. Индикатор резкий:
 * линза и тени появляются только при нажатии/перетаскивании.
 */
@Composable
fun GlassBottomTabBar(
    backdrop: Backdrop,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<GlassTabItem>,
    modifier: Modifier = Modifier,
    lensHeight: Dp = 32.dp,
    lensAmount: Dp = 48.dp,
    surfaceAlpha: Float = 0.13f,
) {
    val containerColor = Color.Black.copy(alpha = surfaceAlpha)
    val tabsBackdrop = rememberLayerBackdrop()

    Box(modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.88f), contentAlignment = Alignment.CenterStart) {
            val density = LocalDensity.current
            val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size }
            val offsetAnimation = remember { Animatable(0f) }
            val panelOffset by remember(density) {
                derivedStateOf {
                    val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                    with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
                }
            }

            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val animationScope = rememberCoroutineScope()
            var currentIndex by remember(selectedTab) { mutableIntStateOf(selectedTab) }

            val dampedDragAnimation = remember(animationScope) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = selectedTab.toFloat(),
                    valueRange = 0f..(tabs.size - 1).toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f, pressedScale = 78f / 56f,
                    onDragStarted = {},
                    onDragStopped = {
                        val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.size - 1)
                        currentIndex = target
                        animateToValue(target.toFloat())
                        animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    },
                    onDrag = { _, dragAmount ->
                        updateValue((targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabs.size - 1).toFloat()))
                        animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
                )
            }

            LaunchedEffect(selectedTab) { snapshotFlow { selectedTab }.collectLatest { currentIndex = it } }
            LaunchedEffect(dampedDragAnimation) {
                snapshotFlow { currentIndex }.drop(1).collectLatest {
                    dampedDragAnimation.animateToValue(it.toFloat())
                    onTabSelected(it)
                }
            }

            val interactiveHighlight = remember(animationScope) {
                InteractiveHighlight(animationScope) { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            }

            // Слой 1: стеклянный бар — blur=2, усиленная линза.
            Row(
                Modifier.graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(backdrop = backdrop, shape = { Capsule() },
                        effects = { vibrancy(); blur(2f.dp.toPx()); lens(lensHeight.toPx(), lensAmount.toPx(), chromaticAberration = true) },
                        highlight = { Highlight.Ambient },
                        shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.25f)) },
                        innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.35f) },
                        layerBlock = { val p = dampedDragAnimation.pressProgress; val s = lerp(1f, 1f + 16f.dp.toPx() / size.width, p); scaleX = s; scaleY = s },
                        onDrawSurface = { drawRect(containerColor) }
                    ).then(interactiveHighlight.modifier).height(BarHeight).fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { tabs.forEachIndexed { i, tab -> GlassTab { TabContent(tab, i == selectedTab) } } }

            // Слой 2: скрытая акцентная копия — проступает сквозь капсулу.
            Row(
                Modifier.clearAndSetSemantics {}.alpha(0f).layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(backdrop = backdrop, shape = { Capsule() },
                        effects = { val p = dampedDragAnimation.pressProgress; vibrancy(); blur(2f.dp.toPx()); lens(lensHeight.toPx() * p, lensHeight.toPx() * p) },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        onDrawSurface = { drawRect(containerColor) }
                    ).then(interactiveHighlight.modifier).height(CapsuleHeight).fillMaxWidth().padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(AccentColor)),
                verticalAlignment = Alignment.CenterVertically
            ) { tabs.forEachIndexed { i, tab -> GlassTab { TabContent(tab, i == selectedTab) } } }

            // Тап-детектор по всей ширине бара.
            Box(Modifier.height(BarHeight).fillMaxWidth().pointerInput(tabs.size) {
                detectTapGestures { offset -> currentIndex = ((offset.x) / (size.width.toFloat() / tabs.size)).toInt().fastCoerceIn(0, tabs.size - 1) }
            })

            // Слой 3: капсула-индикатор — резкая, эффекты только при нажатии.
            Box(
                Modifier.padding(horizontal = 4.dp)
                    .graphicsLayer {
                        translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }.then(interactiveHighlight.gestureModifier).then(dampedDragAnimation.modifier)
                    .drawBackdrop(backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop), shape = { Capsule() },
                        effects = { val p = dampedDragAnimation.pressProgress; lens(14f.dp.toPx() * p, 18f.dp.toPx() * p, chromaticAberration = true) },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                        shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                        innerShadow = { val p = dampedDragAnimation.pressProgress; InnerShadow(radius = 8f.dp * p, alpha = p) },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX; scaleY = dampedDragAnimation.scaleY
                            val v = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val p = dampedDragAnimation.pressProgress
                            drawRect(Color.White.copy(0.1f), alpha = 1f - p)
                            drawRect(Color.Black.copy(alpha = 0.03f * p))
                        }
                    ).height(CapsuleHeight).fillMaxWidth(1f / tabs.size)
            )
        }
    }
}

@Composable
private fun RowScope.GlassTab(content: @Composable () -> Unit) {
    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}

@Composable
private fun TabContent(item: GlassTabItem, isSelected: Boolean) {
    val color by animateColorAsState(if (isSelected) AccentColor else InactiveColor, tween(200), label = "nc")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(
            rememberVectorPainter(item.icon),
            contentDescription = item.label,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(color),
        )
        Spacer(Modifier.height(1.dp))
        BasicText(
            item.label,
            style = TextStyle(
                color = color,
                fontFamily = AdminFont,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}
