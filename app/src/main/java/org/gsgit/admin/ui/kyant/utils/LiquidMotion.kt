package org.gsgit.admin.ui.kyant.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * LiquidMotion — единый словарь пружин для всего приложения (из LiquidGlassKit).
 *
 *  - [snappy] — короткие отклики (пресс, тумблеры, чипы);
 *  - [gentle] — раскрытия/сворачивания и смена размера;
 *  - [bouncy] — редкие «радостные» акценты.
 */
object LiquidMotion {

    fun <T> snappy(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> gentle(): SpringSpec<T> = spring(
        dampingRatio = 0.90f,
        stiffness = 300f
    )

    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Дефолтные степени сжатия под тип элемента (для [liquidClickable]/pressScale). */
    const val PressCard = 0.95f    // крупные карточки/строки списка
    const val PressButton = 0.90f  // кнопки/пилюли/чипы
    const val PressIcon = 0.84f    // мелкие иконки-кнопки — заметнее всего
}
