package org.gsgit.admin.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
        LocalLiquidOverlay provides overlayState,
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E0508))) {
            // Source and consumers are siblings. This ordering is intentional and mandatory.
            LiquidSceneBackground(wallpaperRes, Modifier.fillMaxSize().layerBackdrop(backdrop))
            content()
            overlayState.entry?.let { entry ->
                BackHandler(enabled = entry.dismissOnBack, onBack = entry.onDismiss)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x8F121212))
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
