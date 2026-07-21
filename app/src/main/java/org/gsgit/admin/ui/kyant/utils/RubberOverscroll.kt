package org.gsgit.admin.ui.kyant.utils

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import kotlin.math.abs

/**
 * Рубберный оверскролл в духе iOS: при упоре в край список тянется за пальцем
 * с нарастающим сопротивлением и одной пружиной возвращается на место
 * (пружина из словаря кита) в момент отпускания.
 *
 * Важно для плавности:
 *  - смещение обновляется СИНХРОННО в mutableFloatState прямо в колбэках
 *    nestedScroll — никаких корутин на каждый кадр (иначе шторм snapTo → фризы);
 *  - растяжка копится ТОЛЬКО от пальца (UserInput), инерция (fling) игнорируется,
 *    иначе после отпускания остаточная инерция выталкивает растяжку заново и
 *    пружина проигрывается второй раз;
 *  - возврат — единственная suspend-анимация в onPreFling (вызывается один раз
 *    на отпускание), читается в graphicsLayer (draw-фаза, без рекомпозиций).
 */
fun Modifier.rubberOverscroll(): Modifier = composed {
    val maxPull = with(LocalDensity.current) { 140.dp.toPx() }
    val pull = remember { mutableFloatStateOf(0f) }
    val returnSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 320f, visibilityThreshold = 0.5f)

    val connection = remember(maxPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Есть остаточная растяжка и палец пошёл обратно — сначала
                // стягиваем её к нулю, только потом отдаём скролл списку.
                val current = pull.floatValue
                val dy = available.y
                if (source == NestedScrollSource.UserInput && current != 0f && current * dy < 0f) {
                    val applied = if (abs(dy) > abs(current)) -current else dy
                    pull.floatValue = current + applied
                    return Offset(0f, applied)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                // Только палец: инерция края отдаётся системному стретчу.
                if (source == NestedScrollSource.UserInput && dy != 0f) {
                    val factor = 1f - (abs(pull.floatValue) / maxPull).coerceIn(0f, 1f)
                    pull.floatValue += dy * 0.5f * factor
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Один возврат на отпускание. Инерция fling растяжку не трогает,
                // поэтому анимация не повторяется.
                if (pull.floatValue != 0f) {
                    animate(pull.floatValue, 0f, animationSpec = returnSpring) { value, _ ->
                        pull.floatValue = value
                    }
                }
                return Velocity.Zero
            }
        }
    }

    this
        .nestedScroll(connection)
        .graphicsLayer { translationY = pull.floatValue }
}
