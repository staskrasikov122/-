package org.gsgit.admin.ui.kyant.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Рубберный оверскролл в духе iOS: при упоре в край список тянется за пальцем
 * с нарастающим сопротивлением и пружиной возвращается на место (пружина из
 * словаря кита). Ловит остаток скролла через nestedScroll и сдвигает контент
 * в graphicsLayer (draw-фаза) — без пер-фреймовых рекомпозиций.
 *
 * Тип [NestedScrollSource] используется только в сигнатурах — значения-энумы
 * (переименованные между версиями Compose) не читаются, поэтому модификатор
 * стабилен к версии.
 */
fun Modifier.rubberOverscroll(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val maxPull = with(LocalDensity.current) { 140.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val returnSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 300f, visibilityThreshold = 0.5f)

    val connection = remember(maxPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Есть остаточная растяжка и палец пошёл обратно — сначала
                // стягиваем её к нулю, только потом отдаём скролл списку.
                val current = offset.value
                val dy = available.y
                if (current != 0f && current * dy < 0f) {
                    val applied = if (abs(dy) > abs(current)) -current else dy
                    scope.launch { offset.snapTo(current + applied) }
                    return Offset(0f, applied)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) {
                    // Сопротивление растёт с растяжкой: чем дальше тянешь, тем туже.
                    val factor = 1f - (abs(offset.value) / maxPull).coerceIn(0f, 1f)
                    scope.launch { offset.snapTo(offset.value + dy * 0.5f * factor) }
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value != 0f) offset.animateTo(0f, returnSpring)
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offset.value != 0f) offset.animateTo(0f, returnSpring)
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(connection)
        .graphicsLayer { translationY = offset.value }
}
