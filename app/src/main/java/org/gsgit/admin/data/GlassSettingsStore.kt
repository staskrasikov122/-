package org.gsgit.admin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Полный набор параметров жидкого стекла (по мотивам GlassPlaygroundContent
 * из каталога Kyant) плюс выбор обоев. Значения по умолчанию соответствуют
 * текущему виду интерфейса.
 */
@Immutable
data class GlassSettings(
    val wallpaper: Int = 0,                // 0..10 — индекс в AdminWallpapers
    // Панели
    val cardCornerRadius: Float = 32f,     // dp, 8..48
    val cardBlur: Float = 8f,              // dp, 0..32
    val cardSurfaceAlpha: Float = 0.35f,   // 0..0.8 — плотность заливки 121212
    val refractionHeight: Float = 24f,     // dp, 0..64 — высота линзы
    val refractionAmount: Float = 48f,     // dp, 0..96 — сила линзы
    val depthEffect: Boolean = true,
    val chromaticAberration: Boolean = false,
    val vibrancy: Boolean = true,
    val brightness: Float = 0f,            // -0.5..0.5
    val saturation: Float = 1.5f,          // 0..2
    // Кнопки, чипы, круглые действия
    val tintAlpha: Float = 0.6f,           // 0.2..1 — плотность цветного стекла
    val controlShadow: Float = 0.3f,       // 0..1 — внешняя тень
    val controlInnerShadow: Float = 0.3f,  // 0..1 — внутренняя тень
    val controlStroke: Float = 0.18f,      // 0..0.5 — белая окантовка
    // Нижний бар
    val barLensHeight: Float = 32f,        // dp, 0..64
    val barLensAmount: Float = 48f,        // dp, 0..96
    val barSurfaceAlpha: Float = 0.13f,    // 0..0.5 — тёмный тинт бара
    // Кромки экрана
    val edgeBlur: Float = 10f,             // dp, 0..24
)

/**
 * Синглтон-хранилище: состояние для Compose + сохранение в SharedPreferences.
 * Инициализируется в MainActivity до setContent.
 */
object GlassSettingsStore {
    private const val PREFS = "glass_settings"
    private var prefs: SharedPreferences? = null

    val state: MutableState<GlassSettings> = mutableStateOf(GlassSettings())

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val d = GlassSettings()
        state.value = GlassSettings(
            wallpaper = p.getInt("wallpaper", d.wallpaper),
            cardCornerRadius = p.getFloat("cardCornerRadius", d.cardCornerRadius),
            cardBlur = p.getFloat("cardBlur", d.cardBlur),
            cardSurfaceAlpha = p.getFloat("cardSurfaceAlpha", d.cardSurfaceAlpha),
            refractionHeight = p.getFloat("refractionHeight", d.refractionHeight),
            refractionAmount = p.getFloat("refractionAmount", d.refractionAmount),
            depthEffect = p.getBoolean("depthEffect", d.depthEffect),
            chromaticAberration = p.getBoolean("chromaticAberration", d.chromaticAberration),
            vibrancy = p.getBoolean("vibrancy", d.vibrancy),
            brightness = p.getFloat("brightness", d.brightness),
            saturation = p.getFloat("saturation", d.saturation),
            tintAlpha = p.getFloat("tintAlpha", d.tintAlpha),
            controlShadow = p.getFloat("controlShadow", d.controlShadow),
            controlInnerShadow = p.getFloat("controlInnerShadow", d.controlInnerShadow),
            controlStroke = p.getFloat("controlStroke", d.controlStroke),
            barLensHeight = p.getFloat("barLensHeight", d.barLensHeight),
            barLensAmount = p.getFloat("barLensAmount", d.barLensAmount),
            barSurfaceAlpha = p.getFloat("barSurfaceAlpha", d.barSurfaceAlpha),
            edgeBlur = p.getFloat("edgeBlur", d.edgeBlur),
        )
    }

    fun update(settings: GlassSettings) {
        state.value = settings
        prefs?.edit()?.apply {
            putInt("wallpaper", settings.wallpaper)
            putFloat("cardCornerRadius", settings.cardCornerRadius)
            putFloat("cardBlur", settings.cardBlur)
            putFloat("cardSurfaceAlpha", settings.cardSurfaceAlpha)
            putFloat("refractionHeight", settings.refractionHeight)
            putFloat("refractionAmount", settings.refractionAmount)
            putBoolean("depthEffect", settings.depthEffect)
            putBoolean("chromaticAberration", settings.chromaticAberration)
            putBoolean("vibrancy", settings.vibrancy)
            putFloat("brightness", settings.brightness)
            putFloat("saturation", settings.saturation)
            putFloat("tintAlpha", settings.tintAlpha)
            putFloat("controlShadow", settings.controlShadow)
            putFloat("controlInnerShadow", settings.controlInnerShadow)
            putFloat("controlStroke", settings.controlStroke)
            putFloat("barLensHeight", settings.barLensHeight)
            putFloat("barLensAmount", settings.barLensAmount)
            putFloat("barSurfaceAlpha", settings.barSurfaceAlpha)
            putFloat("edgeBlur", settings.edgeBlur)
            apply()
        }
    }

    fun reset() = update(GlassSettings(wallpaper = state.value.wallpaper))
}
