package org.gsgit.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.gsgit.admin.ui.kyant.components.LiquidSlider
import org.gsgit.admin.ui.liquid.LocalLiquidBackdrop
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * HSV-круг выбора акцента: hue по углу (sweepGradient), saturation по радиусу
 * (radialGradient white→transparent), value отдельным слайдером (переиспользуем
 * ИСПРАВЛЕННЫЙ LiquidSlider). Плюс пресеты и hex.
 *
 * Дёшево: Color.hsv — простая арифметика, запись в стор задебаунсена (400 мс).
 */
@Composable
fun AccentColorWheel(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }        // 0..360
    var sat by remember { mutableFloatStateOf(hsv[1]) }        // 0..1
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.2f, 1f)) } // 0.2..1

    fun emit() = onColorChange(Color.hsv(hue, sat, value))

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(220.dp)
                .pointerInput(Unit) {
                    fun pick(pos: Offset) {
                        val r = size.minDimension / 2f
                        val dx = pos.x - size.width / 2f
                        val dy = pos.y - size.height / 2f
                        hue = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f
                        sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                        emit()
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pick(down.position)
                        drag(down.id) { pick(it.position) }
                    }
                },
        ) {
            val r = size.minDimension / 2f
            val hues = (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
            drawCircle(Brush.sweepGradient(hues, center), r, center)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, r), r, center)
            // затемнение круга под value — превью совпадает с выбранным цветом
            drawCircle(Color.Black.copy(alpha = 1f - value), r, center)
            // маркер выбора
            val ang = Math.toRadians(hue.toDouble())
            val mp = Offset(
                center.x + (cos(ang) * sat * r).toFloat(),
                center.y + (sin(ang) * sat * r).toFloat(),
            )
            drawCircle(Color.White, 8.dp.toPx(), mp, style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color.hsv(hue, sat, value), 6.dp.toPx(), mp)
        }

        Spacer(Modifier.height(14.dp))
        // Яркость — на ИСПРАВЛЕННОМ LiquidSlider
        LiquidSlider(
            value = { value },
            onValueChange = { value = it; emit() },
            valueRange = 0.2f..1f,
            visibilityThreshold = 0.001f,
            backdrop = LocalLiquidBackdrop.current,
        )

        Spacer(Modifier.height(14.dp))
        // Пресеты быстрого выбора
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFF0091FF, 0xFFFF3B30, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2, 0xFF64D2FF, 0xFFFFFFFF)
                .forEach { argb ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .clickable {
                                val h = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb.toInt(), h)
                                hue = h[0]; sat = h[1]; value = h[2].coerceIn(0.2f, 1f); emit()
                            },
                    )
                }
        }

        Spacer(Modifier.height(8.dp))
        AdminText("#%06X".format(0xFFFFFF and Color.hsv(hue, sat, value).toArgb()), fontSize = 12.sp)
    }
}
