package org.gsgit.admin.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.gsgit.admin.R
import org.gsgit.admin.data.GlassSettingsStore
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * The only layer backdrop owned by the application scene.
 *
 * CRASH INVARIANT (confirmed by Kyant): [drawBackdrop] consumers must remain siblings
 * of this captured background. Never move [content] inside [LiquidSceneBackground].
 */
val LocalLiquidBackdrop = compositionLocalOf<Backdrop> {
    error("Liquid Glass component used outside LiquidScene")
}

/**
 * Слой сцены (обои) — никогда не переопределяется карточками. Нажимные
 * контролы преломляют его напрямую, чтобы выглядеть как мини-панели даже
 * внутри карточек: слой карточки под ними однотонный, и линза на нём
 * "не видна".
 */
val LocalLiquidSceneBackdrop = compositionLocalOf<Backdrop> {
    error("Liquid Glass component used outside LiquidScene")
}

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
fun LiquidScene(
    wallpaperRes: Int = R.drawable.admin_wallpaper,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val overlayState = remember { LiquidOverlayState() }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLiquidBackdrop provides backdrop,
        LocalLiquidSceneBackdrop provides backdrop,
        LocalLiquidOverlay provides overlayState,
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E0508))) {
            // Source and consumers are siblings. This ordering is intentional and mandatory.
            LiquidSceneBackground(wallpaperRes, Modifier.fillMaxSize().layerBackdrop(backdrop))
            content()
            // Пружинный поп диалогов (пружины кита): появление — spring(0.55, 380)
            // с лёгким перелётом масштаба, закрытие — быстрое сжатие без отскока.
            // displayed переживает entry на время анимации закрытия.
            val overlayProgress = remember { Animatable(0f) }
            var displayed by remember { mutableStateOf<LiquidOverlayEntry?>(null) }
            LaunchedEffect(overlayState.entry) {
                val target = overlayState.entry
                if (target != null) {
                    displayed = target
                    overlayProgress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
                } else if (displayed != null) {
                    overlayProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f))
                    displayed = null
                }
            }
            displayed?.let { entry ->
                val live = overlayState.entry != null
                BackHandler(enabled = live && entry.dismissOnBack, onBack = entry.onDismiss)
                Box(
                    Modifier
                        .fillMaxSize()
                        // Скрим следует за пружиной — чтение прогресса в draw-фазе.
                        .drawBehind {
                            drawRect(Color(0xFF121212), alpha = 0.56f * overlayProgress.value.coerceIn(0f, 1f))
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = live && entry.dismissOnOutside,
                            onClick = entry.onDismiss,
                        ),
                )
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val p = overlayProgress.value
                        alpha = p.coerceIn(0f, 1f)
                        val scale = 0.92f + 0.08f * p
                        scaleX = scale
                        scaleY = scale
                    },
                    content = entry.content,
                )
            }
        }
    }
}

@Composable
private fun LiquidSceneBackground(wallpaperRes: Int, modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(wallpaperRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Настраиваемый скрим обоев (читается в draw-фазе — без рекомпозиций).
        // Внутри слоя-источника: и контент, и стекло видят затемнённые обои.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val scrim = GlassSettingsStore.state.value.wallpaperScrim
                if (scrim > 0f) drawRect(Color.Black, alpha = scrim.coerceIn(0f, 1f))
            },
        )
    }
}

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
