package org.gsgit.admin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val edgeBlur: Float = 10f,             // dp, 0..24 (legacy, см. edgeBlurTop/Bottom)
    // Блик (главный акцент «жидкого стекла»)
    val highlightWidth: Float = 1.5f,      // dp, 0..6   — толщина световой кромки
    val highlightBlur: Float = 2f,         // dp, 0..12  — размытие блика
    val highlightAlpha: Float = 1f,        // 0..1       — яркость блика
    // Контраст панелей
    val contrast: Float = 1f,              // 0.5..1.5
    // Цвет тонировки панелей
    val tintHue: Float = 0f,               // 0..360
    val tintChroma: Float = 0f,            // 0..0.6 — 0 = нейтрально-тёмный
    // Скрим обоев
    val wallpaperScrim: Float = 0f,        // 0..0.6 — тёмный слой над обоями
    // Геометрия теней контролов
    val controlShadowRadius: Float = 10f,  // dp, 0..24
    val controlInnerRadius: Float = 6f,    // dp, 0..16
    // Кромки раздельно
    val edgeBlurTop: Float = 10f,          // dp, 0..24
    val edgeBlurBottom: Float = 10f,       // dp, 0..24
    val edgeFadeHeight: Float = 32f,       // dp, 16..64 — высота градиентной полосы
)

/**
 * Синглтон-хранилище: состояние для Compose + сохранение в SharedPreferences.
 * Инициализируется в MainActivity до setContent.
 */
object GlassSettingsStore {
    private const val PREFS = "glass_settings"
    private var prefs: SharedPreferences? = null
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

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
            highlightWidth = p.getFloat("highlightWidth", d.highlightWidth),
            highlightBlur = p.getFloat("highlightBlur", d.highlightBlur),
            highlightAlpha = p.getFloat("highlightAlpha", d.highlightAlpha),
            contrast = p.getFloat("contrast", d.contrast),
            tintHue = p.getFloat("tintHue", d.tintHue),
            tintChroma = p.getFloat("tintChroma", d.tintChroma),
            wallpaperScrim = p.getFloat("wallpaperScrim", d.wallpaperScrim),
            controlShadowRadius = p.getFloat("controlShadowRadius", d.controlShadowRadius),
            controlInnerRadius = p.getFloat("controlInnerRadius", d.controlInnerRadius),
            edgeBlurTop = p.getFloat("edgeBlurTop", d.edgeBlurTop),
            edgeBlurBottom = p.getFloat("edgeBlurBottom", d.edgeBlurBottom),
            edgeFadeHeight = p.getFloat("edgeFadeHeight", d.edgeFadeHeight),
        )
    }

    fun update(settings: GlassSettings) {
        state.value = settings
        // Дебаунс: во время драга слайдера тикают десятки обновлений в секунду,
        // на диск пишем только когда значения устаканились.
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(400)
            persist(settings)
        }
    }

    private fun persist(settings: GlassSettings) {
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
            putFloat("highlightWidth", settings.highlightWidth)
            putFloat("highlightBlur", settings.highlightBlur)
            putFloat("highlightAlpha", settings.highlightAlpha)
            putFloat("contrast", settings.contrast)
            putFloat("tintHue", settings.tintHue)
            putFloat("tintChroma", settings.tintChroma)
            putFloat("wallpaperScrim", settings.wallpaperScrim)
            putFloat("controlShadowRadius", settings.controlShadowRadius)
            putFloat("controlInnerRadius", settings.controlInnerRadius)
            putFloat("edgeBlurTop", settings.edgeBlurTop)
            putFloat("edgeBlurBottom", settings.edgeBlurBottom)
            putFloat("edgeFadeHeight", settings.edgeFadeHeight)
            apply()
        }
    }

    fun reset() = update(GlassSettings(wallpaper = state.value.wallpaper))
}
