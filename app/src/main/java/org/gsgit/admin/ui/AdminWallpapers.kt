package org.gsgit.admin.ui

import org.gsgit.admin.R

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
