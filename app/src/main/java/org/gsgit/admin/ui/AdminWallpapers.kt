package org.gsgit.admin.ui

import androidx.compose.runtime.compositionLocalOf
import org.gsgit.admin.R
import org.gsgit.admin.data.GlassSettings

/** 11 обоев: роза по умолчанию + набор из архива пользователя. */
object AdminWallpapers {
    val items = listOf(
        R.drawable.admin_wallpaper,
        R.drawable.admin_wallpaper_01,
        R.drawable.admin_wallpaper_02,
        R.drawable.admin_wallpaper_03,
        R.drawable.admin_wallpaper_04,
        R.drawable.admin_wallpaper_05,
        R.drawable.admin_wallpaper_06,
        R.drawable.admin_wallpaper_07,
        R.drawable.admin_wallpaper_08,
        R.drawable.admin_wallpaper_09,
        R.drawable.admin_wallpaper_10,
    )

    fun resFor(index: Int): Int = items.getOrElse(index) { items.first() }
}

/** Текущие настройки стекла; поставляются из GlassSettingsStore в AdminApp. */
val LocalGlassSettings = compositionLocalOf { GlassSettings() }
