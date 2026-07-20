package org.gsgit.admin.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/**
 * The only layer backdrop owned by the application scene.
 *
 * CRASH INVARIANT (confirmed by Kyant): [drawBackdrop] consumers must remain siblings
 * of this captured background. Never move [content] inside [LiquidSceneBackground].
 */
val LocalLiquidBackdrop = compositionLocalOf<Backdrop> {
    error("Liquid Glass component used outside LiquidScene")
}

@Immutable
data class LiquidGlassStyle(
    val cornerRadius: Dp = 24.dp,
    val blurRadius: Dp = 8.dp,
    val refractionHeight: Dp = 20.dp,
    val refractionAmount: Dp = 32.dp,
    val surfaceColor: Color = Color(0x6611141A),
    val tint: Color = Color.Unspecified,
)

class LiquidOverlayState internal constructor() {
    internal var entry by mutableStateOf<LiquidOverlayEntry?>(null)
}

internal data class LiquidOverlayEntry(
    val token: Any,
    val dismissOnBack: Boolean,
    val dismissOnOutside: Boolean,
    val onDismiss: () -> Unit,
    val content: @Composable BoxScope.() -> Unit,
)

val LocalLiquidOverlay = compositionLocalOf<LiquidOverlayState> {
    error("Liquid overlay used outside LiquidScene")
}

@Composable
fun LiquidScene(content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val overlayState = remember { LiquidOverlayState() }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiquidBackdrop provides backdrop,
        LocalLiquidOverlay provides overlayState,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Source and consumers are siblings. This ordering is intentional and mandatory.
            LiquidSceneBackground(Modifier.fillMaxSize().layerBackdrop(backdrop))
            content()
            overlayState.entry?.let { entry ->
                BackHandler(enabled = entry.dismissOnBack, onBack = entry.onDismiss)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xA6000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = entry.dismissOnOutside,
                            onClick = entry.onDismiss,
                        ),
                )
                Box(Modifier.fillMaxSize(), content = entry.content)
            }
        }
    }
}

@Composable
private fun LiquidSceneBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.linearGradient(
                0f to Color(0xFF05070B),
                0.48f to Color(0xFF0A1015),
                1f to Color(0xFF020304),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xCC267A68), Color(0x44204C50), Color.Transparent),
                center = Offset(size.width * 0.16f, size.height * 0.12f),
                radius = size.minDimension * 0.68f,
            ),
            radius = size.minDimension * 0.68f,
            center = Offset(size.width * 0.16f, size.height * 0.12f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xAA194A72), Color(0x33345770), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.48f),
                radius = size.minDimension * 0.72f,
            ),
            radius = size.minDimension * 0.72f,
            center = Offset(size.width * 0.92f, size.height * 0.48f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x996B456F), Color(0x226B456F), Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.92f),
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.2f, size.height * 0.92f),
        )
    }
}

fun Modifier.liquidGlass(
    backdrop: Backdrop,
    style: LiquidGlassStyle = LiquidGlassStyle(),
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedRectangle(style.cornerRadius) },
    effects = {
        vibrancy()
        blur(style.blurRadius.toPx())
        lens(style.refractionHeight.toPx(), style.refractionAmount.toPx(), depthEffect = true)
    },
    highlight = { Highlight.Plain },
    onDrawSurface = {
        if (style.tint != Color.Unspecified) {
            drawRect(style.tint, blendMode = BlendMode.Hue)
            drawRect(style.tint.copy(alpha = 0.28f))
        }
        drawRect(style.surfaceColor)
    },
)

@Composable
fun RegisterLiquidOverlay(
    dismissOnBack: Boolean = true,
    dismissOnOutside: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalLiquidOverlay.current
    val token = remember { Any() }
    val entry = LiquidOverlayEntry(token, dismissOnBack, dismissOnOutside, onDismiss, content)
    SideEffect { state.entry = entry }
    DisposableEffect(state, token) {
        onDispose {
            if (state.entry?.token === token) state.entry = null
        }
    }
}
